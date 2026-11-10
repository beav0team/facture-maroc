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

## Open

1. **Auto-entrepreneur number** — print it on documents?

## Known limitations

### 2026-11-10 — Clients and suppliers are matched by name
There is no `partyId`. A statement, a supplier's history and the "has this client
bought before?" check all compare trimmed names, case-insensitively. Renaming a
client therefore detaches their history: the invoices keep the old spelling and
the new name starts a new account. Accepted for 0.11 because a rename is rare and
an invented id would need a migration of every stored document to be worth
anything. Fixed before 1.0 by writing `partyId` on issuance **and** keeping the
name fallback for every document already issued.

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
