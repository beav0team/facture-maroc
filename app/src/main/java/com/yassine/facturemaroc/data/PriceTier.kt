package com.yassine.facturemaroc.data

/**
 * A quantity price tier for an article.
 *
 * When the shop sells at least [fromQuantity] units, the unit price becomes
 * [unitPriceHt] instead of the article's base [SavedItem.price]. Tiers are
 * checked at invoice time: the greatest tier whose `fromQuantity` is not above
 * the line's quantity wins. Only whole quantities take part — selling by the
 * metre means 2,5 m has no tier boundary to cross.
 *
 * Prices are HT, in dirhams, like everything else on a document.
 */
data class PriceTier(
    /** From this quantity (inclusive), the tier price applies. */
    val fromQuantity: Int,
    /** The unit price, HT, in dirhams, once this quantity is reached. */
    val unitPriceHt: Double,
)
