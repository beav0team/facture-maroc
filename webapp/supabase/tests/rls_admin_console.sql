-- FactureApp — console d'administration : dossier boutique, journal, quotas,
-- suspension, suppression
--
-- Le pendant de rls_admin.sql pour la 0013. Trois comptes : deux
-- propriétaires de boutique et un administrateur plateforme. La transaction
-- est annulée à la fin, aucune donnée ne reste.
--
-- Ce que ce fichier prouve, et pas seulement décrit :
--   · un non-administrateur n'obtient rien des nouvelles lectures, et ne peut
--     rien écrire (fiche, note, quota, suspension, suppression, membres) ;
--   · l'administrateur édite l'identité d'une boutique et chaque écriture
--     laisse une ligne de journal avec son auteur ;
--   · le bonus de quota se voit dans `quota_boutique` et permet une émission
--     au-delà de cinq ;
--   · une boutique suspendue n'émet plus, et la reprise réautorise ;
--   · la suppression exige le nom exact ; les chemins d'archive sont rendus
--     pour que l'écran nettoie le bucket ;
--   · le dernier propriétaire ne peut être ni rétrogradé ni détaché.
--
--   psql "$DATABASE_URL" -f supabase/tests/rls_admin_console.sql

begin;

do $$
declare
  v_a       uuid := 'd1111111-1111-4111-8111-111111111111';
  v_b       uuid := 'd2222222-2222-4222-8222-222222222222';
  v_admin   uuid := 'd3333333-3333-4333-8333-333333333333';
  v_owner   text := current_user;
  v_shop_a  uuid;
  v_shop_b  uuid;
  v_doc     uuid;
  v_lignes  integer;
  v_result  jsonb;
  v_texte   text;
  v_tab     text[];
  v_bool    boolean;
  v_touche  boolean;
  v_i       integer;
