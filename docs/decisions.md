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

---

## Open

1. **Decimal quantities** — recommended for a general app (kg, m, L), not yet
   confirmed.
2. **Arabic / bilingual** — pending accountant confirmation.
3. **RAS TVA** — note only, or withheld amount with recalculated balance?
4. **Payment terms** — presets (cash / 30 / 60 days) or free text?
5. **Deposit** — should the final invoice deduct a referenced acompte
   automatically?
6. **Auto-entrepreneur number** — print it on documents?
7. **1.0 release** — Play only, or Play + GitHub release?
