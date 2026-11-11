-- 0005 — Platform administration console.
--
-- The console needs things a browser must never hold: the e-mail of every user
-- (auth.users), counts across all shops, and the right to write a subscription
-- for somebody else. Those live in `auth`, reachable only with the service
-- role, so every read and write goes through a SECURITY DEFINER function gated
-- on `est_admin_plateforme()`. `anon` gets nothing; `authenticated` keeps only
-- what the policies of 0003 already allowed.
--
-- Design rule: a console screen never queries a table directly. One function
-- per question returns exactly the rows the admin may see, which keeps the
-- privilege surface in the database instead of in the tab.

-- --------------------------------------------------------------------------
-- Reads
-- --------------------------------------------------------------------------

-- Shops with their owner, their plan and how much they actually use the app.
-- The owner join is LATERAL because the schema allows several rows with the
-- `owner` role; the oldest is the one the shop was created with.
create or replace function public.admin_boutiques()
returns table (
  id              uuid,
  nom             text,
  cree_le         timestamptz,
  proprietaire    text,
  proprietaire_id uuid,
  plan            text,
  jusqu_au        date,
  note            text,
  documents       bigint,
  clients         bigint,
  articles        bigint,
  pro             boolean
)
language sql
stable
security definer
set search_path = public
as $$
  select
    s.id,
    s.nom,
    s.created_at,
    u.email::text,
    m.user_id,
    coalesce(sub.plan, 'gratuit'),
    sub.paid_until,
    coalesce(sub.note, ''),
    (select count(*) from public.documents d where d.shop_id = s.id),
    (select count(*) from public.clients   c where c.shop_id = s.id),
    (select count(*) from public.articles  a where a.shop_id = s.id),
    public.boutique_est_pro(s.id)
  from public.shops s
  left join lateral (
    select mb.user_id
    from public.memberships mb
    where mb.shop_id = s.id and mb.role = 'owner'
    order by mb.created_at
    limit 1
  ) m on true
  left join auth.users u on u.id = m.user_id
  left join public.subscriptions sub on sub.shop_id = s.id
  where public.est_admin_plateforme()
  order by s.created_at;
$$;

-- Every account, the shop it belongs to, and whether it is an administrator.
create or replace function public.admin_utilisateurs()
returns table (
  id                 uuid,
  email              text,
  cree_le            timestamptz,
  derniere_connexion timestamptz,
  boutique           text,
  boutique_id        uuid,
  est_admin          boolean
)
language sql
stable
security definer
set search_path = public
as $$
  select
    u.id,
    u.email::text,
    u.created_at,
    u.last_sign_in_at,
    s.nom,
    s.id,
    exists (select 1 from public.platform_admins a where a.user_id = u.id)
  from auth.users u
  left join lateral (
    select m.shop_id
    from public.memberships m
    where m.user_id = u.id
    order by m.created_at
    limit 1
  ) m on true
  left join public.shops s on s.id = m.shop_id
  where public.est_admin_plateforme()
  order by u.created_at;
$$;

-- One row of figures for the top of the console. `null` for a non-admin rather
-- than an exception: the screen calls it before it has decided what to render.
create or replace function public.admin_statistiques()
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select case
    when public.est_admin_plateforme() then jsonb_build_object(
      'boutiques',        (select count(*) from public.shops),
      'utilisateurs',     (select count(*) from auth.users),
      'administrateurs',  (select count(*) from public.platform_admins),
      'pro',              (select count(*) from public.subscriptions s
                            where public.boutique_est_pro(s.shop_id)),
      'documents',        (select count(*) from public.documents),
      'documents_emis',   (select count(*) from public.documents where statut = 'emis'),
      'archives',         (select count(*) from public.archives),
      'octets_archives',  (select coalesce(sum(taille), 0) from public.archives)
    )
    else null
  end;
$$;

-- --------------------------------------------------------------------------
-- Writes
-- --------------------------------------------------------------------------

-- One subscription writer for the console. A Pro plan without a live end date
-- is refused here rather than trusted from the tab: `boutique_est_pro()` reads
-- a null `paid_until` as "Pro forever", so an accidental empty date would give
-- the plan away permanently.
create or replace function public.admin_abonnement(
  p_shop      uuid,
  p_plan      text,
  p_jusqu_au  date,
  p_note      text default ''
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.est_admin_plateforme() then
    raise exception 'réservé à l’administration' using errcode = '42501';
  end if;
  if p_plan not in ('gratuit', 'pro') then
    raise exception 'plan inconnu : %', p_plan using errcode = '22023';
  end if;
  if not exists (select 1 from public.shops where id = p_shop) then
    raise exception 'boutique inconnue' using errcode = '22023';
  end if;
  if p_plan = 'pro' and (p_jusqu_au is null or p_jusqu_au < current_date) then
    raise exception 'une date de fin future est nécessaire pour le plan Pro'
      using errcode = '22023';
  end if;

  insert into public.subscriptions (shop_id, plan, paid_until, source, note, updated_at)
  values (
    p_shop,
    p_plan,
    case when p_plan = 'pro' then p_jusqu_au else null end,
    'manuel',
    coalesce(btrim(p_note), ''),
    now()
  )
  on conflict (shop_id) do update
    set plan       = excluded.plan,
        paid_until = excluded.paid_until,
        source     = excluded.source,
        note       = excluded.note,
        updated_at = now();
end;
$$;

-- Nominating by e-mail is the whole point: the admin knows the address, not
-- the uuid, and reading auth.users from the browser is exactly what these
-- functions exist to avoid.
create or replace function public.admin_nommer(p_email text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
begin
  if not public.est_admin_plateforme() then
    raise exception 'réservé à l’administration' using errcode = '42501';
  end if;

  select id into v_id
  from auth.users
  where lower(email) = lower(btrim(coalesce(p_email, '')));

  if v_id is null then
    raise exception 'aucun compte avec cet e-mail' using errcode = '22023';
  end if;

  insert into public.platform_admins (user_id) values (v_id)
  on conflict (user_id) do nothing;

  return v_id;
end;
$$;

-- The last administrator cannot be removed: an empty `platform_admins` locks
-- the console and the manual Pro activation for good, with no way back in.
create or replace function public.admin_retirer(p_user uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.est_admin_plateforme() then
    raise exception 'réservé à l’administration' using errcode = '42501';
  end if;

  if (select count(*) from public.platform_admins) <= 1 then
    raise exception 'il faut au moins un administrateur' using errcode = '22023';
  end if;

  delete from public.platform_admins where user_id = p_user;
end;
$$;

-- --------------------------------------------------------------------------
-- Privileges
-- --------------------------------------------------------------------------

revoke all on function public.admin_boutiques()          from public, anon;
revoke all on function public.admin_utilisateurs()       from public, anon;
revoke all on function public.admin_statistiques()       from public, anon;
revoke all on function public.admin_abonnement(uuid, text, date, text) from public, anon;
revoke all on function public.admin_nommer(text)         from public, anon;
revoke all on function public.admin_retirer(uuid)        from public, anon;

grant execute on function public.admin_boutiques()          to authenticated;
grant execute on function public.admin_utilisateurs()       to authenticated;
grant execute on function public.admin_statistiques()       to authenticated;
grant execute on function public.admin_abonnement(uuid, text, date, text) to authenticated;
grant execute on function public.admin_nommer(text)         to authenticated;
grant execute on function public.admin_retirer(uuid)        to authenticated;
