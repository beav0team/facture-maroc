# Facture Maroc — current status

**Read this before planning any work on this app.**
If the version below is older than `app/build.gradle.kts`, audit the code before
answering from memory.

| | |
|---|---|
| Version Play | **0.11.1** (versionCode 18) en ligne ; **0.12.0** (23) prête à téléverser |
| Aperçu | **0.12.0-beta.4** (versionCode 22) — GitHub ; la 0.12.0 (23) est la même base pour le Play |
| Last commit | Audit du 2026-11-10 : paiements, stockage, catalogue, rapports, avoirs, sauvegarde Android |
| Branch | `master` (tag `v0.11.1` à poser après le build R8) |
| Tests | **629 JVM**, **20 instrumented** (les deux suites vertes) |
| Last audit | 2026-11-10 |
| Target | Morocco only, MAD only, French UI |

---

## FactureApp web — en ligne (2026-11-11)

La webapp vendable vit dans `webapp/`, se construit dans `docs/app/` et est
servie sous `/app/`. L'app Android reste hors ligne et inchangée.

**Site marketing — refonte du 2026-11-12**

- La vitrine vend la webapp, en page produit : héros (facture + quatre
  preuves), aiguillage compte / générateur, quatre atouts, trois étapes avec une
  capture réelle de l’éditeur, états du document, dix documents, formules avec
  prix, note du fondateur, FAQ, appel final. Plus aucune page ne cite ni ne lie
  l’application Android
  (section retirée d’`a-propos` et du modèle, lien de pied de page supprimé
  partout, plus de test fermé dans `contact`). `test.html` reste en ligne, en
  `noindex` et sans lien entrant, pour les testeurs qui ont l’adresse.
- `docs/DESIGN.md` fixe le langage : carnet à souches, perforation, tampon,
  numéros de série, et la liste de ce qui est interdit.
- `/tarifs` → `/app/tarifs` en 301 ; le prix du Pro ne vit plus que dans
  `webapp/src/lib/offres.ts`, aucun montant n'est écrit dans le HTML.
- `docs/avis.html` : ce que la webapp fait et ne fait pas (stock, DGI,
  paiements, relances, hors ligne).
- `webapp/scripts/check-site.mjs`, appelé par `build.sh` : chaque page doit
  avoir l’en-tête, un lien `/app/`, aucune ancre morte et aucun prix en dur.
- **CGV** (2026-11-12) : `docs/cgv.html` — plans, activation, prix, durée,
  rétractation, disponibilité, responsabilité, droit marocain. Le prix du Pro y
  est imprimé et vérifié contre `offres.ts`, comme sur l’accueil ; aucune autre
  page ne le recopie.
- **Entrée par l’inscription** : les boutons « Créer ma facture » et « Commencer »
  pointent sur `/app/?mode=signup`. « Connexion » et « Se connecter » restent sur
  `/app/`. Le générateur fait de même après un brouillon.
- **Confidentialité séparée** (2026-11-12) : `confidentialite.html` décrit la
  webapp (compte, boutique, documents, PDF archivés, sous-traitants, droits) et
  remplace `privacy.html` dans tous les pieds de page. `privacy.html` reste
  publiée pour la fiche Play — l’application Android a sa propre politique — et
  n’est plus liée ni listée dans le sitemap. `check-site.mjs` refuse un lien
  vers `privacy.html` depuis une page de la vitrine.
- **Captures du compte** (2026-11-12) : section `#compte` de l’accueil avec
  deux captures réelles (liste des documents, éditeur rempli) prises sur la
  boutique de test de l’agent, après un jeu de démonstration créé par l’API.
  L’éditeur a gagné deux corrections au passage : le sélecteur d’article
  écrasait la désignation (`width: 100%` dans une rangée flex), et le tableau
  respire sur 1 200 px au lieu de 1 100.
- **Plus de promesse de non-pistage** : les mentions « aucun script de mesure
  d’audience » ont été retirées d’`a-propos`, `avis` et `confidentialite`.
  Cloudflare Web Analytics s’active depuis le tableau de bord, sans code ; si
  l’audience est mesurée un jour, `confidentialite.html` doit le dire.
