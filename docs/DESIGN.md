# FactureApp — design language

Read this before changing the marketing site or the web app. It records what
the design is, and the three things that must never be added.

## The idea

A receipt book (*carnet à souches*), not a SaaS dashboard. The product replaces
a numbered, perforated, carbon-copied object that every Moroccan shop already
has — so the site borrows its structure: perforation, serial numbers, stamps,
duplicate pages.

One memorable thing per page: the invoice sheet. Everything around it is quiet.

## Tokens

| Token | Value | Use |
|---|---|---|
| `--papier` | `#F6F8FE` | page background (cool paper, never cream) |
| `--nuit` | `#0B1B4D` | text, rules, dark surfaces |
| `--bleu` | `#1E3FD2` | actions only |
| `--safran` | `#F7C948` | single warm accent; **fill behind navy, never text** |
| `--trait` | `#CBD5EC` | hairlines, 1 px |
| `--ardoise` | `#4D5B7C` | secondary text |
| `--vert` | `#16704A` | « payé » stamp only |
| `--rouge` | `#B3261E` | error text only |

**Type.** Bricolage Grotesque 800, tracking `-0.035em`, for display and
headings. IBM Plex Sans for body (16.5 px / 1.62). IBM Plex Mono for figures and
serial numbers only — it is the book's print, not a decorative label.

**Geometry.** 2 px radius on the marketing site. One shadow, on the sheet.
Hairlines group things. The web app keeps its 14 px radius for touch targets,
but pills are reserved for status and primary actions.

**Measure.** Body ≤ 80 ch; hero chapeau 44–46 ch.

## The three devices

1. **Perforation** — `.perfore`, a half-circle bite out of a sheet's bottom
   edge. Legitimate on the sheet, on the states block, and nowhere else. Three
   per page maximum; a fourth turns it into wallpaper. It is a single repeated
   `radial-gradient` — the only gradient in the system, and not a wash.
2. **Stamp** — `.tampon`, rotated, double-ruled, in the document's vocabulary
   (Brouillon, Émis, Payé). It replaces status pills on marketing. The app keeps
   its single status line; a stamp at 12 px is unreadable.
3. **Serial number** — only where it identifies something: `F-20261111-0001`,
   the SHA-256, the archive fingerprint. Never `01 / 02 / 03`.

## Product shots

One real capture, framed as a browser window (`.fenetre`): the free generator
at `factureapp.ma/app/gratuit`, filled with a believable invoice. Rules:

- It is a capture of the shipped app, never a redrawn mock-up of a screen the
  product does not have.
- The frame carries the real URL in its bar, so the reader knows what they are
  looking at.
- Under 720 px the capture is hidden: a 1040 px editor reduced to 350 px is
  unreadable, and the three steps beside it already say the same thing.
- The share card (`assets/img/apercu-1024.jpg`) is built from the site itself —
  brand, one sentence, the invoice sheet — not a phone screenshot.

## Forbidden

- **Rounded-card grids.** Group with a rule or a border. No floating white card
  with a shadow and the same radius as its neighbour.
- **Eyebrow labels and tracked all-caps headings.** The only uppercase left in
  the system is inside the stamp and the invoice mock, where it is printed.
- **Decorative numerals, icon-in-a-circle feature grids, gradient washes,
  middle-dot meta strings, a second decorative typeface.**
- A second shadow anywhere. If a block needs one to read, the block is wrong.

## Filling in new sections

Use this checklist before shipping a section:

- Does it group with a hairline rather than a box?
- Is every number carrying information?
- Could the reader retype this section's claim as fact? (Prices, document
  types, permissions and storage locations must match the code.)
- Would removing the section lose information, or only decoration?

## Where it applies

- `docs/assets/css/site.css` — the marketing site.
- `webapp/src/app.css` — same tokens, softer geometry. The app may use cards
  where a card is the object being manipulated (a document row, a client row);
  it must not use them for page layout.

The print templates (`InvoicePdf`, `creerPdf`) are **not** covered here yet.
Echoing the stamp into the six templates needs the render tests.
