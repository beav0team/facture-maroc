-- FactureApp — isolation des catalogues (0002)
--
-- Même méthode que rls_isolation.sql : deux utilisateurs, deux boutiques, une
-- seule transaction annulée à la fin. Vérifie qu'un client ou un article de A
-- est invisible et intouchable pour B, et qu'on ne peut pas écrire une ligne
-- dans la boutique d'un autre en forgeant le shop_id.
--
--   psql "$DATABASE_URL" -f supabase/tests/rls_catalogue.sql

begin;

do $$
declare
  v_a       uuid := '33333333-3333-4333-8333-333333333333';
  v_b       uuid := '44444444-4444-4444-8444-444444444444';
  v_owner   text := current_user;
  v_shop_a  uuid;
  v_shop_b  uuid;
  v_client  uuid;
  v_article uuid;
  v_count   integer;
  v_touche  integer;
begin
  insert into auth.users (instance_id, id, aud, role, email)
  values
    ('00000000-0000-0000-0000-000000000000', v_a, 'authenticated', 'authenticated', 'cat-a@example.invalid'),
    ('00000000-0000-0000-0000-000000000000', v_b, 'authenticated', 'authenticated', 'cat-b@example.invalid');

  -- ------------------------------------------------------------------ user A
  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);

  select (public.create_shop_for_me('Catalogue A')).id into v_shop_a;

  insert into public.clients (shop_id, nom, role, telephone)
  values (v_shop_a, 'Garage Atlas', 'client', '0612345678')
  returning id into v_client;

  insert into public.articles (shop_id, libelle, prix, unite, stock)
  values (v_shop_a, 'Huile 5W30', 89.90, 'bidon', 12)
  returning id into v_article;

  -- Un service : stock null, et c'est une valeur légitime, pas un oubli.
  insert into public.articles (shop_id, libelle, prix, stock)
  values (v_shop_a, 'Main-d’œuvre', 150, null);

  select count(*) into v_count from public.clients;
  if v_count <> 1 then
    raise exception 'A devrait voir 1 client, en voit %', v_count;
  end if;

  select count(*) into v_count from public.articles;
  if v_count <> 2 then
    raise exception 'A devrait voir 2 articles, en voit %', v_count;
  end if;

  -- ------------------------------------------------------------------ user B
  perform set_config('role', v_owner, true);
  perform set_config('request.jwt.claims', json_build_object('sub', v_b, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);

  select count(*) into v_count from public.clients;
  if v_count <> 0 then
    raise exception 'B voit % client(s) de A', v_count;
  end if;

  select count(*) into v_count from public.articles;
  if v_count <> 0 then
    raise exception 'B voit % article(s) de A', v_count;
  end if;

  update public.clients set nom = 'détourné' where id = v_client;
  get diagnostics v_touche = row_count;
  if v_touche <> 0 then
    raise exception 'B a modifié % client(s) de A', v_touche;
  end if;

  delete from public.articles where id = v_article;
  get diagnostics v_touche = row_count;
  if v_touche <> 0 then
    raise exception 'B a supprimé % article(s) de A', v_touche;
  end if;

  begin
    insert into public.clients (shop_id, nom) values (v_shop_a, 'Client pirate');
    raise exception 'B a inséré un client dans la boutique de A : la politique manque';
  exception
    when insufficient_privilege then
      null; -- attendu
  end;

  select (public.create_shop_for_me('Catalogue B')).id into v_shop_b;

  insert into public.clients (shop_id, nom) values (v_shop_b, 'Garage Atlas');
  insert into public.articles (shop_id, libelle, prix, code_barre)
  values (v_shop_b, 'Huile 5W30', 95, '6111234567890');

  select count(*) into v_count from public.clients;
  if v_count <> 1 then
    raise exception 'B devrait voir 1 client après création, en voit %', v_count;
  end if;

  -- Le même nom dans deux boutiques est permis : l'unicité est par boutique.
  -- Le même code-barres aussi, pour la même raison.

  -- ---------------------------------------------------------------- verif A
  perform set_config('role', v_owner, true);
  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);

  select count(*) into v_count from public.clients;
  if v_count <> 1 then
    raise exception 'A devrait toujours voir 1 client, en voit %', v_count;
  end if;

  select count(*) into v_count from public.clients where shop_id = v_shop_b;
  if v_count <> 0 then
    raise exception 'A voit le client de B';
  end if;

  select count(*) into v_count from public.articles where shop_id = v_shop_b;
  if v_count <> 0 then
    raise exception 'A voit les articles de B';
  end if;

  raise notice 'RLS catalogue : isolation clients/articles vérifiée.';
end;
$$;

rollback;
