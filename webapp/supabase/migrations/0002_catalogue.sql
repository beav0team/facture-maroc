-- FactureApp — 0002 : clients, articles, réglages de la boutique, logo
--
-- À exécuter après 0001. Idempotent, comme le précédent.
--
-- Deux catalogues par boutique, la même forme que sur Android :
--   clients  — le même enregistrement sert de client et de fournisseur, le
--              rôle les sépare (PartyRole dans InvoiceClient.kt) ;
--   articles — un produit et un service vivent dans la même liste ; `stock`
--              null dit « service », un entier dit « produit ».
--
-- Le logo va dans le bucket `logos`, sous `<shop_id>/…`. La lecture est
-- publique (un logo figure sur une facture destinée au client) ; l'écriture
-- est réservée aux membres de la boutique, vérifiés sur le premier segment du
-- chemin.

-- --------------------------------------------------------------------------
-- Réglages manquants sur shops
-- --------------------------------------------------------------------------

alter table public.shops
  add column if not exists banque              text,
  add column if not exists rib                 text,
  add column if not exists rib_titulaire       text,
  add column if not exists clause_retard       text,
  add column if not exists tva_non_applicable  boolean not null default false,
  add column if not exists prix_ttc            boolean not null default false,
  add column if not exists conditions_paiement text    not null default '30j';

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conrelid = 'public.shops'::regclass
      and conname = 'shops_conditions_paiement_check'
  ) then
    alter table public.shops
      add constraint shops_conditions_paiement_check
      check (conditions_paiement in ('cash', '30j', '60j', 'fin_de_mois', 'custom'));
  end if;
end;
$$;

-- --------------------------------------------------------------------------
-- Clients et fournisseurs
-- --------------------------------------------------------------------------

