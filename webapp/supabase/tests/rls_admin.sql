-- FactureApp — console d'administration : lectures, écritures, verrou du dernier admin
--
-- Trois comptes : un propriétaire de boutique (A), un second (B) et un
-- administrateur plateforme. La transaction est annulée à la fin.
--
-- Ce que la console lit dans `auth.users` n'est accessible que par les
-- fonctions SECURITY DEFINER de 0005 : un compte ordinaire ne doit voir ni les
-- e-mails des autres, ni les compteurs de toutes les boutiques.
--
--   psql "$DATABASE_URL" -f supabase/tests/rls_admin.sql
--
-- Le harnais local doit avoir ajouté `last_sign_in_at` à `auth.users` (elle
-- existe dans Supabase, pas dans le shim minimal).

begin;

do $$
declare
  v_a       uuid := 'a1111111-1111-4111-8111-111111111111';
  v_b       uuid := 'b2222222-2222-4222-8222-222222222222';
  v_admin   uuid := 'c3333333-3333-4333-8333-333333333333';
  v_shop_a  uuid;
  v_shop_b  uuid;
  v_lignes  integer;
  v_result  jsonb;
  v_texte   text;
  v_pro     boolean;
  v_touche  boolean;
  v_owner   text := current_user;
begin
  insert into auth.users (instance_id, id, aud, role, email)
  values
    ('00000000-0000-0000-0000-000000000000', v_a, 'authenticated', 'authenticated', 'admin-a@example.invalid'),
    ('00000000-0000-0000-0000-000000000000', v_b, 'authenticated', 'authenticated', 'admin-b@example.invalid'),
    ('00000000-0000-0000-0000-000000000000', v_admin, 'authenticated', 'authenticated', 'admin-plateforme@example.invalid');

  -- ------------------------------------------------------------------ user A
  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);
  select (public.create_shop_for_me('Console A')).id into v_shop_a;

  -- ------------------------------------------------------------------ user B
  perform set_config('request.jwt.claims', json_build_object('sub', v_b, 'role', 'authenticated')::text, true);
  select (public.create_shop_for_me('Console B')).id into v_shop_b;

  -- --------------------------------------------------- A n'est pas admin
  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);

  select count(*) into v_lignes from public.admin_boutiques();
  if v_lignes <> 0 then
    raise exception 'admin_boutiques() ouvert à un non-administrateur (% lignes)', v_lignes;
  end if;

  select count(*) into v_lignes from public.admin_utilisateurs();
  if v_lignes <> 0 then
    raise exception 'admin_utilisateurs() ouvert à un non-administrateur (% lignes)', v_lignes;
  end if;

  if public.admin_statistiques() is not null then
    raise exception 'admin_statistiques() a répondu à un non-administrateur';
  end if;

  v_touche := false;
  begin
    perform public.admin_abonnement(v_shop_a, 'pro', current_date + 30, 'test');
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'un non-administrateur a écrit un abonnement';
  end if;

  v_touche := false;
  begin
    perform public.admin_nommer('admin-b@example.invalid');
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'un non-administrateur a nommé un administrateur';
  end if;

  -- ------------------------------------------------- l'administrateur peut
  -- L'amorçage se fait hors du rôle `authenticated` : les politiques de
  -- `platform_admins` n'autorisent que les administrateurs à écrire.
  perform set_config('role', v_owner, true);
  insert into public.platform_admins (user_id) values (v_admin);
  perform set_config('role', 'authenticated', true);
  perform set_config('request.jwt.claims', json_build_object('sub', v_admin, 'role', 'authenticated')::text, true);

  select count(*) into v_lignes from public.admin_boutiques();
  if v_lignes <> 2 then
    raise exception 'admin_boutiques() devrait voir 2 boutiques, en a vu %', v_lignes;
  end if;

  select proprietaire into v_texte from public.admin_boutiques() where id = v_shop_a;
  if v_texte <> 'admin-a@example.invalid' then
    raise exception 'propriétaire inattendu : %', v_texte;
  end if;

  select count(*) into v_lignes from public.admin_utilisateurs();
  if v_lignes <> 3 then
    raise exception 'admin_utilisateurs() devrait voir 3 comptes, en a vu %', v_lignes;
  end if;

  v_result := public.admin_statistiques();
  if (v_result ->> 'boutiques')::integer <> 2 then
    raise exception 'statistiques : boutiques = %', v_result ->> 'boutiques';
  end if;

  -- Une date passée est refusée : Pro sans fin vivante donnerait le plan à vie.
  v_touche := false;
  begin
    perform public.admin_abonnement(v_shop_a, 'pro', current_date - 1, 'test');
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'admin_abonnement a accepté une date passée';
  end if;

  perform public.admin_abonnement(v_shop_a, 'pro', current_date + 365, 'activation test');
  select public.boutique_est_pro(v_shop_a) into v_pro;
  if not v_pro then
    raise exception 'la boutique n’est pas Pro après activation';
  end if;

  begin
    perform public.admin_abonnement(v_shop_b, 'gratuit', null, 'retour');
    if exists (select 1 from public.subscriptions where shop_id = v_shop_b and plan = 'pro') then
      raise exception 'le retour en gratuit a laissé un plan Pro';
    end if;
  end;

  -- ------------------------------------------- nommer / retirer un admin
  -- `platform_admins` n'a aucune politique : elle n'est lisible que par les
  -- fonctions SECURITY DEFINER, donc la vérification passe par
  -- admin_utilisateurs() (colonne est_admin).
  perform public.admin_nommer('admin-b@example.invalid');
  select est_admin into v_pro from public.admin_utilisateurs() where id = v_b;
  if not coalesce(v_pro, false) then
    raise exception 'admin_nommer n’a pas nommé B';
  end if;

  perform public.admin_retirer(v_b);
  select est_admin into v_pro from public.admin_utilisateurs() where id = v_b;
  if coalesce(v_pro, true) then
    raise exception 'admin_retirer n’a pas retiré B';
  end if;

  -- Le dernier administrateur ne peut pas partir : plus personne ne pourrait
  -- rouvrir la console ni activer un plan Pro.
  v_touche := false;
  begin
    perform public.admin_retirer(v_admin);
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'le dernier administrateur a pu se retirer';
  end if;

  raise notice 'console d’administration : tout est conforme';
end $$;

rollback;
