-- FactureApp — 0017 : le journal de stock
--
-- Jusqu'ici `articles.stock` était une quantité qu'on corrigeait à la main : la
-- vente ne la touchait pas, et la boutique ne pouvait pas répondre à « pourquoi
-- il en reste trois ? ». Cette migration ajoute le journal — la seule source de
-- vérité — et laisse `articles.stock` en cache, écrit par les mêmes gestes.
--
-- La forme suit celle de l'application Android (`StockEntry` / `StockLedger`)
-- parce que les deux produits fusionneront : mêmes motifs (`invoice`, `credit`,
-- `purchase`, `stocktake`, `adjustment`), même delta signé, même coût unitaire
-- sur les entrées, même règle de direction à l'émission. Fusionner devra être un
-- rapprochement de données, pas une réécriture.
--
-- Ce que cette migration fait :
--   · une table `stock_movements` en ajout seul, lisible par les membres de la
--     boutique et écrite uniquement par des fonctions `security definer` ;
--   · la déduction à l'émission et la remise en stock à l'émission d'un avoir,
--     par un trigger — n'importe quel chemin qui émet un document bouge le
--     stock, y compris un futur écran ou une correction manuelle ;
--   · `enregistrer_mouvement_stock()` pour les gestes humains (réception,
--     inventaire, correction), qui refuse de se faire passer pour une facture ;
--   · `stock_soldes()` et `stock_ecarts()` pour lire les quantités et repérer
--     un cache qui a dérivé.
--
-- Ce qu'elle ne fait pas encore, volontairement : les écrans lisent toujours
-- `articles.stock` et le formulaire d'article écrit la quantité directement.
-- Le passage des écrans au journal (et le verrou qui interdit alors d'écrire la
-- colonne à la main) est l'étape suivante ; ici, tout est additif, donc
-- déployable sans changer un écran.

-- --------------------------------------------------------------------------
-- Le journal
-- --------------------------------------------------------------------------

create table if not exists public.stock_movements (
  id            uuid primary key default gen_random_uuid(),
  shop_id       uuid not null references public.shops (id) on delete cascade,
  article_id    uuid not null references public.articles (id) on delete cascade,
  -- Le document qui a bougé le stock, quand il y en a un. `on delete set null`
  -- garderait un mouvement orphelin si un brouillon disparaissait ; une pièce
  -- émise n'est jamais supprimée, donc le lien tient pour l'essentiel.
  document_id   uuid references public.documents (id) on delete set null,
  -- Signé : négatif quand la marchandise sort, positif quand elle revient.
  delta         integer not null check (delta <> 0 and abs(delta) <= 1000000),
  -- Les identifiants sont ceux d'Android, jamais un libellé : changer un mot
  -- dans l'interface ne doit pas réécrire l'histoire.
  motif         text not null check (motif in (
                  'invoice', 'credit', 'purchase', 'stocktake', 'adjustment')),
  -- Le numéro imprimé au moment du geste (« F-20261114-0001 »), ou le libellé
  -- d'un inventaire. Il reste lisible même si le document disparaît.
  reference     text not null default '',
  note          text not null default '',
  -- Coût HT d'une unité à l'entrée. 0 = inconnu, jamais « gratuit ».
  cout_unitaire numeric(12, 2) not null default 0 check (cout_unitaire >= 0),
  cree_le       timestamptz not null default now(),
  cree_par      uuid
);

create index if not exists stock_movements_article_idx
  on public.stock_movements (shop_id, article_id, cree_le desc);

alter table public.stock_movements enable row level security;

revoke all on public.stock_movements from anon;
-- Lecture seule pour un membre : un mouvement ne se corrige pas, il s'annule
-- par un mouvement inverse. L'écriture passe par les fonctions plus bas.
revoke insert, update, delete on public.stock_movements from authenticated;
grant select on public.stock_movements to authenticated;

drop policy if exists stock_movements_lecture on public.stock_movements;
create policy stock_movements_lecture on public.stock_movements
  for select to authenticated
  using (public.is_shop_member(shop_id));

comment on table public.stock_movements is
  'Journal du stock, en ajout seul : chaque ligne est un changement signé, jamais une quantité. `articles.stock` n''en est que le cache.';

-- --------------------------------------------------------------------------
-- Quelle émission bouge le stock
-- --------------------------------------------------------------------------

