-- FactureApp — les réglages publics (0015)
--
-- Ce que ce fichier prouve :
--   · `anon` lit `reglages_publics()` sans être connecté — c'est la page
--     /app/tarifs qui en vit — et n'obtient qu'une chaîne vide au départ ;
--   · personne ne lit la table en direct, ni `anon` ni un compte connecté ;
--   · un non-administrateur ne peut pas écrire le numéro ;
--   · un numéro marocain est normalisé (« 06 12 34 56 78 » → 212612345678),
--     un numéro invalide est refusé, et une chaîne vide retire le numéro ;
--   · chaque écriture laisse une ligne `reglage_modifie` dans le journal.
--
--   psql "$DATABASE_URL" -f supabase/tests/rls_reglages.sql

begin;

do $$
declare
  v_a       uuid := 'f1111111-1111-4111-8111-111111111111';
  v_admin   uuid := 'f2222222-2222-4222-8222-222222222222';
  v_owner   text := current_user;
  v_public  jsonb;
  v_numero  text;
  v_touche  boolean;
  v_journal integer;
begin
  insert into auth.users (instance_id, id, aud, role, email)
  values
    ('00000000-0000-0000-0000-000000000000', v_a, 'authenticated', 'authenticated', 'reglages-a@example.invalid'),
    ('00000000-0000-0000-0000-000000000000', v_admin, 'authenticated', 'authenticated', 'reglages-admin@example.invalid');

  insert into public.platform_admins (user_id) values (v_admin);

  -- ------------------------------------------------- lecture sans connexion
  perform set_config('request.jwt.claims', '', true);
  perform set_config('role', 'anon', true);

  v_public := public.reglages_publics();
  if coalesce(v_public ->> 'whatsapp', 'null') <> '' then
    raise exception 'un numéro WhatsApp existe déjà : %', v_public;
  end if;
  if v_public ? 'cle' or v_public ? 'par' or v_public ? 'modifie_le' then
    raise exception 'reglages_publics() laisse fuir des colonnes internes : %', v_public;
  end if;

  -- la table elle-même reste fermée
  v_touche := false;
  begin
    perform count(*) from public.plateforme_reglages;
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'anon lit plateforme_reglages en direct';
  end if;

  -- ------------------------------------------------ écriture refusée à un compte
  perform set_config('request.jwt.claims', json_build_object('sub', v_a, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);

  v_touche := false;
  begin
    perform public.admin_reglage_whatsapp('0612345678');
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'un compte non administrateur a écrit le numéro WhatsApp';
  end if;

  v_touche := false;
  begin
    perform count(*) from public.plateforme_reglages;
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'un compte connecté lit plateforme_reglages en direct';
  end if;

  -- ------------------------------------------------------------- écriture admin
  perform set_config('request.jwt.claims', json_build_object('sub', v_admin, 'role', 'authenticated')::text, true);

  v_numero := public.admin_reglage_whatsapp('06 12 34 56 78');
  if v_numero <> '212612345678' then
    raise exception 'numéro marocain mal normalisé : %', v_numero;
  end if;

  v_numero := public.admin_reglage_whatsapp('+212 (6) 12-34-56-78');
  if v_numero <> '212612345678' then
    raise exception 'numéro international mal normalisé : %', v_numero;
  end if;

  v_numero := public.admin_reglage_whatsapp('  ');
  if v_numero <> '' then
    raise exception 'une saisie vide devrait effacer le numéro (reçu %)', v_numero;
  end if;

  -- les formes courtes sont refusées, pas enregistrées
  v_touche := false;
  begin
    perform public.admin_reglage_whatsapp('0612');
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'un numéro trop court a été accepté';
  end if;

  v_touche := false;
  begin
    perform public.admin_reglage_whatsapp('bonjour');
  exception when others then
    v_touche := true;
  end;
  if not v_touche then
    raise exception 'un texte a été accepté comme numéro';
  end if;

  -- ------------------------------------------------- le public relit la valeur
  perform set_config('request.jwt.claims', json_build_object('sub', v_admin, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);
  perform public.admin_reglage_whatsapp('0700112233');

  perform set_config('role', 'anon', true);
  perform set_config('request.jwt.claims', '', true);
  v_public := public.reglages_publics();
  if v_public ->> 'whatsapp' <> '212700112233' then
    raise exception 'anon ne lit pas le numéro posé : %', v_public;
  end if;

  -- ---------------------------------------------------------------- le journal
  perform set_config('role', v_owner, true);
  select count(*) into v_journal
  from public.admin_actions
  where action = 'reglage_modifie'
    and cible_type = 'plateforme';
  if v_journal < 4 then
    raise exception 'le journal ne trace pas les réglages (% lignes)', v_journal;
  end if;

  if not exists (
    select 1 from public.admin_actions
    where action = 'reglage_modifie'
      and cible_type = 'plateforme'
      and details ->> 'nouveau' = '212700112233'
  ) then
    raise exception 'le journal ne dit pas quelle valeur a été posée';
  end if;

  -- sortie propre : la base reste sans numéro
  delete from public.plateforme_reglages;
  delete from public.admin_actions where action = 'reglage_modifie';
  delete from public.platform_admins where user_id = v_admin;
  delete from auth.users where id in (v_a, v_admin);
end;
$$;

rollback;
