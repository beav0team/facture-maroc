-- FactureApp — 0014 : trois paliers, et le prix le plus bas du marché
--
-- La 0003 ne connaissait que deux états : gratuit (cinq documents facturés par
-- mois) ou Pro (illimité). Un commerce qui facture une vingtaine de fois par
-- mois n'avait donc rien à acheter : trop pour le gratuit, pas assez pour
-- payer l'illimité. Cette migration ajoute un palier intermédiaire à 50
-- documents, et fait du plan une limite plutôt qu'un booléen.
--
-- Rien ne change pour l'existant : `subscriptions.plan = 'pro'` garde son sens
-- (illimité), et `boutique_est_pro()` continue de répondre « illimité » aux
-- cinq endroits qui l'interrogent.
--
-- Le prix (240 et 480 DH par an) ne vit pas ici : il vit dans
-- `webapp/src/lib/offres.ts`, et la base ne connaît que les clés de plan.

-- --------------------------------------------------------------------------
-- La contrainte de plan
-- --------------------------------------------------------------------------

-- La 0003 a créé la contrainte sans la nommer : on retire les deux formes
-- possibles avant de reposer la nôtre, pour que ce fichier soit rejouable.
alter table public.subscriptions
  drop constraint if exists subscriptions_plan_check;

do $$
declare
  v_nom text;
begin
  select c.conname into v_nom
  from pg_constraint c
  where c.conrelid = 'public.subscriptions'::regclass
    and c.contype = 'c'
    and pg_get_constraintdef(c.oid) like '%gratuit%';
  if v_nom is not null then
    execute format('alter table public.subscriptions drop constraint %I', v_nom);
  end if;
end;
$$;

alter table public.subscriptions
  add constraint subscriptions_plan_check
  check (plan in ('gratuit', 'essentiel', 'pro'));

-- --------------------------------------------------------------------------
-- Le plan, date de fin comprise
-- --------------------------------------------------------------------------