- **Galerie des six modèles** sur l’accueil (`#modeles`, `assets/img/modele-*`) :
  vignettes rendues par le vrai moteur PDF, rognées au haut de page, le rouleau
  centré sur son cadre.
- **Six modèles de document** (2026-11-12) : Classique, Moderne, Facture Maroc,
  Épuré, Bandeau (A4) et Ticket (rouleau 80 mm). Choisis par document dans
  l’éditeur, réglés par défaut dans les paramètres, et repris par le générateur
  public pour les essayer sans compte. Le modèle est stocké sur le document ;
  une réimpression sort l’archive, donc le modèle d’origine. Migration
  `webapp/supabase/migrations/0006_modeles.sql` à appliquer **avant** le
  déploiement : sans elle, la base refuse les quatre nouveaux identifiants.
- `docs/build.py` ne s’occupe plus que du `sitemap.xml` : le site n’imprime
  aucun numéro de version, donc plus de dépendance à `app/build.gradle.kts`.
  `docs/scripts/site_check.py` accepte les routes de la SPA en lisant
  `_redirects`.

**En production**

- **https://factureapp.ma** (Cloudflare Pages, projet `factureapp`, branche
  `master`, build `bash webapp/scripts/build.sh`, sortie `docs`). Liens
  profonds de l'app à 200 ; GitHub Pages désactivé.
- Supabase `njbfvskjexzlnvenmxml` (eu-north-1), schéma 0001 → 0004, RLS.
- **Google OAuth publié en production** ; connexion Google et e-mail/mot de
  passe vérifiées sur le site.
- Données réelles : 3 comptes/boutiques (Atlas, Benzen, PNEUMATIQUE AGHARAS),
  `documents = 0`, `archives = 0`, `platform_admins = 1`
  (`agnaou1996@gmail.com`).
- Les données de test (Atelier Essai, `essai@factureapp.ma`, 4 factures,
  2 PDF) ont été supprimées et vérifiées.

**Fait**

- Éditeur : facture, devis, avoir, TVA par ligne, remises, frais, acompte,
  arrondi au dirham, HT/TTC, dates, modèle.
- Numérotation et quota atomiques en SQL ; numéro pris à l'émission, snapshot
  boutique figé, documents émis immuables, avoir depuis une facture, 5
  factures gratuites par mois.
- PDF `pdf-lib` déterministe, deux modèles, filigrane gratuit, archivé avec
  SHA-256 ; réimpression depuis l'archive. Partage WhatsApp/natif.
- Paiements multiples, reçu PDF, reste dû, retard, relevé client.
- Générateur public, tarifs, admin, import de sauvegarde Android.
- **Réinitialisation du mot de passe** : le lien envoyé par « Mot de passe
  oublié » ouvre le tableau de bord avec une session de récupération ;
  `typeRecuperation()` lit le fragment avant `getSession()` et l'application
  affiche « Nouveau mot de passe » (deux saisies, refus si différentes), puis
  `updateUser` remplace l'ancien. Un lien expiré ou déjà servi le dit, il ne
  laisse pas l'écran vide.
- **Le plan affiché est le vrai** : l'étiquette de l'accueil était la chaîne
  figée « Plan gratuit · 5 factures / mois » ; elle vient maintenant de
  `quota_boutique` (« Plan Pro », sinon le quota réel) et se relit au retour
  sur l'onglet.
- **Boutique de l'administrateur** : `chargerBoutique()` lit `memberships`
  (scopé à `auth.uid()`), plus `shops` — un administrateur plateforme voyait la
  plus ancienne boutique du site (« Atlas ») au lieu de la sienne, puisque
  « admin lit boutiques » lui ouvre toutes les boutiques.
- **Console d'administration** (`0005_admin.sql`) : boutiques avec leur
  propriétaire et leurs compteurs, comptes avec e-mail et dernière connexion,
  chiffres de la plateforme, abonnement Pro avec date libre et note,
  nomination d'un administrateur par e-mail, retrait (le dernier ne peut pas
  partir). Tout passe par des fonctions `security definer` réservées à
  `est_admin_plateforme()` : le navigateur ne voit jamais `auth.users` ni le
  service_role.
