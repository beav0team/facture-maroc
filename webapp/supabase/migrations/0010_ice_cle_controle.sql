-- FactureApp — 0010 : clé de contrôle ICE côté serveur
--
-- La migration 0008 ne vérifiait que la forme (15 chiffres) de l'ICE client.
-- Un ICE peut avoir 15 chiffres et une clé de contrôle erronée (chiffre
-- mal recopié, transposition). Cette migration ajoute la vérification du
-- modulo 97 côté base de données, pour qu'un appel direct à l'API ne puisse
-- pas contourner le contrôle de l'éditeur.
--
-- L'ICE compte 15 chiffres : 9 pour l'entreprise, 4 pour l'établissement
-- (0000 pour le siège) et 2 chiffres de contrôle. Les 15 chiffres lus comme
-- un nombre doivent donner un reste nul modulo 97.

create or replace function public.valider_ice_cle_controle(p_ice text)
returns boolean
language plpgsql
immutable
as $$
declare
  v_propre   text := replace(replace(coalesce(p_ice, ''), ' ', ''), '-', '');
  v_reste    integer := 0;
  v_i        integer;
  v_char     char(1);
begin
  if length(v_propre) <> 15 or v_propre !~ '^[0-9]{15}$' then
    return false;
  end if;

  for v_i in 1..length(v_propre) loop
    v_char := substring(v_propre from v_i for 1);
    v_reste := (v_reste * 10 + v_char::integer) % 97;
  end loop;

  return v_reste = 0;
end;
$$;

comment on function public.valider_ice_cle_controle(text) is
  'Vérifie la clé de contrôle d’un ICE marocain (modulo 97 sur les 15 chiffres).';

revoke all on function public.valider_ice_cle_controle(text) from public, anon;
grant execute on function public.valider_ice_cle_controle(text) to authenticated;

-- --------------------------------------------------------------------------
-- Émission : ajoute la clé de contrôle ICE client aux contrôles existants
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
  -- Format : 15 chiffres. Clé de contrôle : modulo 97 sur les 15 chiffres = 0.
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
  'Émet un brouillon : date valide et non future, ICE/IF du vendeur sur un document comptable, référence sur avoir/régul, ICE client à 15 chiffres avec clé de contrôle modulo 97, quota gratuit sur tous les documents comptables.';
