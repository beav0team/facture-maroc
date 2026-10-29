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
 * an acompte is money — none of them is a tyre leaving the building, and a stock
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

    /**
     * [items] with the quantities of [data] taken out of the ones that count.
     *
     * Lines are matched to items by label, ignoring case and surrounding space.
     * The label is the only link an invoice line has back to the catalogue, and
     * it is a reliable one because [SavedItemStore.save] refuses to hold two
     * items under the same label. A line that no longer matches anything — the
     * item was renamed or deleted after the draft was written — moves nothing;
     * the invoice is still correct, it just cannot update a row that is gone.
     *
     * Two lines of the same product on one invoice are summed before they are
     * applied, so a tyre and a spare added as separate lines cost two units and
     * not one.
     *
     * [reversing] inverts the movement: used when an issued document is deleted
     * from the history and the shop wants its shelf back as it was.
     */
    fun apply(
        items: List<SavedItem>,
        data: InvoiceData,
        reversing: Boolean = false,
    ): List<SavedItem> {
        val direction = direction(data.documentType)
        if (direction == 0) return items
        val signed = if (reversing) -direction else direction

        val sold = data.lines
            .groupBy { it.description.trim().lowercase() }
            .mapValues { (_, lines) -> lines.sumOf { it.quantity } }

        return items.map { item ->
            val quantity = sold[item.label.trim().lowercase()]
            val stock = item.stock
            // A service (stock == null) is never counted, and a line that
            // matches no item is not this function's business.
            if (quantity == null || stock == null || quantity <= 0) {
                item
            } else {
                item.copy(stock = stock + signed * quantity)
            }
        }
    }

    /** The items [apply] would change, for showing the shop what moved. */
    fun changed(items: List<SavedItem>, data: InvoiceData, reversing: Boolean = false): List<SavedItem> {
        val after = apply(items, data, reversing)
        return after.filterIndexed { index, item -> item != items[index] }
    }
}
