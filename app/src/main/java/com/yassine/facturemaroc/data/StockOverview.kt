package com.yassine.facturemaroc.data

/**
 * One article as the stock tab shows it: what is on the shelf, what it is
 * worth, and whether it needs reordering.
 *
 * The quantity comes from the journal, not from `SavedItem.stock`: the field is
 * a cache the app writes after every movement, and the journal is the thing
 * that can explain itself. When the two disagree, [StockOverview.drift] says so
 * instead of one of them quietly winning.
 */
data class StockLine(
    val item: SavedItem,
    /** What the journal says is on the shelf. Can be negative: a shop can sell
     *  a tyre that arrived this morning and was never received here. */
    val onShelf: Int,
    /** [onShelf] at the article's unit cost, or 0 when no cost is known. */
    val value: Double,
    val reorder: Boolean,
)

/**
 * The shelves in one picture.
 *
 * Ordered the way the tab is used: what has to be reordered first (emptiest
 * first), then everything else alphabetically. A service is not on a shelf and
 * never appears here — it has no quantity to count or to run out of.
 */
data class StockOverview(
    val lines: List<StockLine> = emptyList(),
    /** Units across every counted article. */
    val units: Int = 0,
    /** What those units are worth at the shop's costing method. */
    val value: Double = 0.0,
    /** How many articles are at or below their threshold. */
    val toReorder: Int = 0,
    /** Articles whose cached quantity disagrees with the journal. */
    val drift: Int = 0,
)

fun stockOverview(
    items: List<SavedItem>,
    balances: Map<String, Int>,
    unitCosts: Map<String, Double> = emptyMap(),
): StockOverview {
    val counted = items.filter { it.stock != null }
    val lines = counted.map { item ->
        val onShelf = balances[item.id] ?: 0
        val cost = unitCosts[item.id] ?: item.purchasePrice.takeIf { it > 0.0 } ?: 0.0
        StockLine(
            item = item,
            onShelf = onShelf,
            value = cost * onShelf,
            // The threshold decides, not the cached quantity: they are the same
            // number when nothing has drifted, and the article is the one the
            // shop set the threshold on.
            reorder = item.needsReorder,
        )
    }

    val ordered = lines.sortedWith(
        compareBy<StockLine> { !it.reorder }
            .thenBy { if (it.reorder) it.onShelf else 0 }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.item.label },
    )

    return StockOverview(
        lines = ordered,
        units = lines.sumOf { it.onShelf },
        value = lines.sumOf { it.value },
        toReorder = lines.count { it.reorder },
        drift = counted.count { (balances[it.id] ?: 0) != it.stock },
    )
}
