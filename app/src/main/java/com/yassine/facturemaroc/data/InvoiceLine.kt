package com.yassine.facturemaroc.data

/**
 * One line on an invoice.
 *
 * Held by value rather than by reference into a catalogue, for one reason:
 * prices and items can be edited on the shop side. Copying the fields that
 * appear on the document means the invoice says what it said when it was
 * written.
 */
data class InvoiceLine(
    /** What the line is called on the document. */
    val description: String,
    /** Quantity of this item. */
    val quantity: Int,
    /** Price for one unit, before any discount and before TVA. */
    val unitPrice: Double,
    /** How it is sold: `heure`, `jour`, `kg`, `unité`. Printed beside the name. */
    val unit: String = "",
    /** Discount applied to this line only, before the invoice-level discount. 0 means none. */
    val lineDiscountPercent: Int = 0,
    /**
     * True when TVA does not apply to this line (export, some public bodies).
     *
     * Set per line rather than only per invoice because an invoice can mix a
     * taxed sale and an exempt service, which is exactly what a garage does
     * when it sells a part and exports the fitting.
     */
    val taxExempt: Boolean = false,
    /**
     * The price as the shop typed it, when the shop quotes TTC.
     *
     * [unitPrice] is always HT, because that is what the document prints. A shop
     * that says "150 DH, TVA comprise" typed 150, and that number has to survive
     * a change of buyer: an exempt client pays 150 with no tax inside it, a
     * taxed one pays 125 + 25. Remembering it here is what lets the invoice
     * re-read the price when the client changes, instead of freezing the answer
     * that was true at the moment the line was added.
     *
     * Null means the price was already HT, and an HT price never moves.
     */
    val ttcPrice: Double? = null,
) {
    val total: Double get() =
        quantity * unitPrice * (1.0 - lineDiscountPercent.coerceIn(0, 100) / 100.0)

    /** The line as it is printed: `Main-d'œuvre · heure · −10% · TVA exonérée`. */
    val lineLabel: String
        get() = buildList {
            if (description.isNotBlank()) add(description.trim())
            val unitText = unit.trim()
            if (unitText.isNotEmpty()) add(unitText)
            if (lineDiscountPercent > 0) add("−$lineDiscountPercent%")
            if (taxExempt) add("TVA exonérée")
        }.joinToString(" · ")
}
