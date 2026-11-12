# Facture Maroc — decisions

Short decision log. One entry per decision, with the date and the reason.
If a decision is reversed, add a new entry rather than editing the old one.

---

## Settled

### 2026-10 — Morocco only, MAD only
The app targets Moroccan businesses and Moroccan rules. No multi-currency, no
Peppol, no XRechnung, no Factur-X. UBL 2.1 is exported as a generic archive
format; no DGI compliance is claimed while the official profile is unpublished.

### 2026-10 — French UI only (settled 2026-11-10)
French is the application and document language. **Closed on 2026-11-10: French
only, permanently.** Arabic and RTL are out of scope, not deferred. The reason is
the cost of the three things Arabic would drag in — shaping in `PdfText`, a
left/right audit of 3 400 lines of Compose, and an embedded Arabic font in an
offline app — against a market where the shopkeeper reads French invoices. Play
stays `fr-FR`. The 870 French strings hardcoded in Kotlin stay hardcoded: with
one language, a string table would only add a lookup.

### 2026-11-10 — Issued documents keep a shop snapshot
Issuing stores the printed shop fields and a logo hash on the document. Changing
the shop afterwards must not rewrite an old invoice. Drafts follow live settings.

### 2026-11-10 — Logos are content-addressed
`LogoStore` writes each logo once under its SHA-256; documents keep only the
hash. Legacy 0.9.0 records are migrated at startup.

### 2026-11-10 — VAT 2026: 20 % and 10 %
The 7 % and 14 % rates were removed on 1 January 2026. New documents offer
20 %, 10 %, 0 % and exempt. Historical rates on old documents remain readable.

### 2026-11-10 — VAT will be per line
Art. 145 CGI requires the rate per line and a VAT recap. The tax engine will
move to per-line rates; this is a compliance fix, not a feature.

### 2026-11-10 — Issued documents must not be deletable
Deletion is being removed for issued documents; they are archived and corrected
with a credit note or a rectifying invoice. Drafts can still be deleted.

### 2026-11-10 — Photos: out for now
No item photos in 1.0. Revisit later if the shop needs visual identification.

### 2026-11-10 — Costing: a setting, layers in the model
Cost layers are stored from the start. Settings offer weighted average (default),
FIFO, or no costing. Switching affects future stock only; issued line costs are
frozen.

### 2026-11-10 — Goods receipts are their own series, not a document type
A bon de réception carries `BR-yyyyMMdd-0001` and lives in `purchases.json`, not
in the invoice history: it is a purchase, and putting a supplier's delivery among
the documents the shop handed to customers would make the two lists lie. The
number is derived from the day's existing receipts, so no extra counter has to
be restored or rewound. Regarding a receipt appends one PURCHASE ledger entry
per line and updates the cached quantity and the article's purchase price;
deleting it appends the reverse entries, so the shelf returns to what it would
have been and the history keeps the receipt that was cancelled.

### 2026-11-10 — Unknown cost is not zero
`StockEntry.unitCost` and `InvoiceLine.unitCostHt` are optional facts, not
numbers that default to a useful value: an inbound movement without a price
leaves the weighted average alone, a sale of an article with no known cost gets
no frozen cost, and the margin report counts that line as "sans coût" instead of
reporting it as pure profit. A line sold below its frozen cost raises a
non-blocking `PRIX_SOUS_COUT` warning: a loss leader is a decision, not an error.

### 2026-11-10 — Attributes: free key/value
Item attributes are a list of key/value pairs with neutral suggestions
(Référence, Marque, Modèle, Dimensions, Lot / série…). No hard-coded
industry-specific columns.

### 2026-11-10 — Quantity tiers apply to whole articles only
An article may carry price tiers ("from 10, at 9 DH HT"). The greatest tier whose
threshold is reached wins; decimal quantities (metres, kilos, hours) fall back to
the base price, because a tier boundary is a count of articles on a shelf, not a
length. The matched tier price and its threshold are frozen on the line when it is
picked, so a later edit of the ladder never rewrites an issued document, and the
threshold prints beside the line ("dès 10") so the customer sees why the price is
what it is.

