-- FactureApp — 0001 : boutiques, membres, RLS
--
-- À exécuter dans Supabase → SQL Editor (ou `supabase db push`).
-- Le script est idempotent : on peut le relancer sans casser l'existant.
--
-- Principe : un utilisateur ne voit que les boutiques dont il est membre.
-- Aucune écriture directe n'est autorisée sur ces deux tables : la création
-- d'une boutique passe par la fonction `create_shop_for_me`, qui insère la
-- boutique et l'appartenance « owner » dans la même transaction.

create extension if not exists pgcrypto;

-- --------------------------------------------------------------------------
-- Tables
-- --------------------------------------------------------------------------

create table if not exists public.shops (
  id             uuid primary key default gen_random_uuid(),
  nom            text        not null check (length(btrim(nom)) between 1 and 120),
  ice            text,
  if_fiscal      text,
  rc             text,
  patente        text,
  cnss           text,
  adresse        text,
  telephone      text,
  email          text,
  logo_path      text,
  tva_defaut     numeric(5, 2) not null default 20,
  arrondi_dirham boolean     not null default false,
  prefixes       jsonb       not null default '{}'::jsonb,
  created_by     uuid        references auth.users (id) on delete set null,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);

create table if not exists public.memberships (
  user_id    uuid        not null references auth.users (id) on delete cascade,
  shop_id    uuid        not null references public.shops (id) on delete cascade,
  role       text        not null default 'owner' check (role in ('owner', 'membre', 'admin')),
  created_at timestamptz not null default now(),
  primary key (user_id, shop_id)
);

create index if not exists memberships_shop_id_idx on public.memberships (shop_id);

-- --------------------------------------------------------------------------
-- RLS
-- --------------------------------------------------------------------------

alter table public.shops       enable row level security;
alter table public.memberships enable row level security;

-- SECURITY DEFINER : lit les appartenances sans repasser par la RLS de
-- `memberships`, ce qui évite toute récursion dans les politiques.
create or replace function public.is_shop_member(p_shop uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.memberships m
    where m.shop_id = p_shop
      and m.user_id = auth.uid()
  );
$$;

revoke all on function public.is_shop_member(uuid) from public, anon;
grant execute on function public.is_shop_member(uuid) to authenticated;

drop policy if exists "membre lit sa boutique" on public.shops;
create policy "membre lit sa boutique"
  on public.shops for select
  to authenticated
  using (public.is_shop_member(id));

drop policy if exists "membre met a jour sa boutique" on public.shops;
create policy "membre met a jour sa boutique"
  on public.shops for update
  to authenticated
  using (public.is_shop_member(id))
  with check (public.is_shop_member(id));

drop policy if exists "membre voit ses appartenances" on public.memberships;
create policy "membre voit ses appartenances"
  on public.memberships for select
  to authenticated
  using (user_id = auth.uid());

-- --------------------------------------------------------------------------
-- Privilèges (la RLS filtre ce que ces droits laissent passer)
-- --------------------------------------------------------------------------

revoke all on public.shops       from anon;
revoke all on public.memberships from anon;

grant select, update on public.shops       to authenticated;
grant select         on public.memberships to authenticated;

-- --------------------------------------------------------------------------
-- Création d'une boutique par son propriétaire
-- --------------------------------------------------------------------------

create or replace function public.create_shop_for_me(p_nom text)
returns public.shops
language plpgsql
security definer
set search_path = public
as $$
declare
  v_nom  text := btrim(coalesce(p_nom, ''));
  v_shop public.shops;
begin
  if auth.uid() is null then
    raise exception 'authentification requise' using errcode = '28000';
  end if;

  if v_nom = '' or length(v_nom) > 120 then
    raise exception 'nom de boutique invalide' using errcode = '22023';
  end if;

  insert into public.shops (nom, created_by)
  values (v_nom, auth.uid())
  returning * into v_shop;

  insert into public.memberships (user_id, shop_id, role)
  values (auth.uid(), v_shop.id, 'owner');

  return v_shop;
end;
$$;

revoke all on function public.create_shop_for_me(text) from public, anon;
grant execute on function public.create_shop_for_me(text) to authenticated;

-- --------------------------------------------------------------------------
-- updated_at
-- --------------------------------------------------------------------------

create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists shops_touch_updated_at on public.shops;
create trigger shops_touch_updated_at
  before update on public.shops
  for each row execute function public.touch_updated_at();
