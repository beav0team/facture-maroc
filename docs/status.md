# Facture Maroc — current status

**Read this before planning any work on this app.**
If the version below is older than `app/build.gradle.kts`, audit the code before
answering from memory.

| | |
|---|---|
| Version Play | **0.11.1** (versionCode 18) — 19 corrections d'audit |
| Aperçu | **0.12.0-beta.3** (versionCode 21) — GitHub, non parcourue sur appareil |
| Last commit | Audit du 2026-11-10 : paiements, stockage, catalogue, rapports, avoirs, sauvegarde Android |
| Branch | `master` (tag `v0.11.1` à poser après le build R8) |
| Tests | **597 JVM**, **20 instrumented** |
| Last audit | 2026-11-10 |
| Target | Morocco only, MAD only, French UI |

---

## In progress — refonte de l'interface (2026-11-10)

Publié sur **GitHub** `v0.12.0-beta.3` (APK R8 + AAB + mapping) ;
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
