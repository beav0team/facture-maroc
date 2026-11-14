-- FactureApp — 0016 : les tarifs payants deviennent des données
--
-- La 0014 a mis les trois paliers en base, mais leurs prix et leurs
-- prestations sont restés dans `offres.ts` : changer 240 DH demandait un
-- commit, une relecture du site et un déploiement. Un prix est pourtant une
-- décision commerciale, pas du code — et un barème annuel qui bouge une fois
-- par an ne justifie pas un cycle de livraison.
--
-- Ce qui reste dans le code : les *clés* des paliers (`gratuit`, `essentiel`,
-- `pro`), la limite du gratuit (5 documents, le plancher du marché), et les
-- valeurs par défaut qui servent quand aucune ligne n'existe. Ce qui passe en
-- base : le prix annuel, le nombre de documents inclus, et la liste des
-- prestations affichées.
--
-- La limite n'est pas décorative : `quota_limite()` la lit. Un palier affiché
-- à 60 documents et refusé à 50 au 51e serait le pire des deux mondes.

-- --------------------------------------------------------------------------
-- La clé `tarifs`
-- --------------------------------------------------------------------------

-- Forme stockée (une seule ligne, clé `tarifs`) :
--
--   { "essentiel": { "prix": 240, "quota": 50,
--                    "points": ["50 documents facturés par mois", …] },
--     "pro":       { "prix": 480, "quota": null,
--                    "points": ["Documents facturés illimités", …] } }
--
-- `quota: null` sur Pro veut dire illimité, et c'est la seule valeur acceptée :
-- un Pro limité n'aurait plus de raison d'exister.
create or replace function public.tarifs_regles()
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(
    (select case when r.valeur ~ '^\s*\{' then r.valeur::jsonb else '{}'::jsonb end
       from public.plateforme_reglages r where r.cle = 'tarifs'),
    '{}'::jsonb);
$$;

revoke all on function public.tarifs_regles() from public, anon, authenticated;

-- --------------------------------------------------------------------------
-- Lecture publique (élargie)
-- --------------------------------------------------------------------------

-- `whatsapp` (0015) plus `tarifs`. Même règle : la fonction nomme ce qu'elle
-- expose, la table reste fermée. `{}` veut dire « rien n'a été réglé » : les
-- écrans retombent alors sur les valeurs par défaut d'`offres.ts`.
create or replace function public.reglages_publics()
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'whatsapp', coalesce(
      (select r.valeur from public.plateforme_reglages r where r.cle = 'whatsapp'), ''),
    'tarifs', public.tarifs_regles()
  );
$$;

revoke all on function public.reglages_publics() from public;
grant execute on function public.reglages_publics() to anon, authenticated;

comment on function public.reglages_publics() is
  'Les réglages destinés au public : le numéro WhatsApp et les tarifs payants. Lisible sans connexion, par conception.';

-- --------------------------------------------------------------------------
-- La limite suit le tarif vendu
-- --------------------------------------------------------------------------

-- Essentiel : le quota réglé, sinon 50. Pro : NULL (illimité), toujours.
-- Gratuit : 5 — il n'est pas dans `tarifs` et ne se vend pas.
create or replace function public.quota_limite(p_shop uuid)
returns integer
language sql
stable
security definer
set search_path = public
as $$
  select case public.plan_effectif(p_shop)
    when 'pro' then null
    when 'essentiel' then coalesce(
      (public.tarifs_regles() -> 'essentiel' ->> 'quota')::integer, 50)
      + coalesce((select q.documents_sup from public.quota_bonus q where q.shop_id = p_shop), 0)
    else 5 + coalesce(
      (select q.documents_sup from public.quota_bonus q where q.shop_id = p_shop), 0)
  end;
$$;

revoke all on function public.quota_limite(uuid) from public, anon;
grant execute on function public.quota_limite(uuid) to authenticated;

-- --------------------------------------------------------------------------
-- Écriture
-- --------------------------------------------------------------------------

