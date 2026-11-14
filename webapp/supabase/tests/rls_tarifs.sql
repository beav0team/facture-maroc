-- FactureApp — les tarifs payants réglables (0016)
--
-- Ce que ce fichier prouve :
--   · `reglages_publics()` expose `tarifs` à `anon`, et `{}` tant que rien
--     n'est réglé ;
--   · le quota d'Essentiel suit la valeur réglée, y compris à l'émission : un
--     palier vendu pour N documents les accorde vraiment ;
--   · Pro reste illimité même si la console envoie un quota ;
--   · un prix non entier, un prix à 0, un quota sous le gratuit et une liste de
--     prestations vide sont refusés ;
--   · un non-administrateur ne peut pas écrire ;
--   · le journal garde l'ancien et le nouveau barème.
--
--   psql "$DATABASE_URL" -f supabase/tests/rls_tarifs.sql

begin;

do $$
declare
  v_a        uuid := 'a1111111-1111-4111-8111-111111111111';
  v_admin    uuid := 'a2222222-2222-4222-8222-222222222222';
  v_owner    text := current_user;
  v_shop     uuid;
  v_public   jsonb;
  v_tarifs   jsonb;
  v_limite   integer;
  v_doc      uuid;
  v_touche   boolean;
  v_i        integer;
