-- FactureApp — documents : numérotation, quota, émission, avoir, paiements
--
-- Comme les autres tests RLS : deux utilisateurs, deux boutiques, une
-- transaction annulée à la fin. Couvre ce que le client ne peut pas faire
-- directement (insérer un document, prendre un numéro) et ce que les fonctions
-- doivent garantir (numéro unique, quota de 5, snapshot, avoir).
--
--   psql "$DATABASE_URL" -f supabase/tests/rls_documents.sql

begin;

do $$
declare
  v_a       uuid := '55555555-5555-4555-8555-555555555555';
  v_b       uuid := '66666666-6666-4666-8666-666666666666';
  v_admin   uuid := '77777777-7777-4777-8777-777777777777';
  v_owner   text := current_user;
  v_shop_a  uuid;
  v_shop_b  uuid;
  v_id      uuid;
  v_id2     uuid;
  v_avoir   uuid;
  v_numero  text;
  v_apercu  text;
  v_apercu2 text;
  v_result  jsonb;
  v_count   integer;
  v_touche  integer;
  v_bloque  boolean;
begin
  insert into auth.users (instance_id, id, aud, role, email)
  values
    ('00000000-0000-0000-0000-000000000000', v_a, 'authenticated', 'authenticated', 'doc-a@example.invalid'),
    ('00000000-0000-0000-0000-000000000000', v_b, 'authenticated', 'authenticated', 'doc-b@example.invalid'),
    ('00000000-0000-0000-0000-000000000000', v_admin, 'authenticated', 'authenticated', 'admin@example.invalid');

  -- ------------------------------------------------------------------ user A
  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);
  select (public.create_shop_for_me('Documents A')).id into v_shop_a;

  -- Une facture ne part pas sans l'identité du vendeur : l'ICE et l'IF sont
  -- renseignés tout de suite, comme une boutique réelle les remplit avant de
  -- facturer.
  perform set_config('role', v_owner, true);
  update public.shops set ice = '001234567000062', if_fiscal = '12345678' where id = v_shop_a;
  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);

  -- L'aperçu ne consomme pas.
  v_apercu := public.apercu_numero(v_shop_a, 'facture');
  v_apercu2 := public.apercu_numero(v_shop_a, 'facture');
  if v_apercu <> v_apercu2 then
    raise exception 'apercu_numero a consommé un numéro : % puis %', v_apercu, v_apercu2;
  end if;
  if v_apercu !~ '^F-[0-9]{8}-0001$' then
    raise exception 'aperçu inattendu : %', v_apercu;
  end if;

  -- Un brouillon se crée par la fonction, jamais par un insert direct.
  v_id := public.enregistrer_brouillon(
    null,
    jsonb_build_object(
      'shop_id', v_shop_a,
      'type', 'facture',
      'date_emission', '10/11/2026',
      'client_snapshot', jsonb_build_object('nom', 'Garage Atlas', 'ice', '001234567000062'),
      'conditions_paiement', '30j',
      'remise_pourcent', 10,
      'total_ht', 900, 'remise', 90, 'tva', 162, 'total_ttc', 972, 'net_a_payer', 972
    ),
    jsonb_build_array(
      jsonb_build_object(
        'libelle', 'Huile 5W30', 'quantite', 2, 'prix_unitaire', 500,
        'tva_taux', 20, 'total_ht', 1000
      )
    )
  );

  select count(*) into v_count from public.document_lines where document_id = v_id;
  if v_count <> 1 then
    raise exception 'le brouillon devrait avoir 1 ligne, en a %', v_count;
  end if;

  -- Émission : numéro, date, snapshot figé.
  v_result := public.emettre_document(v_id);
  if v_result ->> 'numero' <> v_apercu then
    raise exception 'numéro émis % différent de l''aperçu %', v_result ->> 'numero', v_apercu;
  end if;
  if (v_result ->> 'date_emission') <> '10/11/2026' then
    raise exception 'la date saisie a été écrasée : %', v_result ->> 'date_emission';
  end if;

  select shop_snapshot ->> 'nom' into v_numero from public.documents where id = v_id;
  if v_numero <> 'Documents A' then
    raise exception 'snapshot boutique manquant : %', v_numero;
  end if;

  -- Le numéro suivant est pris.
  v_id2 := public.enregistrer_brouillon(
    null,
    jsonb_build_object('shop_id', v_shop_a, 'type', 'facture', 'client_snapshot', jsonb_build_object('nom', 'Client 2')),
    jsonb_build_array(jsonb_build_object('libelle', 'Service', 'quantite', 1, 'prix_unitaire', 100, 'tva_taux', 20, 'total_ht', 100))
  );
  v_numero := (public.emettre_document(v_id2)) ->> 'numero';
  if v_numero !~ '-0002$' then
    raise exception 'deuxième facture numérotée %', v_numero;
  end if;

  -- Un document émis ne se modifie plus.
  begin
    perform public.enregistrer_brouillon(v_id, jsonb_build_object('shop_id', v_shop_a, 'type', 'facture'), '[]'::jsonb);
    raise exception 'un document émis a été modifié';
  exception
    when sqlstate '22023' then null;
  end;

  -- Avoir : copie en brouillon, référence la facture, préfixe A.
  v_avoir := public.creer_avoir(v_id);
  select reference into v_numero from public.documents where id = v_avoir;
  if v_numero <> (v_result ->> 'numero') then
    raise exception 'l''avoir référence % au lieu de %', v_numero, v_result ->> 'numero';
  end if;
  v_numero := (public.emettre_document(v_avoir)) ->> 'numero';
  if v_numero !~ '^A-[0-9]{8}-0001$' then
    raise exception 'avoir numéroté %', v_numero;
  end if;

  -- Paiement partiel.
  insert into public.payments (document_id, montant, date_paiement, moyen)
  values (v_id, 500, '12/11/2026', 'cash');
  select count(*) into v_count from public.payments where document_id = v_id;
  if v_count <> 1 then
    raise exception 'paiement non enregistré';
  end if;

  -- Suppression : un brouillon oui, un émis non.
  delete from public.documents where id = v_id;
  get diagnostics v_touche = row_count;
  if v_touche <> 0 then
    raise exception 'un document émis a été supprimé';
  end if;

  -- ------------------------------------------------------------------ user B
  perform set_config('role', v_owner, true);
  perform set_config('request.jwt.claims', json_build_object('sub', v_b, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);
  select (public.create_shop_for_me('Documents B')).id into v_shop_b;

  select count(*) into v_count from public.documents;
  if v_count <> 0 then
    raise exception 'B voit % document(s) de A', v_count;
  end if;

  select count(*) into v_count from public.payments;
  if v_count <> 0 then
    raise exception 'B voit des paiements de A';
  end if;

  begin
    perform public.emettre_document(v_id);
    raise exception 'B a émis un document de A';
  exception
    when sqlstate '42501' then null;
  end;

  begin
    insert into public.documents (shop_id, type) values (v_shop_a, 'facture');
    raise exception 'B a inséré un document directement';
  exception
    when insufficient_privilege then null;
  end;

  -- Le quota : cinq factures par mois pour une boutique gratuite.
  perform set_config('role', v_owner, true);
  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);

  -- Déjà deux factures émises ci-dessus : trois de plus atteignent la limite.
  for i in 1..3 loop
    v_id := public.enregistrer_brouillon(
      null,
      jsonb_build_object('shop_id', v_shop_a, 'type', 'facture', 'client_snapshot', jsonb_build_object('nom', 'Client ' || i)),
      jsonb_build_array(jsonb_build_object('libelle', 'Service', 'quantite', 1, 'prix_unitaire', 100, 'tva_taux', 20, 'total_ht', 100))
    );
    perform public.emettre_document(v_id);
  end loop;

  v_id := public.enregistrer_brouillon(
    null,
    jsonb_build_object('shop_id', v_shop_a, 'type', 'facture', 'client_snapshot', jsonb_build_object('nom', 'Client 6')),
    jsonb_build_array(jsonb_build_object('libelle', 'Service', 'quantite', 1, 'prix_unitaire', 100, 'tva_taux', 20, 'total_ht', 100))
  );

  v_bloque := false;
  begin
    perform public.emettre_document(v_id);
  exception
    when sqlstate 'P0001' then v_bloque := true;
  end;
  if not v_bloque then
    raise exception 'la sixième facture gratuite est passée';
  end if;

  -- Un ticket compte aussi : c'était la fuite de la 0009 (quota limité aux
  -- factures, tickets illimités au comptoir).
  v_id := public.enregistrer_brouillon(
    null,
    jsonb_build_object('shop_id', v_shop_a, 'type', 'ticket', 'client_snapshot', jsonb_build_object('nom', 'Client ticket')),
    jsonb_build_array(jsonb_build_object('libelle', 'Service', 'quantite', 1, 'prix_unitaire', 100, 'tva_taux', 20, 'total_ht', 100))
  );
  v_bloque := false;
  begin
    perform public.emettre_document(v_id);
  exception
    when sqlstate 'P0001' then v_bloque := true;
  end;
  if not v_bloque then
    raise exception 'un ticket est passé alors que le quota est atteint';
  end if;

  -- Un avoir reste possible même quota atteint : corriger une facture est une
  -- obligation, pas une vente.
  v_id := public.enregistrer_brouillon(
    null,
    jsonb_build_object(
      'shop_id', v_shop_a, 'type', 'avoir', 'reference', v_result ->> 'numero',
      'client_snapshot', jsonb_build_object('nom', 'Garage Atlas', 'ice', '001234567000062')
    ),
    jsonb_build_array(jsonb_build_object('libelle', 'Annulation', 'quantite', 1, 'prix_unitaire', 100, 'tva_taux', 20, 'total_ht', 100))
  );
  perform public.emettre_document(v_id);

  -- Le devis reste gratuit : il ne facture rien.
  v_id2 := public.enregistrer_brouillon(
    null,
    jsonb_build_object('shop_id', v_shop_a, 'type', 'devis', 'client_snapshot', jsonb_build_object('nom', 'Client devis')),
    jsonb_build_array(jsonb_build_object('libelle', 'Service', 'quantite', 1, 'prix_unitaire', 100, 'tva_taux', 20, 'total_ht', 100))
  );
  perform public.emettre_document(v_id2);

  v_result := public.quota_boutique(v_shop_a);
  if (v_result ->> 'utilise')::integer <> 5 or (v_result ->> 'limite')::integer <> 5 then
    raise exception 'quota incohérent : %', v_result;
  end if;

  -- ------------------------------------------------------------------ admin
  perform set_config('role', v_owner, true);
  insert into public.platform_admins (user_id) values (v_admin);
  insert into public.subscriptions (shop_id, plan, paid_until, source)
  values (v_shop_a, 'pro', current_date + 30, 'test');

  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);
  v_id := public.enregistrer_brouillon(
    null,
    jsonb_build_object('shop_id', v_shop_a, 'type', 'facture', 'client_snapshot', jsonb_build_object('nom', 'Client Pro')),
    jsonb_build_array(jsonb_build_object('libelle', 'Service', 'quantite', 1, 'prix_unitaire', 100, 'tva_taux', 20, 'total_ht', 100))
  );
  perform public.emettre_document(v_id);
  if not public.boutique_est_pro(v_shop_a) then
    raise exception 'l''abonnement Pro n''est pas lu';
  end if;

  -- Un membre ne peut pas s'offrir un abonnement.
  begin
    insert into public.subscriptions (shop_id, plan, paid_until) values (v_shop_b, 'pro', current_date + 30);
    raise exception 'un membre a écrit un abonnement';
  exception
    when insufficient_privilege then null;
  end;

  -- Remise à zéro annuelle : un compteur de l'année dernière repart à 1.
  perform set_config('role', v_owner, true);
  insert into public.numerotation (shop_id, type, annee, prochaine_valeur)
  values (v_shop_a, 'export', extract(year from current_date)::integer - 1, 42);
  update public.shops set numerotation_annuelle = true where id = v_shop_a;
  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);

  v_apercu := public.apercu_numero(v_shop_a, 'export');
  if v_apercu !~ '^E-[0-9]{8}-0001$' then
    raise exception 'la remise à zéro annuelle n''a pas repris à 1 : %', v_apercu;
  end if;
  v_id := public.enregistrer_brouillon(
    null,
    jsonb_build_object('shop_id', v_shop_a, 'type', 'export', 'client_snapshot', jsonb_build_object('nom', 'Client export')),
    jsonb_build_array(jsonb_build_object('libelle', 'Marchandise', 'quantite', 1, 'prix_unitaire', 100, 'tva_taux', 0, 'total_ht', 100))
  );
  v_numero := (public.emettre_document(v_id)) ->> 'numero';
  if v_numero <> v_apercu then
    raise exception 'export numéroté % au lieu de %', v_numero, v_apercu;
  end if;

  -- ------------------------------------------------------------------ import
  -- Les compteurs d'une sauvegarde Android continuent la série, et ne la
  -- font jamais reculer.
  perform set_config('role', v_owner, true);
  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);

  v_apercu2 := public.apercu_numero(v_shop_a, 'facture');
  perform public.importer_compteurs(v_shop_a, jsonb_build_object('devis', 7, 'facture', 2));
  v_apercu := public.apercu_numero(v_shop_a, 'devis');
  if v_apercu !~ '-0007$' then
    raise exception 'le compteur importé n''a pas été repris : %', v_apercu;
  end if;
  if public.apercu_numero(v_shop_a, 'facture') <> v_apercu2 then
    raise exception 'l''import a fait reculer le compteur de factures';
  end if;

  -- B ne peut pas importer dans la boutique de A.
  perform set_config('role', v_owner, true);
  perform set_config('request.jwt.claims', json_build_object('sub', v_b, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);
  begin
    perform public.importer_compteurs(v_shop_a, jsonb_build_object('devis', 99));
    raise exception 'B a importé des compteurs chez A';
  exception
    when sqlstate '42501' then null;
  end;

  -- ------------------------------------------- contrôles avant émission
  perform set_config('role', v_owner, true);
  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);

  -- Une date qui n'existe pas.
  v_id := public.enregistrer_brouillon(
    null,
    jsonb_build_object('shop_id', v_shop_a, 'type', 'facture', 'date_emission', '31/02/2026',
                       'client_snapshot', jsonb_build_object('nom', 'Client date')),
    jsonb_build_array(jsonb_build_object('libelle', 'Service', 'quantite', 1, 'prix_unitaire', 100, 'tva_taux', 20, 'total_ht', 100))
  );
  v_bloque := false;
  begin
    perform public.emettre_document(v_id);
  exception
    when sqlstate '22007' then v_bloque := true;
  end;
  if not v_bloque then
    raise exception 'une facture datée du 31 février a été émise';
  end if;

  -- Une date future.
  v_id := public.enregistrer_brouillon(
    null,
    jsonb_build_object('shop_id', v_shop_a, 'type', 'facture',
                       'date_emission', to_char(current_date + 1, 'DD/MM/YYYY'),
                       'client_snapshot', jsonb_build_object('nom', 'Client futur')),
    jsonb_build_array(jsonb_build_object('libelle', 'Service', 'quantite', 1, 'prix_unitaire', 100, 'tva_taux', 20, 'total_ht', 100))
  );
  v_bloque := false;
  begin
    perform public.emettre_document(v_id);
  exception
    when sqlstate '22007' then v_bloque := true;
  end;
  if not v_bloque then
    raise exception 'une facture datée de demain a été émise';
  end if;

  -- Un ICE client qui n'a pas quinze chiffres.
  v_id := public.enregistrer_brouillon(
    null,
    jsonb_build_object('shop_id', v_shop_a, 'type', 'facture',
                       'client_snapshot', jsonb_build_object('nom', 'Client ICE', 'ice', '12345')),
    jsonb_build_array(jsonb_build_object('libelle', 'Service', 'quantite', 1, 'prix_unitaire', 100, 'tva_taux', 20, 'total_ht', 100))
  );
  v_bloque := false;
  begin
    perform public.emettre_document(v_id);
  exception
    when sqlstate '22023' then v_bloque := true;
  end;
  if not v_bloque then
    raise exception 'un ICE client à cinq chiffres est passé';
  end if;

  -- Un ICE client à quinze chiffres mais avec une clé de contrôle erronée.
  -- Le modulo 97 sur les 15 chiffres doit donner 0.
  v_id := public.enregistrer_brouillon(
    null,
    jsonb_build_object('shop_id', v_shop_a, 'type', 'facture',
                       'client_snapshot', jsonb_build_object('nom', 'Client ICE clé', 'ice', '001534931000017')),
    jsonb_build_array(jsonb_build_object('libelle', 'Service', 'quantite', 1, 'prix_unitaire', 100, 'tva_taux', 20, 'total_ht', 100))
  );
  v_bloque := false;
  begin
    perform public.emettre_document(v_id);
  exception
    when sqlstate '22023' then v_bloque := true;
  end;
  if not v_bloque then
    raise exception 'un ICE client avec une clé de contrôle erronée est passé';
  end if;

  -- Un ICE client valide (clé de contrôle correcte) passe.
  v_id := public.enregistrer_brouillon(
    null,
    jsonb_build_object('shop_id', v_shop_a, 'type', 'facture',
                       'client_snapshot', jsonb_build_object('nom', 'Client ICE valide', 'ice', '001534931000016')),
    jsonb_build_array(jsonb_build_object('libelle', 'Service', 'quantite', 1, 'prix_unitaire', 100, 'tva_taux', 20, 'total_ht', 100))
  );
  perform public.emettre_document(v_id);

  -- Un client professionnel sans ICE n'est pas bloqué : la boutique ne peut
  -- pas inventer un numéro que son client ne lui a pas envoyé.
  v_id := public.enregistrer_brouillon(
    null,
    jsonb_build_object('shop_id', v_shop_a, 'type', 'facture',
                       'client_snapshot', jsonb_build_object('nom', 'Fournisseur', 'if_fiscal', '87654321')),
    jsonb_build_array(jsonb_build_object('libelle', 'Service', 'quantite', 1, 'prix_unitaire', 100, 'tva_taux', 20, 'total_ht', 100))
  );
  perform public.emettre_document(v_id);

  -- Un avoir sans référence ne dit pas ce qu'il annule.
  v_id := public.enregistrer_brouillon(
    null,
    jsonb_build_object('shop_id', v_shop_a, 'type', 'avoir', 'reference', '',
                       'client_snapshot', jsonb_build_object('nom', 'Client avoir')),
    jsonb_build_array(jsonb_build_object('libelle', 'Service', 'quantite', 1, 'prix_unitaire', 100, 'tva_taux', 20, 'total_ht', 100))
  );
  v_bloque := false;
  begin
    perform public.emettre_document(v_id);
  exception
    when sqlstate '22023' then v_bloque := true;
  end;
  if not v_bloque then
    raise exception 'un avoir sans référence est passé';
  end if;

  -- Une boutique sans ICE ne peut pas émettre de facture, mais un devis passe.
  perform set_config('role', v_owner, true);
  update public.shops set ice = '' where id = v_shop_a;
  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);

  v_id := public.enregistrer_brouillon(
    null,
    jsonb_build_object('shop_id', v_shop_a, 'type', 'facture',
                       'client_snapshot', jsonb_build_object('nom', 'Client sans ICE vendeur')),
    jsonb_build_array(jsonb_build_object('libelle', 'Service', 'quantite', 1, 'prix_unitaire', 100, 'tva_taux', 20, 'total_ht', 100))
  );
  v_bloque := false;
  begin
    perform public.emettre_document(v_id);
  exception
    when sqlstate '22023' then v_bloque := true;
  end;
  if not v_bloque then
    raise exception 'une facture sans ICE vendeur est passée';
  end if;

  v_id := public.enregistrer_brouillon(
    null,
    jsonb_build_object('shop_id', v_shop_a, 'type', 'devis',
                       'client_snapshot', jsonb_build_object('nom', 'Client devis sans ICE')),
    jsonb_build_array(jsonb_build_object('libelle', 'Service', 'quantite', 1, 'prix_unitaire', 100, 'tva_taux', 20, 'total_ht', 100))
  );
  perform public.emettre_document(v_id);

  perform set_config('role', v_owner, true);
  update public.shops set ice = '001234567000062' where id = v_shop_a;

  raise notice 'Documents : numérotation, quota, émission, avoir et contrôles vérifiés.';
end;
$$;

rollback;
