package com.yassine.facturemaroc.data

/**
 * Something the shop sells that is not from a catalogue.
 *
 * A service call. A delivery. Labour at an hourly rate. A product the shop
 * always sells at the same agreed price.
 *
 * The model is Bookipi's and it is deliberately small: a label and a price.
 * That app has been invoicing from a phone for years and the whole item
 * catalogue is still two fields, because an item is a thing you sell and a
 * number you charge for it, and anything more is a shop's own filing system
 * pretending to be a feature.
 *
 * No categories, no stock, no per-item tax. Tax and discount belong to the
 * invoice, not to the item, so that one saved item is right on every invoice
 * rather than only on the ones whose settings happen to match.
 */
data class SavedItem(
    /** What it is called on the invoice. The only required field. */
    val label: String,
    /** The price for one of them, before any discount and before TVA. */
    val price: Double,
    /**
     * How it is sold: `heure`, `jour`, `kg`, `unité`.
     *
     * Appended to the label rather than kept as a separate column on the
     * document, because a line on an invoice reads as one phrase and a
     * qualification in its own column is a word the reader has to interpret
     * before they can price the line against a competitor's.
     */
    val unit: String = "",
    /**
     * The folder the article is filed under.
     *
     * Empty is a real answer — "Sans dossier" — not a missing value: an article
     * does not have to belong to a folder. Folders are a way to keep a long list
     * readable, never a requirement for saving something.
     */
    val folder: String = "",
) {
    /**
     * The saved item as an invoice line.
     *
     * The label goes in the line's description with the price copied into the
     * line. Raising an item's price must not retroactively change an invoice
     * that is already half written, so the number is settled when the line is
     * made.
     */
    fun toLine(quantity: Int = 1): InvoiceLine = InvoiceLine(
        description = label.trim(),
        quantity = quantity,
        unitPrice = price,
        unit = unit,
    )
}
