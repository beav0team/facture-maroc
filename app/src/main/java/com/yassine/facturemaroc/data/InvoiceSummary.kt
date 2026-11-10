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

/**
 * TVA collected in the month, net of credit notes.
 *
 * Read from each document's own [InvoiceData.vatAmount], never recomputed from
 * the totals: a document printed one figure to the customer, and the report has
 * to agree with the paper even when a centime landed on an arrondi.
 */
fun List<InvoiceRecord>.vatCollectedInMonth(monthStamp: String): Double {
    val billed = billedInMonth(monthStamp).sumOf { it.data.vatAmount }
    val credited = creditedInMonth(monthStamp).sumOf { it.data.vatAmount }
    return Math.round((billed - credited) * 100.0) / 100.0
}

/**
 * The month's VAT per rate, base and tax, net of credit notes.
 *
 * This is the table an accountant asks for: the total alone does not say whether
 * the tax is 20 % on sales or 10 % on services. Rates are kept even when a rate
 * nets to zero, so a month of two 10 % sales and one 10 % credit still shows the
 * 10 % line instead of pretending the rate was never used.
 */
fun List<InvoiceRecord>.vatByRateInMonth(monthStamp: String): List<VatRecap> {
    val totals = LinkedHashMap<Double, DoubleArray>() // rate -> [base, amount]
    fun add(records: List<InvoiceRecord>, sign: Double) {
        records.forEach { record ->
            record.data.vatBreakdown.forEach { recap ->
                if (recap.rate <= 0.0) return@forEach
                val row = totals.getOrPut(recap.rate) { DoubleArray(2) }
                row[0] += sign * recap.base
                row[1] += sign * recap.amount
            }
        }
    }
    add(billedInMonth(monthStamp), 1.0)
    add(creditedInMonth(monthStamp), -1.0)
    return totals.entries
        .map { (rate, row) ->
            VatRecap(
                rate = rate,
                base = Math.round(row[0] * 100.0) / 100.0,
                amount = Math.round(row[1] * 100.0) / 100.0,
            )
        }
        .sortedByDescending { it.rate }
}

private val BILLED_DOCUMENTS = setOf(
    DocumentType.FACTURE,
    DocumentType.TICKET,
    DocumentType.SIMPLIFIEE,
    DocumentType.EXPORT,
)
