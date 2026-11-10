# Facture Maroc — current status

**Read this before planning any work on this app.**
If the version below is older than `app/build.gradle.kts`, audit the code before
answering from memory.

| | |
|---|---|
| Version | **0.10.0** (versionCode 16) |
| Last commit | `3a4dc3a` — payments, surcharge, logo store, PDF archive |
| Branch | `master` (pushed; no tag/release for 0.10.0 yet) |
| Tests | **260 JVM**, **15 instrumented** |
| Last audit | 2026-11-10 |
| Target | Morocco only, MAD only, French UI |

---

## Feature status

### Invoicing

| Area | Status | Notes |
|---|---|---|
| Document types | **Exists** | facture, devis, bon de commande, ticket, avoir, proforma, acompte, régularisation, simplifiée, export |
| Templates | **Exists** | Classique, Moderne, Facture Maroc, Épuré, Bandeau, Ticket 80 mm |
| Lines | **Exists** | description, quantity (Int), unit price, unit, line discount %, VAT exemption |
| Global discount | **Partial** | percentage only; no fixed amount |
| Surcharge | **Exists** | fixed amount only; no percentage |
| VAT | **Partial** | one rate per invoice; **no per-line mixed rates** (compliance gap) |
| Payment tracking | **Exists** | amount, date, reference, partial/paid, badge, PDF block, UBL prepaid |
| Receipt PDF | **Missing** | |
| Due date / overdue | **Missing** | only quotes have a validity date |
| Client statement | **Missing** | client card shows document count only |
| Quote → invoice | **Missing** | duplication keeps the same document type |
| Invoice → credit note | **Missing** | credit notes are built manually |
| Deposit deduction | **Missing** | acompte is not subtracted from the final invoice |
| Numbering | **Partial** | per-type sequences; only invoice counter editable; no annual reset |
| ICE validation | **Missing** | free text, no 15-digit check |
| Notes / signature | **Exists** | client note, terms, thank-you, drawn/photo signature |
| Shop snapshot | **Exists** | issued documents keep issue-time details + logo hash |
| UBL 2.1 export | **Exists** | invoice + credit note, discount, prepaid; no DGI profile |
| Archive | **Exists** | hash chain for XML **and** PDF; verification screen in Settings |
| Backups | **Exists** | JSON export, automatic daily copies, restore |
| Reports | **Partial** | monthly billed net of credit notes only |

### Inventory

| Area | Status | Notes |
|---|---|---|
| Item fields | **Exists** | label, price, unit, folder, purchase price, barcode, stock |
| Stock deduction | **Exists** | on issue, restore on credit note/deletion |
| Stock ledger / history | **Missing** | quantity is overwritten with no trace |
| Stable item ID | **Missing** | items are keyed by label |
| Reorder threshold | **Missing** | |
| Stocktake | **Missing** | |
| Purchases / stock-in | **Missing** | |
| Cost layers / average / FIFO | **Missing** | only a static purchase price |
| Margin | **Partial** | unit margin shown in the item editor |
| Price tiers | **Missing** | |
| Attributes | **Missing** | |
| Photos | **Out** | deferred by decision |

---

## Moroccan compliance gaps

1. **VAT per line** — art. 145 CGI requires the rate per line; current model is
   one rate per invoice.
2. **ICE** — should be 15 digits; client ICE needed for business clients.
3. **Issued invoices are deletable** in the UI; the legal route is a credit note
   or a rectifying invoice.
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
| Known stale items | `listing-fr.md` says 4 templates and omits payments/notes/stock; `UPLOAD.md` pins 0.8.2/versionCode 14; contact e-mail line in README |
| Open checks | **16 KB page-size support** on the AAB (CameraX + ML Kit) |

---

## Open decisions

See `docs/decisions.md`. The unresolved ones:

1. Decimal quantities (recommended yes).
2. Arabic / bilingual (pending accountant).
3. RAS TVA: note only or withheld amount with recalculated balance.
4. Payment terms: presets or free text.
5. Deposit: automatic deduction.
6. Auto-entrepreneur number printed.
7. 1.0 release: Play only or Play + GitHub.

---

## Where the plan lives

- Roadmap: `~/.opencode/plan/facture-maroc-plan-roadmap.md`
- Decisions: `docs/decisions.md`
- Release checklist: `docs/release-checklist.md`
