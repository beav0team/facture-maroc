package com.yassine.facturemaroc.data

/**
 * The documents that count as money billed in a month.
 *
 * A devis is a price the shop offered, and a bon de commande is something the
 * shop is buying — neither is revenue, so including them in "Facturé ce mois-ci"
 * would show the shop a number it never invoiced. A ticket is a facture
 * simplifiée and does count, and so do the simplified invoice and the export
 * invoice: they are invoices with a different heading, not a different nature.
 * Drafts never count: nothing was handed over yet.
 *
 * An avoir is not here either, because it is not a document billed — it is a
 * document *unbilled*. [billedTotalInMonth] takes it off the total; listing it
 * as a facture would make the counter say two invoices were issued where the
 * shop issued one and cancelled it.
 *
 * Acompte and régularisation are left out for a different reason: both carry a
 * reference to another document ("annule et remplace la facture N° …"), and the
 * app cannot tell whether the invoice they adjust is in the same month. Adding
 * one could count the same sale twice, which is the one error a monthly total
 * must not make.
 */
fun List<InvoiceRecord>.billedInMonth(monthStamp: String): List<InvoiceRecord> = filter { record ->
    !record.isDraft &&
        record.data.documentType in BILLED_DOCUMENTS &&
        record.data.date.endsWith(monthStamp)
}

/** The credit notes of the month: money given back, not money billed. */
fun List<InvoiceRecord>.creditedInMonth(monthStamp: String): List<InvoiceRecord> = filter { record ->
    !record.isDraft &&
        record.data.documentType == DocumentType.AVOIR &&
        record.data.date.endsWith(monthStamp)
}

/**
 * What the month is really worth: the documents billed, less the credit notes.
 *
 * A shop that issues a facture of 500 DH and an avoir of 500 DH for it invoiced
 * nothing that month, and a total that still says 500 would be wrong in the one
 * direction a shop notices at the end of the month.
 */
fun List<InvoiceRecord>.billedTotalInMonth(monthStamp: String): Double {
    val billed = billedInMonth(monthStamp).sumOf { it.data.total }
    val credited = creditedInMonth(monthStamp).sumOf { it.data.total }
    return Math.round((billed - credited) * 100.0) / 100.0
}

private val BILLED_DOCUMENTS = setOf(
    DocumentType.FACTURE,
    DocumentType.TICKET,
    DocumentType.SIMPLIFIEE,
    DocumentType.EXPORT,
)
