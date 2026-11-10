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
    /**
     * Quantity of this item.
     *
     * A Double because a shop sells by the metre, the kilo or the hour: 2,5 m
     * of cable and 1,25 h of labour are real lines, and a whole-number quantity
     * could not write them without inventing a second unit. Stock, which is
     * counted in whole articles, rounds this — see `StockMovement`.
     */
    val quantity: Double,
    /** Price for one unit, before any discount and before TVA. */
    val unitPrice: Double,
    /** How it is sold: `heure`, `jour`, `kg`, `unité`. Printed beside the name. */
    val unit: String = "",
    /** Discount applied to this line only, before the invoice-level discount. 0 means none. */
    val lineDiscountPercent: Int = 0,
    /**
     * A discount on this line given in dirhams rather than in percent.
     *
     * A shop that says "50 DH off the chair" should not have to work out what
     * percentage that is, and rounding the percentage back to a whole number
     * would silently change the price. When set, it takes over from
     * [lineDiscountPercent]: one discount per line, not two.
     */
    val lineDiscountFixed: Double = 0.0,
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
    /**
     * The saved article this line came from, when it came from one.
     *
     * Copied at the moment the line is made and never resolved through the
     * catalogue: that is what lets the label on the paper stay what the customer
     * read while the shop renames the article in its list. Null means the line
     * was typed by hand or written before ids existed, and the label is then
     * the only link — see `StockMovement`.
     */
    val itemId: String? = null,
    /**
     * What one unit of this line cost the shop, frozen when the document is
     * issued, or null when the cost was not known.
     *
     * Frozen on purpose: a shop that buys the same article cheaper next month
     * must not watch last month's invoice become more profitable. Null is the
     * honest answer for a service, a line typed by hand, and every document
     * issued before costing existed — those lines are excluded from the margin
     * and counted as "sans coût" rather than valued at a guessed zero.
     */
    val unitCostHt: Double? = null,
    /**
     * The quantity threshold of the price tier that was applied, frozen when
     * the line is made. Null when no tier matched.
     *
     * Printed beside the unit price so the customer can see why the price is
     * what it is: `9,00 DH l'unité · dès 10`.
     */
    val tierFromQuantity: Int? = null,
    /**
     * The tier's unit price, frozen at issue. The shop can still override it
     * on the line editor; what matters is that the document keeps what the
     * shelf offered at the moment the line was written.
     */
    val appliedTierPrice: Double? = null,
    /**
     * The article's attributes, copied onto this line so the document is
     * self-contained: `Câble électrique · m · Réf: E-100`.
     *
     * Printed each time the same attribute appears on several lines — lines
     * are not resolved back to the catalogue at print time.
     */
    val lineAttributes: List<ItemAttribute> = emptyList(),
) {
    val total: Double get() {
        val gross = quantity * unitPrice
        // Never below zero: a discount larger than the line is a typing
        // mistake, not a credit note.
        return if (lineDiscountFixed > 0.0) {
            (gross - lineDiscountFixed).coerceAtLeast(0.0)
        } else {
            gross * (1.0 - lineDiscountPercent.coerceIn(0, 100) / 100.0)
        }
    }

    /** The line as it is printed: `Câble électrique · m · Réf: E-100 · −10% · TVA exonérée`. */
    val lineLabel: String
        get() = buildList {
            if (description.isNotBlank()) add(description.trim())
            val unitText = unit.trim()
            if (unitText.isNotEmpty()) add(unitText)
            lineAttributes.forEach { attr ->
                if (attr.key.isNotBlank() && attr.value.isNotBlank()) {
                    add("${attr.key}: ${attr.value}")
                }
            }
            // The tier threshold is printed so the customer sees why the price
            // is what it is: `9,00 DH l'unité · dès 10`.
            tierFromQuantity?.let { add("dès $it") }
            if (lineDiscountPercent > 0) add("−$lineDiscountPercent%")
            if (lineDiscountFixed > 0.0) add("−${moneyText(lineDiscountFixed)} DH")
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

    /**
     * Whether [other] is the same sale, so adding it may just raise the quantity.
     *
     * Matched on everything that prints as a column or changes the money: two
     * lines with the same name at different prices are two sales, and folding
     * them together would silently drop the second price. The fixed discount and
     * the article it comes from are part of that — a -50 DH line and a plain one
     * look identical in the label but are not the same sale, and the same text
     * typed by hand for two different articles must stay two lines.
     */
    fun sameSaleAs(other: InvoiceLine): Boolean =
        description == other.description &&
            unit == other.unit &&
            unitPrice == other.unitPrice &&
            lineDiscountPercent == other.lineDiscountPercent &&
            lineDiscountFixed == other.lineDiscountFixed &&
            taxExempt == other.taxExempt &&
            vatPercent == other.vatPercent &&
            itemId == other.itemId

    /**
     * This line at [quantity], dropping the tier marker once it no longer holds.
     *
     * The tier threshold is printed to explain a price — `9,00 DH · dès 10` — so
     * a line edited down to 5 units must stop claiming a ten-unit tier. The price
     * itself is left alone: it was settled when the line was made, and silently
     * raising a sale's price because the customer took two more would be worse
     * than a line without its explanation.
     */
    fun withQuantity(quantity: Double): InvoiceLine {
        val keepsTier = tierFromQuantity?.let { from ->
            quantity == quantity.toInt().toDouble() && quantity >= from
        } ?: true
        return if (keepsTier) {
            copy(quantity = quantity)
        } else {
            copy(quantity = quantity, tierFromQuantity = null, appliedTierPrice = null)
        }
    }
}

/** `20`, `10`, `7.5` — a rate printed without trailing zeros. */
internal fun trimRate(rate: Double): String =
    if (rate == Math.floor(rate)) rate.toInt().toString()
    else rate.toString().trimEnd('0').trimEnd('.')

/**
 * A quantity as a machine reads it: `2`, `1.5`, `0.25`.
 *
 * Rounding to three decimals keeps a typed `1,234` from printing as
 * `1,2339999999`, while leaving enough precision for a kilo sold by weight.
 * XML uses this form; the paper uses [quantityText].
 */
fun quantityValue(quantity: Double): String {
    if (quantity.isNaN() || quantity.isInfinite()) return "0"
    val rounded = Math.round(quantity * 1000.0) / 1000.0
    return if (rounded == Math.floor(rounded)) {
        rounded.toLong().toString()
    } else {
        java.lang.String
            .format(java.util.Locale.US, "%.3f", rounded)
            .trimEnd('0')
            .trimEnd('.')
    }
}

/**
 * A quantity as it is printed in French: `2`, `1,5`, `0,25`.
 *
 * A line that sells two hours must not print "2.0", and a line that sells
 * 1,5 m must not print "1.5000": the document is read on paper, not parsed.
 */
fun quantityText(quantity: Double): String = quantityValue(quantity).replace('.', ',')

/** `1 234,50` — a French amount, without the currency. */
internal fun moneyText(value: Double): String = Money.plain(value)