-- Le plan qui s'applique aujourd'hui. `subscriptions.plan` garde la valeur
-- brute (pour que la console dise « Essentiel expiré le ... » au lieu de
-- « gratuit », qui effacerait l'histoire) ; celui-ci rend ce qui compte.
create or replace function public.plan_effectif(p_shop uuid)
returns text
language sql
stable
security definer
set search_path = public
as $$
  select case
    when exists (
      select 1 from public.subscriptions s
      where s.shop_id = p_shop
        and s.plan = 'pro'
        and (s.paid_until is null or s.paid_until >= current_date)
    ) then 'pro'
    when exists (
      select 1 from public.subscriptions s
      where s.shop_id = p_shop
        and s.plan = 'essentiel'
        and s.paid_until >= current_date
    ) then 'essentiel'
    else 'gratuit'
  end;
$$;

revoke all on function public.plan_effectif(uuid) from public, anon;
grant execute on function public.plan_effectif(uuid) to authenticated;

-- « Pro » veut toujours dire « illimité » : la définition suit le plan, les
-- appelants ne changent pas.
create or replace function public.boutique_est_pro(p_shop uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public.plan_effectif(p_shop) = 'pro';
$$;

revoke all on function public.boutique_est_pro(uuid) from public, anon;
grant execute on function public.boutique_est_pro(uuid) to authenticated;

-- La limite du mois. NULL veut dire illimité : c'est ce que `quota_boutique`
-- rend déjà au Pro, et ce que l'émission teste avant de compter.
create or replace function public.quota_limite(p_shop uuid)
returns integer
language sql
stable
security definer
set search_path = public
as $$
  select case public.plan_effectif(p_shop)
    when 'pro' then null
    when 'essentiel' then 50 + coalesce(
      (select q.documents_sup from public.quota_bonus q where q.shop_id = p_shop), 0)
    else 5 + coalesce(
      (select q.documents_sup from public.quota_bonus q where q.shop_id = p_shop), 0)
  end;
$$;

revoke all on function public.quota_limite(uuid) from public, anon;
grant execute on function public.quota_limite(uuid) to authenticated;

-- --------------------------------------------------------------------------
-- Lectures
-- --------------------------------------------------------------------------

-- Le quota que la boutique lit. Deux champs de plus : la date de fin et
-- `expire`, pour que l'écran dise « abonnement terminé le ... » plutôt que de
-- laisser croire que les cinq documents sont une panne.
create or replace function public.quota_boutique(p_shop uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_plan    text;
  v_utilise integer;
  v_fin     date;
  v_brut    text;
  v_bonus   integer;
begin
  if not public.is_shop_member(p_shop) then
    raise exception 'boutique inconnue' using errcode = '42501';
  end if;

  v_plan := public.plan_effectif(p_shop);

  select count(*) into v_utilise
  from public.documents
  where shop_id = p_shop
    and public.type_comptable(type)
    and statut = 'emis'
    and emis_le >= date_trunc('month', now());

  select paid_until, plan into v_fin, v_brut
  from public.subscriptions where shop_id = p_shop;

  select coalesce(documents_sup, 0) into v_bonus
  from public.quota_bonus where shop_id = p_shop;
  v_bonus := coalesce(v_bonus, 0);

  return jsonb_build_object(
    'plan', v_plan,
    'paid_until', v_fin,
    'jusqu_au', v_fin,
    'utilise', v_utilise,
    'limite', public.quota_limite(p_shop),
    'bonus', v_bonus,
    'expire', (v_fin is not null and v_fin < current_date and coalesce(v_brut, 'gratuit') <> 'gratuit'),
    'suspendue', exists (select 1 from public.suspensions p where p.shop_id = p_shop)
  );
end;
$$;

revoke all on function public.quota_boutique(uuid) from public, anon;
grant execute on function public.quota_boutique(uuid) to authenticated;

-- Les chiffres du haut de la console : on sépare les deux paliers payants,
-- sinon « 1 en Pro » laisserait croire qu'aucune boutique ne paie.
create or replace function public.admin_statistiques()
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select case
    when public.est_admin_plateforme() then jsonb_build_object(
      'boutiques',        (select count(*) from public.shops),
      'utilisateurs',     (select count(*) from auth.users),
      'administrateurs',  (select count(*) from public.platform_admins),
      'pro',              (select count(*) from public.shops s
                            where public.plan_effectif(s.id) = 'pro'),
      'essentiel',        (select count(*) from public.shops s
                            where public.plan_effectif(s.id) = 'essentiel'),
      'documents',        (select count(*) from public.documents),
      'documents_emis',   (select count(*) from public.documents where statut = 'emis'),
      'archives',         (select count(*) from public.archives),
      'octets_archives',  (select coalesce(sum(taille), 0) from public.archives)
    )
    else null
  end;
$$;

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
         'Abonnement jusqu’au ' || to_char(p.paid_until, 'DD/MM/YYYY'), p.paid_until
  from pro p
  where p.plan <> 'gratuit' and p.paid_until is not null
    and p.paid_until >= current_date
    and p.paid_until between current_date and current_date + 30
  union all
  select 'expire', p.id, p.nom,
         'Abonnement terminé le ' || to_char(p.paid_until, 'DD/MM/YYYY'), p.paid_until
  from pro p
  where coalesce(p.plan, 'gratuit') <> 'gratuit'
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
         'Quota du mois utilisé : ' || u.utilise || ' / ' || public.quota_limite(u.id), null::date
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


-- --------------------------------------------------------------------------
-- Écritures
-- --------------------------------------------------------------------------

-- Le seul écrivain d'abonnement de la console. Trois plans, et la même règle
-- qu'en 0005 : un plan payant sans date de fin vivante est refusé ici, parce
-- qu'une date vide veut dire « pour toujours ».
--
-- Il journalise désormais : c'était le dernier geste de la console qui ne
-- laissait aucune trace, alors qu'il change ce que la boutique peut faire.
create or replace function public.admin_abonnement(
  p_shop      uuid,
  p_plan      text,
  p_jusqu_au  date,
  p_note      text default ''
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_ancien text;
  v_nom    text;
begin
  if not public.est_admin_plateforme() then
    raise exception 'réservé à l’administration' using errcode = '42501';
  end if;
  if p_plan not in ('gratuit', 'essentiel', 'pro') then
    raise exception 'plan inconnu : %', p_plan using errcode = '22023';
  end if;

  select nom into v_nom from public.shops where id = p_shop;
  if v_nom is null then
    raise exception 'boutique inconnue' using errcode = '22023';
  end if;

  if p_plan <> 'gratuit' and (p_jusqu_au is null or p_jusqu_au < current_date) then
    raise exception 'une date de fin future est nécessaire pour un plan payant'
      using errcode = '22023';
  end if;

  select plan into v_ancien from public.subscriptions where shop_id = p_shop;

  insert into public.subscriptions (shop_id, plan, paid_until, source, note, updated_at)
  values (
    p_shop,
    p_plan,
    case when p_plan = 'gratuit' then null else p_jusqu_au end,
    'manuel',
    coalesce(btrim(p_note), ''),
    now()
  )
  on conflict (shop_id) do update
    set plan       = excluded.plan,
        paid_until = excluded.paid_until,
        source     = excluded.source,
        note       = excluded.note,
        updated_at = now();

  perform public.admin_journaliser('abonnement', 'boutique', p_shop, v_nom,
    jsonb_build_object(
      'ancien', coalesce(v_ancien, 'aucun'),
      'nouveau', p_plan,
      'jusqu_au', case when p_plan = 'gratuit' then null else p_jusqu_au end,
      'note', btrim(coalesce(p_note, ''))
    ));
end;
$$;

revoke all on function public.admin_abonnement(uuid, text, date, text) from public, anon;
grant execute on function public.admin_abonnement(uuid, text, date, text) to authenticated;

comment on function public.admin_abonnement(uuid, text, date, text) is
  'Active un palier (gratuit, essentiel, pro) avec une date de fin obligatoire sur les plans payants, et journalise le changement.';

-- --------------------------------------------------------------------------
-- L'émission
-- --------------------------------------------------------------------------

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
  -- Le plan ne rend plus un booléen mais une limite : NULL en Pro (illimité),
  -- 50 en Essentiel, 5 en Gratuit, bonus d'administration compris.
  if public.type_comptable(v_doc.type) then
    v_limite := public.quota_limite(v_doc.shop_id);

    if v_limite is not null then
      select count(*) into v_utilise
      from public.documents
      where shop_id = v_doc.shop_id
        and public.type_comptable(type)
        and statut = 'emis'
        and emis_le >= date_trunc('month', now());

      if v_utilise >= v_limite then
        raise exception 'Quota de % documents atteint pour ce mois.', v_limite
          using errcode = 'P0001', hint = 'Passez à Essentiel ou au Pro pour continuer.';
      end if;
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
