-- FactureApp — trois paliers : gratuit, essentiel, pro
--
-- Le pendant de rls_admin_console.sql pour la 0014. Ce que ce fichier prouve :
--   · le plan gratuit s'arrête à 5 documents facturés par mois ;
--   · Essentiel s'arrête à 50, bonus d'administration compris ;
--   · un Essentiel dont la date est passée retombe à 5 et se signale
--     (`expire`), sans perdre la trace du plan acheté ;
--   · Pro est illimité, et le redevient après une réactivation ;
--   · `plan_effectif` et `quota_limite` disent la même chose que l'émission ;
--   · `admin_abonnement` refuse un plan payant sans date future, un plan
--     inconnu, et un non-administrateur — et journalise chaque changement.
--
--   psql "$DATABASE_URL" -f supabase/tests/rls_plans.sql

begin;

do $$
declare
  v_a       uuid := 'e1111111-1111-4111-8111-111111111111';
  v_admin   uuid := 'e2222222-2222-4222-8222-222222222222';
  v_owner   text := current_user;
  v_shop    uuid;
  v_doc     uuid;
  v_quota   jsonb;
  v_i       integer;
  v_emis    integer;
  v_touche  boolean;
  v_texte   text;
begin
  insert into auth.users (instance_id, id, aud, role, email)
  values
    ('00000000-0000-0000-0000-000000000000', v_a, 'authenticated', 'authenticated', 'plans-a@example.invalid'),
    ('00000000-0000-0000-0000-000000000000', v_admin, 'authenticated', 'authenticated', 'plans-admin@example.invalid');

  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);
  select (public.create_shop_for_me('Plans A')).id into v_shop;

  perform set_config('role', v_owner, true);
  update public.shops set ice = '001234567000062', if_fiscal = '12345678' where id = v_shop;
  insert into public.platform_admins (user_id) values (v_admin);
  perform set_config('role', 'authenticated', true);

  -- ------------------------------------------------------------- le gratuit
  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  v_quota := public.quota_boutique(v_shop);
  if (v_quota ->> 'plan') <> 'gratuit' then
    raise exception 'une boutique neuve devrait être gratuite (%)', v_quota ->> 'plan';
  end if;
  if (v_quota ->> 'limite')::integer <> 5 then
    raise exception 'limite gratuite inattendue : %', v_quota ->> 'limite';
  end if;
  if (v_quota ->> 'expire')::boolean then
    raise exception 'une boutique neuve se dit expirée';
  end if;

  for v_i in 1..5 loop
    v_doc := public.enregistrer_brouillon(
      null,
      jsonb_build_object('shop_id', v_shop, 'type', 'facture',
                         'client_snapshot', jsonb_build_object('nom', 'Client ' || v_i)),
      jsonb_build_array(jsonb_build_object('libelle', 'Prestation', 'quantite', 1,
                                           'prix_unitaire', 100, 'tva_taux', 20, 'total_ht', 100))
    );
    perform public.emettre_document(v_doc);
  end loop;

  v_doc := public.enregistrer_brouillon(
    null,
    jsonb_build_object('shop_id', v_shop, 'type', 'facture',
                       'client_snapshot', jsonb_build_object('nom', 'Client 6')),
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
    raise exception 'le plan gratuit a laissé passer une sixième facture';
  end if;

  -- --------------------------------------------------- refus côté console
  v_touche := false;
  begin
    perform public.admin_abonnement(v_shop, 'essentiel', current_date + 30, 'test');
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'un non-administrateur a activé un plan';
  end if;

  perform set_config('request.jwt.claims', json_build_object('sub', v_admin, 'role', 'authenticated')::text, true);

  v_touche := false;
  begin
    perform public.admin_abonnement(v_shop, 'essentiel', null, 'sans date');
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'un plan payant sans date de fin a été accepté';
  end if;

  v_touche := false;
  begin
    perform public.admin_abonnement(v_shop, 'platine', current_date + 30, 'inconnu');
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'un plan inconnu a été accepté';
  end if;

  -- ----------------------------------------------------------- l'essentiel
  perform public.admin_abonnement(v_shop, 'essentiel', current_date + 365, 'virement reçu');

  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  v_quota := public.quota_boutique(v_shop);
  if (v_quota ->> 'plan') <> 'essentiel' then
    raise exception 'le plan essentiel ne s’applique pas (%)', v_quota ->> 'plan';
  end if;
  if (v_quota ->> 'limite')::integer <> 50 then
    raise exception 'limite essentielle inattendue : %', v_quota ->> 'limite';
  end if;
  if (v_quota ->> 'suspendue')::boolean then
    raise exception 'une boutique payante se dit suspendue';
  end if;

  for v_i in 6..50 loop
    v_doc := public.enregistrer_brouillon(
      null,
      jsonb_build_object('shop_id', v_shop, 'type', 'facture',
                         'client_snapshot', jsonb_build_object('nom', 'Client ' || v_i)),
      jsonb_build_array(jsonb_build_object('libelle', 'Prestation', 'quantite', 1,
                                           'prix_unitaire', 100, 'tva_taux', 20, 'total_ht', 100))
    );
    perform public.emettre_document(v_doc);
  end loop;

  v_doc := public.enregistrer_brouillon(
    null,
    jsonb_build_object('shop_id', v_shop, 'type', 'facture',
                       'client_snapshot', jsonb_build_object('nom', 'Client 51')),
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
    raise exception 'Essentiel a laissé passer une 51e facture';
  end if;

  -- Le bonus d'administration s'ajoute au palier, il ne le remplace pas.
  perform set_config('request.jwt.claims', json_build_object('sub', v_admin, 'role', 'authenticated')::text, true);
  perform public.admin_quota_bonus(v_shop, 3, 'geste commercial');

  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  v_quota := public.quota_boutique(v_shop);
  if (v_quota ->> 'limite')::integer <> 53 then
    raise exception 'le bonus ne s’ajoute pas à Essentiel : %', v_quota ->> 'limite';
  end if;
  perform public.emettre_document(v_doc);

  select count(*) into v_emis from public.documents where shop_id = v_shop and statut = 'emis';
  if v_emis <> 51 then
    raise exception '51 factures attendues, % émises', v_emis;
  end if;

  -- ------------------------------------------------------ l'essentiel expiré
  perform set_config('role', v_owner, true);
  update public.subscriptions set paid_until = current_date - 1 where shop_id = v_shop;
  perform set_config('role', 'authenticated', true);

  v_quota := public.quota_boutique(v_shop);
  if (v_quota ->> 'plan') <> 'gratuit' then
    raise exception 'un Essentiel expiré devrait rendre gratuit (%)', v_quota ->> 'plan';
  end if;
  if not (v_quota ->> 'expire')::boolean then
    raise exception 'l’expiration ne se signale pas';
  end if;
  if (v_quota ->> 'limite')::integer <> 8 then
    raise exception 'limite après expiration inattendue : %', v_quota ->> 'limite';
  end if;

  v_doc := public.enregistrer_brouillon(
    null,
    jsonb_build_object('shop_id', v_shop, 'type', 'facture',
                       'client_snapshot', jsonb_build_object('nom', 'Client 52')),
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
    raise exception 'un Essentiel expiré a continué d’émettre';
  end if;

  -- ------------------------------------------------------------------ Pro
  perform set_config('request.jwt.claims', json_build_object('sub', v_admin, 'role', 'authenticated')::text, true);
  perform public.admin_abonnement(v_shop, 'pro', current_date + 365, 'passage au Pro');

  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  v_quota := public.quota_boutique(v_shop);
  if (v_quota ->> 'plan') <> 'pro' then
    raise exception 'le plan Pro ne s’applique pas (%)', v_quota ->> 'plan';
  end if;
  if (v_quota -> 'limite') <> 'null'::jsonb then
    raise exception 'Pro devrait être illimité, limite = %', v_quota ->> 'limite';
  end if;
  if (v_quota ->> 'expire')::boolean then
    raise exception 'un Pro actif se dit expiré';
  end if;
  perform public.emettre_document(v_doc);

  -- Le bonus reste sans effet : Pro est déjà illimité.
  if public.quota_limite(v_shop) is not null then
    raise exception 'quota_limite devrait être nul en Pro';
  end if;

  -- -------------------------------------------------------------- Pro expiré
  perform set_config('role', v_owner, true);
  update public.subscriptions set paid_until = current_date - 1 where shop_id = v_shop;
  perform set_config('role', 'authenticated', true);

  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  v_quota := public.quota_boutique(v_shop);
  if (v_quota ->> 'plan') <> 'gratuit' or not (v_quota ->> 'expire')::boolean then
    raise exception 'un Pro expiré devrait retomber en gratuit expiré (%)', v_quota ->> 'plan';
  end if;
  if public.plan_effectif(v_shop) <> 'gratuit' then
    raise exception 'plan_effectif ignore l’expiration : %', public.plan_effectif(v_shop);
  end if;

  -- ------------------------------------------------------------- le journal
  perform set_config('request.jwt.claims', json_build_object('sub', v_admin, 'role', 'authenticated')::text, true);

  select count(*) into v_emis
  from public.admin_journal(50, v_shop)
  where action = 'abonnement' and acteur = 'plans-admin@example.invalid';
  if v_emis <> 2 then
    raise exception 'le journal devrait garder 2 changements d’abonnement, en a %', v_emis;
  end if;

  select details ->> 'nouveau' into v_texte
  from public.admin_journal(50, v_shop)
  where action = 'abonnement' and details ->> 'nouveau' = 'pro';
  if v_texte is null then
    raise exception 'le journal ne dit pas quel palier a été activé';
  end if;

  -- Le journal ne montre que la trace : le plan brut reste pour la console.
  select plan into v_texte from public.subscriptions where shop_id = v_shop;
  if v_texte <> 'pro' then
    raise exception 'le plan brut a été perdu (%)', v_texte;
  end if;

  -- ------------------------------------------------------- les statistiques
  if (public.admin_statistiques() ->> 'essentiel') is null then
    raise exception 'admin_statistiques ne compte pas les Essentiel';
  end if;

  raise notice 'trois paliers (0014) : tout est conforme';
end $$;

rollback;
