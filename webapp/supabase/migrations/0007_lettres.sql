-- Montant en toutes lettres sous les totaux.
--
-- Réglage par boutique, actif par défaut : c'est un usage constant sur une
-- facture marocaine (les administrations le demandent), donc une boutique qui
-- ne l'a jamais réglé doit l'avoir. Il ne change pas la légalité du document,
-- seulement la ligne « ARRÊTÉE LA PRÉSENTE FACTURE À LA SOMME DE : … ».
--
-- Les documents déjà émis ne bougent pas : leur PDF est archivé, et une
-- réimpression sort l'archive. Ce réglage ne s'applique qu'aux documents
-- émis après son changement.

alter table public.shops
  add column if not exists montant_lettres boolean not null default true;

comment on column public.shops.montant_lettres is
  'Imprime le montant arrêté en toutes lettres sous les totaux (documents et reçus).';
