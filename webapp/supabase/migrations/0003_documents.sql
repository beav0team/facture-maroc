-- FactureApp — 0003 : documents, paiements, archives, abonnements
--
-- À exécuter après 0001 et 0002. Idempotent.
--
-- Un document naît brouillon, avec ses lignes, puis devient émis par
-- `emettre_document()` : c'est à cet instant que le numéro est pris, que la
-- boutique est figée dans `shop_snapshot` et que le quota du plan gratuit est
-- vérifié. Aucune écriture directe n'est accordée sur `documents` : tout passe
-- par les fonctions, pour qu'un onglet ne puisse pas contourner la
-- numérotation ou le quota.

-- --------------------------------------------------------------------------
-- Réglages de numérotation et de rendu
-- --------------------------------------------------------------------------

alter table public.shops
  add column if not exists numerotation_annuelle boolean not null default false,
  add column if not exists design_defaut         text    not null default 'classique';

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.shops'::regclass
      and conname = 'shops_design_defaut_check'
  ) then
    alter table public.shops
      add constraint shops_design_defaut_check
      check (design_defaut in ('classique', 'facture_maroc'));
  end if;
end;
$$;

-- --------------------------------------------------------------------------
-- Tables
-- --------------------------------------------------------------------------

create table if not exists public.documents (
  id                   uuid primary key default gen_random_uuid(),
  shop_id              uuid        not null references public.shops (id) on delete cascade,
  type                 text        not null check (type in (
                         'facture', 'devis', 'bon_commande', 'ticket', 'avoir',
                         'proforma', 'acompte', 'regul', 'simplifiee', 'export')),
  statut               text        not null default 'brouillon' check (statut in ('brouillon', 'emis')),
  numero               text        not null default '',
  date_emission        text        not null default '',
  date_validite        text,
  echeance             text,
  client_id            uuid        references public.clients (id) on delete set null,
  -- Ce que le client était au moment de la saisie : une fiche modifiée ou
  -- supprimée ensuite ne réécrit pas le document.
  client_snapshot      jsonb       not null default '{}'::jsonb,
  reference            text        not null default '',
  incoterm             text        not null default '',
  tva_non_applicable   boolean     not null default false,
  -- Le taux par défaut du document ; les lignes exonérées portent tva_taux null.
  taux_tva             numeric(5, 2) not null default 20,
  arrondi_dirham       boolean     not null default false,
  conditions_paiement  text        not null default '30j',
  clause_retard        text        not null default '',
  note_client          text        not null default '',
  note_remerciement    text        not null default '',
  surcharge_libelle    text        not null default '',
  surcharge_montant    numeric(12, 2) not null default 0,
  surcharge_pourcent   numeric(5, 2)  not null default 0,
  remise_pourcent      integer     not null default 0 check (remise_pourcent between 0 and 100),
  remise_montant       numeric(12, 2) not null default 0,
  acompte_montant      numeric(12, 2) not null default 0,
  acompte_reference    text        not null default '',
  design               text        not null default 'classique' check (design in ('classique', 'facture_maroc')),
  total_ht             numeric(12, 2) not null default 0,
  remise               numeric(12, 2) not null default 0,
  surcharge            numeric(12, 2) not null default 0,
  tva                  numeric(12, 2) not null default 0,
  total_ttc            numeric(12, 2) not null default 0,
  net_a_payer          numeric(12, 2) not null default 0,
  shop_snapshot        jsonb,
  emis_le              timestamptz,
  created_by           uuid        references auth.users (id) on delete set null,
  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now()
);

create index if not exists documents_shop_date_idx on public.documents (shop_id, emis_le desc nulls last);
create index if not exists documents_shop_client_idx on public.documents (shop_id, client_id);
create unique index if not exists documents_numero_unique
  on public.documents (shop_id, numero) where numero <> '';