- **Aperçu PDF** : le bouton ouvre une fenêtre où le navigateur dessine le PDF,
  avec « Télécharger » (`<a download>`) et « Ouvrir dans un onglet ». Le
  document émis ouvre l'archive ; sans archive, la fenêtre le dit et propose le
  rendu reconstruit. Une erreur de rendu s'affiche au lieu de laisser le bouton
  muet.
- 168 tests Vitest, 0 erreur `svelte-check` ; preuves RLS locales (quatre
  suites, dont `rls_admin.sql`).

**Reste à faire pour vendre**

- ~~Nommer un administrateur plateforme~~ — fait : `agnaou1996@gmail.com`
  (uid `7fc28c1c…`) est dans `platform_admins` depuis le 2026-11-11.
- **Sauvegardes** : non installées. Le rôle `factureapp_backup` existe mais ne
  se connecte pas (IPv6 absent, pooler sans rôles custom). Choisir entre export
  JSON+PDF via `service_role` ou reset du mot de passe Postgres + `pg_dump`.
- **SMTP** : celui de Supabase est limité ; configurer un fournisseur pour les
  confirmations et réinitialisations.
- **CNDP** et relecture du texte de `confidentialite.html` (version web).
- Monitoring, disponibilité et test de restauration réels.

---

## In progress — refonte de l'interface (2026-11-10)

Publié sur **GitHub** `v0.12.0-beta.4` (APK R8 + AAB + mapping) ;
la 0.11.1 reste la version Play et le téléchargement « Latest ». L'aperçu n'a
pas été parcouru sur un appareil : l'émulateur de la machine de développement
gèle, les 577 tests JVM passent et le rendu PDF est inchangé.

**Fait**

- `ui/Theme.kt` : échelle de surfaces M3 complète (jusqu'à
  `surfaceContainerHighest`, `inverse*`, `scrim`), rôles typographiques
  manquants, et un rôle `Typography.numerals` en chiffres tabulaires.
- `data/Money.kt` : un seul formateur d'argent (virgule, deux décimales,
  espace fine insécable). `asDirhams`, `moneyText`, `PdfText.money`,
  `PaymentReminder` et `PaymentQrText` y délèguent ; l'écran et le PDF
  impriment enfin le même nombre.
- Liste des documents : une seule ligne de statut (`data/DocumentStatus.kt`)
  au lieu de trois pastilles, montant plafonné à 132 dp pour que le nom du
  client garde sa place, couleur = état de paiement uniquement.
- Accueil : la carte d'en-tête dit « Reste à encaisser » (reste dû + retard),
  le facturé du mois passe en ligne secondaire (`List.outstanding()`).
- Articles : tri nom / prix / stock / marge, icône qui distingue prestation,
  produit et article épuisé, balayage sans recomposition par image
  (`graphicsLayer` + `mutableFloatStateOf`, une animation par geste, retour
  haptique).
- **Onglet Stock** : la liste des rayons. Chaque article compté avec sa
  quantité réelle (lue dans le journal, jamais dans le cache), la valeur du
  stock au coût unitaire, et « À commander » en tête. Une ligne s'ouvre sur la
  fiche ; son menu écrit un mouvement — Reçu, Sorti, Corriger — chacun
  enregistré comme une entrée du journal (`SavedItemStore.moveStock`). Les
  réceptions de plusieurs articles et le journal complet sont dans la barre de
  l'onglet. Un écart entre le cache et le journal s'affiche en rouge avec le
  chemin de réparation (Paramètres → Diagnostic).
- L'inventaire (compter les rayons) est dans Articles, près de la liste qu'il
  compte.
- Fiche client **et fournisseur** (`PartyDetailScreen`) : identité, chiffres
  (solde dû et relevé pour un client, achats HT et nombre de réceptions pour un
  fournisseur), documents, et deux actions — Modifier, Nouveau document.
