-- FactureApp — 0011 : dossiers d'articles, imbriqués
--
-- À exécuter après 0010. Idempotent.
--
-- Un dossier est un *chemin*, « Mobilier/Chaises », et non un nœud avec un id et
-- un parent. C'est la règle du catalogue Android (SavedItemStore.kt) et elle a
-- deux avantages ici : `articles.dossier` reste la clé de jointure — donc
-- l'import Android, l'export et la restauration ne changent pas d'un octet — et
-- une arborescence se lit dans le dump sans jointure.
--
-- `article_folders` existe pour ce que la colonne ne peut pas dire : un dossier
-- **vide**. Sans cette table, supprimer le dernier article d'un rayon faisait
-- disparaître le rayon, et renommer un dossier n'avait aucune ligne à réécrire.
--
-- Aucune écriture directe : la table est en lecture seule pour les boutiques.
-- Tout passe par les fonctions de 0012, et par le déclencheur ci-dessous qui
-- matérialise les parents d'un chemin dès qu'un article en reçoit un.

-- --------------------------------------------------------------------------
-- La table
-- --------------------------------------------------------------------------

create table if not exists public.article_folders (
  id         uuid primary key default gen_random_uuid(),
  shop_id    uuid        not null references public.shops (id) on delete cascade,
  chemin     text        not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  -- Un chemin n'a ni segment vide, ni barre oblique au début ou à la fin :
  -- « A//B » et « /A » ne sont pas des dossiers, ce sont des fautes de frappe
  -- que le reste du code lirait comme deux dossiers différents.
  constraint article_folders_chemin_forme check (
    char_length(chemin) between 1 and 200
    and chemin = btrim(chemin)
    and chemin <> ''
    and chemin !~ '^/'
    and chemin !~ '/$'
    and chemin !~ '//'
  )
);

-- La casse ne fait pas deux dossiers : « Mobilier » et « mobilier » sont le même
-- rayon, et l'index est ce qui l'empêche d'apparaître deux fois dans la grille.
create unique index if not exists article_folders_chemin_unique
  on public.article_folders (shop_id, lower(chemin));

create index if not exists article_folders_shop_idx
  on public.article_folders (shop_id, chemin);

alter table public.article_folders enable row level security;

drop policy if exists "membre lit article_folders" on public.article_folders;
create policy "membre lit article_folders" on public.article_folders
  for select to authenticated
  using (public.is_shop_member(shop_id));

-- Pas de politique d'écriture : le navigateur ne crée pas un dossier en
-- insérant une ligne, il appelle `creer_dossier`, qui valide le nom, refuse un
-- doublon et materialise les parents. Une insertion directe laisserait un chemin
-- sans ses ancêtres, donc une tuile que personne ne peut atteindre.

revoke all on public.article_folders from anon;
grant select on public.article_folders to authenticated;

drop trigger if exists article_folders_touch_updated_at on public.article_folders;
create trigger article_folders_touch_updated_at
  before update on public.article_folders
  for each row execute function public.touch_updated_at();

-- --------------------------------------------------------------------------
-- Dire si un chemin est dans un autre
--
-- La même règle que `estDans` côté web et `isIn` côté Kotlin, en SQL : égalité
-- ou préfixe **suivi du séparateur**. Un `like 'Mobilier%'` attraperait
-- « Mobilier d'appoint » ; un dossier nommé « 100% » casserait un like sans
-- échappement. Cette fonction est la seule à décider, et elle est utilisée par
-- tous les gestes de 0012.
-- --------------------------------------------------------------------------

create or replace function public.dossier_est_dans(p_dossier text, p_chemin text)
returns boolean
language sql
immutable
as $$
  select case
    when p_chemin is null or btrim(p_chemin) = '' then
      -- Rien n'est dans « Sans dossier » : la cible vide ne contient que le vide.
      btrim(coalesce(p_dossier, '')) = ''
    else
      lower(btrim(p_dossier)) = lower(btrim(p_chemin))
      or left(lower(btrim(p_dossier)), char_length(btrim(p_chemin)) + 1) =
         lower(btrim(p_chemin)) || '/'
  end
$$;

revoke all on function public.dossier_est_dans(text, text) from public, anon;
grant execute on function public.dossier_est_dans(text, text) to authenticated;

