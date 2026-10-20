package com.yassine.facturemaroc.data

/**
 * Something the shop sells that is not from a catalogue.
 *
 * A wheel alignment. A valve. A tube. Labour at an hourly rate. A service the
 * shop always provides at the same agreed price.
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
     * How it is sold: `per tyre`, `per wheel`, `per hour`.
     *
     * Appended to the label rather than kept as a separate column on the
     * document, because a line on an invoice reads as one phrase and a
     * qualification in its own column is a word the reader has to interpret
     * before they can price the line against a competitor's.
     */
    val unit: String = "",
) {
    /** The line as it appears on the document. */
    val description: String
        get() = if (unit.isBlank()) label.trim() else "${label.trim()} ($unit)"

    /**
     * The saved item as an invoice line.
     *
     * The label goes in the line's description with the price copied into the
     * line. Raising an item's price must not retroactively change an invoice
     * that is already half written, so the number is settled when the line is
     * made.
     */
    fun toLine(quantity: Int = 1): InvoiceLine = InvoiceLine(
        description = description,
        quantity = quantity,
        unitPrice = price,
        unit = unit,
    )
}