- Fiche article (`ArticleDetailScreen`) : prix, marge HT, coût d'achat, stock,
  paliers, caractéristiques et six derniers mouvements ; la ligne ouvre la
  fiche, « Modifier » ouvre le formulaire. Suppression depuis la fiche, avec
  « Annuler ».
- Formulaire d'article en pleine page (`ItemEditorScreen`) : en-tête, contenu
  défilant, et « Annuler / Enregistrer » dans une barre fixe — une boîte de
  dialogue coupait le formulaire au milieu à 360 dp.
- **Clients** : deux icônes d'appel et de message WhatsApp (ACTION_DIAL et
  `wa.me`, aucune permission), la carte dit le reste dû ou le retard au lieu
  d'un nombre de documents, filtres « Doit de l'argent / En retard » avec
  compte, tri par nom, solde ou date.
- **Articles** : un seul modèle de navigation — la carte des dossiers avec
  « Tous » et « Sans dossier », puis une liste de lignes avec fil d'Ariane ;
  la recherche couvre toujours tout le catalogue et le dit ; scan d'un
  code-barres pour remplir la recherche ; le tri n'offre que les ordres dont
  le champ est visible ; la quantité vient du journal de stock
  (`StockLedger.balances()`), plus du cache ; « marge inconnue » au lieu d'une
  ligne absente.
- **Identité des tiers** : les clients et fournisseurs ont un `id` stable
  (`PartyIdentity`), les documents émis avant sont estampillés une fois par
  leur nom, et un nom sans id reste la clé de secours. Renommer un client ne
  détache plus son historique.
