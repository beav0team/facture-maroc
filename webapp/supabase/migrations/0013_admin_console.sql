-- FactureApp — 0013 : la console d'administration devient un vrai outil
--
-- La 0005 savait lire les boutiques, les comptes et les chiffres, activer un
-- plan et nommer un administrateur. Un administrateur y cherchait en vain la
-- fiche d'une boutique, ses membres, ses documents, ses PDF, une note interne
-- datée, un dépassement de quota, une suspension, une suppression, et surtout
-- la trace de ce qu'il avait fait lui-même la veille.
--
-- Cette migration ajoute tout cela sans changer la règle de la 0005 : l'écran
-- n'interroge jamais une table. Chaque question a sa fonction `security
-- definer` gardée par `est_admin_plateforme()`, chaque écriture journalise son
-- auteur. Les nouvelles tables n'ont aucune politique : ni `anon` ni
-- `authenticated` ne les lisent, même en direct.
--
-- Ce qui reste impossible ici : supprimer une identité `auth.users` (c'est
-- l'API d'administration GoTrue, pas le SQL) et envoyer un e-mail de
-- réinitialisation. Supabase Studio le fait en deux clics ; la console le dit
-- au lieu de faire semblant.

-- --------------------------------------------------------------------------
-- Tables
-- --------------------------------------------------------------------------

-- Qui a fait quoi. Écrit uniquement par les fonctions ci-dessous : une table
-- de journal que l'administrateur pourrait modifier ne prouverait rien.
create table if not exists public.admin_actions (
  id         uuid        primary key default gen_random_uuid(),
  acteur     uuid        references auth.users (id) on delete set null,
  action     text        not null check (length(btrim(action)) between 1 and 60),
  cible_type text        not null default 'boutique' check (cible_type in ('boutique', 'compte', 'plateforme')),
  cible_id   uuid,
  cible_nom  text        not null default '',
  details    jsonb       not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists admin_actions_date_idx  on public.admin_actions (created_at desc);
create index if not exists admin_actions_cible_idx on public.admin_actions (cible_id, created_at desc);

-- Une suspension vit hors de `shops` : la politique « membre met a jour sa
-- boutique » laisserait le propriétaire effacer sa propre suspension.
create table if not exists public.suspensions (
  shop_id uuid        primary key references public.shops (id) on delete cascade,
  raison  text        not null check (length(btrim(raison)) between 1 and 300),
  depuis  timestamptz not null default now(),
  par     uuid        references auth.users (id) on delete set null
);

-- Notes internes datées. `subscriptions.note` est écrasée à chaque changement
-- de plan ; une note de suivi ne doit pas disparaître avec elle.
create table if not exists public.notes_admin (
  id         uuid        primary key default gen_random_uuid(),
  shop_id    uuid        not null references public.shops (id) on delete cascade,
  note       text        not null check (length(btrim(note)) between 1 and 1000),
  par        uuid        references auth.users (id) on delete set null,
  par_email  text        not null default '',
  created_at timestamptz not null default now()
);

create index if not exists notes_admin_shop_idx on public.notes_admin (shop_id, created_at desc);

-- Documents comptables offerts au-delà des cinq du plan gratuit, sans donner
-- Pro (qui ouvrirait en plus les modèles et l'usage illimité).
create table if not exists public.quota_bonus (
  shop_id       uuid        primary key references public.shops (id) on delete cascade,
  documents_sup integer     not null default 0 check (documents_sup between 0 and 500),
  raison        text        not null default '',
  modifie_le    timestamptz not null default now()
);

alter table public.admin_actions enable row level security;
alter table public.suspensions   enable row level security;
alter table public.notes_admin   enable row level security;
alter table public.quota_bonus   enable row level security;

-- Aucune politique : les droits directs sont retirés aussi, pour qu'un futur
-- `grant` oublié ne devienne pas une fuite de journal ou de notes privées.
revoke all on public.admin_actions from anon, authenticated;
revoke all on public.suspensions   from anon, authenticated;
revoke all on public.notes_admin   from anon, authenticated;
revoke all on public.quota_bonus   from anon, authenticated;

-- --------------------------------------------------------------------------
-- Petits outils
-- --------------------------------------------------------------------------

-- Le journal. Pas de garde à l'intérieur : seules les fonctions gardées
-- l'appellent, et une exception ici annulerait l'écriture qu'elle raconte.
create or replace function public.admin_journaliser(
  p_action     text,
  p_cible_type text,
  p_cible_id   uuid,
  p_cible_nom  text,
  p_details    jsonb default '{}'::jsonb
)
returns void
language sql
security definer
set search_path = public
as $$
  insert into public.admin_actions (acteur, action, cible_type, cible_id, cible_nom, details)
  values (auth.uid(), p_action, p_cible_type, p_cible_id, coalesce(p_cible_nom, ''), coalesce(p_details, '{}'::jsonb));
$$;

revoke all on function public.admin_journaliser(text, text, uuid, text, jsonb) from public, anon, authenticated;

-- La limite du plan gratuit, bonus compris. Une seule définition, lue par le
-- quota affiché et par l'émission.
create or replace function public.quota_limite(p_shop uuid)
returns integer
language sql
stable
security definer
set search_path = public
as $$
  select 5 + coalesce(
    (select q.documents_sup from public.quota_bonus q where q.shop_id = p_shop),
    0
  )
$$;

revoke all on function public.quota_limite(uuid) from public, anon;
grant execute on function public.quota_limite(uuid) to authenticated;

-- --------------------------------------------------------------------------
-- Lectures
-- --------------------------------------------------------------------------

-- La liste change de colonnes (suspension, membres, dernière activité,
-- identité) : `returns table` interdit le `create or replace`.
drop function if exists public.admin_boutiques();

create function public.admin_boutiques()
returns table (
  id                 uuid,
  nom                text,
  cree_le            timestamptz,
  proprietaire       text,
  proprietaire_id    uuid,
  plan               text,
  jusqu_au           date,
  note               text,
  documents          bigint,
  clients            bigint,
  articles           bigint,
  pro                boolean,
  suspendue          boolean,
  membres            integer,
  derniere_activite  timestamptz,
  ice                text,
  if_fiscal          text
)
language sql
stable
security definer
set search_path = public
as $$
  select
    s.id,
    s.nom,
    s.created_at,
    u.email::text,
    m.user_id,
    coalesce(sub.plan, 'gratuit'),
    sub.paid_until,
    coalesce(sub.note, ''),
    (select count(*) from public.documents d where d.shop_id = s.id),
    (select count(*) from public.clients   c where c.shop_id = s.id),
    (select count(*) from public.articles  a where a.shop_id = s.id),
    public.boutique_est_pro(s.id),
    exists (select 1 from public.suspensions p where p.shop_id = s.id),
    (select count(*)::integer from public.memberships mb where mb.shop_id = s.id),
    (select max(d.coalesce_le) from (
       select max(d.created_at) as coalesce_le from public.documents d where d.shop_id = s.id
       union all
       select max(c.created_at) from public.clients c where c.shop_id = s.id
       union all
       select max(a.created_at) from public.articles a where a.shop_id = s.id
     ) d),
    s.ice,
    s.if_fiscal
  from public.shops s
  left join lateral (
    select mb.user_id
    from public.memberships mb
    where mb.shop_id = s.id and mb.role = 'owner'
    order by mb.created_at
    limit 1
  ) m on true
  left join auth.users u on u.id = m.user_id
  left join public.subscriptions sub on sub.shop_id = s.id
  where public.est_admin_plateforme()
  order by s.created_at;
$$;

comment on function public.admin_boutiques() is
  'Toutes les boutiques, avec propriétaire, plan, compteurs, suspension et dernière activité. Réservé à est_admin_plateforme().';

-- `admin_utilisateurs` gagne la confirmation d'e-mail : un compte non confirmé
-- explique un « je n'arrive pas à me connecter » mieux que la date d'inscription.
drop function if exists public.admin_utilisateurs();

create function public.admin_utilisateurs()
returns table (
  id                 uuid,
  email              text,
  cree_le            timestamptz,
  derniere_connexion timestamptz,
  boutique           text,
  boutique_id        uuid,
  est_admin          boolean,
  confirme           boolean
)
language sql
stable
security definer
set search_path = public
as $$
  select
    u.id,
    u.email::text,
    u.created_at,
    u.last_sign_in_at,
    s.nom,
    s.id,
    exists (select 1 from public.platform_admins a where a.user_id = u.id),
    (u.email_confirmed_at is not null)
  from auth.users u
  left join lateral (
    select m.shop_id
    from public.memberships m
    where m.user_id = u.id
    order by m.created_at
    limit 1
  ) m on true
  left join public.shops s on s.id = m.shop_id
  where public.est_admin_plateforme()
  order by u.created_at;
$$;

-- Le dossier complet d'une boutique en un appel : identité, abonnement, quota,
-- membres, suspension, bonus, notes. Un `jsonb` plutôt que trente colonnes —
-- la fiche évolue, l'interface la lit par nom.
create or replace function public.admin_boutique(p_shop uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_json   jsonb;
  v_utilise integer;
begin
  if not public.est_admin_plateforme() then
    raise exception 'réservé à l’administration' using errcode = '42501';
  end if;

  select count(*) into v_utilise
  from public.documents
  where shop_id = p_shop
    and public.type_comptable(type)
    and statut = 'emis'
    and emis_le >= date_trunc('month', now());

  select jsonb_build_object(
    'boutique', to_jsonb(s),
    'proprietaire', (select u.email::text from public.memberships mb
                       join auth.users u on u.id = mb.user_id
                      where mb.shop_id = s.id and mb.role = 'owner'
                      order by mb.created_at limit 1),
    'proprietaire_id', (select mb.user_id from public.memberships mb
                      where mb.shop_id = s.id and mb.role = 'owner'
                      order by mb.created_at limit 1),
    'membres', (select count(*)::integer from public.memberships mb where mb.shop_id = s.id),
    'pro', public.boutique_est_pro(s.id),
    'abonnement', jsonb_build_object(
      'plan', coalesce(sub.plan, 'gratuit'),
      'jusqu_au', sub.paid_until,
      'note', coalesce(sub.note, ''),
      'modifie_le', sub.updated_at
    ),
    'quota', jsonb_build_object(
      'utilise', v_utilise,
      'limite', case when public.boutique_est_pro(s.id) then null else public.quota_limite(s.id) end,
      'bonus', coalesce((select q.documents_sup from public.quota_bonus q where q.shop_id = s.id), 0)
    ),
    'suspension', (select jsonb_build_object('raison', p.raison, 'depuis', p.depuis)
                     from public.suspensions p where p.shop_id = s.id),
    'documents', (select count(*) from public.documents d where d.shop_id = s.id),
    'clients', (select count(*) from public.clients c where c.shop_id = s.id),
    'articles', (select count(*) from public.articles a where a.shop_id = s.id),
    'archives', jsonb_build_object(
      'nombre', (select count(*) from public.archives ar
                  join public.documents d2 on d2.id = ar.document_id
                 where d2.shop_id = s.id),
      'octets', (select coalesce(sum(ar.taille), 0) from public.archives ar
                  join public.documents d3 on d3.id = ar.document_id
                 where d3.shop_id = s.id)
    ),
    'notes', coalesce((
      select jsonb_agg(jsonb_build_object(
               'id', n.id, 'note', n.note, 'par', n.par_email, 'quand', n.created_at)
             order by n.created_at desc)
      from public.notes_admin n where n.shop_id = s.id
    ), '[]'::jsonb)
  )
  into v_json
  from public.shops s
  left join public.subscriptions sub on sub.shop_id = s.id
  where s.id = p_shop;

  if v_json is null then
    raise exception 'boutique inconnue' using errcode = '22023';
  end if;

  return v_json;
end;
$$;

-- Les comptes d'une boutique. `memberships` n'est lisible que par son propre
-- membre (politique 0001) : l'administrateur, lui, passe par ici.
create or replace function public.admin_membres(p_shop uuid)
returns table (
  user_id            uuid,
  email              text,
  role               text,
  depuis             timestamptz,
  derniere_connexion timestamptz
)
language sql
stable
security definer
set search_path = public
as $$
  select mb.user_id, u.email::text, mb.role, mb.created_at, u.last_sign_in_at
  from public.memberships mb
  left join auth.users u on u.id = mb.user_id
  where mb.shop_id = p_shop
    and public.est_admin_plateforme()
  order by mb.created_at;
$$;

-- Les derniers documents d'une boutique, avec l'archive quand elle existe.
-- Pagination par curseur : la date d'émission peut être vide sur un brouillon,
-- donc on trie sur l'arrivée en base, seule valeur toujours présente.
create or replace function public.admin_documents(
  p_shop   uuid,
  p_limite integer     default 20,
  p_avant  timestamptz default null
)
returns table (
  id             uuid,
  numero         text,
  type           text,
  statut         text,
  total_ttc      numeric,
  date_emission  text,
  quand          timestamptz,
  client         text,
  archive_path   text,
  archive_taille bigint
)
language sql
stable
security definer
set search_path = public
as $$
  select
    d.id,
    d.numero,
    d.type,
    d.statut,
    d.total_ttc,
    d.date_emission,
    coalesce(d.emis_le, d.created_at),
    coalesce(d.client_snapshot ->> 'nom', c.nom, ''),
    ar.storage_path,
    ar.taille
  from public.documents d
  left join public.clients c on c.id = d.client_id
  left join public.archives ar on ar.document_id = d.id
  where d.shop_id = p_shop
    and public.est_admin_plateforme()
    and (p_avant is null or coalesce(d.emis_le, d.created_at) < p_avant)
  order by coalesce(d.emis_le, d.created_at) desc, d.id desc
  limit greatest(1, least(coalesce(p_limite, 20), 100));
$$;

-- Ce qui demande une décision aujourd'hui : Pro qui expire, boutique sans
-- propriétaire, quota atteint, boutique suspendue, identité incomplète (une
-- facture sans ICE ni IF est refusée à l'émission).
create or replace function public.admin_alertes()
returns table (
  genre    text,
  shop_id  uuid,
  nom      text,
  detail   text,
  echeance date
)
language sql
stable
security definer
set search_path = public
as $$
  with pro as (
    select s.id, s.nom, sub.plan, sub.paid_until, public.boutique_est_pro(s.id) as est_pro
    from public.shops s
    left join public.subscriptions sub on sub.shop_id = s.id
  ),
  usage as (
    select s.id,
           (select count(*) from public.documents d
             where d.shop_id = s.id and public.type_comptable(d.type)
               and d.statut = 'emis' and d.emis_le >= date_trunc('month', now())) as utilise
    from public.shops s
  )
  select 'expire_bientot'::text, p.id, p.nom,
         'Pro jusqu’au ' || to_char(p.paid_until, 'DD/MM/YYYY'), p.paid_until
  from pro p
  where p.est_pro and p.paid_until is not null
    and p.paid_until between current_date and current_date + 30
  union all
  select 'expire', p.id, p.nom,
         'Plan Pro terminé', p.paid_until
  from pro p
  where coalesce(p.plan, 'gratuit') = 'pro'
    and p.paid_until is not null and p.paid_until < current_date
  union all
  select 'sans_proprietaire', s.id, s.nom,
         'Aucun compte « owner » sur cette boutique', null::date
  from public.shops s
  where not exists (
    select 1 from public.memberships mb where mb.shop_id = s.id and mb.role = 'owner'
  )
  union all
  select 'quota_atteint', u.id, s.nom,
         'Quota gratuit utilisé : ' || u.utilise || ' / ' || public.quota_limite(u.id), null::date
  from usage u
  join public.shops s on s.id = u.id
  where not public.boutique_est_pro(u.id)
    and u.utilise >= public.quota_limite(u.id)
  union all
  select 'suspendue', s.id, s.nom,
         'Suspendue : ' || p.raison, null::date
  from public.suspensions p
  join public.shops s on s.id = p.shop_id
  union all
  select 'identite_incomplete', s.id, s.nom,
         'ICE ou IF manquant : la facture sera refusée à l’émission', null::date
  from public.shops s
  where coalesce(btrim(s.ice), '') = '' or coalesce(btrim(s.if_fiscal), '') = ''
  order by 1, 3;
$$;

-- Le journal, éventuellement filtré sur une boutique.
create or replace function public.admin_journal(
  p_limite integer default 50,
  p_shop   uuid    default null
)
returns table (
  id         uuid,
  quand      timestamptz,
  acteur     text,
  action     text,
  cible_type text,
  cible_id   uuid,
  cible_nom  text,
  details    jsonb
)
language sql
stable
security definer
set search_path = public
as $$
  select j.id, j.created_at, coalesce(u.email::text, '—'), j.action,
         j.cible_type, j.cible_id, j.cible_nom, j.details
  from public.admin_actions j
  left join auth.users u on u.id = j.acteur
  where public.est_admin_plateforme()
    and (p_shop is null or j.cible_id = p_shop)
  order by j.created_at desc
  limit greatest(1, least(coalesce(p_limite, 50), 200));
$$;

-- --------------------------------------------------------------------------
-- Écritures — boutique
-- --------------------------------------------------------------------------

-- Édition de l'identité par l'administrateur. Liste blanche de colonnes : une
-- clé inconnue est ignorée, jamais concaténée dans du SQL. Les valeurs sont
-- revérifiées ici, même si le formulaire les a déjà contrôlées.
create or replace function public.admin_modifier_boutique(p_shop uuid, p_champs jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_nom    text;
  v_tva    numeric;
  v_design text;
begin
  if not public.est_admin_plateforme() then
    raise exception 'réservé à l’administration' using errcode = '42501';
  end if;
  if p_champs is null or jsonb_typeof(p_champs) <> 'object' then
    raise exception 'aucun champ à modifier' using errcode = '22023';
  end if;
  if not exists (select 1 from public.shops where id = p_shop) then
    raise exception 'boutique inconnue' using errcode = '22023';
  end if;

  if p_champs ? 'nom' then
    v_nom := btrim(coalesce(p_champs ->> 'nom', ''));
    if length(v_nom) < 1 or length(v_nom) > 120 then
      raise exception 'nom de boutique invalide' using errcode = '22023';
    end if;
  end if;

  if p_champs ? 'tva_defaut' then
    begin
      v_tva := (p_champs ->> 'tva_defaut')::numeric;
    exception when others then
      raise exception 'taux de TVA invalide' using errcode = '22023';
    end;
    if v_tva < 0 or v_tva > 100 then
      raise exception 'le taux de TVA doit être entre 0 et 100' using errcode = '22023';
    end if;
  end if;

  if p_champs ? 'design_defaut' then
    v_design := p_champs ->> 'design_defaut';
    if v_design not in ('classique', 'facture_maroc') then
      raise exception 'modèle inconnu : %', v_design using errcode = '22023';
    end if;
  end if;

  if p_champs ? 'prefixes' and jsonb_typeof(p_champs -> 'prefixes') <> 'object' then
    raise exception 'les préfixes doivent être un objet' using errcode = '22023';
  end if;

  update public.shops set
    nom                  = case when p_champs ? 'nom' then v_nom else nom end,
    ice                  = case when p_champs ? 'ice' then nullif(btrim(coalesce(p_champs ->> 'ice', '')), '') else ice end,
    if_fiscal            = case when p_champs ? 'if_fiscal' then nullif(btrim(coalesce(p_champs ->> 'if_fiscal', '')), '') else if_fiscal end,
    rc                   = case when p_champs ? 'rc' then nullif(btrim(coalesce(p_champs ->> 'rc', '')), '') else rc end,
    patente              = case when p_champs ? 'patente' then nullif(btrim(coalesce(p_champs ->> 'patente', '')), '') else patente end,
    cnss                 = case when p_champs ? 'cnss' then nullif(btrim(coalesce(p_champs ->> 'cnss', '')), '') else cnss end,
    adresse              = case when p_champs ? 'adresse' then nullif(btrim(coalesce(p_champs ->> 'adresse', '')), '') else adresse end,
    telephone            = case when p_champs ? 'telephone' then nullif(btrim(coalesce(p_champs ->> 'telephone', '')), '') else telephone end,
    email                = case when p_champs ? 'email' then nullif(btrim(coalesce(p_champs ->> 'email', '')), '') else email end,
    banque               = case when p_champs ? 'banque' then nullif(btrim(coalesce(p_champs ->> 'banque', '')), '') else banque end,
    rib                  = case when p_champs ? 'rib' then nullif(btrim(coalesce(p_champs ->> 'rib', '')), '') else rib end,
    rib_titulaire        = case when p_champs ? 'rib_titulaire' then nullif(btrim(coalesce(p_champs ->> 'rib_titulaire', '')), '') else rib_titulaire end,
    clause_retard        = case when p_champs ? 'clause_retard' then btrim(coalesce(p_champs ->> 'clause_retard', '')) else clause_retard end,
    conditions_paiement  = case when p_champs ? 'conditions_paiement' then coalesce(nullif(btrim(p_champs ->> 'conditions_paiement'), ''), conditions_paiement) else conditions_paiement end,
    tva_defaut           = case when p_champs ? 'tva_defaut' then v_tva else tva_defaut end,
    arrondi_dirham       = case when p_champs ? 'arrondi_dirham' then (p_champs ->> 'arrondi_dirham')::boolean else arrondi_dirham end,
    prix_ttc             = case when p_champs ? 'prix_ttc' then (p_champs ->> 'prix_ttc')::boolean else prix_ttc end,
    tva_non_applicable   = case when p_champs ? 'tva_non_applicable' then (p_champs ->> 'tva_non_applicable')::boolean else tva_non_applicable end,
    montant_lettres      = case when p_champs ? 'montant_lettres' then (p_champs ->> 'montant_lettres')::boolean else montant_lettres end,
    numerotation_annuelle = case when p_champs ? 'numerotation_annuelle' then (p_champs ->> 'numerotation_annuelle')::boolean else numerotation_annuelle end,
    design_defaut        = case when p_champs ? 'design_defaut' then v_design else design_defaut end,
    prefixes             = case when p_champs ? 'prefixes' then p_champs -> 'prefixes' else prefixes end
  where id = p_shop;

  perform public.admin_journaliser(
    'boutique_modifiee', 'boutique', p_shop,
    (select nom from public.shops where id = p_shop),
    jsonb_build_object('champs', (
      select coalesce(jsonb_object_agg(k, p_champs -> k), '{}'::jsonb)
      from jsonb_object_keys(p_champs) as k
      where k in ('nom','ice','if_fiscal','rc','patente','cnss','adresse','telephone',
                  'email','banque','rib','rib_titulaire','clause_retard',
                  'conditions_paiement','tva_defaut','arrondi_dirham','prix_ttc',
                  'tva_non_applicable','montant_lettres','numerotation_annuelle',
                  'design_defaut','prefixes')
    ))
  );
end;
$$;

create or replace function public.admin_note(p_shop uuid, p_note text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_note  text := btrim(coalesce(p_note, ''));
  v_email text;
  v_nom   text;
begin
  if not public.est_admin_plateforme() then
    raise exception 'réservé à l’administration' using errcode = '42501';
  end if;
  if length(v_note) = 0 then
    raise exception 'note vide' using errcode = '22023';
  end if;
  if length(v_note) > 1000 then
    raise exception 'note trop longue (1000 caractères)' using errcode = '22023';
  end if;

  select nom into v_nom from public.shops where id = p_shop;
  if v_nom is null then
    raise exception 'boutique inconnue' using errcode = '22023';
  end if;

  select email::text into v_email from auth.users where id = auth.uid();

  insert into public.notes_admin (shop_id, note, par, par_email)
  values (p_shop, v_note, auth.uid(), coalesce(v_email, ''));

  perform public.admin_journaliser('note_ajoutee', 'boutique', p_shop, v_nom,
    jsonb_build_object('note', v_note));
end;
$$;

create or replace function public.admin_quota_bonus(
  p_shop      uuid,
  p_documents integer,
  p_raison    text default ''
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_nom text;
  v_n   integer := coalesce(p_documents, 0);
begin
  if not public.est_admin_plateforme() then
    raise exception 'réservé à l’administration' using errcode = '42501';
  end if;
  if v_n < 0 or v_n > 500 then
    raise exception 'bonus hors limites (0 à 500)' using errcode = '22023';
  end if;

  select nom into v_nom from public.shops where id = p_shop;
  if v_nom is null then
    raise exception 'boutique inconnue' using errcode = '22023';
  end if;

  insert into public.quota_bonus (shop_id, documents_sup, raison, modifie_le)
  values (p_shop, v_n, coalesce(btrim(p_raison), ''), now())
  on conflict (shop_id) do update
    set documents_sup = excluded.documents_sup,
        raison        = excluded.raison,
        modifie_le    = now();

  perform public.admin_journaliser('quota_bonus', 'boutique', p_shop, v_nom,
    jsonb_build_object('documents_sup', v_n, 'raison', btrim(coalesce(p_raison, ''))));
end;
$$;

create or replace function public.admin_suspendre(p_shop uuid, p_raison text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_raison text := btrim(coalesce(p_raison, ''));
  v_nom    text;
begin
  if not public.est_admin_plateforme() then
    raise exception 'réservé à l’administration' using errcode = '42501';
  end if;
  if length(v_raison) = 0 then
    raise exception 'une raison est nécessaire pour suspendre' using errcode = '22023';
  end if;

  select nom into v_nom from public.shops where id = p_shop;
  if v_nom is null then
    raise exception 'boutique inconnue' using errcode = '22023';
  end if;

  insert into public.suspensions (shop_id, raison, par)
  values (p_shop, v_raison, auth.uid())
  on conflict (shop_id) do update
    set raison = excluded.raison, depuis = now(), par = excluded.par;

  perform public.admin_journaliser('boutique_suspendue', 'boutique', p_shop, v_nom,
    jsonb_build_object('raison', v_raison));
end;
$$;

create or replace function public.admin_reprendre(p_shop uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_nom text;
begin
  if not public.est_admin_plateforme() then
    raise exception 'réservé à l’administration' using errcode = '42501';
  end if;

  select nom into v_nom from public.shops where id = p_shop;
  if v_nom is null then
    raise exception 'boutique inconnue' using errcode = '22023';
  end if;

  delete from public.suspensions where shop_id = p_shop;

  perform public.admin_journaliser('boutique_reprise', 'boutique', p_shop, v_nom);
end;
$$;

-- La suppression exige le nom exact, revérifié ici : un appel direct à l'API
-- sans le nom tapé échoue. Les PDF du bucket ne cascadent pas (`storage.objects`
-- ne connaît pas la boutique) : la fonction rend les chemins, l'écran les
-- supprime via l'API Storage. Un objet oublié ne casse rien, il reste orphelin.
create or replace function public.admin_supprimer_boutique(p_shop uuid, p_nom_confirme text)
returns text[]
language plpgsql
security definer
set search_path = public
as $$
declare
  v_nom    text;
  v_chemins text[];
begin
  if not public.est_admin_plateforme() then
    raise exception 'réservé à l’administration' using errcode = '42501';
  end if;

  select nom into v_nom from public.shops where id = p_shop;
  if v_nom is null then
    raise exception 'boutique inconnue' using errcode = '22023';
  end if;

  if btrim(coalesce(p_nom_confirme, '')) <> v_nom then
    raise exception 'le nom tapé ne correspond pas à la boutique' using errcode = '22023';
  end if;

  select coalesce(array_agg(ar.storage_path), '{}'::text[]) into v_chemins
  from public.archives ar
  join public.documents d on d.id = ar.document_id
  where d.shop_id = p_shop;

  perform public.admin_journaliser('boutique_supprimee', 'boutique', p_shop, v_nom,
    jsonb_build_object('documents', (select count(*) from public.documents where shop_id = p_shop),
                       'archives', coalesce(array_length(v_chemins, 1), 0)));

  delete from public.shops where id = p_shop;

  return v_chemins;
end;
$$;

-- Purge des PDF archivés avant une date. Le document reste, son aperçu
-- reconstruit le rendu — et le dit.
create or replace function public.admin_purger_archives(p_shop uuid, p_avant date)
returns text[]
language plpgsql
security definer
set search_path = public
as $$
declare
  v_nom     text;
  v_chemins text[];
begin
  if not public.est_admin_plateforme() then
    raise exception 'réservé à l’administration' using errcode = '42501';
  end if;
  if p_avant is null or p_avant > current_date then
    raise exception 'date de purge invalide' using errcode = '22023';
  end if;

  select nom into v_nom from public.shops where id = p_shop;
  if v_nom is null then
    raise exception 'boutique inconnue' using errcode = '22023';
  end if;

  select coalesce(array_agg(ar.storage_path), '{}'::text[]) into v_chemins
  from public.archives ar
  join public.documents d on d.id = ar.document_id
  where d.shop_id = p_shop and ar.created_at < p_avant::timestamptz;

  delete from public.archives ar
  using public.documents d
  where d.id = ar.document_id
    and d.shop_id = p_shop
    and ar.created_at < p_avant::timestamptz;

  perform public.admin_journaliser('archives_purgees', 'boutique', p_shop, v_nom,
    jsonb_build_object('avant', p_avant, 'nombre', coalesce(array_length(v_chemins, 1), 0)));

  return v_chemins;
end;
$$;

-- --------------------------------------------------------------------------
-- Écritures — membres
-- --------------------------------------------------------------------------

create or replace function public.admin_role_membre(p_shop uuid, p_user uuid, p_role text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_nom     text;
  v_ancien  text;
  v_email   text;
  v_owners  integer;
begin
  if not public.est_admin_plateforme() then
    raise exception 'réservé à l’administration' using errcode = '42501';
  end if;
  if p_role not in ('owner', 'membre', 'admin') then
    raise exception 'rôle inconnu : %', p_role using errcode = '22023';
  end if;

  select nom into v_nom from public.shops where id = p_shop;
  if v_nom is null then
    raise exception 'boutique inconnue' using errcode = '22023';
  end if;

  select role into v_ancien
  from public.memberships where shop_id = p_shop and user_id = p_user;
  if v_ancien is null then
    raise exception 'ce compte n’est pas membre de la boutique' using errcode = '22023';
  end if;

  if v_ancien = 'owner' and p_role <> 'owner' then
    select count(*) into v_owners
    from public.memberships where shop_id = p_shop and role = 'owner';
    if v_owners <= 1 then
      raise exception 'il faut au moins un propriétaire : transférez la boutique d’abord'
        using errcode = '22023';
    end if;
  end if;

  update public.memberships set role = p_role
  where shop_id = p_shop and user_id = p_user;

  select email::text into v_email from auth.users where id = p_user;

  perform public.admin_journaliser('membre_role', 'boutique', p_shop, v_nom,
    jsonb_build_object('compte', v_email, 'ancien', v_ancien, 'nouveau', p_role));
end;
$$;

-- Transférer la propriété : l'ancien propriétaire devient membre, le nouveau
-- compte (membre ou non) devient propriétaire. Une seule transaction, donc
-- jamais deux propriétaires et jamais zéro.
create or replace function public.admin_transferer(p_shop uuid, p_email text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_nom      text;
  v_cible    uuid;
  v_email    text := btrim(coalesce(p_email, ''));
  v_ancien   text;
begin
  if not public.est_admin_plateforme() then
    raise exception 'réservé à l’administration' using errcode = '42501';
  end if;

  select nom into v_nom from public.shops where id = p_shop;
  if v_nom is null then
    raise exception 'boutique inconnue' using errcode = '22023';
  end if;

  select id into v_cible from auth.users where lower(email) = lower(v_email);
  if v_cible is null then
    raise exception 'aucun compte avec cet e-mail' using errcode = '22023';
  end if;

  select email::text into v_ancien
  from auth.users u
  join public.memberships mb on mb.user_id = u.id
  where mb.shop_id = p_shop and mb.role = 'owner'
  order by mb.created_at limit 1;

  if v_ancien is not null and lower(v_ancien) = lower(v_email) then
    raise exception 'ce compte est déjà propriétaire' using errcode = '22023';
  end if;

  update public.memberships set role = 'membre'
  where shop_id = p_shop and role = 'owner';

  insert into public.memberships (user_id, shop_id, role)
  values (v_cible, p_shop, 'owner')
  on conflict (user_id, shop_id) do update set role = 'owner';

  perform public.admin_journaliser('propriete_transferee', 'boutique', p_shop, v_nom,
    jsonb_build_object('ancien', v_ancien, 'nouveau', v_email));
end;
$$;

create or replace function public.admin_detacher(p_shop uuid, p_user uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_nom    text;
  v_role   text;
  v_email  text;
  v_owners integer;
begin
  if not public.est_admin_plateforme() then
    raise exception 'réservé à l’administration' using errcode = '42501';
  end if;

  select nom into v_nom from public.shops where id = p_shop;
  if v_nom is null then
    raise exception 'boutique inconnue' using errcode = '22023';
  end if;

  select role into v_role
  from public.memberships where shop_id = p_shop and user_id = p_user;
  if v_role is null then
    raise exception 'ce compte n’est pas membre de la boutique' using errcode = '22023';
  end if;

  if v_role = 'owner' then
    select count(*) into v_owners
    from public.memberships where shop_id = p_shop and role = 'owner';
    if v_owners <= 1 then
      raise exception 'il faut au moins un propriétaire : transférez la boutique d’abord'
        using errcode = '22023';
    end if;
  end if;

  delete from public.memberships where shop_id = p_shop and user_id = p_user;

  select email::text into v_email from auth.users where id = p_user;

  perform public.admin_journaliser('membre_detache', 'boutique', p_shop, v_nom,
    jsonb_build_object('compte', v_email, 'role', v_role));
end;
$$;

-- --------------------------------------------------------------------------
-- Le quota du plan gratuit inclut le bonus, et une boutique suspendue n'émet
-- plus. Mêmes contrôles que la 0010, plus ces deux règles.
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
  v_bonus   integer;
begin
  if not public.is_shop_member(p_shop) then
    raise exception 'boutique inconnue' using errcode = '42501';
  end if;

  v_pro := public.boutique_est_pro(p_shop);

  select count(*) into v_utilise
  from public.documents
  where shop_id = p_shop
    and public.type_comptable(type)
    and statut = 'emis'
    and emis_le >= date_trunc('month', now());

  select paid_until into v_fin from public.subscriptions where shop_id = p_shop;
  select coalesce(documents_sup, 0) into v_bonus
  from public.quota_bonus where shop_id = p_shop;
  v_bonus := coalesce(v_bonus, 0);

  return jsonb_build_object(
    'plan', case when v_pro then 'pro' else 'gratuit' end,
    'paid_until', v_fin,
    'utilise', v_utilise,
    'limite', case when v_pro then null else 5 + v_bonus end,
    'bonus', v_bonus,
    'suspendue', exists (select 1 from public.suspensions p where p.shop_id = p_shop)
  );
end;
$$;

revoke all on function public.quota_boutique(uuid) from public, anon;
grant execute on function public.quota_boutique(uuid) to authenticated;

create or replace function public.emettre_document(p_document uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_doc      public.documents;
  v_shop     public.shops;
  v_numero   text;
  v_snapshot jsonb;
  v_utilise  integer;
  v_limite   integer;
  v_date     text;
  v_jour     date;
  v_ice      text;
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

  select * into v_shop from public.shops where id = v_doc.shop_id;
  if not found then
    raise exception 'boutique inconnue' using errcode = '42501';
  end if;

  -- ------------------------------------------------------ boutique suspendue
  -- La suspension ne cache rien : elle empêche d'émettre. Un document déjà
  -- émis reste lisible, téléchargeable et corrigeable par un avoir.
  if exists (select 1 from public.suspensions s where s.shop_id = v_doc.shop_id) then
    raise exception 'Cette boutique est suspendue : aucun document ne peut être émis.'
      using errcode = '42501', hint = 'Contactez l’administration du service.';
  end if;

  -- ---------------------------------------------------------------- la date
  v_date := coalesce(nullif(btrim(v_doc.date_emission), ''), to_char(current_date, 'DD/MM/YYYY'));
  if v_date !~ '^[0-9]{1,2}/[0-9]{1,2}/[0-9]{4}$' then
    raise exception 'La date d''émission n''est pas une date valide (JJ/MM/AAAA).'
      using errcode = '22007';
  end if;
  begin
    v_jour := make_date(
      split_part(v_date, '/', 3)::integer,
      split_part(v_date, '/', 2)::integer,
      split_part(v_date, '/', 1)::integer
    );
  exception when others then
    raise exception 'La date d''émission n''est pas une date valide (JJ/MM/AAAA).'
      using errcode = '22007';
  end;
  if v_jour > current_date then
    raise exception 'La date d''émission ne peut pas être dans le futur.'
      using errcode = '22007';
  end if;

  -- ------------------------------------------------------- identité du vendeur
  if public.type_comptable(v_doc.type) or v_doc.type = 'avoir' then
    if coalesce(btrim(v_shop.ice), '') = '' then
      raise exception 'L''ICE de votre boutique manque : il est obligatoire sur une facture.'
        using errcode = '22023',
              hint = 'Renseignez-le dans Paramètres → Boutique, puis émettrez à nouveau.';
    end if;
    if coalesce(btrim(v_shop.if_fiscal), '') = '' then
      raise exception 'Votre identifiant fiscal (IF) manque : il est obligatoire sur une facture.'
        using errcode = '22023',
              hint = 'Renseignez-le dans Paramètres → Boutique, puis émettrez à nouveau.';
    end if;
  end if;

  -- ----------------------------------------------------- document corrigé
  if v_doc.type in ('avoir', 'regul') and coalesce(btrim(v_doc.reference), '') = '' then
    raise exception 'Un document de correction doit porter le numéro de la facture qu''il corrige.'
      using errcode = '22023',
            hint = 'Renseignez la référence du document annulé ou rectifié.';
  end if;

  -- ------------------------------------------------------------------ ICE client
  v_ice := replace(replace(coalesce(v_doc.client_snapshot ->> 'ice', ''), ' ', ''), '-', '');
  if v_ice <> '' then
    if not public.valider_ice_cle_controle(v_ice) then
      raise exception 'L''ICE du client est mal formé : vérifiez les 15 chiffres.'
        using errcode = '22023';
    end if;
  end if;

  -- -------------------------------------------------------------------- quota
  if public.type_comptable(v_doc.type) and not public.boutique_est_pro(v_doc.shop_id) then
    select count(*) into v_utilise
    from public.documents
    where shop_id = v_doc.shop_id
      and public.type_comptable(type)
      and statut = 'emis'
      and emis_le >= date_trunc('month', now());

    v_limite := public.quota_limite(v_doc.shop_id);

    if v_utilise >= v_limite then
      raise exception 'Quota de % documents atteint pour ce mois.', v_limite
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

comment on function public.emettre_document(uuid) is
  'Émet un brouillon : boutique non suspendue, date valide et non future, ICE/IF du vendeur sur un document comptable, référence sur avoir/régul, ICE client à 15 chiffres avec clé de contrôle modulo 97, quota gratuit (bonus compris).';

-- --------------------------------------------------------------------------
-- Le bucket des archives : un administrateur peut lire le PDF qu'il inspecte
-- --------------------------------------------------------------------------

do $$
begin
  if exists (select 1 from pg_tables where schemaname = 'storage' and tablename = 'objects') then
    execute 'drop policy if exists "archives lecture admin" on storage.objects';
    execute 'create policy "archives lecture admin" on storage.objects
               for select to authenticated
               using (bucket_id = ''archives'' and public.est_admin_plateforme())';
    -- Supprimer une boutique ne supprime pas ses objets : la fonction rend les
    -- chemins, et l'écran les efface. Sans cette politique il faudrait le
    -- service_role dans l'onglet, exactement ce que la 0005 évite.
    execute 'drop policy if exists "archives suppression admin" on storage.objects';
    execute 'create policy "archives suppression admin" on storage.objects
               for delete to authenticated
               using (bucket_id = ''archives'' and public.est_admin_plateforme())';
  end if;
end;
$$;

-- --------------------------------------------------------------------------
-- Privilèges
-- --------------------------------------------------------------------------

revoke all on function public.admin_boutique(uuid)               from public, anon;
revoke all on function public.admin_membres(uuid)                from public, anon;
revoke all on function public.admin_documents(uuid, integer, timestamptz) from public, anon;
revoke all on function public.admin_alertes()                    from public, anon;
revoke all on function public.admin_journal(integer, uuid)       from public, anon;
revoke all on function public.admin_modifier_boutique(uuid, jsonb) from public, anon;
revoke all on function public.admin_note(uuid, text)             from public, anon;
revoke all on function public.admin_quota_bonus(uuid, integer, text) from public, anon;
revoke all on function public.admin_suspendre(uuid, text)        from public, anon;
revoke all on function public.admin_reprendre(uuid)              from public, anon;
revoke all on function public.admin_supprimer_boutique(uuid, text) from public, anon;
revoke all on function public.admin_purger_archives(uuid, date)  from public, anon;
revoke all on function public.admin_role_membre(uuid, uuid, text) from public, anon;
revoke all on function public.admin_transferer(uuid, text)       from public, anon;
revoke all on function public.admin_detacher(uuid, uuid)         from public, anon;

grant execute on function public.admin_boutique(uuid)               to authenticated;
grant execute on function public.admin_membres(uuid)                to authenticated;
grant execute on function public.admin_documents(uuid, integer, timestamptz) to authenticated;
grant execute on function public.admin_alertes()                    to authenticated;
grant execute on function public.admin_journal(integer, uuid)       to authenticated;
grant execute on function public.admin_modifier_boutique(uuid, jsonb) to authenticated;
grant execute on function public.admin_note(uuid, text)             to authenticated;
grant execute on function public.admin_quota_bonus(uuid, integer, text) to authenticated;
grant execute on function public.admin_suspendre(uuid, text)        to authenticated;
grant execute on function public.admin_reprendre(uuid)              to authenticated;
grant execute on function public.admin_supprimer_boutique(uuid, text) to authenticated;
grant execute on function public.admin_purger_archives(uuid, date)  to authenticated;
grant execute on function public.admin_role_membre(uuid, uuid, text) to authenticated;
grant execute on function public.admin_transferer(uuid, text)       to authenticated;
grant execute on function public.admin_detacher(uuid, uuid)         to authenticated;
