package com.yassine.facturemaroc.data

/**
 * How the app values the articles on the shelf.
 *
 * Stored as a short id, so a wording change never rewrites what a shop chose.
 * The method never touches an issued document: every issued line keeps the cost
 * frozen on it at the moment it was written, and switching here only changes
 * the cost of movements from now on. That is why a shop can try FIFO for a
 * month and go back without a single invoice changing.
 */
enum class CostMethod(val id: String, val label: String, val description: String) {
    /**
     * Weighted average: every unit on the shelf is worth the same.
     *
     * The default, because it is the method a shop without an accountant can
     * explain: "I paid 3 000 for the ten I have, so each is worth 300".
     */
    CMP(
        id = "cmp",
        label = "Coût moyen pondéré",
        description = "Chaque unité vaut la moyenne de ce qui est entré. Le plus simple à expliquer.",
    ),

    /**
     * First in, first out: the oldest delivery leaves first.
     *
     * The method that matches a shelf where the boxes are rotated, and the one
     * that reports the true cost of a sale when prices move a lot.
     */
    FIFO(
        id = "fifo",
        label = "Premier entré, premier sorti (FIFO)",
        description = "La livraison la plus ancienne sort en premier ; chaque couche garde son prix.",
    ),

    /** No costing at all: quantities only, no value and no margin. */
    NONE(
        id = "none",
        label = "Aucune valorisation",
        description = "Uniquement les quantités : ni coût, ni marge, ni valeur du stock.",
    ),
    ;

    companion object {
        /** The method a shop gets until it chooses another one. */
        val DEFAULT: CostMethod = CMP

        /** An unknown id (a file written by a later build) reads as the default. */
        fun fromId(id: String?): CostMethod =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * One article's shelf, reduced to a quantity, a unit cost and a value.
 *
 * [unitCost] is null when the method is [CostMethod.NONE] or when nothing
 * inbound ever carried a price. [value] is then 0, never a number invented from
 * a sale price.
 *
 * [negative] is a warning, not a failure: a shop can sell three of something it
 * recorded two of, and the honest answer is "minus one, at the last known cost"
 * rather than a crash or a floor at zero.
 */
data class StockValuation(
    val quantity: Int,
    val unitCost: Double?,
    val value: Double,
    val negative: Boolean = false,
)

/**
 * The cost of what is on the shelf, from the ledger alone.
 *
 * Pure and chronological: the entries are replayed in the order they happened,
 * so the same history always produces the same answer and a method change is a
 * new reading of the same facts rather than a rewrite of them. A sale consumes
 * the layers implicitly — this replay is what "consuming a layer" means for a
 * ledger that only stores deltas.
 *
 * Divides only after checking the quantity is above zero, so a shelf that is
 * empty or short can never produce a NaN.
 */
object StockCost {

    /** The valuation of [entries] (chronological) under [method]. */
    fun valuation(entries: List<StockEntry>, method: CostMethod): StockValuation =
        when (method) {
            CostMethod.NONE -> StockValuation(
                quantity = entries.sumOf { it.delta },
                unitCost = null,
                value = 0.0,
            )

            CostMethod.CMP -> weightedAverage(entries)
            CostMethod.FIFO -> fifo(entries)
        }

    /** The unit cost of one article, or null when it is not known. */
    fun unitCost(entries: List<StockEntry>, method: CostMethod): Double? =
        valuation(entries, method).unitCost

    /**
     * Weighted average over the movements.
     *
     * An inbound with no price does not dilute the average: the goods are real
     * and countable, but their cost is unknown, so the shelf keeps the average
     * it had. An outbound leaves at the running average, which is exactly what
     * a weighted-average inventory does. When the shelf goes negative the last
     * average is kept — it is the best estimate anyone has — and the valuation
     * is flagged.
     */
    private fun weightedAverage(entries: List<StockEntry>): StockValuation {
        var quantity = 0
        var value = 0.0
        var average = 0.0
        var known = false

        entries.forEach { entry ->
            if (entry.delta > 0) {
                if (entry.unitCost > 0.0) {
                    value += entry.delta * entry.unitCost
                    quantity += entry.delta
                    average = if (quantity > 0) value / quantity else entry.unitCost
                    known = true
                } else {
                    // Unknown cost: the quantity arrives, the value does not
                    // move, and the average is left as the last true figure.
                    quantity += entry.delta
                }
            } else if (entry.delta < 0) {
                val outgoing = -entry.delta
                quantity -= outgoing
                if (known && average > 0.0) value -= outgoing * average
                // A negative shelf is valued at the last known cost, so the
                // number does not quietly turn into a credit.
                if (quantity <= 0) value = quantity * average
            }
        }

        val negative = quantity < 0
        val cost = when {
            !known || average <= 0.0 -> null
            else -> round(average)
        }
        return StockValuation(
            quantity = quantity,
            unitCost = cost,
            value = if (cost == null) 0.0 else round(quantity * cost),
            negative = negative,
        )
    }

    /**
     * First in, first out: the oldest priced layer leaves first.
     *
     * A layer with no price still occupies a quantity — the goods are there —
     * but contributes nothing to the value. Outbound movements consume the
     * queue from the front; a shelf that runs short keeps the cost of the last
     * layer it actually consumed.
     */
    private fun fifo(entries: List<StockEntry>): StockValuation {
        val layers = ArrayDeque<Pair<Int, Double>>()
        var knownCost = 0.0
        var known = false
        var soldBeyondStock = false
        var lastConsumed = 0.0

        entries.forEach { entry ->
            if (entry.delta > 0) {
                val unitCost = entry.unitCost.coerceAtLeast(0.0)
                layers.addLast(entry.delta to unitCost)
                if (unitCost > 0.0) {
                    knownCost = unitCost
                    known = true
                }
            } else if (entry.delta < 0) {
                var remaining = -entry.delta
                while (remaining > 0 && layers.isNotEmpty()) {
                    val (quantity, cost) = layers.removeFirst()
                    val used = minOf(quantity, remaining)
                    if (cost > 0.0) {
                        lastConsumed = cost
                        knownCost = cost
                        known = true
                    }
                    remaining -= used
                    if (quantity > used) layers.addFirst((quantity - used) to cost)
                }
                if (remaining > 0) soldBeyondStock = true
            }
        }

        val quantity = entries.sumOf { it.delta }
        val pricedUnits = layers.filter { it.second > 0.0 }.sumOf { it.first }
        val layerValue = layers.sumOf { (units, cost) -> if (cost > 0.0) units * cost else 0.0 }
        val cost = when {
            pricedUnits > 0 && layerValue > 0.0 -> round(layerValue / pricedUnits)
            !known -> null
            lastConsumed > 0.0 -> round(lastConsumed)
            else -> round(knownCost)
        }
        return StockValuation(
            quantity = quantity,
            unitCost = cost,
            value = when {
                cost == null -> 0.0
                // A shelf that ran short is valued at the cost of its last
                // layer for the missing units too, so the figure is negative
                // rather than a free lunch.
                quantity < 0 -> round(quantity * cost)
                else -> round(layerValue)
            },
            negative = soldBeyondStock || quantity < 0,
        )
    }

    private fun round(value: Double): Double =
        if (value.isNaN() || value.isInfinite()) 0.0 else Math.round(value * 100.0) / 100.0
}