create table if not exists public.clients (
  id          uuid primary key default gen_random_uuid(),
  shop_id     uuid        not null references public.shops (id) on delete cascade,
  nom         text        not null check (length(btrim(nom)) between 1 and 160),
  adresse     text,
  ice         text,
  if_fiscal   text,
  rc          text,
  patente     text,
  cnss        text,
  telephone   text,
  email       text,
  role        text        not null default 'client' check (role in ('client', 'fournisseur')),
  exonere_tva boolean     not null default false,
  note        text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

create index if not exists clients_shop_role_idx
  on public.clients (shop_id, role, lower(nom));

-- Le nom identifie une partie sur Android (partyKey = rôle + nom). Deux
-- « Garage Atlas » dans la même boutique seraient la même fiche : on l'empêche
-- ici plutôt que de laisser deux cartes se disputer les mêmes documents.
create unique index if not exists clients_nom_unique
  on public.clients (shop_id, role, lower(btrim(nom)));

-- --------------------------------------------------------------------------
-- Articles et services
-- --------------------------------------------------------------------------

create table if not exists public.articles (
  id           uuid primary key default gen_random_uuid(),
  shop_id      uuid        not null references public.shops (id) on delete cascade,
  libelle      text        not null check (length(btrim(libelle)) between 1 and 200),
  prix         numeric(12, 2) not null default 0 check (prix >= 0),
  unite        text        not null default '',
  dossier      text        not null default '',
  prix_achat   numeric(12, 2) not null default 0 check (prix_achat >= 0),
  code_barre   text        not null default '',
  -- null = service : un service ne se compte pas et ne sort pas d'un stock.
  stock        integer,
  seuil_rea    integer check (seuil_rea is null or seuil_rea >= 0),
  attributs    jsonb       not null default '[]'::jsonb,
  paliers      jsonb       not null default '[]'::jsonb,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

create index if not exists articles_shop_dossier_idx
  on public.articles (shop_id, dossier, lower(libelle));

-- Un code-barres vide n'est pas un code-barres : l'unicité ne porte que sur
-- les codes réellement saisis, et seulement dans la boutique.
create unique index if not exists articles_code_barre_unique
  on public.articles (shop_id, code_barre)
  where code_barre <> '';

-- --------------------------------------------------------------------------
-- RLS
-- --------------------------------------------------------------------------

alter table public.clients  enable row level security;
alter table public.articles enable row level security;

do $$
declare
  v_table text;
begin
  foreach v_table in array array['clients', 'articles']
  loop
    execute format('drop policy if exists "membre lit %1$s" on public.%1$I', v_table);
    execute format(
      'create policy "membre lit %1$s" on public.%1$I for select to authenticated
         using (public.is_shop_member(shop_id))', v_table);

    execute format('drop policy if exists "membre cree %1$s" on public.%1$I', v_table);
    execute format(
      'create policy "membre cree %1$s" on public.%1$I for insert to authenticated
         with check (public.is_shop_member(shop_id))', v_table);

    execute format('drop policy if exists "membre modifie %1$s" on public.%1$I', v_table);
    execute format(
      'create policy "membre modifie %1$s" on public.%1$I for update to authenticated
         using (public.is_shop_member(shop_id))
         with check (public.is_shop_member(shop_id))', v_table);

    execute format('drop policy if exists "membre supprime %1$s" on public.%1$I', v_table);
    execute format(
      'create policy "membre supprime %1$s" on public.%1$I for delete to authenticated
         using (public.is_shop_member(shop_id))', v_table);

    execute format('revoke all on public.%I from anon', v_table);
    execute format('grant select, insert, update, delete on public.%I to authenticated', v_table);

    execute format('drop trigger if exists %1$s_touch_updated_at on public.%1$I', v_table);
    execute format(
      'create trigger %1$s_touch_updated_at before update on public.%1$I
         for each row execute function public.touch_updated_at()', v_table);
  end loop;
end;
$$;

-- --------------------------------------------------------------------------
-- Logo (Supabase Storage)
--
-- Sur un Postgres nu (test RLS local) le schéma storage n'existe pas : le bloc
-- ne fait rien plutôt que d'échouer.
-- --------------------------------------------------------------------------

do $$
begin
  if not exists (select 1 from pg_namespace where nspname = 'storage') then
    raise notice 'schéma storage absent : politiques du logo ignorées';
    return;
  end if;

  insert into storage.buckets (id, name, public)
  values ('logos', 'logos', true)
  on conflict (id) do nothing;

  execute 'drop policy if exists "logos lecture publique" on storage.objects';
  execute $sql$
    create policy "logos lecture publique"
      on storage.objects for select
      using (bucket_id = 'logos')
  $sql$;

  -- Le chemin doit commencer par un uuid de boutique, sinon le cast lèverait
  -- une erreur au lieu de refuser la ligne.
  execute 'drop policy if exists "logos ecriture membre" on storage.objects';
  execute $sql$
    create policy "logos ecriture membre"
      on storage.objects for insert to authenticated
      with check (
        bucket_id = 'logos'
        and name ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/'
        and public.is_shop_member((split_part(name, '/', 1))::uuid)
      )
  $sql$;

  execute 'drop policy if exists "logos remplacement membre" on storage.objects';
  execute $sql$
    create policy "logos remplacement membre"
      on storage.objects for update to authenticated
      using (
        bucket_id = 'logos'
        and name ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/'
        and public.is_shop_member((split_part(name, '/', 1))::uuid)
      )
      with check (
        bucket_id = 'logos'
        and name ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/'
        and public.is_shop_member((split_part(name, '/', 1))::uuid)
      )
  $sql$;

  execute 'drop policy if exists "logos suppression membre" on storage.objects';
  execute $sql$
    create policy "logos suppression membre"
      on storage.objects for delete to authenticated
      using (
        bucket_id = 'logos'
        and name ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/'
        and public.is_shop_member((split_part(name, '/', 1))::uuid)
      )
  $sql$;
end;
$$;
