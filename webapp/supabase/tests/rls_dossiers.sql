-- FactureApp — dossiers imbriqués (0011, 0012)
--
-- Même méthode que rls_catalogue.sql : deux utilisateurs, deux boutiques, une
-- transaction annulée à la fin. Vérifie les règles que l'écran ne peut pas
-- prouver — la matérialisation des parents, la casse, le préfixe qui n'attrape
-- pas « Mobilier d'appoint », la copie sans code-barres, les deux modes de
-- suppression — plus l'isolation entre boutiques.
--
--   psql "$DATABASE_URL" -f supabase/tests/rls_dossiers.sql

begin;

do $$
declare
  v_a       uuid := '55555555-5555-4555-8555-555555555555';
  v_b       uuid := '66666666-6666-4666-8666-666666666666';
  v_owner   text := current_user;
  v_shop_a  uuid;
  v_shop_b  uuid;
  v_res     jsonb;
  v_count   integer;
  v_touche  integer;
  v_article uuid;
  v_libelle text;
  v_code    text;
begin
  insert into auth.users (instance_id, id, aud, role, email)
  values
    ('00000000-0000-0000-0000-000000000000', v_a, 'authenticated', 'authenticated', 'dos-a@example.invalid'),
    ('00000000-0000-0000-0000-000000000000', v_b, 'authenticated', 'authenticated', 'dos-b@example.invalid');

  -- ------------------------------------------------------------------ user A
  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);

  select (public.create_shop_for_me('Dossiers A')).id into v_shop_a;

  insert into public.articles (shop_id, libelle, prix, dossier, code_barre)
  values (v_shop_a, 'Chaise en rotin', 850, 'Mobilier/Chaises', '6111234567890'),
         (v_shop_a, 'Table basse', 1200, 'Mobilier', '6111234567891'),
         (v_shop_a, 'Nettoyage', 300, '', '');

  -- Le déclencheur a écrit les parents, sans quoi la grille ne dessinerait pas
  -- « Mobilier » et le rayon serait inatteignable.
  select count(*) into v_count
    from public.article_folders
   where shop_id = v_shop_a;
  if v_count <> 2 then
    raise exception 'attendu 2 dossiers après insertion, obtenu %', v_count;
  end if;

  -- « MobiLier / Chaises » est le même rayon : le chemin est canonicalisé et la
  -- première orthographe de la tuile est conservée.
  insert into public.articles (shop_id, libelle, prix, dossier)
  values (v_shop_a, 'Rotin brut', 90, '  Mobilier /  Chaises  ');
  select dossier into v_libelle from public.articles where libelle = 'Rotin brut';
  if v_libelle <> 'Mobilier/Chaises' then
    raise exception 'chemin non canonicalisé : « % »', v_libelle;
  end if;
  select count(*) into v_count from public.article_folders where shop_id = v_shop_a;
  if v_count <> 2 then
    raise exception 'la casse ou les espaces ont créé un doublon de dossier (%)', v_count;
  end if;

  -- Un frère qui commence par les mêmes lettres n'est pas dans le dossier.
  if public.dossier_est_dans('Mobilier d''appoint', 'Mobilier') then
    raise exception '« Mobilier d''appoint » lu comme dans « Mobilier »';
  end if;
  if not public.dossier_est_dans('Mobilier/Chaises', 'Mobilier') then
    raise exception 'un sous-dossier n''est pas vu dans son parent';
  end if;

  -- ------------------------------------------------------------ créer/renommer
  v_res := public.creer_dossier(v_shop_a, '', 'Entretien');
  if not (v_res ->> 'ok')::boolean then
    raise exception 'création d''un dossier vide refusée : %', v_res;
  end if;

  v_res := public.creer_dossier(v_shop_a, '', 'entretien');
  if (v_res ->> 'raison') is distinct from 'nom_pris' then
    raise exception 'un doublon de dossier (casse différente) a été accepté : %', v_res;
  end if;

  v_res := public.creer_dossier(v_shop_a, '', 'Chaises/Rotin');
  if (v_res ->> 'raison') is distinct from 'nom_invalide' then
    raise exception 'un nom contenant « / » a été accepté : %', v_res;
  end if;

  -- Un dossier vide survit : c'est toute la raison de la table.
  select count(*) into v_count from public.article_folders
   where shop_id = v_shop_a and chemin = 'Entretien';
  if v_count <> 1 then
    raise exception 'le dossier vide n''a pas été créé';
  end if;

  v_res := public.renommer_dossier(v_shop_a, 'Mobilier', 'Meubles');
  if not (v_res ->> 'ok')::boolean then
    raise exception 'renommage refusé : %', v_res;
  end if;
  select dossier into v_libelle from public.articles where libelle = 'Chaise en rotin';
  if v_libelle <> 'Meubles/Chaises' then
    raise exception 'le sous-arbre n''a pas suivi le renommage : « % »', v_libelle;
  end if;
  select count(*) into v_count from public.article_folders
   where shop_id = v_shop_a and chemin like 'Meubles%';
  if v_count <> 2 then
    raise exception 'les dossiers du sous-arbre n''ont pas suivi : %', v_count;
  end if;

  v_res := public.renommer_dossier(v_shop_a, 'Meubles', 'Entretien');
  if (v_res ->> 'raison') is distinct from 'nom_pris' then
    raise exception 'un renommage vers un frère existant a été accepté : %', v_res;
  end if;

  -- ---------------------------------------------------------------- déplacer
  v_res := public.deplacer_dossier(v_shop_a, 'Meubles/Chaises', '');
  if (v_res ->> 'chemin') is distinct from 'Chaises' then
    raise exception 'déplacement vers la racine raté : %', v_res;
  end if;
  select dossier into v_libelle from public.articles where libelle = 'Chaise en rotin';
  if v_libelle <> 'Chaises' then
    raise exception 'les articles n''ont pas suivi le déplacement : « % »', v_libelle;
  end if;

  v_res := public.deplacer_dossier(v_shop_a, 'Meubles', 'Meubles');
  if (v_res ->> 'raison') is distinct from 'cible_dans_source' then
    raise exception 'un dossier déplacé dans lui-même a été accepté : %', v_res;
  end if;

  v_res := public.deplacer_dossier(v_shop_a, 'Meubles', '');
  if not coalesce((v_res ->> 'inchange')::boolean, false) then
    raise exception 'déplacer un dossier là où il est déjà aurait dû être un no-op : %', v_res;
  end if;

  -- ------------------------------------------------------------------- copier
  v_res := public.copier_dossier(v_shop_a, 'Meubles', 'Entretien');
  if not (v_res ->> 'ok')::boolean then
    raise exception 'copie refusée : %', v_res;
  end if;
  if (v_res ->> 'chemin') <> 'Entretien/Meubles' then
    raise exception 'la copie a atterri sur « % »', v_res ->> 'chemin';
  end if;

  select count(*) into v_count from public.articles
   where shop_id = v_shop_a and public.dossier_est_dans(dossier, 'Entretien/Meubles');
  if v_count <> 1 then
    raise exception 'la copie devrait contenir 1 article, en contient %', v_count;
  end if;

  -- Le code-barres ne se duplique pas : un code est un objet physique, et
  -- l'index d'unicité le refuserait de toute façon.
  select code_barre into v_code from public.articles
   where shop_id = v_shop_a and public.dossier_est_dans(dossier, 'Entretien/Meubles')
   limit 1;
  if btrim(coalesce(v_code, '')) <> '' then
    raise exception 'la copie a gardé le code-barres « % »', v_code;
  end if;
  select code_barre into v_code from public.articles where libelle = 'Table basse';
  if v_code <> '6111234567891' then
    raise exception 'l''original a perdu son code-barres';
  end if;

  -- Le libellé gagne « (copie) », comme sur Android.
  select libelle into v_libelle from public.articles
   where shop_id = v_shop_a and libelle = 'Table basse (copie)';
  if v_libelle is null then
    raise exception 'la copie n''a pas été renommée « (copie) »';
  end if;

  -- --------------------------------------------------------------- supprimer
  -- Refusé tant qu'il reste un sous-dossier.
  v_res := public.supprimer_dossier(v_shop_a, 'Entretien');
  if (v_res ->> 'raison') is distinct from 'sous_dossiers' then
    raise exception 'une suppression avec sous-dossier a été acceptée : %', v_res;
  end if;

  -- Le mode récursif exige le nom du dossier.
  v_res := public.supprimer_dossier(v_shop_a, 'Entretien', 'recursif', 'non');
  if (v_res ->> 'raison') is distinct from 'confirmation_requise' then
    raise exception 'le mode récursif s''est lancé sans confirmation : %', v_res;
  end if;

  v_res := public.supprimer_dossier(v_shop_a, 'Entretien', 'recursif', 'entretien');
  if not (v_res ->> 'ok')::boolean then
    raise exception 'suppression récursive confirmée refusée : %', v_res;
  end if;
  select count(*) into v_count from public.articles
   where shop_id = v_shop_a and public.dossier_est_dans(dossier, 'Entretien');
  if v_count <> 0 then
    raise exception 'la suppression récursive a laissé % article(s)', v_count;
  end if;

  -- Le mode normal ne supprime pas les articles : il les remonte au parent.
  insert into public.articles (shop_id, libelle, prix, dossier)
  values (v_shop_a, 'Vis', 2, 'Quincaillerie/Visserie');
  v_res := public.supprimer_dossier(v_shop_a, 'Quincaillerie/Visserie');
  if not (v_res ->> 'ok')::boolean then
    raise exception 'suppression d''un dossier feuiIlle refusée : %', v_res;
  end if;
  select dossier into v_libelle from public.articles where libelle = 'Vis';
  if v_libelle <> 'Quincaillerie' then
    raise exception 'l''article n''est pas remonté au parent : « % »', v_libelle;
  end if;
  select count(*) into v_count from public.articles where libelle = 'Vis';
  if v_count <> 1 then
    raise exception 'l''article a été supprimé au lieu de remonter';
  end if;

  -- Une facture émise ne bouge pas quand son article disparaît.
  -- Écrit en tant que propriétaire : `authenticated` n'a pas d'insert sur
  -- `documents` (le numéro et le quota se décident dans les fonctions), et ce
  -- point vérifie une clé étrangère, pas une politique.
  declare
    v_doc uuid;
  begin
    perform set_config('role', v_owner, true);

    insert into public.documents (shop_id, type) values (v_shop_a, 'facture')
    returning id into v_doc;

    insert into public.articles (shop_id, libelle, prix)
    values (v_shop_a, 'Article facturé', 100)
    returning id into v_article;

    insert into public.document_lines (document_id, libelle, quantite, prix_unitaire, article_id)
    values (v_doc, 'Article facturé', 1, 100, v_article);

    delete from public.articles where id = v_article;

    select count(*) into v_count
      from public.document_lines
     where document_id = v_doc and article_id is null and libelle = 'Article facturé';
    if v_count <> 1 then
      raise exception 'la ligne de facture a perdu son libellé avec l''article';
    end if;

    perform set_config('role', 'authenticated', true);
  end;

  -- ------------------------------------------------------------------- RLS
  perform set_config('role', v_owner, true);
  perform set_config('request.jwt.claims', json_build_object('sub', v_b, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);

  select count(*) into v_count from public.article_folders;
  if v_count <> 0 then
    raise exception 'B voit % dossier(s) de A', v_count;
  end if;

  begin
    perform public.creer_dossier(v_shop_a, '', 'Pirate');
    raise exception 'B a créé un dossier dans la boutique de A';
  exception
    when insufficient_privilege then
      null; -- attendu
  end;

  begin
    insert into public.article_folders (shop_id, chemin) values (v_shop_a, 'Pirate');
    raise exception 'B a inséré directement dans article_folders : la politique manque';
  exception
    when insufficient_privilege then
      null; -- attendu
  end;

  -- B a sa propre boutique : les mêmes chemins y sont indépendants.
  select (public.create_shop_for_me('Dossiers B')).id into v_shop_b;
  insert into public.articles (shop_id, libelle, prix, dossier)
  values (v_shop_b, 'Chaise en rotin', 900, 'Mobilier/Chaises');
  select count(*) into v_count from public.article_folders;
  if v_count <> 2 then
    raise exception 'B devrait voir 2 dossiers chez lui, en voit %', v_count;
  end if;

  perform set_config('role', v_owner, true);

  raise notice 'RLS dossiers : parents, casse, renommage, copie, suppression et isolation vérifiés.';
end;
$$;

rollback;
