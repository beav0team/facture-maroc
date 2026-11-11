-- FactureApp — RLS isolation test
--
-- Proves that two shops cannot see or touch each other's data.
--
-- Run it in Supabase → SQL Editor on a development project, or with psql:
--   psql "$DATABASE_URL" -f supabase/tests/rls_isolation.sql
--
-- It creates two throwaway users in auth.users, has each one create a shop
-- through the public RPC, then checks that neither can read, update, insert
-- or delete across the boundary. Everything happens inside one transaction
-- that ends with rollback: no row survives the run, and existing data is
-- neither read nor changed.

begin;

do $$
declare
  v_a       uuid    := '11111111-1111-4111-8111-111111111111';
  v_b       uuid    := '22222222-2222-4222-8222-222222222222';
  v_owner   text    := current_user;
  v_shop_a  uuid;
  v_shop_b  uuid;
  v_count   integer;
  v_touche  integer;
begin
  insert into auth.users (instance_id, id, aud, role, email)
  values
    ('00000000-0000-0000-0000-000000000000', v_a, 'authenticated', 'authenticated', 'rls-a@example.invalid'),
    ('00000000-0000-0000-0000-000000000000', v_b, 'authenticated', 'authenticated', 'rls-b@example.invalid');

  -- ------------------------------------------------------------------ user A
  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', v_a, 'role', 'authenticated')::text,
    true
  );
  perform set_config('role', 'authenticated', true);

  select (public.create_shop_for_me('Boutique A')).id into v_shop_a;

  select count(*) into v_count from public.shops;
  if v_count <> 1 then
    raise exception 'A devrait voir 1 boutique, en voit %', v_count;
  end if;

  select count(*) into v_count from public.shops where id <> v_shop_a;
  if v_count <> 0 then
    raise exception 'A voit une boutique qui n''est pas la sienne';
  end if;

  select count(*) into v_count from public.memberships;
  if v_count <> 1 then
    raise exception 'A devrait voir 1 appartenance, en voit %', v_count;
  end if;

  -- ------------------------------------------------------------------ user B
  perform set_config('role', v_owner, true);
  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', v_b, 'role', 'authenticated')::text,
    true
  );
  perform set_config('role', 'authenticated', true);

  select count(*) into v_count from public.shops;
  if v_count <> 0 then
    raise exception 'B voit % boutique(s) avant même d''en avoir créé une', v_count;
  end if;

  select count(*) into v_count from public.memberships;
  if v_count <> 0 then
    raise exception 'B voit % appartenance(s) qui ne sont pas les siennes', v_count;
  end if;

  update public.shops set nom = 'détournée' where id = v_shop_a;
  get diagnostics v_touche = row_count;
  if v_touche <> 0 then
    raise exception 'B a modifié % ligne(s) de la boutique de A', v_touche;
  end if;

  delete from public.shops where id = v_shop_a;
  get diagnostics v_touche = row_count;
  if v_touche <> 0 then
    raise exception 'B a supprimé % ligne(s) de la boutique de A', v_touche;
  end if;

  begin
    insert into public.shops (nom, created_by) values ('Boutique pirate', v_b);
    raise exception 'B a inséré une boutique directement : la politique manque';
  exception
    when insufficient_privilege then
      null; -- attendu : la RLS refuse l'insertion
  end;

  select (public.create_shop_for_me('Boutique B')).id into v_shop_b;

  select count(*) into v_count from public.shops;
  if v_count <> 1 then
    raise exception 'B devrait voir 1 boutique après création, en voit %', v_count;
  end if;

  select count(*) into v_count from public.shops where id = v_shop_a;
  if v_count <> 0 then
    raise exception 'B voit encore la boutique de A';
  end if;

  -- ---------------------------------------------------------------- verif A
  perform set_config('role', v_owner, true);
  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', v_a, 'role', 'authenticated')::text,
    true
  );
  perform set_config('role', 'authenticated', true);

  select count(*) into v_count from public.shops;
  if v_count <> 1 then
    raise exception 'A devrait toujours voir 1 boutique, en voit %', v_count;
  end if;

  select count(*) into v_count from public.shops where id = v_shop_b;
  if v_count <> 0 then
    raise exception 'A voit la boutique de B';
  end if;

  raise notice 'RLS : isolation vérifiée entre deux boutiques.';
end;
$$;

rollback;
