package com.yassine.facturemaroc.data

/**
 * Something the shop sells: a product it keeps on a shelf, or a service it
 * performs.
 *
 * The two live in the same list on purpose. A shop bills "Montage" beside the
 * article it assembled, and putting them in separate screens would mean deciding
 * which screen to open before knowing whether the thing is countable. The
 * difference is carried by [stock]: a product has a quantity, a service has
 * `null` and is never counted or deducted.
 *
 * Prices are held the way the shop quotes them — [price] follows
 * [ShopSettings.pricesIncludeVat] and is converted once, at the moment it goes
 * on an invoice (see [InvoiceViewModel.inShopPriceBasis]). [purchasePrice] is
 * always HT, because a supplier's invoice is HT.
 */
data class SavedItem(
    /** What it is called on the invoice. The only required field. */
    val label: String,
    /**
     * The price for one of them, before any discount.
     *
     * In the shop's own basis: TTC when it sells TTC, HT when it sells HT. The
     * invoice always stores HT, so this is converted on the way in.
     */
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
    /**
     * What the shop paid for it, HT. 0 means "not filled in", not "free".
     *
     * Kept apart from [price] because the two answer different questions: the
     * sale price is what goes on the invoice, the purchase price is what tells
     * the shop whether the sale was worth making.
     */
    val purchasePrice: Double = 0.0,
    /**
     * The barcode, typed or read off the box.
     *
     * Stored as text rather than a number: leading zeroes are part of an EAN,
     * and a 13-digit code does not fit a phone's integer habits comfortably.
     * Searchable, so the normal way to find an article is to type the digits from
     * the label.
     */
    val barcode: String = "",
    /**
     * Units on the shelf, or `null` for something that cannot be counted.
     *
     * `null` is a service — an hour of labour has no quantity to deduct, and
     * giving it a stock of zero would make it look sold out. Negative is a real
     * value: it says more was sold than was recorded, which is exactly what the
     * shop needs to see rather than a number quietly floored at zero.
     */
    val stock: Int? = null,
    /**
     * The quantity at which the shop wants to be told to reorder.
     *
     * `null` is "no threshold set", which is different from `0`: zero would
     * say "order when it is already out", and a shop that has not thought
     * about it should not be nagged. The comparison is `stock <= threshold`,
     * because a threshold of 2 means the third one sold leaves a shelf that
     * needs refilling.
     */
    val reorderThreshold: Int? = null,
    /**
     * The stable name of this article inside the app.
     *
     * The label used to be the key, which meant a rename broke every link an
     * invoice line had back to the shelf: the line still printed the old name,
     * but nothing could tell that it was the same article, so a stock movement
     * after a rename silently did nothing. A UUID survives a rename, and the
     * same id is copied onto every [InvoiceLine] the article produces.
     *
     * `""` means "not assigned yet": files written before this field existed,
     * and items built by a test. [SavedItemStore] assigns an id before it
     * writes an item, so a blank id is never a value the shop can see twice.
     *
     * Declared last on purpose: every constructor that predates it passes
     * label, price and optionals positionally, and moving it to the front would
     * have silently turned a label into an id.
     */
    val id: String = "",
    /**
     * Quantity price tiers, from cheapest to most expensive quantity.
     *
     * Absent (empty) means the single [price] always applies. When tiers exist,
     * [tierPrice] picks the greatest tier whose `fromQuantity` does not exceed
     * the line's quantity; decimal quantities fall back to [price] because a
     * tier boundary only makes sense on whole articles.
     */
    val priceTiers: List<PriceTier> = emptyList(),
    /**
     * Free key/value attributes, carried onto every invoice line this article
     * produces.
     *
     * Keys are neutral suggestions (Référence, Marque, Modèle, Dimensions, Lot
     * / série…); the shop may add any key/value pair. Nothing is industry
     * specific.
     */
    val attributes: List<ItemAttribute> = emptyList(),
) {
    /** Whether this is a countable thing rather than a service. */
    val tracksStock: Boolean get() = stock != null

    /** Stock at or below zero, where the next sale is a promise the shop cannot keep. */
    val outOfStock: Boolean get() = stock != null && stock <= 0

    /**
     * Whether this article has reached the level the shop chose to reorder at.
     *
     * A service and an article with no threshold both answer false: one has
     * nothing to count, the other has never been given a number to count to.
     */
    val needsReorder: Boolean get() = stock != null && reorderThreshold != null && stock <= reorderThreshold

    /**
     * What one unit earns before TVA, or `null` when the purchase price is not
     * known or the two prices are not on the same basis.
     *
     * Returns null rather than an approximate figure when the shop quotes TTC,
     * because comparing a TTC sale price with an HT purchase price would report
     * a margin 20 % better than the real one.
     */
    fun margin(salePriceExcludingVat: Double): Double? =
        if (purchasePrice > 0.0) salePriceExcludingVat - purchasePrice else null

    /**
     * The unit price for [quantity] units, honouring the quantity price tiers.
     *
     * Only whole quantities take part: selling by the metre means 2,5 m has no
     * tier boundary to cross, so the base [price] stands. Returns the greatest
     * tier whose `fromQuantity` does not exceed [quantity], or [price] when no
     * tier matches.
     */
    fun tierPrice(quantity: Double): Double {
        // Decimal quantities fall back to the base price: a tier boundary only
        // makes sense on whole articles.
        if (quantity != quantity.toInt().toDouble()) return price
        val qty = quantity.toInt()
        return priceTiers
            .filter { it.fromQuantity <= qty }
            .maxByOrNull { it.fromQuantity }
            ?.unitPriceHt ?: price
    }

    /**
     * The saved item as an invoice line.
     *
     * The label goes in the line's description with the price copied into the
     * line. Raising an item's price must not retroactively change an invoice
     * that is already half written, so the number is settled when the line is
     * made.
     */
    fun toLine(quantity: Double = 1.0): InvoiceLine = InvoiceLine(
        description = label.trim(),
        quantity = quantity,
        unitPrice = tierPrice(quantity),
        unit = unit,
        // Freeze the tier price when a tier matches: the document keeps what the
        // shelf offered at the moment the line was written, even if the shop
        // later edits the tiers or raises the base price.
        tierFromQuantity = matchingTier(quantity)?.fromQuantity,
        appliedTierPrice = matchingTier(quantity)?.unitPriceHt,
        // The link back to the shelf. A blank id (a test, or a catalogue entry
        // that somehow skipped the store) leaves it null so the line is still a
        // valid document; the label fallback in StockMovement handles it.
        itemId = id.takeIf { it.isNotBlank() },
        lineAttributes = attributes,
    )

    /** The tier that applies at [quantity], or null when none matches. */
    private fun matchingTier(quantity: Double): PriceTier? {
        if (quantity != quantity.toInt().toDouble()) return null
        val qty = quantity.toInt()
        return priceTiers
            .filter { it.fromQuantity <= qty }
            .maxByOrNull { it.fromQuantity }
    }
}
