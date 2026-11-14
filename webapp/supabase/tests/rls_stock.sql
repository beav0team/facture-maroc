-- FactureApp — journal de stock (0017)
--
-- Ce que ce fichier prouve :
--   · un article naît avec son stock d'ouverture dans le journal, une
--     prestation n'en a pas et refuse un mouvement ;
--   · une facture sort la marchandise (les lignes du même article s'additionnent),
--     un devis ne touche à rien, un avoir émis la remet ;
--   · un mouvement manuel (réception, inventaire, correction) passe par la
--     fonction, porte son coût et met le cache à jour ;
--   · un mouvement manuel ne peut pas se faire passer pour une facture ;
--   · la lecture est réservée aux membres ; écrire la table directement échoue ;
--   · `stock_soldes()` et `stock_ecarts()` disent la même chose que le cache.
--
--   psql "$DATABASE_URL" -f supabase/tests/rls_stock.sql

begin;

do $$
declare
  v_a        uuid := 'b1111111-1111-4111-8111-111111111111';
  v_b        uuid := 'b2222222-2222-4222-8222-222222222222';
  v_owner    text := current_user;
  v_shop_a   uuid;
  v_shop_b   uuid;
  v_produit  uuid;
  v_service  uuid;
  v_facture  uuid;
  v_devis    uuid;
  v_avoir    uuid;
  v_lu       integer;
  v_stock    integer;
  v_reponse  jsonb;
  v_touche   boolean;
  v_motif    text;
  v_cout     numeric;
  v_ref      text;
  v_mois     text := 'Test';
begin
  insert into auth.users (instance_id, id, aud, role, email)
  values
    ('00000000-0000-0000-0000-000000000000', v_a, 'authenticated', 'authenticated', 'stock-a@example.invalid'),
    ('00000000-0000-0000-0000-000000000000', v_b, 'authenticated', 'authenticated', 'stock-b@example.invalid');

  -- ---------------------------------------------------------------- boutique A
  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);
  select (public.create_shop_for_me('Stock A')).id into v_shop_a;

  perform set_config('role', v_owner, true);
  update public.shops set ice = '001234567000062', if_fiscal = '12345678' where id = v_shop_a;

  perform set_config('request.jwt.claims', json_build_object('sub', v_b, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);
  select (public.create_shop_for_me('Stock B')).id into v_shop_b;

  -- Une facture ne part pas sans l'identité du vendeur.
  perform set_config('role', v_owner, true);
  update public.shops set ice = '001234567000062', if_fiscal = '12345678' where id = v_shop_b;

  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);

  -- -------------------------------------------------------- articles de départ
  insert into public.articles (shop_id, libelle, prix, stock, prix_achat)
  values (v_shop_a, 'Pneu 205/55 R16', 800, 10, 500)
  returning id into v_produit;

  insert into public.articles (shop_id, libelle, prix, stock)
  values (v_shop_a, 'Montage', 50, null)
  returning id into v_service;

  -- Le stock d'ouverture est dans le journal, et le cache le dit.
  select coalesce(sum(delta), 0) into v_lu
  from public.stock_movements
  where article_id = v_produit;
  if v_lu <> 10 then
    raise exception 'stock d''ouverture absent du journal : %', v_lu;
  end if;
  select stock into v_stock from public.articles where id = v_produit;
  if v_stock <> 10 then
    raise exception 'le cache ne dit pas 10 : %', v_stock;
  end if;
  if exists (select 1 from public.stock_movements where article_id = v_service) then
    raise exception 'une prestation a reçu un mouvement de stock';
  end if;

  -- ------------------------------------------------------------- une facture
  v_facture := public.enregistrer_brouillon(
    null,
    jsonb_build_object('shop_id', v_shop_a, 'type', 'facture',
                       'client_snapshot', jsonb_build_object('nom', 'Garage Atlas')),
    jsonb_build_array(
      jsonb_build_object('article_id', v_produit, 'libelle', 'Pneu', 'quantite', 2,
                         'prix_unitaire', 800, 'tva_taux', 20, 'total_ht', 1600),
      jsonb_build_object('article_id', v_produit, 'libelle', 'Pneu', 'quantite', 1,
                         'prix_unitaire', 800, 'tva_taux', 20, 'total_ht', 800),
      jsonb_build_object('article_id', v_service, 'libelle', 'Montage', 'quantite', 1,
                         'prix_unitaire', 50, 'tva_taux', 20, 'total_ht', 50)));
  perform public.emettre_document(v_facture);

  select coalesce(sum(delta), 0) into v_lu from public.stock_movements where article_id = v_produit;
  select stock into v_stock from public.articles where id = v_produit;
  if v_lu <> 7 or v_stock <> 7 then
    raise exception 'la vente de 3 n''a pas sorti 3 : journal % cache %', v_lu, v_stock;
  end if;

  select delta, motif, reference into v_lu, v_motif, v_ref
  from public.stock_movements
  where article_id = v_produit and motif = 'invoice';
  if v_lu <> -3 or v_motif <> 'invoice' or v_ref = '' then
    raise exception 'mouvement de vente inattendu : delta % motif % référence « % »', v_lu, v_motif, v_ref;
  end if;

  -- Un devis n'est pas une vente.
  v_devis := public.enregistrer_brouillon(
    null,
    jsonb_build_object('shop_id', v_shop_a, 'type', 'devis',
                       'client_snapshot', jsonb_build_object('nom', 'Garage Atlas')),
    jsonb_build_array(
      jsonb_build_object('article_id', v_produit, 'libelle', 'Pneu', 'quantite', 5,
                         'prix_unitaire', 800, 'tva_taux', 20, 'total_ht', 4000)));
  perform public.emettre_document(v_devis);

  select coalesce(sum(delta), 0) into v_lu from public.stock_movements where article_id = v_produit;
  if v_lu <> 7 then
    raise exception 'un devis émis a bougé le stock : %', v_lu;
  end if;

  -- ----------------------------------------------------------------- un avoir
  v_avoir := public.creer_avoir(v_facture);
  perform public.emettre_document(v_avoir);

  select coalesce(sum(delta), 0) into v_lu from public.stock_movements where article_id = v_produit;
  select stock into v_stock from public.articles where id = v_produit;
  if v_lu <> 10 or v_stock <> 10 then
    raise exception 'l''avoir n''a pas remis les 3 : journal % cache %', v_lu, v_stock;
  end if;
  if not exists (
    select 1 from public.stock_movements
    where article_id = v_produit and motif = 'credit' and delta = 3
  ) then
    raise exception 'le mouvement d''avoir n''est pas écrit comme un crédit de 3';
  end if;

  -- ------------------------------------------------------ mouvements manuels
  v_reponse := public.enregistrer_mouvement_stock(v_produit, 4, 'purchase', 'Livraison', 520);
  if (v_reponse ->> 'quantite')::integer <> 14 then
    raise exception 'réception non appliquée : %', v_reponse;
  end if;
  select cout_unitaire into v_cout
  from public.stock_movements
  where article_id = v_produit and motif = 'purchase';
  if v_cout <> 520 then
    raise exception 'coût unitaire non enregistré : %', v_cout;
  end if;

  -- Une sortie ne porte pas de coût : c'est l'entrée qui vaut.
  perform public.enregistrer_mouvement_stock(v_produit, -2, 'stocktake', 'Casse', 900);
  select cout_unitaire into v_cout
  from public.stock_movements
  where article_id = v_produit and motif = 'stocktake';
  if v_cout <> 0 then
    raise exception 'une sortie a gardé un coût : %', v_cout;
  end if;

  select coalesce(sum(delta), 0) into v_lu from public.stock_movements where article_id = v_produit;
  select stock into v_stock from public.articles where id = v_produit;
  if v_lu <> 12 or v_stock <> 12 then
    raise exception 'le journal et le cache divergent après gestes manuels : % / %', v_lu, v_stock;
  end if;

  -- ----------------------------------------------------------- refus attendus
  for v_motif in select unnest(array['invoice', 'credit']) loop
    v_touche := false;
    begin
      perform public.enregistrer_mouvement_stock(v_produit, 1, v_motif);
    exception when others then
      v_touche := true;
    end;
    if not v_touche then
      raise exception 'un mouvement « % » a été accepté à la main', v_motif;
    end if;
  end loop;

  v_touche := false;
  begin
    perform public.enregistrer_mouvement_stock(v_service, 1, 'purchase');
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'une prestation a accepté un mouvement de stock';
  end if;

  v_touche := false;
  begin
    perform public.enregistrer_mouvement_stock(v_produit, 0, 'adjustment');
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'un mouvement de zéro a été accepté';
  end if;

  -- ------------------------------------------------- lecture et confinement
  if exists (select 1 from public.stock_ecarts(v_shop_a)) then
    raise exception 'stock_ecarts signale un écart sur une boutique saine';
  end if;
  select quantite into v_stock from public.stock_soldes(v_shop_a) where article_id = v_produit;
  if v_stock <> 12 then
    raise exception 'stock_soldes ne dit pas 12 : %', v_stock;
  end if;

  -- B ne lit rien de A.
  perform set_config('request.jwt.claims', json_build_object('sub', v_b, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);
  select count(*) into v_lu from public.stock_movements where shop_id = v_shop_a;
  if v_lu <> 0 then
    raise exception 'un autre compte lit % mouvements de stock', v_lu;
  end if;
  if exists (select 1 from public.stock_soldes(v_shop_a)) then
    raise exception 'stock_soldes rend des lignes à un non-membre';
  end if;

  v_touche := false;
  begin
    perform public.enregistrer_mouvement_stock(v_produit, 1, 'adjustment');
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'un non-membre a écrit un mouvement de stock';
  end if;

  -- Un membre peut lire le journal mais pas l'écrire à la main.
  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  v_lu := 0;
  begin
    insert into public.stock_movements (shop_id, article_id, delta, motif)
    values (v_shop_a, v_produit, 5, 'adjustment');
  exception when insufficient_privilege then
    v_lu := 1;
  end;
  if v_lu <> 1 then
    raise exception 'un membre a inséré un mouvement directement dans la table';
  end if;

  -- Sortie propre : la transaction est annulée, mais on ne laisse pas de
  -- données derrière soi si elle était rejouée sans rollback.
  perform set_config('role', v_owner, true);
  delete from public.stock_movements where shop_id in (v_shop_a, v_shop_b);
  delete from public.documents where shop_id in (v_shop_a, v_shop_b);
  delete from public.articles where shop_id in (v_shop_a, v_shop_b);
  delete from public.memberships where shop_id in (v_shop_a, v_shop_b);
  delete from public.shops where id in (v_shop_a, v_shop_b);
  delete from auth.users where id in (v_a, v_b);

  raise notice using message = 'Stock : journal, émission, avoir, gestes manuels et confinement vérifiés.';
end;
$$;

rollback;