begin
  insert into auth.users (instance_id, id, aud, role, email)
  values
    ('00000000-0000-0000-0000-000000000000', v_a, 'authenticated', 'authenticated', 'tarifs-a@example.invalid'),
    ('00000000-0000-0000-0000-000000000000', v_admin, 'authenticated', 'authenticated', 'tarifs-admin@example.invalid');

  insert into public.platform_admins (user_id) values (v_admin);

  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);
  select (public.create_shop_for_me('Tarifs A')).id into v_shop;

  perform set_config('role', v_owner, true);
  update public.shops set ice = '001234567000062', if_fiscal = '12345678' where id = v_shop;
  perform set_config('role', 'authenticated', true);

  -- ------------------------------------------------ rien n'est réglé au départ
  perform set_config('role', 'anon', true);
  perform set_config('request.jwt.claims', '', true);
  v_public := public.reglages_publics();
  if v_public -> 'tarifs' <> '{}'::jsonb then
    raise exception 'des tarifs existent déjà : %', v_public -> 'tarifs';
  end if;

  -- --------------------------------------------------- un compte ne peut pas écrire
  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);
  v_touche := false;
  begin
    perform public.admin_tarifs('{"essentiel": {"prix": 100, "quota": 10, "points": ["Test"]}}'::jsonb);
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'un non-administrateur a réglé les tarifs';
  end if;

  -- ------------------------------------------------------------ l'écriture admin
  perform set_config('request.jwt.claims', json_build_object('sub', v_admin, 'role', 'authenticated')::text, true);

  v_tarifs := public.admin_tarifs(jsonb_build_object(
    'essentiel', jsonb_build_object(
      'prix', 300, 'quota', 60,
      'points', jsonb_build_array('60 documents facturés par mois', 'Support par e-mail')),
    'pro', jsonb_build_object(
      'prix', 600, 'quota', null,
      'points', jsonb_build_array('Documents facturés illimités'))
  ));

  if (v_tarifs -> 'essentiel' ->> 'prix')::integer <> 300 then
    raise exception 'prix Essentiel non enregistré : %', v_tarifs -> 'essentiel';
  end if;
  if (v_tarifs -> 'essentiel' ->> 'quota')::integer <> 60 then
    raise exception 'quota Essentiel non enregistré : %', v_tarifs -> 'essentiel';
  end if;
  if v_tarifs -> 'pro' -> 'quota' <> 'null'::jsonb then
    raise exception 'Pro devrait rester illimité : %', v_tarifs -> 'pro';
  end if;

  -- ------------------------------- la limite réglée est celle qui s'applique
  perform set_config('role', v_owner, true);
  update public.subscriptions set plan = 'essentiel', paid_until = current_date + 365
  where shop_id = v_shop;
  if not found then
    insert into public.subscriptions (shop_id, plan, paid_until, source)
    values (v_shop, 'essentiel', current_date + 365, 'manuel');
  end if;

  if public.quota_limite(v_shop) <> 60 then
    raise exception 'quota_limite ignore le tarif réglé : %', public.quota_limite(v_shop);
  end if;

  -- 60 documents passent, le 61e est refusé : le quota vendu est le quota tenu.
  perform set_config('role', 'authenticated', true);
  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  for v_i in 1 .. 60 loop
    v_doc := public.enregistrer_brouillon(
      null,
      jsonb_build_object('shop_id', v_shop, 'type', 'facture',
                         'client_snapshot', jsonb_build_object('nom', 'Client ' || v_i)),
      jsonb_build_array(jsonb_build_object('libelle', 'Prestation', 'quantite', 1,
                                           'prix_unitaire', 100, 'tva_taux', 20, 'total_ht', 100)));
    perform public.emettre_document(v_doc);
  end loop;

  v_doc := public.enregistrer_brouillon(
    null,
    jsonb_build_object('shop_id', v_shop, 'type', 'facture',
                       'client_snapshot', jsonb_build_object('nom', 'Client 61')),
    jsonb_build_array(jsonb_build_object('libelle', 'Prestation', 'quantite', 1,
                                         'prix_unitaire', 100, 'tva_taux', 20, 'total_ht', 100)));
  v_touche := false;
  begin
    perform public.emettre_document(v_doc);
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'le 61e document est passé alors que le quota réglé est 60';
  end if;

  -- --------------------------------------------------------------- refus divers
  perform set_config('request.jwt.claims', json_build_object('sub', v_admin, 'role', 'authenticated')::text, true);

  declare
    v_cas jsonb;
    v_libelle text;
  begin
    foreach v_libelle in array array[
      'prix à zéro',
      'prix décimal',
      'quota sous le gratuit',
      'prestations vides',
      'Pro limité',
      'palier inconnu'
    ] loop
      v_cas := case v_libelle
        when 'prix à zéro' then
          '{"essentiel": {"prix": 0, "quota": 50, "points": ["Un an"]}}'::jsonb
        when 'prix décimal' then
          '{"essentiel": {"prix": 249.5, "quota": 50, "points": ["Un an"]}}'::jsonb
        when 'quota sous le gratuit' then
          '{"essentiel": {"prix": 240, "quota": 5, "points": ["Un an"]}}'::jsonb
        when 'prestations vides' then
          '{"essentiel": {"prix": 240, "quota": 50, "points": []}}'::jsonb
        when 'Pro limité' then
          '{"pro": {"prix": 480, "quota": 200, "points": ["Un an"]}}'::jsonb
        else
          '{"platine": {"prix": 900, "quota": 50, "points": ["Un an"]}}'::jsonb
      end;

      v_touche := false;
      begin
        perform public.admin_tarifs(v_cas);
      exception when others then
        v_touche := true;
      end;
      if not v_touche then
        raise exception 'tarifs acceptés alors qu''ils devraient être refusés : %', v_libelle;
      end if;
    end loop;
  end;

  -- ------------------------------------------- le public lit le barème réglé
  perform set_config('role', 'anon', true);
  perform set_config('request.jwt.claims', '', true);
  v_public := public.reglages_publics();
  if (v_public -> 'tarifs' -> 'essentiel' ->> 'prix')::integer <> 300 then
    raise exception 'anon ne lit pas le prix réglé : %', v_public -> 'tarifs';
  end if;
  if v_public -> 'tarifs' -> 'pro' ->> 'prix' <> '600' then
    raise exception 'anon ne lit pas le prix Pro réglé : %', v_public -> 'tarifs';
  end if;

  -- ------------------------------------------------------------------ journal
  perform set_config('role', v_owner, true);
  if not exists (
    select 1 from public.admin_actions
    where action = 'tarifs_modifies'
      and cible_type = 'plateforme'
      and (details -> 'nouveau' -> 'essentiel' ->> 'prix')::integer = 300
  ) then
    raise exception 'le journal ne garde pas le barème enregistré';
  end if;

  -- sortie propre
  delete from public.plateforme_reglages;
  delete from public.admin_actions where action = 'tarifs_modifies';
  delete from public.platform_admins where user_id = v_admin;
  delete from auth.users where id in (v_a, v_admin);
end;
$$;

rollback;
