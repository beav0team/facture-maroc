-- --------------------------------------------------------------------------
-- Six modèles de document
-- --------------------------------------------------------------------------
--
-- 0003 n'en connaissait que deux. Les identifiants existants ne bougent pas :
-- `facture_maroc` reste `facture_maroc`, sinon les documents déjà émis et les
-- boutiques déjà réglées perdraient leur modèle. On ne fait qu'élargir la
-- contrainte, jamais renommer une valeur.
--
-- `ticket` sort sur un rouleau de 80 mm ; les cinq autres sur A4.

alter table public.shops
  drop constraint if exists shops_design_defaut_check;

alter table public.shops
  add constraint shops_design_defaut_check
  check (design_defaut in ('classique', 'moderne', 'facture_maroc', 'epure', 'bandeau', 'ticket'));

alter table public.documents
  drop constraint if exists documents_design_check;

alter table public.documents
  add constraint documents_design_check
  check (design in ('classique', 'moderne', 'facture_maroc', 'epure', 'bandeau', 'ticket'));
