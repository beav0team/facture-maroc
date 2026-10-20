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
    /** How it is sold: `per tyre`, `per wheel`, `per hour`. Printed beside the name. */
    val unit: String = "",
    /** Discount applied to this line only, before the invoice-level discount. 0 means none. */
    val lineDiscountPercent: Int = 0,
) {
    val total: Double get() =
        quantity * unitPrice * (1.0 - lineDiscountPercent.coerceIn(0, 100) / 100.0)

    /** The line as it is printed: `Wheel alignment · (per tyre) · −10%`. */
    val lineLabel: String
        get() = buildList {
            if (description.isNotBlank()) add(description.trim())
            val unitText = unit.trim()
            if (unitText.isNotEmpty()) add("($unitText)")
            if (lineDiscountPercent > 0) add("−$lineDiscountPercent%")
        }.joinToString(" · ")
}
