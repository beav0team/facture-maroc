package com.yassine.facturemaroc.data

/**
 * A quantity price tier for an article.
 *
 * When the shop sells at least [fromQuantity] units, the unit price becomes
 * [unitPrice] instead of the article's base [SavedItem.price]. Tiers are
 * checked at invoice time: the greatest tier whose `fromQuantity` is not above
 * the line's quantity wins. Only whole quantities take part — selling by the
 * metre means 2,5 m has no tier boundary to cross.
 *
 * The price follows [SavedItem.price] and is therefore in the shop's own basis:
 * TTC when the shop quotes TTC, HT when it quotes HT. The field used to be
 * labelled and named as HT while the value went onto the line like a shop-basis
 * price, so a TTC shop typed 100 and sold at 100 TTC — the 120 it meant. One
 * basis for the whole article removes that, and the label now follows the
 * shop's setting so the number typed is the number the customer is told.
 *
 * The JSON key was `unitPriceHt`; it is still read, because a catalogue written
 * by 0.11.0 carries it.
 */
data class PriceTier(
    /** From this quantity (inclusive), the tier price applies. */
    val fromQuantity: Int,
    /** The unit price, in the shop's basis, once this quantity is reached. */
    val unitPrice: Double,
)