### 2026-11-10 — One stock location in 1.0
Multi-location is out of scope for 1.0. The model keeps a nullable location for
later, but no UI.

### 2026-11-10 — Play Store is the official release channel
1.0 is intended for Play. GitHub releases remain the internal/beta channel.
Personal Play account: production requires 12 opted-in testers for 14 continuous
days.

### 2026-11-10 — Decimal quantities
A line quantity is a `Double`. Shops sell by the metre, the kilo and the hour,
and a whole number forced them to invent a second unit. Existing JSON files are
read unchanged (an integer is a Double). Stock stays counted in whole articles:
a line quantity is rounded to the nearest unit and that is stated in the code,
because silently turning stock into a decimal would change what "10 en stock"
means. Quantities print without trailing zeros, with a comma on paper and a dot
in XML.

### 2026-11-10 — Numbering: prefix per type, optional annual reset
Each document type can carry its own prefix (letters, digits, `-`, `_`; empty
means the built-in one) because shops number their devis and invoices
differently. Annual reset is a switch, off by default: many shops number a
series continuously, and resetting without being asked would reissue numbers.
When it is on, each counter stores the year it was last taken, so a restore or
a cancelled share cannot restart the sequence in the middle of a year. Numbers
stay `PREFIX-yyyyMMdd-0000`.

### 2026-11-10 — Payment terms: presets plus the shop's own words
Conditions de règlement offer four presets (comptant, 30 jours, 60 jours, fin de
mois) that fill the printed note, and the note stays editable. The stored value
is the preset, so the app can tell a cash sale from a deferred one without
parsing prose. A preset never replaces hand-typed text without asking: the
dialog offers "Remplacer" or "Garder mon texte", and a hand-edited sentence is
stored as free text. Legacy documents are read unchanged and their preset is
derived from the note.

### 2026-11-10 — Late clause: a model, never presented as legal advice
The late-payment clause is a Settings field, shipped with one short French model
clause that names no rate and no amount. Settings says to have it checked by the
accountant. It prints under the totals only on a document that is not paid at
the counter and only when the shop filled it; a cleared field prints nothing.
The clause is copied onto the document, so a reprint says what the customer
read.

### 2026-11-10 — Payment QR: plain text, the shop's own account
The QR is a design setting, off by default, drawn only when a RIB exists. It
encodes plain text — bank, RIB, account holder, amount due, document number —
and never a URL or a payment-page link. A link would be a payment instruction
the app invented; the shop owns the account and the code must say only what the
shop typed.

### 2026-11-10 — PDF names: one pattern, one sanitiser
The exported invoice follows a Settings pattern (default `facture_{number}`,
with `{client}`, `{date}` and `{type}`). Every name goes through one sanitiser
and always ends in `.pdf`; the receipt and the statement use the same rules.
Anything a file system, mail client or USB stick refuses becomes `_`, a leading
dot goes away, and a pattern that leaves nothing usable falls back to the
default rather than to a file called `.pdf`.

### 2026-11-10 — Archive state is per document, not per card
The detail sheet says whether the PDF of that document is really kept. It reads
the ledger and checks that the `.pdf` file exists, so "Oui" means the archive
can produce the document, not merely that an entry mentions it. Deliberately a
small row, not a badge on every history card: the archive screen remains the
place that verifies the whole chain.

### 2026-11-10 — Payments are a history, not a figure
A document keeps the list of the payments it received, not one overwritten
amount: a customer who pays 400 today and the rest next month leaves two
entries, each with its own date, method and note, and each receiptable on its
own. `paidAmount` is the sum, clamped to what is left to pay, and the old single
`payment` object is read back as a one-entry history so every document already
issued opens with its money intact.

### 2026-11-10 — Only a document that asks for money can be paid
Facture, ticket, simplifiée and export. A devis is a price offered, a bon de
commande is money going out, a proforma has no accounting value, an avoir gives
money back, and an acompte is paid when issued. Recording a receipt against any
of them turns a quote into a sale that never happened.

### 2026-11-10 — Tier prices follow the article's basis
`PriceTier` is priced like `SavedItem.price`: TTC when the shop quotes TTC. The
field used to be named and labelled HT while the number went onto the line as a
shop-basis figure, so a TTC shop typing 100 charged 100 TTC instead of the 120
it meant. The old `unitPriceHt` key is still read; the label follows the shop's
setting.