-- --------------------------------------------------------------------------
-- Canonicaliser le chemin d'un article
--
-- Le champ « Dossier » du formulaire est du texte libre : « Mobilier /  Chaises »
-- et « Mobilier/Chaises » doivent être le même rayon. Le déclencheur réécrit le
-- chemin en segments propres, puis l'autre déclencheur crée les parents — dans
-- cet ordre, sinon les ancêtres seraient calculés sur une forme non canonique.
-- --------------------------------------------------------------------------

create or replace function public.article_dossier_normalise()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_segments text[];
  v_propres  text[];
begin
  if new.dossier is null then
    new.dossier := '';
    return new;
  end if;

  v_segments := string_to_array(btrim(new.dossier), '/');
  select coalesce(array_agg(btrim(segment) order by ordre), '{}')
    into v_propres
    from unnest(v_segments) with ordinality as t(segment, ordre)
   where btrim(segment) <> '';

  new.dossier := array_to_string(v_propres, '/');
  return new;
end;
$$;

drop trigger if exists articles_dossier_normalise on public.articles;
create trigger articles_dossier_normalise
  before insert or update of dossier on public.articles
  for each row execute function public.article_dossier_normalise();

-- --------------------------------------------------------------------------
-- Matérialiser les parents
--
-- « Mobilier/Chaises » sans ligne « Mobilier » donnerait une grille où l'on ne
-- peut pas entrer dans le rayon : les tuiles ne dessinent que les enfants du
-- dossier courant. Le déclencheur écrit donc chaque préfixe, en gardant la
-- première orthographe rencontrée (celle de la grille que le commerce voit).
-- --------------------------------------------------------------------------

create or replace function public.article_dossier_ancetres()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_segments text[];
  v_courant  text := '';
  v_segment  text;
begin
  if new.dossier is null or btrim(new.dossier) = '' then
    return new;
  end if;

  v_segments := string_to_array(btrim(new.dossier), '/');
  foreach v_segment in array v_segments loop
    continue when btrim(v_segment) = '';
    v_courant := case
      when v_courant = '' then btrim(v_segment)
      else v_courant || '/' || btrim(v_segment)
    end;

    insert into public.article_folders (shop_id, chemin)
    values (new.shop_id, v_courant)
    on conflict (shop_id, lower(chemin)) do nothing;
  end loop;

  return new;
end;
$$;

drop trigger if exists articles_dossier_ancetres on public.articles;
create trigger articles_dossier_ancetres
  after insert or update of dossier on public.articles
  for each row execute function public.article_dossier_ancetres();

-- --------------------------------------------------------------------------
-- Reprise des catalogues existants
--
-- Les articles écrits avant cette migration portent un `dossier` en texte libre
-- et aucune ligne dans article_folders. On recrée l'arbre une fois, avec la même
-- règle de casse que le déclencheur (première orthographe rencontrée).
-- --------------------------------------------------------------------------

do $$
declare
  r        record;
  v_seg    text[];
  v_courant text;
  v_segment text;
begin
  -- Passer les dossiers existants en forme canonique, sinon « A / B » resterait
  -- une tuile à part entière et le déclencheur des ancêtres créerait « A » ET
  -- « A  ».
  update public.articles
     set dossier = (
       select coalesce(string_agg(btrim(segment), '/' order by ordre), '')
         from unnest(string_to_array(btrim(dossier), '/')) with ordinality as t(segment, ordre)
        where btrim(segment) <> ''
     )
   where dossier is not null
     and btrim(dossier) <> ''
     and dossier <> (
       select coalesce(string_agg(btrim(segment), '/' order by ordre), '')
         from unnest(string_to_array(btrim(dossier), '/')) with ordinality as t(segment, ordre)
        where btrim(segment) <> ''
     );

  for r in
    select distinct on (a.shop_id, lower(a.dossier))
           a.shop_id, btrim(a.dossier) as dossier
      from public.articles a
     where a.dossier is not null and btrim(a.dossier) <> ''
     order by a.shop_id, lower(a.dossier), btrim(a.dossier)
  loop
    v_courant := '';
    v_seg := string_to_array(r.dossier, '/');
    foreach v_segment in array v_seg loop
      continue when btrim(v_segment) = '';
      v_courant := case
        when v_courant = '' then btrim(v_segment)
        else v_courant || '/' || btrim(v_segment)
      end;

      insert into public.article_folders (shop_id, chemin)
      values (r.shop_id, v_courant)
      on conflict (shop_id, lower(chemin)) do nothing;
    end loop;
  end loop;
end;
$$;
