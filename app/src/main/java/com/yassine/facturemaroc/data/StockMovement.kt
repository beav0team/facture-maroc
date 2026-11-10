package com.yassine.facturemaroc.data

/**
 * What issuing a document does to the shelf.
 *
 * Kept as a pure function over a list, with no file and no Android in sight, for
 * one reason: this is the arithmetic a shop will argue with. "I sold twelve, why
 * does it say nine?" has to be answerable by a test rather than by reading a
 * screen and hoping.
 *
 * The rule is deliberately narrow. A document that records goods leaving the
 * shop takes them out of stock; a credit note puts them back; everything else
 * leaves the shelf alone. A devis is a question, a proforma is an estimate and
 * an acompte is money — none of them is an article leaving the shelf, and a stock
 * that drops when a quote is written is a stock the shop stops trusting.
 */
object StockMovement {

    /**
     * The direction one issued document moves stock: -1 out, +1 back, 0 nowhere.
     *
     * TICKET is a sale like any other, only printed narrow. REGULARISATION
     * corrects an earlier invoice's figures rather than moving goods, so it is
     * not counted here — a correction that silently restocked would make the two
     * documents disagree.
     */
    fun direction(type: DocumentType): Int = when (type) {
        DocumentType.FACTURE,
        DocumentType.TICKET,
        DocumentType.SIMPLIFIEE,
        DocumentType.EXPORT,
        -> -1

        DocumentType.AVOIR -> +1

        DocumentType.DEVIS,
        DocumentType.BON_DE_COMMANDE,
        DocumentType.PROFORMA,
        DocumentType.ACOMPTE,
        DocumentType.REGULARISATION,
        -> 0
    }

    /** One article's actual signed change, ready to be written in the ledger. */
    data class Move(val item: SavedItem, val units: Int)

    /**
     * The quantity each article is sold in [data], in the order of [items].
     *
     * A line written since ids existed carries [InvoiceLine.itemId] and matches
     * that article even after a rename. A line written before, or typed by hand,
     * has no id and falls back to its description — which is exactly the link the
     * migration has already tried to make permanent. A line matching nothing moves
     * nothing: the invoice stays correct, it just cannot update a row that is gone.
     *
     * Two lines of the same product on one invoice are summed before they are
     * applied, so the same article added as two separate lines costs two units,
     * not one.
     */
    private fun soldQuantities(items: List<SavedItem>, data: InvoiceData): List<Double> {
        val sold = HashMap<String, Double>()
        data.lines.forEach { line ->
            val id = line.itemId?.takeIf { it.isNotBlank() }
            val key = if (id != null) {
                "id:$id"
            } else {
                "label:${line.description.trim().lowercase()}"
            }
            sold.merge(key, line.quantity, Double::plus)
        }
        return items.map { item ->
            val byId = item.id.takeIf { it.isNotBlank() }?.let { sold["id:$it"] } ?: 0.0
            val byLabel = sold["label:${item.label.trim().lowercase()}"] ?: 0.0
            byId + byLabel
        }
    }

    /**
     * One signed movement per article whose shelf really changes.
     *
     * Stock is counted in whole articles while a line may sell 1,5 m of cable, so
     * the line's quantity is rounded to the nearest unit — not floored, not
     * silently treated as a decimal stock: an article on a shelf is countable,
     * and 2,5 sold means three left the shelf's ledger.
     *
     * [reversing] inverts the movement: used when an issued document is deleted
     * from the history and the shop wants its shelf back as it was.
     */
    fun moves(
        items: List<SavedItem>,
        data: InvoiceData,
        reversing: Boolean = false,
    ): List<Move> {
        val direction = direction(data.documentType)
        if (direction == 0) return emptyList()
        val signed = if (reversing) -direction else direction
        val sold = soldQuantities(items, data)
        return items.mapIndexedNotNull { index, item ->
            if (item.stock == null) return@mapIndexedNotNull null
            val units = Math.round(sold[index]).toInt()
            if (units <= 0) return@mapIndexedNotNull null
            Move(item, signed * units)
        }
    }

    /**
     * [items] with the quantities of [data] taken out of the ones that count.
     *
     * The pure arithmetic behind [SavedItemStore.applyStockMovement], kept here
     * so the same rule can be checked without a file. [moves] is the authoritative
     * list of what changed; this only applies it.
     */
    fun apply(
        items: List<SavedItem>,
        data: InvoiceData,
        reversing: Boolean = false,
    ): List<SavedItem> {
        val direction = direction(data.documentType)
        if (direction == 0) return items
        val signed = if (reversing) -direction else direction
        val sold = soldQuantities(items, data)
        return items.mapIndexed { index, item ->
            val stock = item.stock ?: return@mapIndexed item
            val units = Math.round(sold[index]).toInt()
            if (units <= 0) item else item.copy(stock = stock + signed * units)
        }
    }

    /** The items [apply] would change, for showing the shop what moved. */
    fun changed(items: List<SavedItem>, data: InvoiceData, reversing: Boolean = false): List<SavedItem> {
        return moves(items, data, reversing).map { it.item }
    }
}