### 2026-11-10 — Android backup is off
`allowBackup="false"` and a `data-extraction-rules` file excluding every domain
from cloud backup and from device transfer. The app promises it is offline and
that the shop's documents do not leave the phone; a platform backup running in
the background contradicted that without ever appearing on screen. Moving to a
new phone goes through the application's own backup file, which the shop makes
and keeps.

### 2026-11-10 — A credit note is written as a negative sale
In the accountant CSV the avoir carries negative HT, VAT, TTC and balance, and
the status "Avoir". Positive, it added itself to the year's turnover and its
"reste" read as money the client still owed. On screen it shows a negative
amount, an "Avoir" badge, no deadline and is never late.

---

### 2026-11-11 — Appeler et écrire depuis une fiche, sans permission
Les icônes d'appel et de WhatsApp utilisent `ACTION_DIAL` et un lien
`https://wa.me/…`. `ACTION_CALL` aurait exigé `CALL_PHONE` et une autorisation à
l'installation pour un geste que le composeur fait aussi bien ; `wa.me` remet le
numéro à WhatsApp ou au navigateur, qui possèdent le réseau. **L'application
garde zéro permission réseau** : elle ne charge aucune donnée, et le lien n'est
ouvert que si le magasin touche l'icône. Le numéro est normalisé
(`06 12 34 56 78` → `+212612345678`) et un numéro étranger garde son pays.

### 2026-11-11 — Le stock peut ne pas être déduit à l'émission
Un magasin peut tenir son stock ailleurs, ou facturer surtout des prestations.
Le réglage « Déduire le stock à l'émission » (Catalogue et stock → Stock et
facturation) est **actif par défaut** : une installation existante ne change pas
de comportement. Désactivé, émettre une facture ou exporter un XML n'écrit aucun
mouvement et laisse `stockApplied` à faux, pour qu'une suppression ne crédite
jamais une étagère qui n'a pas été débitée. Le choix voyage dans la sauvegarde ;
un fichier antérieur, sans la clé, restaure en déduisant.

### 2026-11-11 — La webapp est le produit vendu, l'app Android reste le terrain
Google Play ne permet pas l'enregistrement marchand au Maroc : la monétisation
passe par `factureapp.ma/app`. L'app Android reste hors ligne, gratuite sur le
Play, et sert d'entonnoir. Les deux partagent le même moteur (fixtures Kotlin).

### 2026-11-11 — Les documents émis sont écrits en SQL, jamais par le client
Le numéro, le snapshot boutique et le quota du plan gratuit sont décidés dans
`emettre_document()`. Aucun privilège `insert/update` n'est accordé sur
`documents` : un onglet ne peut pas contourner la numérotation. Un brouillon peut
être supprimé, un document émis seulement corrigé par un avoir.

### 2026-11-11 — Le PDF archivé est la vérité, pas un rendu à la demande
À l'émission, le PDF est rendu avec `pdf-lib`, haché en SHA-256 et stocké dans le
bucket privé `archives`. Une réimpression télécharge ces octets. Les paiements
ultérieurs ne réécrivent pas l'archive : ils vivent dans `payments` et sur le
relevé.

### 2026-11-11 — Le plan Pro s'active à la main, sans paiement en ligne
Le lancement encaisse par virement ou espèces, puis l'admin met
`subscriptions.paid_until`. Payzone/YouCan Pay viendront plus tard ; aucune
commission n'est prélevée sur les factures.

### 2026-11-11 — L'import Android ramène des brouillons
Un numéro déjà imprimé n'est jamais réémis par la webapp. L'import reprend les
clients, les articles et les compteurs, puis recrée les documents en brouillons
avec leur numéro Android dans `reference`.

### 2026-11-12 — La vitrine vend la webapp ; le prix vit dans offres.ts
Le site marketing ne vend plus l'application Android : l'accueil mène au compte
web, et `test.html` reste la seule page Android. Aucun montant n'est imprimé
dans le HTML ; `/tarifs` redirige en 301 vers `/app/tarifs`, qui lit
`webapp/src/lib/offres.ts`. Le tarif Pro (990 DH) reste provisoire tant que le
paiement en ligne n'est pas ouvert.

