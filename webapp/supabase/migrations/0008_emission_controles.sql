-- FactureApp — 0008 : contrôles avant émission
--
-- `emettre_document()` prend un numéro qui ne se rend pas et fige le PDF : les
-- mentions qu'un contrôle cherche d'abord sont vérifiées ici, avant le numéro,
-- et pas seulement dans l'éditeur. Un client qui appellerait l'API directement
-- — ou un onglet dont le JavaScript a échoué — ne peut donc pas émettre une
-- facture sans ICE, sans IF, datée demain ou sans la référence de la facture
-- qu'un avoir annule.
--
-- Choix explicite : un client professionnel (IF, RC, patente ou CNSS renseigné)
-- sans ICE n'est PAS bloqué ici. La boutique ne peut pas inventer un numéro que
-- son client ne lui a pas envoyé, et refuser la vente au comptoir serait pire
-- que le champ manquant. L'éditeur demande une confirmation ; la base, elle,
-- accepte. Seul un ICE présent et mal formé est refusé.

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
  if v_doc.type in ('facture', 'ticket', 'simplifiee', 'export', 'avoir', 'regul', 'acompte') then
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

comment on function public.emettre_document(uuid) is
  'Émet un brouillon : date valide et non future, ICE/IF du vendeur sur un document comptable, référence sur avoir/régul, ICE client à 15 chiffres, quota gratuit.';