-- Tout est revérifié ici, comme pour les autres écritures de la console : un
-- appel direct à l'API doit être refusé aussi proprement qu'un formulaire mal
-- rempli. Les bornes sont larges mais fermes : un prix à 0 DH ou un quota sous
-- le gratuit ne sont pas des erreurs de frappe anodines, ce sont des offres
-- incohérentes.
create or replace function public.admin_tarifs(p_tarifs jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_ancien jsonb := public.tarifs_regles();
  v_plan   text;
  v_prix   numeric;
  v_quota  numeric;
  v_points jsonb;
  v_point  text;
  v_i      integer;
  v_propre jsonb := '{}'::jsonb;
begin
  if not public.est_admin_plateforme() then
    raise exception 'réservé à l’administration' using errcode = '42501';
  end if;

  if p_tarifs is null or jsonb_typeof(p_tarifs) <> 'object' then
    raise exception 'aucun tarif à enregistrer' using errcode = '22023';
  end if;

  if p_tarifs - 'essentiel' - 'pro' <> '{}'::jsonb then
    raise exception 'palier inconnu dans les tarifs'
      using errcode = '22023', hint = 'Seuls « essentiel » et « pro » se règlent.';
  end if;

  foreach v_plan in array array['essentiel', 'pro'] loop
    if not (p_tarifs ? v_plan) then
      continue;
    end if;
    if jsonb_typeof(p_tarifs -> v_plan) <> 'object' then
      raise exception 'tarif % : objet attendu', v_plan using errcode = '22023';
    end if;

    -- ------------------------------------------------------------------ prix
    begin
      v_prix := (p_tarifs -> v_plan ->> 'prix')::numeric;
    exception when others then
      raise exception 'tarif % : prix illisible', v_plan using errcode = '22023';
    end;
    if v_prix is null or v_prix <> round(v_prix) or v_prix < 50 or v_prix > 50000 then
      raise exception 'tarif % : le prix doit être un nombre entier entre 50 et 50 000 DH', v_plan
        using errcode = '22023';
    end if;

    -- ----------------------------------------------------------------- quota
    if v_plan = 'pro' then
      if p_tarifs -> v_plan ? 'quota'
         and p_tarifs -> v_plan -> 'quota' <> 'null'::jsonb then
        raise exception 'Pro reste illimité : son quota ne se règle pas'
          using errcode = '22023';
      end if;
    else
      begin
        v_quota := (p_tarifs -> v_plan ->> 'quota')::numeric;
      exception when others then
        raise exception 'tarif % : quota illisible', v_plan using errcode = '22023';
      end;
      if v_quota is null or v_quota <> round(v_quota) or v_quota < 6 or v_quota > 100000 then
        raise exception 'tarif % : le quota doit être un entier entre 6 et 100 000 documents', v_plan
          using errcode = '22023', hint = 'Le plan gratuit en inclut déjà 5.';
      end if;
    end if;

    -- ---------------------------------------------------------------- points
    v_points := p_tarifs -> v_plan -> 'points';
    if v_points is null or jsonb_typeof(v_points) <> 'array'
       or jsonb_array_length(v_points) < 1 or jsonb_array_length(v_points) > 8 then
      raise exception 'tarif % : entre 1 et 8 prestations', v_plan using errcode = '22023';
    end if;

    v_propre := v_propre || jsonb_build_object(v_plan, jsonb_build_object(
      'prix', v_prix::integer,
      'quota', case when v_plan = 'pro' then null else v_quota::integer end,
      'points', '[]'::jsonb
    ));

    for v_i in 0 .. jsonb_array_length(v_points) - 1 loop
      v_point := btrim(coalesce(v_points ->> v_i, ''));
      if length(v_point) < 3 or length(v_point) > 140 then
        raise exception 'tarif % : une prestation fait entre 3 et 140 caractères', v_plan
          using errcode = '22023';
      end if;
      v_propre := jsonb_set(
        v_propre, array[v_plan, 'points'],
        (v_propre -> v_plan -> 'points') || to_jsonb(v_point));
    end loop;
  end loop;

  if v_propre = '{}'::jsonb then
    raise exception 'aucun palier à enregistrer' using errcode = '22023';
  end if;

  -- Un palier absent de l'appel garde ses valeurs : la console peut n'envoyer
  -- que ce qui a changé sans effacer l'autre carte.
  v_propre := v_ancien || v_propre;

  insert into public.plateforme_reglages (cle, valeur, modifie_le, par)
  values ('tarifs', v_propre::text, now(), auth.uid())
  on conflict (cle) do update
    set valeur     = excluded.valeur,
        modifie_le = now(),
        par        = excluded.par;

  perform public.admin_journaliser(
    'tarifs_modifies', 'plateforme', null, 'Plateforme',
    jsonb_build_object('ancien', v_ancien, 'nouveau', v_propre));

  return v_propre;
end;
$$;

revoke all on function public.admin_tarifs(jsonb) from public, anon;
grant execute on function public.admin_tarifs(jsonb) to authenticated;

comment on function public.admin_tarifs(jsonb) is
  'Règle les prix, quotas et prestations des paliers payants, refuse un prix non entier, un quota sous le gratuit, un Pro limité, et journalise l''ancien et le nouveau barème.';