create table if not exists public.document_lines (
  id              uuid primary key default gen_random_uuid(),
  document_id     uuid        not null references public.documents (id) on delete cascade,
  ordre           integer     not null default 0,
  article_id      uuid        references public.articles (id) on delete set null,
  libelle         text        not null check (length(btrim(libelle)) between 1 and 300),
  quantite        numeric(12, 3) not null default 1,
  unite           text        not null default '',
  prix_unitaire   numeric(12, 2) not null default 0,
  remise_pourcent integer     not null default 0 check (remise_pourcent between 0 and 100),
  remise_montant  numeric(12, 2) not null default 0,
  exoneree        boolean     not null default false,
  tva_taux        numeric(5, 2),
  total_ht        numeric(12, 2) not null default 0,
  attributs       jsonb       not null default '[]'::jsonb
);

create index if not exists document_lines_document_idx on public.document_lines (document_id, ordre);

create table if not exists public.payments (
  id             uuid primary key default gen_random_uuid(),
  document_id    uuid        not null references public.documents (id) on delete cascade,
  montant        numeric(12, 2) not null check (montant > 0),
  date_paiement  text        not null default '',
  moyen          text        not null default 'cash',
  reference      text        not null default '',
  created_by     uuid        references auth.users (id) on delete set null,
  created_at     timestamptz not null default now()
);

create index if not exists payments_document_idx on public.payments (document_id, created_at);

create table if not exists public.archives (
  document_id   uuid primary key references public.documents (id) on delete cascade,
  storage_path  text        not null,
  sha256        text        not null,
  taille        bigint      not null default 0,
  created_at    timestamptz not null default now()
);

create table if not exists public.subscriptions (
  shop_id     uuid primary key references public.shops (id) on delete cascade,
  plan        text        not null default 'gratuit' check (plan in ('gratuit', 'pro')),
  paid_until  date,
  source      text        not null default 'manuel',
  note        text        not null default '',
  updated_at  timestamptz not null default now()
);

create table if not exists public.platform_admins (
  user_id    uuid primary key references auth.users (id) on delete cascade,
  created_at timestamptz not null default now()
);

-- Le compteur d'une série. Un enregistrement par boutique et par type ; le
-- millésime est stocké pour savoir s'il faut repartir de 1 en janvier.
create table if not exists public.numerotation (
  shop_id          uuid    not null references public.shops (id) on delete cascade,
  type             text    not null,
  annee            integer not null,
  prochaine_valeur integer not null default 1 check (prochaine_valeur >= 1),
  primary key (shop_id, type)
);

-- --------------------------------------------------------------------------
-- Helpers
-- --------------------------------------------------------------------------

create or replace function public.est_admin_plateforme()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.platform_admins a where a.user_id = auth.uid()
  );
$$;

revoke all on function public.est_admin_plateforme() from public, anon;
grant execute on function public.est_admin_plateforme() to authenticated;

create or replace function public.boutique_est_pro(p_shop uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.subscriptions s
    where s.shop_id = p_shop
      and s.plan = 'pro'
      and (s.paid_until is null or s.paid_until >= current_date)
  );
$$;

revoke all on function public.boutique_est_pro(uuid) from public, anon;
grant execute on function public.boutique_est_pro(uuid) to authenticated;

/** Le préfixe d'un type, celui de la boutique s'il est valide, sinon le défaut. */
create or replace function public.prefixe_document(p_shop uuid, p_type text)
returns text
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_prefixe text;
  v_defaut  text;
begin
  select nullif(btrim(coalesce(prefixes ->> p_type, '')), '')
    into v_prefixe
    from public.shops where id = p_shop;

  v_defaut := case p_type
    when 'facture' then 'F'
    when 'devis' then 'D'
    when 'bon_commande' then 'BC'
    when 'ticket' then 'T'
    when 'avoir' then 'A'
    when 'proforma' then 'PF'
    when 'acompte' then 'AC'
    when 'regul' then 'REG'
    when 'simplifiee' then 'FS'
    when 'export' then 'E'
    else 'F'
  end;

  if v_prefixe is null or v_prefixe !~ '^[[:alnum:]_-]+$' then
    return v_defaut;
  end if;
  return v_prefixe;
