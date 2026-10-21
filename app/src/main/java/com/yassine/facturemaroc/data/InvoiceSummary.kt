package com.yassine.facturemaroc.data

/**
 * The documents that count as money billed in a month.
 *
 * A devis is a price the shop offered, and a bon de commande is something the
 * shop is buying — neither is revenue, so including them in "Facturé ce mois-ci"
 * would show the shop a number it never invoiced. A ticket is a facture
 * simplifiée and does count. Drafts never do: nothing was handed over yet.
 */
fun List<InvoiceRecord>.billedInMonth(monthStamp: String): List<InvoiceRecord> = filter { record ->
    !record.isDraft &&
        record.data.documentType in BILLED_DOCUMENTS &&
        record.data.date.endsWith(monthStamp)
}

private val BILLED_DOCUMENTS = setOf(DocumentType.FACTURE, DocumentType.TICKET)
