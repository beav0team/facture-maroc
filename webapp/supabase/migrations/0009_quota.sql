-- FactureApp — 0009 : le quota compte tous les documents comptables
--
-- La 0008 ne comptait que `type = 'facture'` : une boutique gratuite pouvait
-- émettre ses cinq factures puis continuer à facturer en tickets, factures
-- d'acompte, régularisations, simplifiées ou exports, sans jamais passer au
-- plan Pro. Les tickets sont justement le document le plus courant au
-- comptoir : c'était la fuite la plus large.
--
-- `type_comptable()` est la seule liste des documents qui facturent. L'avoir
-- en est exclu volontairement : corriger une facture est une obligation, pas
-- une vente, et refuser un avoir parce que le quota est atteint laisserait la
-- boutique sans moyen de réparer une erreur. Devis, bon de commande et
-- proforma n'ouvrent ni créance ni droit à déduction : ils restent libres.

create or replace function public.type_comptable(p_type text)
returns boolean
language sql
immutable
as $$
  select p_type in ('facture', 'ticket', 'acompte', 'regul', 'simplifiee', 'export')
$$;

comment on function public.type_comptable(text) is
  'Documents qui facturent : comptés par le quota gratuit, soumis aux mentions du vendeur.';

revoke all on function public.type_comptable(text) from public, anon;
grant execute on function public.type_comptable(text) to authenticated;

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
    and public.type_comptable(type)
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
-- Émission : mêmes contrôles que la 0008, quota élargi aux documents comptables
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

  -- ---------------------------------------------------------------- la date
  -- Une date vide prend le jour de l'émission ; une date écrite doit être un
  -- vrai jour. `make_date` refuse le 31 février, là où `to_date` le reporte au
  -- 3 mars et laisse la boutique relancer un client sur une date que personne
  -- n'a écrite.
  v_date := coalesce(nullif(btrim(v_doc.date_emission), ''), to_char(current_date, 'DD/MM/YYYY'));
  if v_date !~ '^\d{1,2}/\d{1,2}/\d{4}$' then
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
  -- Un document comptable sans ICE ni IF n'est pas une facture : le contrôle
  -- le rejette. Devis, bon de commande et proforma restent libres — ils
  -- n'ouvrent ni droit à déduction ni créance.
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
  v_ice := regexp_replace(coalesce(v_doc.client_snapshot ->> 'ice', ''), '[\s-]', '', 'g');
  if v_ice <> '' and v_ice !~ '^\d{15}$' then
    raise exception 'L''ICE du client doit comporter 15 chiffres.'
      using errcode = '22023';
  end if;

  -- -------------------------------------------------------------------- quota
  if public.type_comptable(v_doc.type) and not public.boutique_est_pro(v_doc.shop_id) then
    select count(*) into v_utilise
    from public.documents
    where shop_id = v_doc.shop_id
      and public.type_comptable(type)
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


comment on function public.emettre_document(uuid) is
  'Émet un brouillon : date valide et non future, ICE/IF du vendeur sur un document comptable, référence sur avoir/régul, ICE client à 15 chiffres, quota gratuit sur tous les documents comptables.';
