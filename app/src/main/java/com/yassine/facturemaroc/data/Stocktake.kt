package com.yassine.facturemaroc.data

/** One article on a stocktake sheet: what the app expected against what was counted. */
data class StocktakeRow(
    val itemId: String,
    val label: String,
    /** What the ledger said was on the shelf when the count started. */
    val expected: Int,
    /** What the shop actually counted. */
    val counted: Int,
    /** Purchase price, for turning the difference into money. */
    val purchasePrice: Double,
) {
    /** Positive when more was found than expected, negative when stock is missing. */
    val difference: Int get() = counted - expected

    /**
     * What the difference is worth at purchase price.
     *
     * A negative value is not hidden: a shortage is the number the shop needs to
     * see, and showing it as a gain would be the opposite of an inventory.
     */
    val differenceValue: Double get() = difference * purchasePrice
}

/** The line under a stocktake sheet, already reduced to numbers. */
data class StocktakeSummary(
    val rows: Int,
    /** Articles whose count differs from the expectation. */
    val adjusted: Int,
    val expectedUnits: Int,
    val countedUnits: Int,
    val differenceUnits: Int,
    val differenceValue: Double,
)

/**
 * A physical count, and the movements it implies.
 *
 * The count itself is not a stock value to overwrite: it is evidence of a
 * difference, and the difference is what the ledger records. Everything here is
 * pure so the arithmetic a shop will question — "I counted four, why did it
 * write three?" — is checked by a test rather than by re-reading a screen.
 */
object Stocktake {

    /**
     * The sheet for [items], taking the expectation from [expectedOf].
     *
     * The expectation comes from the ledger, not from the cached quantity: a
     * count taken on top of a cache that has drifted would preserve the drift
     * as if it were a physical fact. Only counted articles appear — a service
     * has no shelf to count, and an article the shop skipped is not a count of
     * zero.
     */
    fun rows(
        items: List<SavedItem>,
        counts: Map<String, Int>,
        expectedOf: (SavedItem) -> Int,
    ): List<StocktakeRow> = items.mapNotNull { item ->
        if (item.stock == null) return@mapNotNull null
        val expected = expectedOf(item)
        StocktakeRow(
            itemId = item.id,
            label = item.label,
            expected = expected,
            // An article left untouched keeps its expected number: the sheet is
            // a record of what was counted, and "not counted" is not "counted
            // zero".
            counted = counts[item.id] ?: expected,
            purchasePrice = item.purchasePrice,
        )
    }

    fun summary(rows: List<StocktakeRow>): StocktakeSummary = StocktakeSummary(
        rows = rows.size,
        adjusted = rows.count { it.difference != 0 },
        expectedUnits = rows.sumOf { it.expected },
        countedUnits = rows.sumOf { it.counted },
        differenceUnits = rows.sumOf { it.difference },
        differenceValue = rows.sumOf { it.differenceValue },
    )

    /**
     * The entries a validated count writes.
     *
     * One STOCKTAKE movement per article whose number changed, and nothing for
     * an article that matched: a ledger full of "+0" lines would bury the real
     * corrections. [reason] is required by the caller and copied into [StockEntry.note];
     * [reference] is the sheet's name (a date), so the history can say which
     * count a correction belongs to.
     */
    fun entries(
        rows: List<StocktakeRow>,
        reason: String,
        reference: String,
        at: Long = System.currentTimeMillis(),
    ): List<StockEntry> = rows
        .filter { it.difference != 0 }
        .map { row ->
            StockEntry(
                itemId = row.itemId,
                // The delta is the difference between what was counted and what
                // the ledger expected, which is exactly what makes the new
                // balance equal the counted number.
                delta = row.difference,
                reason = StockReason.STOCKTAKE,
                reference = reference,
                note = reason.trim(),
                at = at,
            )
        }
}