### 2026-11-12 — Les captures viennent de la vraie application
Les deux captures du compte sont prises sur la boutique de test de l’agent
(`agent-test@factureapp.ma`), remplie d’un jeu de démonstration identique à
celui du site (Atelier Atlas, Société Nomade). Aucune donnée d’une boutique
réelle n’est lue ni montrée. La boutique de test a été renommée « Atelier
Atlas » pour que la capture et le site racontent la même histoire ;
`./scripts/compte-test.sh reinitialiser` la vide.

### 2026-11-12 — Le site ne promet plus l’absence de mesure d’audience
Yassine n’a jamais pris cet engagement. Les phrases « aucun script de mesure
d’audience » sont retirées ; restent « aucune publicité » et « aucune revente
de données », qui sont vrais. Une mesure d’audience (Cloudflare Web Analytics,
sans cookie) ne demande aucun changement de code : elle s’active dans le
tableau de bord, et la politique de confidentialité doit alors la citer.

### 2026-11-12 — Le prix s’écrit à deux endroits, vérifiés
L’accueil affiche le tarif du Pro et les CGV doivent l’énoncer : deux copies
qui peuvent diverger. `check-site.mjs` lit `offres.ts` et refuse un prix qui ne
correspond plus, sur l’une comme sur l’autre page. Aucune autre page n’a le
droit de le recopier.

### 2026-11-12 — Les boutons mènent à l’inscription, pas à la connexion
Un visiteur qui clique « Créer ma facture » n’a pas de compte : toutes les
actions de création pointent sur `/app/?mode=signup`. Les liens de connexion
(`Connexion`, `Se connecter`) restent sur `/app/`.

### 2026-11-12 — Six modèles de document, comme sur Android
La webapp n’en connaissait que deux (`classique`, `facture_maroc`). Elle reprend
les six modèles de l’application : Classique, Moderne, Facture Maroc, Épuré,
Bandeau, et Ticket sur rouleau 80 mm. Les identifiants existants ne changent pas
— `facture_maroc` reste `facture_maroc`, sinon les documents déjà émis
perdraient leur modèle — et la contrainte SQL est seulement élargie
(`0006_modeles.sql`). Le choix se fait par document, se règle par défaut sur la
boutique, et le générateur public le propose pour juger le rendu sans compte.

### 2026-11-12 — La vitrine prend la forme d’une page produit
L’accueil suit la structure d’une page de logiciel de facturation (celle de
factura-ma.com) : héros avec la facture et les quatre preuves, aiguillage
compte / générateur, quatre atouts avec leur miniature, trois étapes avec une
capture réelle de l’éditeur, états du document, dix documents, deux formules avec
leur prix, note du fondateur, FAQ, appel final. Le langage visuel reste celui de
`DESIGN.md` : la structure est empruntée, pas les cartes arrondies ni les dégradés.
La capture vient de `app/gratuit` (le seul écran public) ; elle est masquée sous
720 px. L’image de partage est refaite depuis le site — l’ancienne montrait
l’application Android.

### 2026-11-12 — La vitrine ne cite plus l’application Android
La webapp est le produit, et le site ne vend qu’elle : la section Android de
`a-propos.html` et de `facture-modele-marocain-gratuit.html` est supprimée, le
lien « version Android de test » disparaît de tous les pieds de page, et
`contact.html` ne propose plus de test fermé. `test.html` reste en ligne, en
`noindex`, sans lien entrant, pour les testeurs qui ont déjà l’adresse.
`webapp/scripts/check-site.mjs` refuse désormais toute mention d’Android ou du
Play Store dans une page de la vitrine.

### 2026-11-12 — Une page de limites assumée
`docs/avis.html` énumère ce que la webapp ne fait pas : journal de stock, export
UBL, facture électronique DGI, relances, comptabilité, mode hors ligne. Toute
promesse retirée du site doit y être ajoutée, et inversement.