-- La règle d'Android, reprise telle quelle : un document qui vend de la
-- marchandise la sort ; un avoir la remet ; tout le reste ne touche pas au
-- rayon. Un devis est une question, un proforma une estimation, un acompte de
-- l'argent — un stock qui baisse quand on écrit un devis est un stock auquel la
-- boutique cesse de se fier. `regul` corrige des chiffres, pas des quantités.
create or replace function public.stock_direction(p_type text)
returns integer
language sql
immutable
as $$
  select case
    when p_type in ('facture', 'ticket', 'simplifiee', 'export') then -1
    when p_type = 'avoir' then 1
    else 0
  end
$$;

revoke all on function public.stock_direction(text) from public, anon;
grant execute on function public.stock_direction(text) to authenticated;

-- --------------------------------------------------------------------------
-- Émission : déduire, ou remettre
-- --------------------------------------------------------------------------

-- Les lignes sont additionnées par article avant d'être appliquées : le même
-- produit saisi sur deux lignes sort deux unités, pas une. Une quantité
-- décimale (1,5 m de câble) est arrondie à l'unité, comme sur Android : un
-- article se compte, et le journal ne connaît que des entiers.
create or replace function public.stock_apres_document()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_direction integer := public.stock_direction(new.type);
begin
  if v_direction = 0 then
    return new;
  end if;

  with unites as (
    select l.article_id, sum(round(l.quantite))::integer as quantite
    from public.document_lines l
    join public.articles a on a.id = l.article_id
    where l.document_id = new.id
      and a.stock is not null   -- une prestation ne se compte pas
    group by l.article_id
  ), ecrits as (
    insert into public.stock_movements (
      shop_id, article_id, document_id, delta, motif, reference, cree_par
    )
    select
      new.shop_id,
      u.article_id,
      new.id,
      v_direction * u.quantite,
      case when v_direction > 0 then 'credit' else 'invoice' end,
      new.numero,
      auth.uid()
    from unites u
    where u.quantite <> 0
    returning article_id, delta
  )
  update public.articles a
  set stock = a.stock + e.delta
  from ecrits e
  where a.id = e.article_id;

  return new;
end;
$$;

revoke all on function public.stock_apres_document() from public, anon, authenticated;

drop trigger if exists documents_stock on public.documents;
create trigger documents_stock
  after update of statut on public.documents
  for each row
  when (old.statut = 'brouillon' and new.statut = 'emis')
  execute function public.stock_apres_document();

-- --------------------------------------------------------------------------
-- Stock d'ouverture : un article naît avec sa quantité dans le journal
-- --------------------------------------------------------------------------

-- Sans cette ligne, un article créé avec 12 en stock afficherait 12 sans
-- qu'aucun mouvement ne l'explique — et le premier écart serait
-- incompréhensible.
create or replace function public.stock_ouverture_article()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.stock is not null and new.stock <> 0 then
    insert into public.stock_movements (
      shop_id, article_id, delta, motif, note, cout_unitaire, cree_par
    ) values (
      new.shop_id, new.id, new.stock, 'adjustment', 'Stock d''ouverture',
      coalesce(new.prix_achat, 0), auth.uid()
    );
  end if;
  return new;
end;
$$;

revoke all on function public.stock_ouverture_article() from public, anon, authenticated;

drop trigger if exists articles_stock_ouverture on public.articles;
create trigger articles_stock_ouverture
  after insert on public.articles
  for each row
  execute function public.stock_ouverture_article();

-- Les articles déjà en base reçoivent leur stock d'ouverture, sinon leur cache
-- dirait 12 et le journal 0.
insert into public.stock_movements (shop_id, article_id, delta, motif, note, cout_unitaire)
select a.shop_id, a.id, a.stock, 'adjustment', 'Stock d''ouverture', coalesce(a.prix_achat, 0)
from public.articles a
where a.stock is not null
  and a.stock <> 0
  and not exists (select 1 from public.stock_movements m where m.article_id = a.id);

-- --------------------------------------------------------------------------
-- Les gestes humains
-- --------------------------------------------------------------------------

