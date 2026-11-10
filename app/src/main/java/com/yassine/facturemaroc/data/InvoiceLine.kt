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
     * The rate of this line, when it differs from the document's.
     *
     * Moroccan invoices must break VAT down line by line (CGI art. 145), so a
     * document may mix a 20 % article and a 10 % service. Null means "the rate
     * in the shop settings at the time the line was written" — which is what
     * every line written before this field existed means, and what a shop that
     * sells at one rate keeps meaning.
     */
    val vatPercent: Double? = null,
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
            // Only an explicit rate is printed: a document at one rate says it
            // once in the totals, and repeating it on every line is noise. The
            // space before the sign is non-breaking, so a narrow ticket wraps
            // the whole "TVA 10 %" onto its second line instead of leaving a
            // lone "%" behind.
            vatPercent?.takeIf { !taxExempt }?.let { add("TVA ${trimRate(it)}\u00A0%") }
        }.joinToString(" · ")

    /**
     * The rate this line is taxed at on a document whose own rate is [documentRate].
     *
     * An exempt line is 0 whatever the document says; so is every line when the
     * seller is an auto-entrepreneur, which the caller resolves before calling
     * this (see `InvoiceData.rateOf`).
     */
    fun rateFor(documentRate: Double): Double =
        if (taxExempt) 0.0 else (vatPercent ?: documentRate).coerceAtLeast(0.0)
}

/** `20`, `10`, `7.5` — a rate printed without trailing zeros. */
internal fun trimRate(rate: Double): String =
    if (rate == Math.floor(rate)) rate.toInt().toString()
    else rate.toString().trimEnd('0').trimEnd('.')