### 2026-11-12 — Les URL publiques n’ont pas d’extension
Cloudflare Pages redirige `/avis.html` vers `/avis` en 308. Les liens, les
canonicals, les `og:url`, le JSON-LD et le sitemap désignent donc la version
finale, sans `.html` ; `site_check.py` échoue si un canonical repointe vers une
URL redirigée. Sur disque les fichiers restent `avis.html` : le serveur local
réécrit `/avis` vers le fichier. `privacy.html` reste publiée pour la fiche
Play, mais en `noindex` — elle ne doit pas être indexée par le site.

### 2026-11-12 — Une facture sans TVA dit « Total », pas « Total TTC »
« TTC » suppose une taxe collectée. Une facture d’auto-entrepreneur, ou toute
facture dont la TVA ne s’applique pas, n’imprime donc ni ligne de TVA ni
« Total TTC » : le PDF écrit « Total », ajoute « TVA non applicable » sous les
totaux, et laisse la référence légale (article 91-II-3° pour les prestataires,
seuil de l’article 89 pour les détaillants) à la note du document. Le libellé
vient de `libelleTotal()` : l’éditeur, le générateur et les deux rendus PDF ne
peuvent pas diverger.

### 2026-11-12 — Trois pages pour trois intentions de recherche
`facture-auto-entrepreneur-maroc`, `logiciel-facturation-gratuit-maroc` et
`factureapp-ou-hisab` ne se remplacent pas : la première explique le régime et
les mentions, la deuxième ce que le plan gratuit couvre, la troisième compare
deux produits. La comparaison est datée, non affiliée, et n’affirme sur Hisab
que ce que son site public annonce ; elle ne doit pas être réécrite de mémoire.
Les trois pages sont liées depuis tous les pieds de page pour ne pas dépendre du
seul maillage contextuel.

## Open

1. **Auto-entrepreneur number** — print it on documents?

## Known limitations

### 2026-11-11 — Parties have a stable id; the name stays the fallback
`InvoiceClient.id` (and `Purchase.supplierId`) now identify a party, so renaming
a client no longer detaches the invoices issued under the old name. The id is
written on the party and carried in every document snapshot; a document issued
before ids existed is stamped **once**, at launch, with the id of the party it
names (`PartyIdentity`), and only when exactly one party carries that name in
that role — an ambiguous name is left alone rather than attached to the wrong
account. Anything that still has no id (a walk-in client typed on the invoice)
keeps matching by trimmed, case-insensitive name: that fallback is permanent, not
a migration step. The editor still refuses two parties with the same name and
role, because the shop at the counter cannot tell them apart; the data tolerates
it, since an import or a hand-edited file can produce it. Documents keep printing
the name — no template, PDF or XML change.

## Closed since the roadmap was written

- **RAS TVA** — the user asked to continue without it; no field is invented.
- **Deposit** — implemented: the final invoice deducts a referenced acompte from
  the balance, never from the taxable base.
- **1.0 release** — Play is the official channel; GitHub releases are the
  internal/beta channel.
- **Decimal quantities** — implemented (`Double` lines, whole-unit stock).
- **Payment terms** — presets plus free text, implemented.
- **Photos** — deliberately out of 1.0.

### 2026-11-10 — The site is named "Facture Maroc hors ligne"
The query "facture maroc" belongs to DATRON's app (`com.facturemaroc.invoices`,
Play and App Store, 4.8 stars, 5,000+ downloads). The product keeps its name —
it is descriptive, and it is already in the repository, the domain and the
documents — but everything a search engine judges carries the qualifier: site
title, `og:site_name`, JSON-LD `name`, the future Play listing title, and the
first paragraph of every page. The claim nobody else can make is technical, not
commercial: **no network permission at all**. The package stays
`com.yassine.facturemaroc`, distinct from DATRON's, so the two cannot be
confused at install time.

### 2026-11-10 — The site publishes three editorial pages
`a-propos`, `contact`, and a guide on the mandatory fields of a Moroccan
invoice. Reasoning: a search engine and a language model both need an entity
(name, author, address, source) and pages that answer one precise question.
Every guide uses the same shape: an H1 that repeats the question, a 40–60 word
direct answer, a table of the facts, the legal reference, a last-updated date and
a named author. No bulk-generated content: six written pages beat six hundred
empty ones.
