package com.yassine.facturemaroc.data

/**
 * Reading a price the way the shop typed it.
 *
 * A document only ever prints HT + TVA, because that is the arithmetic an
 * invoice has to show. A shop, though, quotes whichever number it quotes:
 * "150 DH, TVA comprise" or "125 DH HT". This converts the first into the
 * second, and does nothing to the second.
 *
 * The conversion keeps every decimal instead of rounding the unit price to the
 * centime, for one reason: the shop quoted a TTC price and that price must come
 * back. `99,99 DH TTC` at 20 % is `83,325 DH HT`; rounding it to `83,33` makes
 * the invoice total `99,996 → 100,00`, a centime more than the customer was
 * told. Rounding happens once, on the totals, where [InvoiceData] already does
 * it.
 */
object PriceBasis {

    /**
     * The HT price behind [price], read as TTC when [vatPercent] is positive.
     *
     * A zero rate means the document is exempt, and an exempt price is already
     * its own HT price: dividing by 1 would be harmless but the branch says
     * which case this is.
     */
    fun excludingVat(price: Double, vatPercent: Double): Double =
        if (vatPercent <= 0.0) price else price / (1.0 + vatPercent / 100.0)

    /**
     * The TTC price behind [price], for showing a line back to the shop.
     *
     * Rounded to the centime because this number goes on a screen, not into an
     * addition: `83,325 HT` comes back as `99,99 TTC`, which is the figure that
     * was typed. Only the display is rounded — see [excludingVat].
     */
    fun includingVat(price: Double, vatPercent: Double): Double {
        if (vatPercent <= 0.0) return price
        val gross = price * (1.0 + vatPercent / 100.0)
        return Math.round(gross * 100.0) / 100.0
    }

    /**
     * The same lines, with every TTC-quoted price re-read at [vatPercent].
     *
     * Whether the number the shop typed has TVA inside it depends on who is
     * buying: an exempt client pays that number with no tax in it, a taxed one
     * pays it with the tax taken out. Re-reading the price when the client
     * changes is what keeps the invoice charging the figure that is on the
     * screen; freezing it at the moment the line was added charges 20 % too
     * little to an exempt buyer, or 20 % too much when the client goes the
     * other way.
     *
     * A line whose price was typed HT has no [InvoiceLine.ttcPrice] and is
     * returned untouched: an HT price does not move when the buyer changes.
     */
    fun repriceTtcLines(lines: List<InvoiceLine>, vatPercent: Double): List<InvoiceLine> =
        lines.map { withExemption(it, it.taxExempt, vatPercent) }

    /**
     * [line] with [exempt] set, and its price read again if it was quoted TTC.
     *
     * Exempting a line does not change the number the customer is told — it
     * changes whether that number contains tax. `150 DH TTC` becomes `150 DH`
     * with no TVA, not `125 DH`; and a line that stops being exempt goes back
     * to `125 + 25`. The typed figure is on the line, so the answer is read
     * again rather than left at whichever rate happened to be in force when it
     * was written.
     */
    fun withExemption(line: InvoiceLine, exempt: Boolean, vatPercent: Double): InvoiceLine {
        val typed = line.ttcPrice ?: return line.copy(taxExempt = exempt)
        val rate = if (exempt) 0.0 else vatPercent
        return line.copy(taxExempt = exempt, unitPrice = excludingVat(typed, rate))
    }
}