end;
$$;

create or replace function public.formater_numero(p_prefixe text, p_date date, p_valeur integer)
returns text
language sql
immutable
as $$
  select case
    when btrim(regexp_replace(coalesce(p_prefixe, ''), '-+$', '')) = ''
      then to_char(p_date, 'YYYYMMDD') || '-' || lpad(greatest(p_valeur, 1)::text, 4, '0')
    else btrim(regexp_replace(p_prefixe, '-+$', '')) || '-'
      || to_char(p_date, 'YYYYMMDD') || '-' || lpad(greatest(p_valeur, 1)::text, 4, '0')
  end;
$$;

/** Le prochain numéro, sans le consommer : ce que l'éditeur affiche. */
create or replace function public.apercu_numero(p_shop uuid, p_type text)
returns text
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_annee   integer := extract(year from current_date)::integer;
  v_reset   boolean;
  v_ligne   public.numerotation;
  v_valeur  integer;
begin
  if not public.is_shop_member(p_shop) then
    raise exception 'boutique inconnue' using errcode = '42501';
  end if;

  select numerotation_annuelle into v_reset from public.shops where id = p_shop;
  select * into v_ligne from public.numerotation where shop_id = p_shop and type = p_type;

  if not found then
    v_valeur := 1;
  elsif v_reset and v_ligne.annee <> v_annee then
    v_valeur := 1;
  else
    v_valeur := greatest(v_ligne.prochaine_valeur, 1);
  end if;

  return public.formater_numero(public.prefixe_document(p_shop, p_type), current_date, v_valeur);
end;
$$;

/** Prend le numéro : réservé à `emettre_document`, jamais appelé par le client. */
create or replace function public.consommer_numero(p_shop uuid, p_type text)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_annee  integer := extract(year from current_date)::integer;
  v_reset  boolean;
  v_ligne  public.numerotation;
  v_valeur integer;
begin
  select numerotation_annuelle into v_reset from public.shops where id = p_shop;

  insert into public.numerotation (shop_id, type, annee, prochaine_valeur)
  values (p_shop, p_type, v_annee, 1)
  on conflict (shop_id, type) do nothing;

  select * into v_ligne
  from public.numerotation
  where shop_id = p_shop and type = p_type
  for update;

  if v_reset and v_ligne.annee <> v_annee then
    v_valeur := 1;
  else
    v_valeur := greatest(v_ligne.prochaine_valeur, 1);
  end if;

  update public.numerotation
  set annee = v_annee, prochaine_valeur = v_valeur + 1
  where shop_id = p_shop and type = p_type;

  return public.formater_numero(public.prefixe_document(p_shop, p_type), current_date, v_valeur);
end;
$$;

revoke all on function public.consommer_numero(uuid, text) from public, anon, authenticated;

-- --------------------------------------------------------------------------
-- Quota du plan gratuit
-- --------------------------------------------------------------------------