- **Dossiers** : déplacer un dossier avec tout son contenu sous un autre, et le
  copier (sous-dossiers + articles, chaque copie recevant un id, un nom unique
  et son stock d'ouverture dans le journal).
- **Factures** : sélecteur de type (facture, devis, bon de commande, avoir…),
  appliqué aux brouillons comme aux documents émis.
- **Stock** : liste des rayons (quantité lue dans le journal, valeur au coût
  unitaire), Reçu / Sorti / Corriger par ligne, écart cache/journal signalé.
- **Réglage** « Déduire le stock à l'émission », actif par défaut, transporté
  par la sauvegarde.
- Paramètres : index de cinq groupes avec recherche insensible aux accents
  (Boutique, Facturation, Catalogue et stock, Données, Diagnostic), valeur
  courante affichée sous chaque groupe (« TVA 20 % », « Version … »), retour =
  un cran en arrière. « Diagnostic » est séparé parce que recalculer le stock
  est une réparation, pas une préférence.
- Paramètres, contenu réorganisé : « Paiement et banque » éclaté en
  « Coordonnées bancaires » (Boutique) et « Retard de paiement » +
  « Nom des fichiers partagés » (Facturation) ; « Export comptable » séparé de
  la sauvegarde complète ; logo ramené à une section comme les autres ;
  « À propos » ajouté (version installée, aucune donnée ne sort) ; une seule
  action « Enregistrer », et seulement dans un groupe.

**Reste**

- Suppression d'article annulable (snackbar) au lieu d'un effacement immédiat.
- La carte d'index des Paramètres ne montre pas encore la valeur courante de
  chaque groupe (« TVA 20 % », « Prochaine facture F-… »).
- Bump du Compose BOM (2024.12.01 → version Expressive), isolé dans son propre
  commit après la revue de la 0.11.1.

---

## In progress — 0.11.0 / Phase A

A1 (VAT and compliance engine) has landed on top of 0.10.0 and is **not
released**: per-line VAT, recap by rate on every template, UBL per rate, ICE
validation, detail sheet rows, fixed-amount discounts (global and per line) and
a percentage surcharge. A1 is complete. A2 (payments lifecycle) is complete:
the payment deadline, the method per payment, the A5 receipt, the client
statement and the shareable payment reminder have landed; RAS TVA is
deliberately out. A3 has landed:
issued documents are no longer deletable, credit notes and quote conversion are
one tap, deposit deduction, configurable numbering (prefix per type, annual
reset) and decimal quantities. A4 (legal and print finish) has landed:
structured payment terms with presets, the model late-payment clause, bank
details and QR on the six templates, a configurable PDF file name and the
archive state on each record's detail sheet. Listing and README are refreshed.

**Phase B1 has landed** (inventory foundation): stable item ids with a one-shot
migration, an append-only stock ledger with a per-item history screen, the
stocktake (physical count, motivated difference, summary), a reorder threshold
with a "à commander" filter, a Settings drift check that shows but never
silently repairs, and the ledger inside the JSON backup.

**Phase B2 has landed** (purchases and costing): numbered goods receipts
(`BR-yyyyMMdd-0001`) that write PURCHASE movements and update the cached
quantity and the article's purchase price; a receipt is deleted by reversing its
ledger entries, never by leaving phantom stock. Ledger entries carry a unit cost
(HT), the opening line takes the article's purchase price, and a manual
correction keeps the cost already known. The valuation method is a setting
(weighted average by default, FIFO, or none) and only affects future movements.
A line freezes the article's cost when the document is issued, the margin is
reported per document, per article and per month, and a below-cost sale raises a
non-blocking warning. B3 has landed: quantity price tiers on each article
(applied when the line is picked, frozen on the line), free key/value attributes
printed beside the line, and both carried through JSON and the line editor.
B4 has landed: the month card shows VAT collected (net of credit notes) with a
per-rate line, the article summary keeps the stock value and the reorder count,
and Settings exports the issued documents as a CSV the accountant can open
(semicolons, French decimals, one row per document, drafts excluded).
**A5 is in progress** — a rich-data render test across all six templates plus
the receipt and the statement, the total invariants (HT + TVA = TTC, recap =
tax, lines = subtotal), version 0.11.0 and the R8 release. C1 (hardening) and
C2 (Play) follow.

## Audit corrections — 2026-11-10

Nineteen defects found by reading the code are fixed on `master`, each with the
test that would have caught it. Grouped by what they broke:

- **Paiements** — un document garde l'historique de ses encaissements au lieu
  d'un montant unique ; le reçu peut être établi par paiement ; le dialogue
  propose le reste à payer ; seuls facture, ticket, simplifiée et export
  peuvent être encaissés (plus de paiement sur devis, proforma, bon de commande
  ni avoir).
- **Stockage** — un catalogue illisible est copié en `-corrompu.json` avant
  tout enregistrement ; une ligne de registre illisible au milieu est signalée
  au lieu d'être ignorée (seule la dernière ligne peut être déchirée) ; un hash
  de logo doit faire 64 caractères hexadécimaux ; une écriture qui échoue n'est
  plus annoncée comme réussie (snackbar, l'écran ne bouge pas).
- **Catalogue** — les paliers de prix suivent la base de la boutique (TTC ou
  HT) et ne sont plus divisés deux fois ; `addLine` ne fusionne plus deux
  lignes qui diffèrent par la remise fixe ou l'article ; « dès N » disparaît
  quand la quantité repasse sous le seuil.
- **Rapports** — le solde du relevé additionne les documents (un acompte n'est
  plus réclamé deux fois) ; un avoir ne recopie plus l'acompte de la facture ;
  le CSV comptable écrit les avoirs en négatif avec le statut « Avoir » ; la
  remise au niveau du document entre dans la marge, répartie sur les lignes.
- **Avoirs** — un avoir s'affiche en négatif avec une pastille « Avoir », n'a
  pas d'échéance et n'est jamais « en retard ».
- **Échelle** — les coûts du catalogue sont lus en une passe sur le registre au
  lieu d'une par article ; le plafond de sauvegarde passe de 8 à 16 Mo
  (5 000 documents mesurés à 7,8 Mo).
- **Confidentialité** — la sauvegarde Android est désactivée et
  `data_extraction_rules.xml` exclut toutes les données du cloud et du
  transfert d'appareil à appareil.

**RAS TVA stays out** — settled in `docs/decisions.md`: the user asked to
continue without it, so no field is invented. Revisit only on request.

## Feature status

### Invoicing

| Area | Status | Notes |
|---|---|---|
| Document types | **Exists** | facture, devis, bon de commande, ticket, avoir, proforma, acompte, régularisation, simplifiée, export |
| Templates | **Exists** | Classique, Moderne, Facture Maroc, Épuré, Bandeau, Ticket 80 mm |
| Lines | **Exists** | description, **quantity (decimal: m, kg, heure)**, unit price, unit, line discount % or amount, **VAT rate per line (20/10/0/exempt)** |
| Global discount | **Exists** | percentage or fixed amount, one per document; amount drops the percentage |
| Surcharge | **Exists** | fixed amount or percentage of the discounted total, taxed at the document rate |
| VAT | **Exists** | per line since A1: 20/10/0/exempt, legacy 7/14 readable; recap by rate on the 6 templates; one UBL `TaxSubtotal` per rate |
| Payment tracking | **Exists** | amount, date, reference, **method per payment**, partial/paid, badge, PDF block, UBL prepaid |
| Payment reminder | **Exists** | shareable French text (SMS/WhatsApp) for an unpaid or partial document: amount still owed, deadline or "dépassée", shop name and phone; nothing for a paid document, draft, quote or avoir |
| Receipt PDF | **Exists** | A5 receipt for any recorded payment: sum in words, method, reference, invoice/settled/balance |
| Due date / overdue | **Exists** | deadline (comptant/30/60 or a typed date), printed on the 6 templates, overdue pill and detail row; validity date stays for quotes |
| Payment terms | **Exists** | presets (comptant, 30, 60 jours, fin de mois) filling an editable note; a preset warns before replacing hand-typed text |
| Late-payment clause | **Exists** | model clause in Settings, printed under the totals only when the document is not cash terms and the shop filled it |
| Bank details / QR | **Exists** | banque, RIB (24 digits, warning only), titulaire in the payment block; optional plain-text payment QR (bank, RIB, amount, number), off by default, only with a RIB |
| PDF file name | **Exists** | pattern in Settings (`facture_{number}` by default) with `{client}`, `{date}`, `{type}`; sanitised, `.pdf` forced; receipt and statement use the same rules |
| Client statement | **Exists** | per client: billed / credited / paid / balance, credit notes subtracted, drafts, quotes and orders excluded; A4 PDF from the client card |
| Quote → invoice | **Exists** | one tap from a devis; lines, prices, rates and discount copied; quote number kept as reference |
| Invoice → credit note | **Exists** | one tap; the avoir repeats the invoice exactly and drops payment/deadline |
| Deposit deduction | **Exists** | `depositAmount`/`depositReference`, deducted from the balance only (never from the VAT base), printed with the payment block, carried by the receipt and the statement |
| Numbering | **Exists** | per-type sequences; **per-type prefix override** (letters/digits/-/_, empty = default); **optional annual reset** (counter year stamped on every take/rewind); invoice counter editable |
| ICE validation | **Exists** | 15-digit check + missing-ICE warning on a business invoice (non-blocking) |
| Notes / signature | **Exists** | client note, terms (preset or free text), late clause, thank-you, drawn/photo signature |
| Shop snapshot | **Exists** | issued documents keep issue-time details + logo hash |
| UBL 2.1 export | **Exists** | invoice + credit note, discount, prepaid; no DGI profile |
| Archive | **Exists** | hash chain for XML **and** PDF; verification screen in Settings; each record's detail sheet says whether its PDF is really archived |
| Backups | **Exists** | JSON export, automatic daily copies, restore |
| Reports | **Exists** | month: billed net of credit notes, **VAT collected with a per-rate line**, margin (revenue/cost/lines without cost); stock value and "à commander" in the article summary |
| Accountant export | **Exists** | CSV from Settings: one row per issued document (date, number, type, client, ICE, HT, VAT, TTC, paid, due, status), semicolons and French decimals, drafts excluded |

### Inventory

| Area | Status | Notes |
|---|---|---|
| Item fields | **Exists** | label, price, unit, folder, purchase price, barcode, stock |
| Stock deduction | **Exists** | on issue, restore on credit note/deletion |
| Stock ledger / history | **Exists** | append-only JSONL ledger (`StockEntry`: invoice, avoir, achat, inventaire, ajustement + document reference), per-item history screen, cache recomputable and a Settings drift check that reports before it repairs |
| Stable item ID | **Exists** | UUID per article, `InvoiceLine.itemId`; one-shot migration binds old lines by label, renames keep the history |
| Reorder threshold | **Exists** | per item, "à commander" filter and count in the stock view |
| Stocktake | **Exists** | counted vs expected per item, motivated difference written as a STOCKTAKE entry, summary with the difference value |
| Purchases / stock-in | **Exists** | numbered goods receipts (`BR-yyyyMMdd-0001`), supplier picker or free text, article lines with quantity and unit cost; a receipt writes PURCHASE movements and updates the cached quantity and purchase price; deleting reverses the movements |
| Cost layers / average / FIFO | **Exists** | `StockEntry.unitCost`; `StockCost` replays the ledger in order: weighted average (CMP, default), FIFO layers, or none; Settings "Valorisation du stock"; switching affects future movements only |
| Margin | **Exists** | cost frozen on each issued line (`InvoiceLine.unitCostHt`); margin per document, per article and per month (credit notes subtracted), lines without cost excluded and counted; below-cost warning |
| Price tiers | **Exists** | per article: "from this quantity, at this HT price"; the picker freezes the tier price and threshold on the line (`appliedTierPrice`, `tierFromQuantity`); whole quantities only, decimals use the base price; edited in the article form |
| Attributes | **Exists** | free key/value (Référence, Marque, Modèle, Dimensions, Lot / série…), edited in the article form, copied onto every line and printed after the unit (`Réf: E-100`) |
| Photos | **Out** | deferred by decision |

---

## Moroccan compliance gaps

1. **VAT per line** — art. 145 CGI requires the rate per line; current model is
   one rate per invoice.
2. **ICE** — should be 15 digits; client ICE needed for business clients.
3. ~~Issued invoices are deletable~~ — fixed in A3: only drafts can be deleted,
   and the menu/sheet offer an avoir instead.
4. **Retention** — 10 years (CGI art. 211 / law 9-88); no accountant export or
   age indicator.
5. **VAT withholding (RAS TVA)** — 75 %/100 % withheld by some clients; no field.
6. **E-invoicing** — phased from 2026; no published technical spec. Do not claim
   DGI compliance.
7. **Language** — no Arabic requirement found; French accepted in practice.
   Arabic is a commercial choice pending accountant confirmation.

---

## Play Store

| | |
|---|---|
| Account | Personal, verified 2026-10-30 |
| Tracks | Closed test open |
| Production gate | **12 opted-in testers for 14 continuous days** — not reached yet |
| Listing assets | `play_store/` (icon, feature graphic, 4 screenshots, `listing-fr.md`, `UPLOAD.md`) |
| Known stale items | screenshots predate 0.11.0 (refresh from Waydroid); Play contact e-mail still to enter in the console |
| Open checks | **16 KB verified** on the 0.11.1 release APK (`zipalign -P 16` passes, ELF `LOAD` align `0x4000`); AAB 15 734 173 octets, SHA-256 dans `UPLOAD.md` |
| For 0.12 | Play signale « native code, no debug symbols » sur la 0.11.1 (avertissement non bloquant). Ajouter `ndk { debugSymbolLevel = "SYMBOL_TABLE" }` au buildType `release`, puis joindre `app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip` à la release |

---

## Open decisions

See `docs/decisions.md`. The unresolved ones:

1. Arabic / bilingual (pending accountant).
2. RAS TVA: note only or withheld amount with recalculated balance.
3. Deposit: automatic deduction.
4. Auto-entrepreneur number printed.
5. 1.0 release: Play only or Play + GitHub.

---

## Where the plan lives

- Roadmap: `~/.opencode/plan/facture-maroc-plan-roadmap.md`
- Decisions: `docs/decisions.md`
- Release checklist: `docs/release-checklist.md`