-- Réception, inventaire, correction. Le motif `invoice`/`credit` est refusé
-- ici : ces mouvements-là portent un numéro de document, et un geste manuel qui
-- se ferait passer pour une facture rendrait le journal inutilisable comme
-- preuve.
create or replace function public.enregistrer_mouvement_stock(
  p_article uuid,
  p_delta integer,
  p_motif text default 'adjustment',
  p_note text default '',
  p_cout numeric default 0
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_article public.articles;
  v_motif   text := coalesce(nullif(btrim(p_motif), ''), 'adjustment');
  v_note    text := btrim(coalesce(p_note, ''));
  v_cout    numeric := coalesce(p_cout, 0);
  v_apres   integer;
begin
  if auth.uid() is null then
    raise exception 'authentification requise' using errcode = '28000';
  end if;

  select * into v_article from public.articles where id = p_article for update;
  if not found or not public.is_shop_member(v_article.shop_id) then
    raise exception 'article inconnu' using errcode = '42501';
  end if;
  if v_article.stock is null then
    raise exception 'Une prestation ne se compte pas : cet article n''a pas de stock.'
      using errcode = '22023';
  end if;

  if v_motif not in ('purchase', 'stocktake', 'adjustment') then
    raise exception 'Un mouvement de facture ou d''avoir vient du document, pas d''ici.'
      using errcode = '22023',
            hint = 'Émettez la facture ou l''avoir : le stock suit tout seul.';
  end if;

  if p_delta is null or p_delta = 0 then
    raise exception 'Un mouvement de zéro ne change rien.' using errcode = '22023';
  end if;
  if abs(p_delta) > 1000000 then
    raise exception 'Mouvement trop grand : 1 000 000 au maximum.' using errcode = '22023';
  end if;

  if v_cout < 0 then
    raise exception 'Le coût unitaire ne peut pas être négatif.' using errcode = '22023';
  end if;
  -- Une sortie n'a pas de coût : c'est ce qui est entré qui vaut. On l'ignore
  -- plutôt que d'écrire un prix qui n'a pas de sens.
  if p_delta < 0 then
    v_cout := 0;
  end if;

  insert into public.stock_movements (
    shop_id, article_id, delta, motif, note, cout_unitaire, cree_par
  ) values (
    v_article.shop_id, v_article.id, p_delta, v_motif, v_note, v_cout, auth.uid()
  );

  update public.articles
  set stock = stock + p_delta
  where id = v_article.id
  returning stock into v_apres;

  return jsonb_build_object(
    'article_id', v_article.id,
    'delta', p_delta,
    'quantite', v_apres,
    'motif', v_motif
  );
end;
$$;

revoke all on function public.enregistrer_mouvement_stock(uuid, integer, text, text, numeric)
  from public, anon;
grant execute on function public.enregistrer_mouvement_stock(uuid, integer, text, text, numeric)
  to authenticated;

comment on function public.enregistrer_mouvement_stock(uuid, integer, text, text, numeric) is
  'Réception, inventaire ou correction : écrit un mouvement signé et met le cache à jour. Refuse les articles sans stock (prestations) et les motifs facture/avoir, qui viennent des documents.';

-- --------------------------------------------------------------------------
-- Lecture
-- --------------------------------------------------------------------------

-- La quantité réelle d'une boutique, telle que le journal la calcule. Un article
-- sans mouvement sort à 0, ce qui est la vérité : il n'a jamais été compté.
create or replace function public.stock_soldes(p_shop uuid)
returns table (article_id uuid, quantite integer, dernier_mouvement timestamptz)
language sql
stable
security definer
set search_path = public
as $$
  select a.id, coalesce(sum(m.delta), 0)::integer, max(m.cree_le)
  from public.articles a
  left join public.stock_movements m on m.article_id = a.id
  where a.shop_id = p_shop
    and public.is_shop_member(p_shop)
  group by a.id
$$;

revoke all on function public.stock_soldes(uuid) from public, anon;
grant execute on function public.stock_soldes(uuid) to authenticated;

-- Là où le cache et le journal se contredisent. Le cache reste ce que les
-- écrans affichent tant que l'étape suivante n'est pas faite ; cet écart est le
-- seul moyen de le savoir sans relire l'historique à la main.
create or replace function public.stock_ecarts(p_shop uuid)
returns table (article_id uuid, libelle text, cache integer, journal integer)
language sql
stable
security definer
set search_path = public
as $$
  select a.id, a.libelle, a.stock, coalesce(sum(m.delta), 0)::integer
  from public.articles a
  left join public.stock_movements m on m.article_id = a.id
  where a.shop_id = p_shop
    and a.stock is not null
    and public.is_shop_member(p_shop)
  group by a.id, a.libelle, a.stock
  having a.stock <> coalesce(sum(m.delta), 0)
$$;

revoke all on function public.stock_ecarts(uuid) from public, anon;
grant execute on function public.stock_ecarts(uuid) to authenticated;

comment on function public.stock_ecarts(uuid) is
  'Les articles dont la quantité en cache ne correspond plus au journal. Vide sur une boutique saine.';
