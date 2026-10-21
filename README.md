# Facture Maroc

An offline Android app for a Moroccan shop that needs to hand a customer a
proper document: a facture, a devis, a bon de commande, or an 80mm ticket.

Nothing leaves the phone. The app declares **no permissions at all** — no
internet, no storage, no contacts. Documents are written to the app's own cache
and shared through a `FileProvider`, which hands the receiving app permission
for that one file through the intent itself. Sharing a document the app made is
not the same permission as reading the user's files, and this app never asks for
the second one.

## What it does

Build a document on one screen: lines at the top, where the typing happens, then
the client, the discount, the payment method and the totals.

- **Lines** come from a tyre-free catalogue of **my items and services** (a
  fitting, an alignment, labour) or are typed **by hand**. A saved item is a label
  and a price; raising its price next month does not change a document that is
  already written, because the price is copied onto the line.
- **Quantity** is a stepper or a typed number. **Unit price** is typeable, which
  is how a negotiated price is recorded.
- **Clients** are typed per document. This release does not keep a client list
  yet, and nothing is recorded about a sale once the PDF is written.
- **Document types**: facture, devis (with a validity date), bon de commande
  (which asks for a supplier instead of a client) and ticket. Each has its own
  number counter: `F-20261021-0001`, `D-…`, `BC-…`, `T-…`.

### On the document

- The shop's name, address, phone and logo, plus the identifiers a Moroccan
  document is expected to carry: **ICE, IF, RC, Patente and CNSS**, printed only
  when they are filled in. There is deliberately **no timbre fiscal** — the stamp
  duty was abolished in Morocco in 2020, so a field for it would only invite a
  number that no longer exists.
- **HT, TVA and TTC**, with the rate set once in the shop's details and defaulting
  to 20%. TVA is applied **after** any discount, which is the order the tax
  authority expects.
- **The total spelled out in French**, on the line a Moroccan document uses:
  `ARRÊTÉE LA PRÉSENTE FACTURE À LA SOMME DE : …`. It is generated from the same
  rounded total that is printed, never typed, and it is the line that makes the
  document hard to alter by hand. The French rules that are easy to get wrong are
  the ones that matter here: `soixante-dix`, `quatre-vingts`, `quatre-vingt-un`,
  `cent` against `cents`, and the centimes.
- Amounts in French formatting: `1 390,00 DH`.

### Four templates

Chosen because they print differently, not merely because they look different.
The design screen previews the **real PDF** — the preview is drawn by the same
code that writes the file.

| Template | For |
|---|---|
| **Classique** | Coloured header, alternating rows. The safe all-rounder. |
| **Moderne** | Rules instead of fills. Stays readable photocopied in black and white. |
| **Facture Maroc** | Boxed header, full grid on the table, signature space for the stamp. |
| **Ticket** | An 80mm till roll, cut to the length of the sale. Monochrome, because a thermal printer cannot print an accent colour. |

## What it deliberately does not do

- **No payment status, no credit tracking.** This app writes documents; it does
  not know what is owed. That needs a receivables ledger, and a second ledger in
  a second app is how two ledgers start disagreeing.
- **No stock, no reports, no cloud.** Nothing is recorded, so there is nothing to
  report on.
- **No account, no sign-in, no analytics.**

A draft is held in memory: it survives a rotation but not the app being closed.
A number is only spent when a document is really produced, so browsing the
builder never leaves a gap in the sequence.

## Status

**0.1.0** — the first version that builds and runs. Four templates, four document
types, saved items, numbering, zero permissions. There are **no unit tests** in
this repository yet; the rendering and the French number-to-words rules are the
two places where tests belong.

The invoicing code was first written for **Tyre Finder**, an app that prices tyres
for a Moroccan shop, and this app is that work without the catalogue: the same
templates, the same amount-in-words rules, the same "print it and record
nothing" scope.

## Building

Requires **JDK 17 or newer** and the Gradle wrapper in this repository.

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease     # R8, signed
```

Release builds read `keystore.properties`, which is not in the repository. Copy
`keystore.properties.example` and point it at your own keystore; without it the
release build is unsigned.

Stack: AGP 8.7.3, Kotlin 2.1.0, Compose (BOM 2024.12.01), minSdk 26, targetSdk 35.
