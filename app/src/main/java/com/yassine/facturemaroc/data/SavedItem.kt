package com.yassine.facturemaroc.data

/**
 * Something the shop sells: a product it keeps on a shelf, or a service it
 * performs.
 *
 * The two live in the same list on purpose. A tyre shop bills "Montage" beside
 * the tyre it fitted, and putting them in separate screens would mean deciding
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
     * Searchable, so the normal way to find a tyre is to type the digits from
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
) {
    /** Whether this is a countable thing rather than a service. */
    val tracksStock: Boolean get() = stock != null

    /** Stock at or below zero, where the next sale is a promise the shop cannot keep. */
    val outOfStock: Boolean get() = stock != null && stock <= 0

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
     * The saved item as an invoice line.
     *
     * The label goes in the line's description with the price copied into the
     * line. Raising an item's price must not retroactively change an invoice
     * that is already half written, so the number is settled when the line is
     * made.
     */
    fun toLine(quantity: Int = 1): InvoiceLine = InvoiceLine(
        description = label.trim(),
        quantity = quantity,
        unitPrice = price,
        unit = unit,
    )
}
