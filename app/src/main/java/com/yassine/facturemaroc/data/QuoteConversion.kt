package com.yassine.facturemaroc.data

/**
 * Turning an accepted quote into the invoice that bills it.
 *
 * A quote is a price the shop offered; the invoice is the same sale, so the
 * lines, prices, VAT rates, discount and surcharge travel unchanged — retyping
 * them is how a customer ends up invoiced at a figure they never accepted.
 *
 * What changes is only what belongs to each document's life: the quote's
 * validity date goes (a quote that became an invoice no longer expires), the
 * deadline is left for the shop to state, and the invoice keeps the quote's
 * number as its reference so the customer can match the two papers.
 */
object QuoteConversion {

    /** The invoice draft built from [record], or null if it is not a quote. */
    fun invoiceFrom(record: InvoiceRecord): InvoiceRecord? {
        if (record.data.documentType != DocumentType.DEVIS) return null
        return record.copy(
            data = record.data.copy(
                documentType = DocumentType.FACTURE,
                referenceNumber = record.data.number,
                validityDate = null,
                dueDate = null,
            ),
        )
    }
}