create or replace function public.quota_boutique(p_shop uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_pro     boolean;
  v_utilise integer;
  v_fin     date;
begin
  if not public.is_shop_member(p_shop) then
    raise exception 'boutique inconnue' using errcode = '42501';
  end if;

  v_pro := public.boutique_est_pro(p_shop);

  select count(*) into v_utilise
  from public.documents
  where shop_id = p_shop
    and type = 'facture'
    and statut = 'emis'
    and emis_le >= date_trunc('month', now());

  select paid_until into v_fin from public.subscriptions where shop_id = p_shop;

  return jsonb_build_object(
    'plan', case when v_pro then 'pro' else 'gratuit' end,
    'paid_until', v_fin,
    'utilise', v_utilise,
    'limite', case when v_pro then null else 5 end
  );
end;
$$;

revoke all on function public.quota_boutique(uuid) from public, anon;
grant execute on function public.quota_boutique(uuid) to authenticated;

-- --------------------------------------------------------------------------
-- Enregistrement d'un brouillon
-- --------------------------------------------------------------------------

create or replace function public.enregistrer_brouillon(
  p_document uuid,
  p_entete   jsonb,
  p_lignes   jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_shop   uuid := (p_entete ->> 'shop_id')::uuid;
  v_id     uuid;
  v_statut text;
begin
  if auth.uid() is null then
    raise exception 'authentification requise' using errcode = '28000';
  end if;
  if v_shop is null or not public.is_shop_member(v_shop) then
    raise exception 'boutique inconnue' using errcode = '42501';
  end if;

  if p_document is null then
    insert into public.documents (
      shop_id, type, date_emission, date_validite, echeance, client_id,
      client_snapshot, reference, incoterm, tva_non_applicable, taux_tva, arrondi_dirham,
      conditions_paiement, clause_retard, note_client, note_remerciement,
      surcharge_libelle, surcharge_montant, surcharge_pourcent,
      remise_pourcent, remise_montant, acompte_montant, acompte_reference,
      design, total_ht, remise, surcharge, tva, total_ttc, net_a_payer, created_by
    ) values (
      v_shop,
      coalesce(p_entete ->> 'type', 'facture'),
      coalesce(p_entete ->> 'date_emission', ''),
      nullif(p_entete ->> 'date_validite', ''),
      nullif(p_entete ->> 'echeance', ''),
      nullif(p_entete ->> 'client_id', '')::uuid,
      coalesce(p_entete -> 'client_snapshot', '{}'::jsonb),
      coalesce(p_entete ->> 'reference', ''),
      coalesce(p_entete ->> 'incoterm', ''),
      coalesce((p_entete ->> 'tva_non_applicable')::boolean, false),
      coalesce((p_entete ->> 'taux_tva')::numeric, 20),
      coalesce((p_entete ->> 'arrondi_dirham')::boolean, false),
      coalesce(p_entete ->> 'conditions_paiement', '30j'),
      coalesce(p_entete ->> 'clause_retard', ''),
      coalesce(p_entete ->> 'note_client', ''),
      coalesce(p_entete ->> 'note_remerciement', ''),
      coalesce(p_entete ->> 'surcharge_libelle', ''),
      coalesce((p_entete ->> 'surcharge_montant')::numeric, 0),
      coalesce((p_entete ->> 'surcharge_pourcent')::numeric, 0),
      coalesce((p_entete ->> 'remise_pourcent')::integer, 0),
      coalesce((p_entete ->> 'remise_montant')::numeric, 0),
      coalesce((p_entete ->> 'acompte_montant')::numeric, 0),
      coalesce(p_entete ->> 'acompte_reference', ''),
      coalesce(p_entete ->> 'design', 'classique'),
      coalesce((p_entete ->> 'total_ht')::numeric, 0),
      coalesce((p_entete ->> 'remise')::numeric, 0),
      coalesce((p_entete ->> 'surcharge')::numeric, 0),
      coalesce((p_entete ->> 'tva')::numeric, 0),
      coalesce((p_entete ->> 'total_ttc')::numeric, 0),
      coalesce((p_entete ->> 'net_a_payer')::numeric, 0),
      auth.uid()
    )
    returning id into v_id;
  else
    select statut into v_statut
    from public.documents
    where id = p_document and shop_id = v_shop
    for update;

    if not found then
      raise exception 'document inconnu' using errcode = '42501';
    end if;
    if v_statut <> 'brouillon' then
      raise exception 'un document émis ne se modifie plus' using errcode = '22023';
    end if;

    update public.documents set
      type                = coalesce(p_entete ->> 'type', type),
      date_emission       = coalesce(p_entete ->> 'date_emission', date_emission),
      date_validite       = nullif(p_entete ->> 'date_validite', ''),
      echeance            = nullif(p_entete ->> 'echeance', ''),
      client_id           = nullif(p_entete ->> 'client_id', '')::uuid,
      client_snapshot     = coalesce(p_entete -> 'client_snapshot', client_snapshot),
      reference           = coalesce(p_entete ->> 'reference', reference),
      incoterm            = coalesce(p_entete ->> 'incoterm', incoterm),
      tva_non_applicable  = coalesce((p_entete ->> 'tva_non_applicable')::boolean, tva_non_applicable),
      taux_tva            = coalesce((p_entete ->> 'taux_tva')::numeric, taux_tva),
      arrondi_dirham      = coalesce((p_entete ->> 'arrondi_dirham')::boolean, arrondi_dirham),
      conditions_paiement = coalesce(p_entete ->> 'conditions_paiement', conditions_paiement),
      clause_retard       = coalesce(p_entete ->> 'clause_retard', clause_retard),
      note_client         = coalesce(p_entete ->> 'note_client', note_client),
      note_remerciement   = coalesce(p_entete ->> 'note_remerciement', note_remerciement),
      surcharge_libelle   = coalesce(p_entete ->> 'surcharge_libelle', surcharge_libelle),
      surcharge_montant   = coalesce((p_entete ->> 'surcharge_montant')::numeric, surcharge_montant),
      surcharge_pourcent  = coalesce((p_entete ->> 'surcharge_pourcent')::numeric, surcharge_pourcent),
      remise_pourcent     = coalesce((p_entete ->> 'remise_pourcent')::integer, remise_pourcent),
      remise_montant      = coalesce((p_entete ->> 'remise_montant')::numeric, remise_montant),
      acompte_montant     = coalesce((p_entete ->> 'acompte_montant')::numeric, acompte_montant),
      acompte_reference   = coalesce(p_entete ->> 'acompte_reference', acompte_reference),
      design              = coalesce(p_entete ->> 'design', design),
      total_ht            = coalesce((p_entete ->> 'total_ht')::numeric, total_ht),
      remise              = coalesce((p_entete ->> 'remise')::numeric, remise),
      surcharge           = coalesce((p_entete ->> 'surcharge')::numeric, surcharge),
      tva                 = coalesce((p_entete ->> 'tva')::numeric, tva),
      total_ttc           = coalesce((p_entete ->> 'total_ttc')::numeric, total_ttc),
      net_a_payer         = coalesce((p_entete ->> 'net_a_payer')::numeric, net_a_payer)
    where id = p_document;

    v_id := p_document;
  end if;

  delete from public.document_lines where document_id = v_id;

  insert into public.document_lines (
    document_id, ordre, article_id, libelle, quantite, unite, prix_unitaire,
    remise_pourcent, remise_montant, exoneree, tva_taux, total_ht, attributs
  )
  select
    v_id,
    coalesce((ligne ->> 'ordre')::integer, 0),
    nullif(ligne ->> 'article_id', '')::uuid,
    btrim(ligne ->> 'libelle'),
    coalesce((ligne ->> 'quantite')::numeric, 1),
    coalesce(ligne ->> 'unite', ''),
    coalesce((ligne ->> 'prix_unitaire')::numeric, 0),
    coalesce((ligne ->> 'remise_pourcent')::integer, 0),
    coalesce((ligne ->> 'remise_montant')::numeric, 0),
    coalesce((ligne ->> 'exoneree')::boolean, false),
    nullif(ligne ->> 'tva_taux', '')::numeric,
    coalesce((ligne ->> 'total_ht')::numeric, 0),
    coalesce(ligne -> 'attributs', '[]'::jsonb)
  from jsonb_array_elements(coalesce(p_lignes, '[]'::jsonb)) as t(ligne);

  return v_id;
end;
$$;

revoke all on function public.enregistrer_brouillon(uuid, jsonb, jsonb) from public, anon;
grant execute on function public.enregistrer_brouillon(uuid, jsonb, jsonb) to authenticated;

-- --------------------------------------------------------------------------
-- Émission : numéro, snapshot, quota
-- --------------------------------------------------------------------------

create or replace function public.emettre_document(p_document uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_doc      public.documents;
  v_numero   text;
  v_snapshot jsonb;
  v_utilise  integer;
begin
  if auth.uid() is null then
    raise exception 'authentification requise' using errcode = '28000';
  end if;

  select * into v_doc from public.documents where id = p_document for update;
  if not found or not public.is_shop_member(v_doc.shop_id) then
    raise exception 'document inconnu' using errcode = '42501';
  end if;
  if v_doc.statut <> 'brouillon' then
    raise exception 'document déjà émis' using errcode = '22023';
  end if;

  if v_doc.type = 'facture' and not public.boutique_est_pro(v_doc.shop_id) then
    select count(*) into v_utilise
    from public.documents
    where shop_id = v_doc.shop_id
      and type = 'facture'
      and statut = 'emis'
      and emis_le >= date_trunc('month', now());

    if v_utilise >= 5 then
      raise exception 'Quota de 5 factures atteint pour ce mois.'
        using errcode = 'P0001', hint = 'Passez au plan Pro pour continuer.';
    end if;
  end if;

  v_numero := public.consommer_numero(v_doc.shop_id, v_doc.type);

  select jsonb_build_object(
    'nom', nom, 'adresse', adresse, 'telephone', telephone, 'email', email,
    'ice', ice, 'if_fiscal', if_fiscal, 'rc', rc, 'patente', patente, 'cnss', cnss,
    'banque', banque, 'rib', rib, 'rib_titulaire', rib_titulaire,
    'tva_defaut', tva_defaut, 'tva_non_applicable', tva_non_applicable,
    'prix_ttc', prix_ttc, 'logo_path', logo_path
  )
  into v_snapshot
  from public.shops where id = v_doc.shop_id;

  update public.documents
  set statut = 'emis',
      numero = v_numero,
      shop_snapshot = v_snapshot,
      date_emission = coalesce(nullif(date_emission, ''), to_char(current_date, 'DD/MM/YYYY')),
      emis_le = now()
  where id = p_document
  returning * into v_doc;

  return jsonb_build_object(
    'id', v_doc.id,
    'numero', v_doc.numero,
    'date_emission', v_doc.date_emission,
    'emis_le', v_doc.emis_le
  );
end;
$$;

revoke all on function public.emettre_document(uuid) from public, anon;
grant execute on function public.emettre_document(uuid) to authenticated;

-- --------------------------------------------------------------------------
-- Avoir depuis une facture
-- --------------------------------------------------------------------------

create or replace function public.creer_avoir(p_document uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_src  public.documents;
  v_id   uuid;
  v_shop uuid;
begin
  select * into v_src from public.documents where id = p_document;
  if not found or not public.is_shop_member(v_src.shop_id) then
    raise exception 'document inconnu' using errcode = '42501';
  end if;
  if v_src.statut <> 'emis' or v_src.type = 'avoir' then
    raise exception 'seul un document émis peut être crédité' using errcode = '22023';
  end if;

  v_shop := v_src.shop_id;

  insert into public.documents (
    shop_id, type, date_emission, client_id, client_snapshot, reference,
    tva_non_applicable, taux_tva, arrondi_dirham, conditions_paiement, design,
    surcharge_libelle, surcharge_montant, surcharge_pourcent,
    remise_pourcent, remise_montant, total_ht, remise, surcharge, tva,
    total_ttc, net_a_payer, created_by
  ) values (
    v_shop, 'avoir', to_char(current_date, 'DD/MM/YYYY'), v_src.client_id,
    v_src.client_snapshot, v_src.numero, v_src.tva_non_applicable,
    v_src.taux_tva, v_src.arrondi_dirham, v_src.conditions_paiement, v_src.design,
    v_src.surcharge_libelle, v_src.surcharge_montant, v_src.surcharge_pourcent,
    v_src.remise_pourcent, v_src.remise_montant, v_src.total_ht, v_src.remise,
    v_src.surcharge, v_src.tva, v_src.total_ttc, v_src.net_a_payer, auth.uid()
  )
  returning id into v_id;

  insert into public.document_lines (
    document_id, ordre, article_id, libelle, quantite, unite, prix_unitaire,
    remise_pourcent, remise_montant, exoneree, tva_taux, total_ht, attributs
  )
  select
    v_id, ordre, article_id, libelle, quantite, unite, prix_unitaire,
    remise_pourcent, remise_montant, exoneree, tva_taux, total_ht, attributs
  from public.document_lines where document_id = p_document;

  return v_id;
end;
$$;

revoke all on function public.creer_avoir(uuid) from public, anon;
grant execute on function public.creer_avoir(uuid) to authenticated;

-- --------------------------------------------------------------------------
-- RLS
-- --------------------------------------------------------------------------

alter table public.documents      enable row level security;
alter table public.document_lines enable row level security;
alter table public.payments       enable row level security;
alter table public.archives       enable row level security;
alter table public.subscriptions  enable row level security;
alter table public.numerotation   enable row level security;
alter table public.platform_admins enable row level security;

drop policy if exists "membre lit documents" on public.documents;
create policy "membre lit documents" on public.documents
  for select to authenticated using (public.is_shop_member(shop_id));

drop policy if exists "membre supprime brouillon" on public.documents;
create policy "membre supprime brouillon" on public.documents
  for delete to authenticated
  using (public.is_shop_member(shop_id) and statut = 'brouillon');

drop policy if exists "membre lit lignes" on public.document_lines;
create policy "membre lit lignes" on public.document_lines
  for select to authenticated
  using (exists (
    select 1 from public.documents d
    where d.id = document_id and public.is_shop_member(d.shop_id)
  ));

drop policy if exists "membre voit paiements" on public.payments;
create policy "membre voit paiements" on public.payments
  for select to authenticated
  using (exists (
    select 1 from public.documents d
    where d.id = document_id and public.is_shop_member(d.shop_id)
  ));

drop policy if exists "membre encaisse" on public.payments;
create policy "membre encaisse" on public.payments
  for insert to authenticated
  with check (exists (
    select 1 from public.documents d
    where d.id = document_id and public.is_shop_member(d.shop_id)
  ));

drop policy if exists "membre annule encaissement" on public.payments;
create policy "membre annule encaissement" on public.payments
  for delete to authenticated
  using (exists (
    select 1 from public.documents d
    where d.id = document_id and public.is_shop_member(d.shop_id)
  ));

drop policy if exists "membre lit archive" on public.archives;
create policy "membre lit archive" on public.archives
  for select to authenticated
  using (exists (
    select 1 from public.documents d
    where d.id = document_id and public.is_shop_member(d.shop_id)
  ));

drop policy if exists "membre depose archive" on public.archives;
create policy "membre depose archive" on public.archives
  for insert to authenticated
  with check (exists (
    select 1 from public.documents d
    where d.id = document_id and d.statut = 'emis'
      and public.is_shop_member(d.shop_id)
  ));

drop policy if exists "membre met a jour archive" on public.archives;
create policy "membre met a jour archive" on public.archives
  for update to authenticated
  using (exists (
    select 1 from public.documents d
    where d.id = document_id and public.is_shop_member(d.shop_id)
  ))
  with check (exists (
    select 1 from public.documents d
    where d.id = document_id and public.is_shop_member(d.shop_id)
  ));

drop policy if exists "membre lit abonnement" on public.subscriptions;
create policy "membre lit abonnement" on public.subscriptions
  for select to authenticated using (public.is_shop_member(shop_id));

drop policy if exists "admin lit abonnements" on public.subscriptions;
create policy "admin lit abonnements" on public.subscriptions
  for select to authenticated using (public.est_admin_plateforme());

drop policy if exists "admin ecrit abonnements" on public.subscriptions;
create policy "admin ecrit abonnements" on public.subscriptions
  for all to authenticated
  using (public.est_admin_plateforme())
  with check (public.est_admin_plateforme());

drop policy if exists "membre lit numerotation" on public.numerotation;
create policy "membre lit numerotation" on public.numerotation
  for select to authenticated using (public.is_shop_member(shop_id));

drop policy if exists "admin lit boutiques" on public.shops;
create policy "admin lit boutiques" on public.shops
  for select to authenticated using (public.est_admin_plateforme());

-- --------------------------------------------------------------------------
-- Privilèges
-- --------------------------------------------------------------------------

revoke all on public.documents       from anon;
revoke all on public.document_lines  from anon;
revoke all on public.payments        from anon;
revoke all on public.archives        from anon;
revoke all on public.subscriptions   from anon;
revoke all on public.numerotation    from anon;

grant select, delete on public.documents      to authenticated;
grant select          on public.document_lines to authenticated;
grant select, insert, delete on public.payments to authenticated;
grant select, insert, update on public.archives to authenticated;
grant select          on public.subscriptions  to authenticated;
grant insert, update, delete on public.subscriptions to authenticated;
grant select          on public.numerotation   to authenticated;

-- --------------------------------------------------------------------------
-- Triggers
-- --------------------------------------------------------------------------

drop trigger if exists documents_touch_updated_at on public.documents;
create trigger documents_touch_updated_at
  before update on public.documents
  for each row execute function public.touch_updated_at();

drop trigger if exists subscriptions_touch_updated_at on public.subscriptions;
create trigger subscriptions_touch_updated_at
  before update on public.subscriptions
  for each row execute function public.touch_updated_at();

-- --------------------------------------------------------------------------
-- Bucket des PDF archivés (privé : seulement les membres de la boutique)
-- --------------------------------------------------------------------------

do $$
begin
  if not exists (select 1 from pg_namespace where nspname = 'storage') then
    raise notice 'schéma storage absent : politiques des archives ignorées';
    return;
  end if;

  insert into storage.buckets (id, name, public)
  values ('archives', 'archives', false)
  on conflict (id) do nothing;

  execute 'drop policy if exists "archives lecture membre" on storage.objects';
  execute $sql$
    create policy "archives lecture membre"
      on storage.objects for select to authenticated
      using (
        bucket_id = 'archives'
        and name ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/'
        and public.is_shop_member((split_part(name, '/', 1))::uuid)
      )
  $sql$;

  execute 'drop policy if exists "archives ecriture membre" on storage.objects';
  execute $sql$
    create policy "archives ecriture membre"
      on storage.objects for insert to authenticated
      with check (
        bucket_id = 'archives'
        and name ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/'
        and public.is_shop_member((split_part(name, '/', 1))::uuid)
      )
  $sql$;

  execute 'drop policy if exists "archives remplacement membre" on storage.objects';
  execute $sql$
    create policy "archives remplacement membre"
      on storage.objects for update to authenticated
      using (
        bucket_id = 'archives'
        and name ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/'
        and public.is_shop_member((split_part(name, '/', 1))::uuid)
      )
      with check (
        bucket_id = 'archives'
        and name ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/'
        and public.is_shop_member((split_part(name, '/', 1))::uuid)
      )
  $sql$;

  execute 'drop policy if exists "archives suppression membre" on storage.objects';
  execute $sql$
    create policy "archives suppression membre"
      on storage.objects for delete to authenticated
      using (
        bucket_id = 'archives'
        and name ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/'
        and public.is_shop_member((split_part(name, '/', 1))::uuid)
      )
  $sql$;
end;
$$;
