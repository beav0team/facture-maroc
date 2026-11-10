package com.yassine.facturemaroc.data

/**
 * What a period, a document or an article earned once the goods are paid for.
 *
 * [revenueHt] is the sum of the line totals after both discounts: each line's
 * own, and the document-level one spread over the lines in proportion to what
 * they are worth (see [InvoiceData.keepFactor]). Leaving the document discount
 * out used to report a margin on money the shop never collected. [cost] is the
 * sum of `quantity × unitCostHt` over the lines whose cost is known; a line with
 * no frozen cost contributes nothing to either side and is counted in
 * [linesWithoutCost], so a margin can never be quietly inflated by treating an
 * unknown cost as zero.
 */
data class MarginTotals(
    val revenueHt: Double,
    val cost: Double,
    /** Lines that carry a frozen cost. */
    val linesWithCost: Int = 0,
    /** Lines whose cost is unknown: excluded from [cost], never guessed. */
    val linesWithoutCost: Int = 0,
) {
    val margin: Double get() = round(revenueHt - cost)

    /** Margin as a share of revenue, or null when nothing was billed. */
    val marginPercent: Double?
        get() = if (revenueHt > 0.0) round((revenueHt - cost) / revenueHt * 100.0) else null

    /** How many lines there are in total, costed or not. */
    val lines: Int get() = linesWithCost + linesWithoutCost

    /** Share of lines with no cost, for the warning under a margin block. */
    val shareWithoutCost: Double
        get() = if (lines == 0) 0.0 else round(linesWithoutCost * 100.0 / lines)

    operator fun plus(other: MarginTotals) = MarginTotals(
        revenueHt = revenueHt + other.revenueHt,
        cost = cost + other.cost,
        linesWithCost = linesWithCost + other.linesWithCost,
        linesWithoutCost = linesWithoutCost + other.linesWithoutCost,
    )

    operator fun minus(other: MarginTotals) = MarginTotals(
        revenueHt = revenueHt - other.revenueHt,
        cost = cost - other.cost,
        linesWithCost = linesWithCost - other.linesWithCost,
        linesWithoutCost = linesWithoutCost - other.linesWithoutCost,
    )

    companion object {
        val ZERO = MarginTotals(0.0, 0.0)

        private fun round(value: Double): Double =
            if (value.isNaN() || value.isInfinite()) 0.0 else Math.round(value * 100.0) / 100.0
    }
}

/** One article's share of what was billed, for the margin list. */
data class ItemMargin(
    val itemId: String,
    val label: String,
    val totals: MarginTotals,
)

/**
 * Margin arithmetic, kept pure and away from any screen.
 *
 * The cost comes only from [InvoiceLine.unitCostHt], the number frozen when the
 * document was issued. That is what makes the report stable: a purchase entered
 * next month changes the shelf, never a closed invoice.
 */
object MarginReport {

    /** One document's margin, drafts included: the caller decides. */
    fun forDocument(data: InvoiceData): MarginTotals {
        // The document discount is a discount on every line, so it has to be
        // carried into the revenue; a fixed -200 DH is not attributable to one
        // article, which is why it is spread rather than subtracted once.
        val keep = data.keepFactor
        return data.lines.fold(MarginTotals.ZERO) { sum, line -> sum + forLine(line, keep) }
    }

    /** An issued document's margin; null for a draft that billed nothing. */
    fun forRecord(record: InvoiceRecord): MarginTotals? =
        if (record.isDraft) null else forDocument(record.data)

    /**
     * The month's margin, credit notes taken off both sides.
     *
     * An avoir reverses a sale and its cost together. Subtracting only the
     * revenue would report a margin below cost for a month where everything was
     * credited, which is exactly the kind of number a shop rings its accountant
     * about.
     */
    fun forMonth(records: List<InvoiceRecord>, monthStamp: String): MarginTotals {
        val billed = records.billedInMonth(monthStamp).fold(MarginTotals.ZERO) { sum, record ->
            sum + forDocument(record.data)
        }
        val credited = records.creditedInMonth(monthStamp).fold(MarginTotals.ZERO) { sum, record ->
            sum + forDocument(record.data)
        }
        return billed - credited
    }

    /**
     * One row per article over the issued documents given.
     *
     * Grouped by the stable id when the line has one, and by its printed label
     * otherwise — that fallback is how lines written before ids existed still
     * earn a row instead of vanishing from the report.
     */
    fun byItem(records: List<InvoiceRecord>): List<ItemMargin> {
        val rows = LinkedHashMap<String, ItemMargin>()
        records.filterNot { it.isDraft }.forEach { record ->
            val keep = record.data.keepFactor
            record.data.lines.forEach { line ->
                val key = line.itemId?.takeIf { it.isNotBlank() }
                    ?: "label:${line.description.trim().lowercase()}"
                val current = rows[key]
                val add = forLine(line, keep)
                rows[key] = ItemMargin(
                    itemId = line.itemId.orEmpty(),
                    label = current?.label ?: line.description.trim(),
                    totals = (current?.totals ?: MarginTotals.ZERO) + add,
                )
            }
        }
        return rows.values.sortedByDescending { it.totals.revenueHt }
    }

    /**
     * The margin of one issued line, used by [byItem] and by a single article.
     *
     * [keep] is the document's share still standing after its discount; it only
     * touches the revenue, since the cost of the goods does not drop because the
     * shop gave a rebate.
     */
    fun forLine(line: InvoiceLine, keep: Double = 1.0): MarginTotals {
        val cost = line.unitCostHt
        val revenue = line.total * keep
        return if (cost == null) {
            MarginTotals(revenue, 0.0, linesWithCost = 0, linesWithoutCost = 1)
        } else {
            MarginTotals(revenue, line.quantity * cost, linesWithCost = 1)
        }
    }

    /** Everything billed for one article, over [records]. */
    fun forItem(records: List<InvoiceRecord>, itemId: String): MarginTotals =
        records.filterNot { it.isDraft }
            .fold(MarginTotals.ZERO) { sum, record ->
                val keep = record.data.keepFactor
                record.data.lines
                    .filter { it.itemId == itemId }
                    .fold(sum) { lineSum, line -> lineSum + forLine(line, keep) }
            }

    private fun round(value: Double): Double =
        if (value.isNaN() || value.isInfinite()) 0.0 else Math.round(value * 100.0) / 100.0
}
