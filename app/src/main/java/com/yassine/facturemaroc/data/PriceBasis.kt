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
}