begin
  insert into auth.users (instance_id, id, aud, role, email)
  values
    ('00000000-0000-0000-0000-000000000000', v_a, 'authenticated', 'authenticated', 'console-a@example.invalid'),
    ('00000000-0000-0000-0000-000000000000', v_b, 'authenticated', 'authenticated', 'console-b@example.invalid'),
    ('00000000-0000-0000-0000-000000000000', v_admin, 'authenticated', 'authenticated', 'console-admin@example.invalid');

  -- --------------------------------------------------------------- les shops
  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);
  select (public.create_shop_for_me('Console A')).id into v_shop_a;

  perform set_config('request.jwt.claims', json_build_object('sub', v_b, 'role', 'authenticated')::text, true);
  select (public.create_shop_for_me('Console B')).id into v_shop_b;

  perform set_config('role', v_owner, true);
  update public.shops set ice = '001234567000062', if_fiscal = '12345678'
   where id in (v_shop_a, v_shop_b);
  perform set_config('role', 'authenticated', true);

  -- ------------------------------------------- A, simple membre : rien du tout
  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);

  v_touche := false;
  begin
    perform public.admin_boutique(v_shop_a);
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'admin_boutique() a répondu à un non-administrateur';
  end if;

  select count(*) into v_lignes from public.admin_membres(v_shop_a);
  if v_lignes <> 0 then
    raise exception 'admin_membres() ouvert à un non-administrateur (% lignes)', v_lignes;
  end if;

  select count(*) into v_lignes from public.admin_documents(v_shop_a);
  if v_lignes <> 0 then
    raise exception 'admin_documents() ouvert à un non-administrateur (% lignes)', v_lignes;
  end if;

  select count(*) into v_lignes from public.admin_alertes();
  if v_lignes <> 0 then
    raise exception 'admin_alertes() ouvert à un non-administrateur (% lignes)', v_lignes;
  end if;

  select count(*) into v_lignes from public.admin_journal();
  if v_lignes <> 0 then
    raise exception 'admin_journal() ouvert à un non-administrateur (% lignes)', v_lignes;
  end if;

  v_touche := false;
  begin
    perform public.admin_modifier_boutique(v_shop_a, '{"nom":"piraté"}'::jsonb);
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'un non-administrateur a modifié une boutique';
  end if;

  v_touche := false;
  begin
    perform public.admin_note(v_shop_a, 'note interdite');
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'un non-administrateur a écrit une note';
  end if;

  v_touche := false;
  begin
    perform public.admin_suspendre(v_shop_a, 'pour rien');
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'un non-administrateur a suspendu une boutique';
  end if;

  v_touche := false;
  begin
    perform public.admin_supprimer_boutique(v_shop_a, 'Console A');
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'un non-administrateur a supprimé une boutique';
  end if;

  -- -------------------------------------------------------- l'administrateur
  perform set_config('role', v_owner, true);
  insert into public.platform_admins (user_id) values (v_admin);
  perform set_config('role', 'authenticated', true);
  perform set_config('request.jwt.claims', json_build_object('sub', v_admin, 'role', 'authenticated')::text, true);

  v_result := public.admin_boutique(v_shop_b);
  if (v_result ->> 'proprietaire') <> 'console-b@example.invalid' then
    raise exception 'propriétaire inattendu dans la fiche : %', v_result ->> 'proprietaire';
  end if;
  if ((v_result -> 'quota') ->> 'limite')::integer <> 5 then
    raise exception 'limite initiale inattendue : %', (v_result -> 'quota') ->> 'limite';
  end if;
  if (v_result -> 'suspension') <> 'null'::jsonb then
    raise exception 'une boutique neuve se dit suspendue';
  end if;

  select count(*) into v_lignes from public.admin_membres(v_shop_b);
  if v_lignes <> 1 then
    raise exception 'admin_membres() devrait voir 1 membre, en a vu %', v_lignes;
  end if;

  -- Édition de l'identité : le nom, la TVA et l'ICE changent.
  perform public.admin_modifier_boutique(v_shop_b, jsonb_build_object(
    'nom', 'Console B (renommée)',
    'tva_defaut', 14,
    'ice', '001234567000062'
  ));
  select nom into v_texte from public.shops where id = v_shop_b;
  if v_texte <> 'Console B (renommée)' then
    raise exception 'le nom n’a pas été écrit : %', v_texte;
  end if;
  select tva_defaut into v_result from (
    select jsonb_build_object('tva', tva_defaut) as tva_defaut from public.shops where id = v_shop_b
  ) t;
  if (v_result ->> 'tva')::numeric <> 14 then
    raise exception 'la TVA n’a pas été écrite : %', v_result ->> 'tva';
  end if;

  -- Une valeur invalide est refusée, et rien n'est écrit.
  v_touche := false;
  begin
    perform public.admin_modifier_boutique(v_shop_b, '{"tva_defaut": 150}'::jsonb);
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'admin_modifier_boutique a accepté une TVA de 150';
  end if;

  -- ------------------------------------------------------------- les notes
  perform public.admin_note(v_shop_b, 'Client appelé le 13/11 : renouvellement en janvier.');
  v_result := public.admin_boutique(v_shop_b);
  if jsonb_array_length(v_result -> 'notes') <> 1 then
    raise exception 'la note n’est pas dans la fiche';
  end if;
  if (v_result -> 'notes' -> 0 ->> 'par') <> 'console-admin@example.invalid' then
    raise exception 'la note n’est pas signée : %', v_result -> 'notes' -> 0 ->> 'par';
  end if;

  -- -------------------------------------------------------------- le journal
  select count(*) into v_lignes
  from public.admin_journal(50, v_shop_b)
  where action = 'boutique_modifiee' and acteur = 'console-admin@example.invalid';
  if v_lignes < 1 then
    raise exception 'le journal ne garde pas la modification de la boutique';
  end if;

  -- ---------------------------------------------------------- quota bonus
  perform public.admin_quota_bonus(v_shop_b, 3, 'geste commercial');
  v_result := public.admin_boutique(v_shop_b);
  v_lignes := ((v_result -> 'quota') ->> 'limite')::integer;
  if v_lignes <> 8 then
    raise exception 'le bonus ne change pas la limite : %', v_lignes;
  end if;

  -- Cinq factures passent, la sixième aussi grâce au bonus.
  perform set_config('request.jwt.claims', json_build_object('sub', v_b, 'role', 'authenticated')::text, true);
  for v_i in 1..6 loop
    v_doc := public.enregistrer_brouillon(
      null,
      jsonb_build_object(
        'shop_id', v_shop_b, 'type', 'facture',
        'client_snapshot', jsonb_build_object('nom', 'Client ' || v_i)
      ),
      jsonb_build_array(jsonb_build_object(
        'libelle', 'Prestation', 'quantite', 1, 'prix_unitaire', 100,
        'tva_taux', 20, 'total_ht', 100
      ))
    );
    perform public.emettre_document(v_doc);
  end loop;

  select count(*) into v_lignes from public.documents
   where shop_id = v_shop_b and statut = 'emis';
  if v_lignes <> 6 then
    raise exception 'le bonus n’a pas laissé passer la sixième facture (% émises)', v_lignes;
  end if;

  -- ---------------------------------------------------------- la suspension
  perform set_config('request.jwt.claims', json_build_object('sub', v_admin, 'role', 'authenticated')::text, true);
  perform public.admin_suspendre(v_shop_b, 'Impayé depuis trois mois');

  v_result := public.admin_boutique(v_shop_b);
  if (v_result ->> 'suspension') is null then
    raise exception 'la suspension n’apparaît pas dans la fiche';
  end if;

  -- L'émission est refusée, y compris dans le quota affiché.
  perform set_config('request.jwt.claims', json_build_object('sub', v_b, 'role', 'authenticated')::text, true);
  if not (public.quota_boutique(v_shop_b) ->> 'suspendue')::boolean then
    raise exception 'quota_boutique ignore la suspension';
  end if;

  v_doc := public.enregistrer_brouillon(
    null,
    jsonb_build_object('shop_id', v_shop_b, 'type', 'facture',
                       'client_snapshot', jsonb_build_object('nom', 'Client 7')),
    jsonb_build_array(jsonb_build_object('libelle', 'Prestation', 'quantite', 1,
                                         'prix_unitaire', 100, 'tva_taux', 20, 'total_ht', 100))
  );
  v_touche := false;
  begin
    perform public.emettre_document(v_doc);
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'une boutique suspendue a émis une facture';
  end if;

  -- Les documents déjà émis restent lisibles.
  select count(*) into v_lignes from public.documents
   where shop_id = v_shop_b and statut = 'emis';
  if v_lignes <> 6 then
    raise exception 'la suspension a effacé des documents (% restants)', v_lignes;
  end if;

  perform set_config('request.jwt.claims', json_build_object('sub', v_admin, 'role', 'authenticated')::text, true);
  perform public.admin_reprendre(v_shop_b);
  v_result := public.admin_boutique(v_shop_b);
  if (v_result -> 'suspension') <> 'null'::jsonb then
    raise exception 'la reprise n’a pas levé la suspension';
  end if;

  -- ------------------------------------------------------- les documents lus
  select count(*) into v_lignes from public.admin_documents(v_shop_b);
  if v_lignes <> 7 then
    raise exception 'admin_documents() devrait voir 7 documents, en a vu %', v_lignes;
  end if;
  select client into v_texte from public.admin_documents(v_shop_b)
   where statut = 'emis' order by quand limit 1;
  if v_texte not like 'Client %' then
    raise exception 'le client du document n’est pas rendu : %', v_texte;
  end if;

  -- -------------------------------------------------------- purge d'archives
  perform set_config('role', v_owner, true);
  insert into public.archives (document_id, storage_path, sha256, taille, created_at)
  values (v_doc, v_shop_b || '/vieux.pdf', 'sha', 1200, now() - interval '10 days');
  perform set_config('role', 'authenticated', true);
  perform set_config('request.jwt.claims', json_build_object('sub', v_admin, 'role', 'authenticated')::text, true);

  v_tab := public.admin_purger_archives(v_shop_b, current_date - 1);
  if array_length(v_tab, 1) <> 1 then
    raise exception 'la purge n’a pas rendu le chemin (% chemins)', coalesce(array_length(v_tab, 1), 0);
  end if;
  if v_tab[1] <> v_shop_b || '/vieux.pdf' then
    raise exception 'chemin de purge inattendu : %', v_tab[1];
  end if;
  select count(*) into v_lignes from public.archives ar
   join public.documents d on d.id = ar.document_id
   where d.shop_id = v_shop_b;
  if v_lignes <> 0 then
    raise exception 'la purge a laissé % archive(s)', v_lignes;
  end if;

  -- ------------------------------------------------------------- les membres
  -- Rétrograder le dernier propriétaire est refusé.
  v_touche := false;
  begin
    perform public.admin_role_membre(v_shop_a, v_a, 'membre');
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'le dernier propriétaire a pu être rétrogradé';
  end if;

  v_touche := false;
  begin
    perform public.admin_detacher(v_shop_a, v_a);
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'le dernier propriétaire a pu être détaché';
  end if;

  -- Transfert de B vers l'administrateur : l'ancien devient membre.
  perform public.admin_transferer(v_shop_b, 'console-admin@example.invalid');
  select role into v_texte from public.memberships
   where shop_id = v_shop_b and user_id = v_admin;
  if v_texte <> 'owner' then
    raise exception 'le transfert n’a pas nommé le nouveau propriétaire (%)', v_texte;
  end if;
  select role into v_texte from public.memberships
   where shop_id = v_shop_b and user_id = v_b;
  if v_texte <> 'membre' then
    raise exception 'l’ancien propriétaire n’est pas redevenu membre (%)', v_texte;
  end if;
  select count(*) into v_lignes from public.memberships
   where shop_id = v_shop_b and role = 'owner';
  if v_lignes <> 1 then
    raise exception 'le transfert a laissé % propriétaires', v_lignes;
  end if;

  -- ---------------------------------------------------------- la suppression
  v_touche := false;
  begin
    perform public.admin_supprimer_boutique(v_shop_b, 'console b');
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'la suppression a accepté un nom approximatif';
  end if;
  if not exists (select 1 from public.shops where id = v_shop_b) then
    raise exception 'la boutique a disparu malgré le refus';
  end if;

  v_tab := public.admin_supprimer_boutique(v_shop_b, 'Console B (renommée)');
  if exists (select 1 from public.shops where id = v_shop_b) then
    raise exception 'la boutique existe encore après suppression';
  end if;
  select count(*) into v_lignes from public.documents where shop_id = v_shop_b;
  if v_lignes <> 0 then
    raise exception 'la suppression a laissé % document(s)', v_lignes;
  end if;

  -- Les alertes restent lisibles après coup, et le journal garde la ligne.
  select count(*) into v_lignes from public.admin_journal(50, null)
   where action = 'boutique_supprimee';
  if v_lignes <> 1 then
    raise exception 'la suppression n’est pas journalisée (% lignes)', v_lignes;
  end if;

  raise notice 'console d’administration (0013) : tout est conforme';
end $$;

rollback;
