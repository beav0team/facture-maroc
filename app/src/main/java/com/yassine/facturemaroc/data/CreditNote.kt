package com.yassine.facturemaroc.data

/**
 * Turning an invoice into the credit note that cancels it.
 *
 * The copy is deliberately literal: same lines, same prices, same rates, same
 * discount and surcharge. An avoir is the *reverse* of a document, not a new
 * negotiation — a shop that corrected a price while crediting would leave two
 * papers that no longer add up to zero, which is exactly what an accountant
 * checks first.
 *
 * What is dropped is everything that belongs to the money that actually moved:
 * the payment (it stays on the invoice), the deadline (a credit is not chased)
 * and the quote's validity date. The invoice number is kept as the reference so
 * the two documents name each other.
 */
object CreditNote {

    /** The draft avoir that reverses [record], or null if it cannot be credited. */
    fun from(record: InvoiceRecord): InvoiceRecord? {
        if (!record.data.documentType.canBeCredited) return null
        return record.copy(
            data = record.data.copy(
                documentType = DocumentType.AVOIR,
                referenceNumber = record.data.number,
                payment = null,
                dueDate = null,
                validityDate = null,
            ),
        )
    }
}
