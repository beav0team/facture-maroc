# Facture Maroc — decisions

Short decision log. One entry per decision, with the date and the reason.
If a decision is reversed, add a new entry rather than editing the old one.

---

## Settled

### 2026-10 — Morocco only, MAD only
The app targets Moroccan businesses and Moroccan rules. No multi-currency, no
Peppol, no XRechnung, no Factur-X. UBL 2.1 is exported as a generic archive
format; no DGI compliance is claimed while the official profile is unpublished.

### 2026-10 — French UI only (for now)
French is the application and document language. Arabic is not required by any
text found; it remains an open commercial choice.

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

### 2026-11-10 — Attributes: free key/value
Item attributes are a list of key/value pairs with neutral suggestions
(Référence, Marque, Modèle, Dimensions, Lot / série…). No hard-coded
industry-specific columns.

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

---

## Open

1. **Arabic / bilingual** — pending accountant confirmation.
2. **RAS TVA** — note only, or withheld amount with recalculated balance?
3. **Deposit** — should the final invoice deduct a referenced acompte
   automatically?
4. **Auto-entrepreneur number** — print it on documents?
5. **1.0 release** — Play only, or Play + GitHub release?
