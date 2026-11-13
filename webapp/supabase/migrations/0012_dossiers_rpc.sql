-- FactureApp — 0012 : les gestes sur les dossiers
--
-- À exécuter après 0011. Idempotent.
--
-- Pourquoi des fonctions et pas des `update` depuis l'onglet : renommer ou
-- déplacer un dossier réécrit **tout son sous-arbre** — les chemins de
-- article_folders et de chaque article. Un navigateur le ferait en plusieurs
-- requêtes, et une connexion qui tombe au milieu laisse la moitié des articles
-- dans l'ancien rayon et l'autre dans le nouveau. Ici, chaque geste est une
-- transaction, et la seule écriture possible.
--
-- Toutes retournent un jsonb `{ok: …}`. Un refus est une valeur, pas une
-- exception : l'écran doit pouvoir dire « ce dossier contient 2 sous-dossiers »
-- sans lire un message d'erreur, et une exception dans une fonction `security
-- definer` remonte au client sous une forme que rien ne garantit stable.

-- --------------------------------------------------------------------------
-- Résoudre un chemin tel qu'il est écrit en base
-- --------------------------------------------------------------------------

create or replace function public.dossier_resolu(p_shop uuid, p_chemin text)
returns text
language sql
stable
security definer
set search_path = public
as $$
  select f.chemin
    from public.article_folders f
   where f.shop_id = p_shop
     and lower(f.chemin) = lower(btrim(coalesce(p_chemin, '')))
   limit 1
$$;

revoke all on function public.dossier_resolu(uuid, text) from public, anon;
grant execute on function public.dossier_resolu(uuid, text) to authenticated;

-- Le parent et le nom d'un chemin, sans découpage répété dans chaque fonction.
create or replace function public.dossier_parent(p_chemin text)
returns text
language sql
immutable
as $$
  select case
    when p_chemin is null or position('/' in p_chemin) = 0 then ''
    else left(p_chemin, length(p_chemin) - position('/' in reverse(p_chemin)))
  end
$$;

create or replace function public.dossier_nom(p_chemin text)
returns text
language sql
immutable
as $$
  select case
    when p_chemin is null then ''
    else split_part(p_chemin, '/', array_length(string_to_array(p_chemin, '/'), 1))
  end
$$;

revoke all on function public.dossier_parent(text) from public, anon;
grant execute on function public.dossier_parent(text) to authenticated;
revoke all on function public.dossier_nom(text) from public, anon;
grant execute on function public.dossier_nom(text) to authenticated;

-- Le garde de chaque geste : authentifié, et membre de la boutique.
create or replace function public.dossier_garde(p_shop uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'authentification requise' using errcode = '28000';
  end if;
  if p_shop is null or not public.is_shop_member(p_shop) then
    raise exception 'boutique inconnue' using errcode = '42501';
  end if;
end;
$$;

revoke all on function public.dossier_garde(uuid) from public, anon;
grant execute on function public.dossier_garde(uuid) to authenticated;

-- Le nom d'un dossier : un segment, pas un chemin.
create or replace function public.dossier_nom_valide(p_nom text)
returns text
language plpgsql
immutable
as $$
declare
  v_nom text := btrim(coalesce(p_nom, ''));
begin
  if v_nom = '' then
    return 'nom_vide';
  end if;
  if position('/' in v_nom) > 0 then
    return 'nom_invalide';
  end if;
  if char_length(v_nom) > 80 then
    return 'nom_trop_long';
  end if;
  return null;
end;
$$;

-- --------------------------------------------------------------------------
-- Aperçu : ce qu'un dossier contient, pour que la confirmation soit chiffrée
-- --------------------------------------------------------------------------

create or replace function public.dossier_apercu(p_shop uuid, p_chemin text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_connu    text;
  v_articles integer;
  v_sous     integer;
begin
  perform public.dossier_garde(p_shop);

  v_connu := public.dossier_resolu(p_shop, p_chemin);
  if v_connu is null then
    return jsonb_build_object('ok', false, 'raison', 'dossier_inexistant');
  end if;

  select count(*) into v_articles
    from public.articles a
   where a.shop_id = p_shop and public.dossier_est_dans(a.dossier, v_connu);

  select count(*) into v_sous
    from public.article_folders f
   where f.shop_id = p_shop
     and public.dossier_est_dans(f.chemin, v_connu)
     and lower(f.chemin) <> lower(v_connu);

  return jsonb_build_object(
    'ok', true,
    'chemin', v_connu,
    'nom', public.dossier_nom(v_connu),
    'parent', public.dossier_parent(v_connu),
    'articles', v_articles,
    'sous_dossiers', v_sous
  );
end;
$$;

revoke all on function public.dossier_apercu(uuid, text) from public, anon;
grant execute on function public.dossier_apercu(uuid, text) to authenticated;

-- --------------------------------------------------------------------------
-- Créer
-- --------------------------------------------------------------------------

create or replace function public.creer_dossier(p_shop uuid, p_parent text, p_nom text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_parent text := btrim(coalesce(p_parent, ''));
  v_nom    text := btrim(coalesce(p_nom, ''));
  v_raison text;
  v_chemin text;
begin
  perform public.dossier_garde(p_shop);

  if v_parent <> '' then
    v_parent := public.dossier_resolu(p_shop, v_parent);
    if v_parent is null then
      return jsonb_build_object('ok', false, 'raison', 'parent_inconnu');
    end if;
  end if;

  v_raison := public.dossier_nom_valide(v_nom);
  if v_raison is not null then
    return jsonb_build_object('ok', false, 'raison', v_raison);
  end if;

  v_chemin := case when v_parent = '' then v_nom else v_parent || '/' || v_nom end;

  if char_length(v_chemin) > 200 then
    return jsonb_build_object('ok', false, 'raison', 'chemin_trop_long');
  end if;
  if public.dossier_resolu(p_shop, v_chemin) is not null then
    return jsonb_build_object('ok', false, 'raison', 'nom_pris');
  end if;

  insert into public.article_folders (shop_id, chemin) values (p_shop, v_chemin);

  return jsonb_build_object('ok', true, 'chemin', v_chemin);
end;
$$;

revoke all on function public.creer_dossier(uuid, text, text) from public, anon;
grant execute on function public.creer_dossier(uuid, text, text) to authenticated;

-- --------------------------------------------------------------------------
-- Renommer : dernier segment seulement, tout le sous-arbre suit
-- --------------------------------------------------------------------------

create or replace function public.renommer_dossier(p_shop uuid, p_chemin text, p_nom text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_connu  text;
  v_nom    text := btrim(coalesce(p_nom, ''));
  v_raison text;
  v_parent text;
  v_nouveau text;
  v_trouve text;
begin
  perform public.dossier_garde(p_shop);

  v_connu := public.dossier_resolu(p_shop, p_chemin);
  if v_connu is null then
    return jsonb_build_object('ok', false, 'raison', 'dossier_inexistant');
  end if;

  v_raison := public.dossier_nom_valide(v_nom);
  if v_raison is not null then
    return jsonb_build_object('ok', false, 'raison', v_raison);
  end if;

  v_parent := public.dossier_parent(v_connu);
  v_nouveau := case when v_parent = '' then v_nom else v_parent || '/' || v_nom end;

  v_trouve := public.dossier_resolu(p_shop, v_nouveau);
  -- Se renommer soi-même (même casse mise à part) est permis ; prendre le nom
  -- d'un frère ne l'est pas.
  if v_trouve is not null and lower(v_trouve) <> lower(v_connu) then
    return jsonb_build_object('ok', false, 'raison', 'nom_pris');
  end if;

  update public.article_folders
     set chemin = v_nouveau || substr(chemin, char_length(v_connu) + 1)
   where shop_id = p_shop
     and public.dossier_est_dans(chemin, v_connu);

  update public.articles
     set dossier = v_nouveau || substr(dossier, char_length(v_connu) + 1)
   where shop_id = p_shop
     and public.dossier_est_dans(dossier, v_connu);

  return jsonb_build_object('ok', true, 'chemin', v_nouveau, 'ancien', v_connu);
end;
$$;

revoke all on function public.renommer_dossier(uuid, text, text) from public, anon;
grant execute on function public.renommer_dossier(uuid, text, text) to authenticated;

-- --------------------------------------------------------------------------
-- Déplacer : sous un autre parent, avec tout son contenu
-- --------------------------------------------------------------------------

create or replace function public.deplacer_dossier(p_shop uuid, p_chemin text, p_parent text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_connu   text;
  v_parent  text := btrim(coalesce(p_parent, ''));
  v_nom     text;
  v_actuel  text;
  v_nouveau text;
  v_trouve  text;
begin
  perform public.dossier_garde(p_shop);

  v_connu := public.dossier_resolu(p_shop, p_chemin);
  if v_connu is null then
    return jsonb_build_object('ok', false, 'raison', 'dossier_inexistant');
  end if;

  if v_parent <> '' then
    v_parent := public.dossier_resolu(p_shop, v_parent);
    if v_parent is null then
      return jsonb_build_object('ok', false, 'raison', 'parent_inconnu');
    end if;
  end if;

  -- Déplacer un dossier dans lui-même ou dans son propre sous-arbre le
  -- détacherait de la grille : le dossier n'aurait plus de parent atteignable.
  if v_parent <> '' and public.dossier_est_dans(v_parent, v_connu) then
    return jsonb_build_object('ok', false, 'raison', 'cible_dans_source');
  end if;

  v_nom := public.dossier_nom(v_connu);
  v_actuel := public.dossier_parent(v_connu);

  if lower(v_actuel) = lower(v_parent) then
    -- Le déposer là où il est déjà ne réécrit rien.
    return jsonb_build_object('ok', true, 'chemin', v_connu, 'inchange', true);
  end if;

  v_nouveau := case when v_parent = '' then v_nom else v_parent || '/' || v_nom end;

  v_trouve := public.dossier_resolu(p_shop, v_nouveau);
  if v_trouve is not null then
    return jsonb_build_object('ok', false, 'raison', 'nom_pris');
  end if;

  update public.article_folders
     set chemin = v_nouveau || substr(chemin, char_length(v_connu) + 1)
   where shop_id = p_shop
     and public.dossier_est_dans(chemin, v_connu);

  update public.articles
     set dossier = v_nouveau || substr(dossier, char_length(v_connu) + 1)
   where shop_id = p_shop
     and public.dossier_est_dans(dossier, v_connu);

  return jsonb_build_object('ok', true, 'chemin', v_nouveau, 'ancien', v_connu);
end;
$$;

revoke all on function public.deplacer_dossier(uuid, text, text) from public, anon;
grant execute on function public.deplacer_dossier(uuid, text, text) to authenticated;

-- --------------------------------------------------------------------------
-- Copier : une vraie seconde étagère, pas un raccourci
--
-- Deux choses ne se copient pas telles quelles :
--
--   * le code-barres. L'index `articles_code_barre_unique` interdit deux
--     articles au même code dans une boutique, et il a raison : un code est un
--     objet physique. La copie est un doublon de *catalogue*, elle arrive donc
--     sans code, et le compte est rendu pour que l'écran puisse le dire.
--
--   * le libellé. Rien ne l'interdit en base, mais deux « Chaise en rotin » dans
--     le sélecteur d'une facture sont un piège. On ajoute « (copie) », comme le
--     catalogue Android, pour que les deux applications se lisent pareil.
-- --------------------------------------------------------------------------

create or replace function public.copier_dossier(p_shop uuid, p_chemin text, p_parent text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_connu    text;
  v_parent   text := btrim(coalesce(p_parent, ''));
  v_nom      text;
  v_copie    text;
  v_essai    integer := 0;
  v_article  record;
  v_libelle  text;
  v_suffixe  integer;
  v_articles integer := 0;
  v_codes    integer := 0;
begin
  perform public.dossier_garde(p_shop);

  v_connu := public.dossier_resolu(p_shop, p_chemin);
  if v_connu is null then
    return jsonb_build_object('ok', false, 'raison', 'dossier_inexistant');
  end if;

  if v_parent <> '' then
    v_parent := public.dossier_resolu(p_shop, v_parent);
    if v_parent is null then
      return jsonb_build_object('ok', false, 'raison', 'parent_inconnu');
    end if;
  end if;

  if v_parent <> '' and public.dossier_est_dans(v_parent, v_connu) then
    return jsonb_build_object('ok', false, 'raison', 'cible_dans_source');
  end if;

  v_nom := public.dossier_nom(v_connu);

  -- « Chaises » garde son nom quand il est libre, puis « (copie) », « (copie 2) »…
  -- Même règle que `suggestionCopie` côté écran, et que le catalogue Android.
  loop
    v_copie := (case when v_parent = '' then '' else v_parent || '/' end)
      || v_nom
      || (case
            when v_essai = 0 then ''
            when v_essai = 1 then ' (copie)'
            else ' (copie ' || v_essai || ')'
          end);

    exit when public.dossier_resolu(p_shop, v_copie) is null;
    v_essai := v_essai + 1;
    if v_essai > 100 then
      return jsonb_build_object('ok', false, 'raison', 'nom_pris');
    end if;
  end loop;

  if char_length(v_copie) > 200 then
    return jsonb_build_object('ok', false, 'raison', 'chemin_trop_long');
  end if;

  insert into public.article_folders (shop_id, chemin)
  select p_shop, v_copie || substr(f.chemin, char_length(v_connu) + 1)
    from public.article_folders f
   where f.shop_id = p_shop
     and public.dossier_est_dans(f.chemin, v_connu);

  for v_article in
    select a.*
      from public.articles a
     where a.shop_id = p_shop
       and public.dossier_est_dans(a.dossier, v_connu)
     order by a.libelle
  loop
    v_libelle := v_article.libelle;
    v_suffixe := 1;
    while exists (
      select 1 from public.articles a
       where a.shop_id = p_shop
         and lower(btrim(a.libelle)) = lower(btrim(v_libelle))
    ) loop
      v_libelle := v_article.libelle || case
        when v_suffixe = 1 then ' (copie)'
        else ' (copie ' || v_suffixe || ')'
      end;
      v_suffixe := v_suffixe + 1;
      if v_suffixe > 100 then
        return jsonb_build_object('ok', false, 'raison', 'nom_pris');
      end if;
    end loop;

    if btrim(coalesce(v_article.code_barre, '')) <> '' then
      v_codes := v_codes + 1;
    end if;

    insert into public.articles (
      shop_id, libelle, prix, unite, dossier, prix_achat,
      code_barre, stock, seuil_rea, attributs, paliers
    ) values (
      p_shop, v_libelle, v_article.prix, v_article.unite,
      v_copie || substr(v_article.dossier, char_length(v_connu) + 1),
      v_article.prix_achat, '', v_article.stock, v_article.seuil_rea,
      v_article.attributs, v_article.paliers
    );

    v_articles := v_articles + 1;
  end loop;

  return jsonb_build_object(
    'ok', true, 'chemin', v_copie, 'articles', v_articles, 'codes_retires', v_codes
  );
end;
$$;

revoke all on function public.copier_dossier(uuid, text, text) from public, anon;
grant execute on function public.copier_dossier(uuid, text, text) to authenticated;

-- --------------------------------------------------------------------------
-- Supprimer
--
--   mode 'remonter'  (le geste normal) : refusé s'il reste des sous-dossiers ;
--                    sinon les articles remontent chez le parent et le dossier
--                    disparaît. Rien n'est perdu.
--
--   mode 'recursif'  (le geste explicite) : le dossier, ses sous-dossiers et
--                    leurs articles sont supprimés. `p_confirmation` doit
--                    répéter le nom du dossier — un onglet resté ouvert ou un
--                    double clic ne peuvent pas lancer ça par accident.
--
-- Les factures déjà émises ne bougent pas : `document_lines.article_id` est en
-- `on delete set null`, et la ligne garde son libellé. Une facture réimprimée
-- reste identique à ce qui a été remis au client.
-- --------------------------------------------------------------------------

create or replace function public.supprimer_dossier(
  p_shop uuid,
  p_chemin text,
  p_mode text default 'remonter',
  p_confirmation text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_connu    text;
  v_mode     text := lower(btrim(coalesce(p_mode, 'remonter')));
  v_nom      text;
  v_parent   text;
  v_articles integer := 0;
  v_sous     integer := 0;
begin
  perform public.dossier_garde(p_shop);

  v_connu := public.dossier_resolu(p_shop, p_chemin);
  if v_connu is null then
    return jsonb_build_object('ok', false, 'raison', 'dossier_inexistant');
  end if;

  v_nom := public.dossier_nom(v_connu);
  v_parent := public.dossier_parent(v_connu);

  select count(*) into v_sous
    from public.article_folders f
   where f.shop_id = p_shop
     and public.dossier_est_dans(f.chemin, v_connu)
     and lower(f.chemin) <> lower(v_connu);

  select count(*) into v_articles
    from public.articles a
   where a.shop_id = p_shop and public.dossier_est_dans(a.dossier, v_connu);

  if v_mode = 'recursif' then
    if lower(btrim(coalesce(p_confirmation, ''))) <> lower(v_nom) then
      return jsonb_build_object('ok', false, 'raison', 'confirmation_requise', 'nom', v_nom);
    end if;

    delete from public.articles a
     where a.shop_id = p_shop and public.dossier_est_dans(a.dossier, v_connu);

    delete from public.article_folders f
     where f.shop_id = p_shop and public.dossier_est_dans(f.chemin, v_connu);

    return jsonb_build_object(
      'ok', true, 'mode', 'recursif', 'articles', v_articles, 'sous_dossiers', v_sous
    );
  end if;

  if v_mode <> 'remonter' then
    return jsonb_build_object('ok', false, 'raison', 'mode_inconnu');
  end if;

  if v_sous > 0 then
    return jsonb_build_object('ok', false, 'raison', 'sous_dossiers', 'nombre', v_sous);
  end if;

  update public.articles
     set dossier = v_parent
   where shop_id = p_shop
     and public.dossier_est_dans(dossier, v_connu);

  delete from public.article_folders
   where shop_id = p_shop
     and lower(chemin) = lower(v_connu);

  return jsonb_build_object(
    'ok', true, 'mode', 'remonter', 'articles', v_articles, 'parent', v_parent
  );
end;
$$;

revoke all on function public.supprimer_dossier(uuid, text, text, text) from public, anon;
grant execute on function public.supprimer_dossier(uuid, text, text, text) to authenticated;

-- --------------------------------------------------------------------------
-- Réparer : recréer les parents manquants d'un catalogue importé
-- --------------------------------------------------------------------------

create or replace function public.reparer_dossiers(p_shop uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  r         record;
  v_segment text;
  v_courant text;
  v_crees   integer := 0;
begin
  perform public.dossier_garde(p_shop);

  for r in
    select distinct on (lower(btrim(dossier))) btrim(dossier) as dossier
      from public.articles
     where shop_id = p_shop and btrim(coalesce(dossier, '')) <> ''
     order by lower(btrim(dossier))
  loop
    v_courant := '';
    foreach v_segment in array string_to_array(r.dossier, '/') loop
      continue when btrim(v_segment) = '';
      v_courant := case
        when v_courant = '' then btrim(v_segment)
        else v_courant || '/' || btrim(v_segment)
      end;

      if public.dossier_resolu(p_shop, v_courant) is null then
        insert into public.article_folders (shop_id, chemin) values (p_shop, v_courant);
        v_crees := v_crees + 1;
      end if;
    end loop;
  end loop;

  return jsonb_build_object('ok', true, 'crees', v_crees);
end;
$$;

revoke all on function public.reparer_dossiers(uuid) from public, anon;
grant execute on function public.reparer_dossiers(uuid) to authenticated;
