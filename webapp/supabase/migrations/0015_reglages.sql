-- FactureApp — 0015 : les réglages de la plateforme, à commencer par WhatsApp
--
-- Les prix vivent dans `offres.ts` parce qu'ils changent une fois par an et
-- qu'un diff doit les montrer. Un numéro WhatsApp, lui, ne peut pas attendre un
-- déploiement : il doit se poser depuis la console, sans redémarrer le site.
--
-- Une seule table clé/valeur, sans aucune politique : ni `anon` ni
-- `authenticated` ne la lisent, même en direct. La seule ouverture est
-- `reglages_publics()`, qui ne rend que les clés destinées au public — c'est ce
-- qui permet à /app/tarifs, page visitée avant toute connexion, d'afficher le
-- bouton d'achat. Ajouter une clé ici n'ouvre donc rien tant qu'elle n'est pas
-- nommée dans cette fonction.

-- --------------------------------------------------------------------------
-- La table
-- --------------------------------------------------------------------------

create table if not exists public.plateforme_reglages (
  cle        text        primary key check (cle ~ '^[a-z][a-z0-9_]{1,40}$'),
  valeur     text        not null default '',
  modifie_le timestamptz not null default now(),
  par        uuid        references auth.users (id) on delete set null
);

alter table public.plateforme_reglages enable row level security;
revoke all on public.plateforme_reglages from anon, authenticated;

-- --------------------------------------------------------------------------
-- Lecture publique
-- --------------------------------------------------------------------------

-- Un `jsonb` plutôt que `returns table` : le nombre de réglages publics bouge,
-- les appelants lisent par nom. Une clé absente vaut chaîne vide, jamais null,
-- pour que l'écran teste `''` au lieu de gérer deux vides différents.
create or replace function public.reglages_publics()
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'whatsapp', coalesce(
      (select r.valeur from public.plateforme_reglages r where r.cle = 'whatsapp'), '')
  );
$$;

revoke all on function public.reglages_publics() from public;
grant execute on function public.reglages_publics() to anon, authenticated;

comment on function public.reglages_publics() is
  'Les réglages destinés au public (aujourd''hui : le numéro WhatsApp). Lisible sans connexion, par conception.';

-- --------------------------------------------------------------------------
-- Écriture
-- --------------------------------------------------------------------------

-- Le numéro est normalisé ici, pas seulement dans le formulaire : un appel
-- direct à l'API doit produire la même valeur qu'un clic dans la console.
--
--   « +212 6 12 34 56 78 », « 0612345678 », « 06-12-34-56-78 »
--     → 212612345678
--   chaîne vide (ou que des séparateurs)
--     → le lien disparaît du site, la clé reste à ''
--
-- Tout le reste est refusé : un numéro faux ne casse pas la page, il envoie un
-- client au mauvais endroit.
create or replace function public.admin_reglage_whatsapp(p_numero text)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_numero text;
  v_ancien text;
begin
  if not public.est_admin_plateforme() then
    raise exception 'réservé à l’administration' using errcode = '42501';
  end if;

  -- Seuls les chiffres et les séparateurs d'usage sont acceptés. Sans ce
  -- contrôle, « bonjour » perdrait toutes ses lettres et ressemblerait à une
  -- demande d'effacement : un texte collé par erreur effacerait le numéro.
  if coalesce(p_numero, '') !~ '^[0-9+()\s./-]*$' then
    raise exception 'numéro WhatsApp invalide'
      using errcode = '22023',
            hint = 'Un numéro ne contient que des chiffres, des espaces, + - ( ) . /';
  end if;

  v_numero := regexp_replace(coalesce(p_numero, ''), '[^0-9]', '', 'g');

  -- Un numéro marocain local (06…, 07…, 05…) vaut 212 + les neuf chiffres.
  if length(v_numero) = 10 and left(v_numero, 1) = '0' then
    v_numero := '212' || substr(v_numero, 2);
  end if;

  if v_numero <> '' then
    if v_numero !~ '^[1-9][0-9]{7,14}$' then
      raise exception 'numéro WhatsApp invalide'
        using errcode = '22023',
              hint = 'Donnez un numéro marocain (06 12 34 56 78) ou le format international (212612345678).';
    end if;
  end if;

  select valeur into v_ancien
  from public.plateforme_reglages where cle = 'whatsapp';

  insert into public.plateforme_reglages (cle, valeur, modifie_le, par)
  values ('whatsapp', v_numero, now(), auth.uid())
  on conflict (cle) do update
    set valeur     = excluded.valeur,
        modifie_le = now(),
        par        = excluded.par;

  perform public.admin_journaliser(
    'reglage_modifie', 'plateforme', null, 'Plateforme',
    jsonb_build_object(
      'cle', 'whatsapp',
      'ancien', coalesce(v_ancien, ''),
      'nouveau', v_numero
    ));

  return v_numero;
end;
$$;

revoke all on function public.admin_reglage_whatsapp(text) from public, anon;
grant execute on function public.admin_reglage_whatsapp(text) to authenticated;

comment on function public.admin_reglage_whatsapp(text) is
  'Pose ou retire le numéro WhatsApp public, le normalise, et journalise l''ancien et le nouveau.';
