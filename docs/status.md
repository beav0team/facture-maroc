# Facture Maroc — current status

**Read this before planning any work on this app.**
If the version below is older than `app/build.gradle.kts`, audit the code before
answering from memory.

| | |
|---|---|
| Version | **0.10.0** (versionCode 16) — 0.11.0 en préparation, non publiée |
| Last commit | Phase B2 — bons de réception, coûts et marge (commits locaux) |
| Branch | `master` (Phase A/B local commits; no tag/release for 0.11.0 yet) |
| Tests | **487 JVM**, **18 instrumented** |
| Last audit | 2026-11-10 |
| Target | Morocco only, MAD only, French UI |

---

## In progress — 0.11.0 / Phase A

A1 (VAT and compliance engine) has landed on top of 0.10.0 and is **not
released**: per-line VAT, recap by rate on every template, UBL per rate, ICE
validation, detail sheet rows, fixed-amount discounts (global and per line) and
a percentage surcharge. A1 is complete. A2 (payments lifecycle) is in
progress: the payment deadline, the method per payment, the A5 receipt and the
client statement have landed; RAS and the reminder text remain. A3 has landed:
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
**B4 (stock view and reports) is next.**

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
| Reports | **Partial** | monthly billed net of credit notes only |

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
| Known stale items | contact e-mail line in README; screenshots predate A2–A4 |
| Open checks | **16 KB page-size support** on the AAB (CameraX + ML Kit) |

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
