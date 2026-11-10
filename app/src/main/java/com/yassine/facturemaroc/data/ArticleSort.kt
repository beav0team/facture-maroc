package com.yassine.facturemaroc.data

/**
 * The orders a shop asks its catalogue in.
 *
 * The catalogue used to arrive alphabetically and could not be reordered. That
 * is the right default — a shop looks for a name it knows — but it is the wrong
 * only answer: "what is worth the most on the shelf" and "what do I need to
 * reorder" are questions the same list has to answer without a search.
 */
enum class ArticleSort(val label: String) {
    /** A→Z, ignoring case and accents' case, which is the default. */
    NAME("Nom"),

    /** Most expensive first: what earns the most per sale. */
    PRICE("Prix"),

    /** Least in stock first: what to reorder, services last. */
    STOCK("Stock"),

    /** Best margin first, articles with no purchase price last. */
    MARGIN("Marge"),
}

/**
 * Sorts [items] for display. Stable in the sense that every order ends on the
 * name, so two articles at the same price do not swap places on each redraw.
 */
fun sortArticles(items: List<SavedItem>, sort: ArticleSort): List<SavedItem> =
    when (sort) {
        ArticleSort.NAME -> items.sortedWith(byName)

        ArticleSort.PRICE -> items.sortedWith(
            compareByDescending<SavedItem> { it.price }.then(byName),
        )

        // A product with two left comes before one with forty. A service has no
        // count at all, so it cannot be "out of stock" and belongs at the end
        // rather than at the top as zero.
        ArticleSort.STOCK -> items.sortedWith(
            compareBy<SavedItem> { it.stock == null }.thenBy { it.stock ?: 0 }.then(byName),
        )

        ArticleSort.MARGIN -> items.sortedWith(
            compareBy<SavedItem> { marginOrNull(it) == null }
                .thenByDescending { marginOrNull(it) ?: 0.0 }
                .then(byName),
        )
    }

/** What one sale leaves, or null when the shop never filled the cost in. */
private fun marginOrNull(item: SavedItem): Double? =
    if (item.purchasePrice > 0.0) item.price - item.purchasePrice else null

private val byName: Comparator<SavedItem> =
    compareBy(String.CASE_INSENSITIVE_ORDER) { item: SavedItem -> item.label }
