package com.yassine.facturemaroc.data

import java.util.Date

/**
 * The one line a party card can say without opening anything.
 *
 * « 4 documents » was true and useless: it counts things, it does not answer
 * either question a shop has about a client — do they owe me, and am I late
 * chasing them? Built from the same statements the statement page prints, so the
 * card and the page cannot disagree.
 *
 * A supplier has no balance in this app: purchases are cash or on account with
 * the supplier's own paperwork, and nothing here reconciles them. [purchasedHt]
 * is what the receipts say, never a « solde fournisseur ».
 */
data class PartySummary(
    /** Issued documents for a client; receipts plus bons de commande for a supplier. */
    val documents: Int = 0,
    /** Money still owed, net of credit notes. Client only. */
    val due: Double = 0.0,
    /** The part of [due] whose deadline has passed. Client only. */
    val overdue: Double = 0.0,
    /** Receipts HT for a supplier. Zero for a client. */
    val purchasedHt: Double = 0.0,
    /** Receipts alone, so the card can say how many deliveries landed. */
    val receipts: Int = 0,
    /** `dd/MM/yyyy`, or empty when nothing was ever issued. */
    val lastActivity: String = "",
) {
    val owes: Boolean get() = due > 0.004
    val isLate: Boolean get() = overdue > 0.004
}

/**
 * Every party's summary in one pass, keyed by [partyKey].
 *
 * Grouped by name because that is still how the app identifies a party (see
 * `docs/decisions.md`), and the grouping is done once here rather than inside a
 * composable that redraws on every keystroke.
 */
fun List<InvoiceRecord>.partySummaries(
    clients: List<InvoiceClient>,
    purchases: List<Purchase>,
    today: Date = Date(),
): Map<String, PartySummary> {
    val records = this
    return clients.associate { client ->
        val key = partyKey(client.role, client.name)
        val summary = if (client.role == PartyRole.SUPPLIER) {
            supplierSummary(records, purchases, client.name)
        } else {
            clientSummary(records, client.name, today)
        }
        key to summary
    }
}

/** How the summaries are keyed: role plus a case-insensitive name. */
fun partyKey(role: PartyRole, name: String): String =
    "${role.id}|${name.trim().lowercase()}"

private fun clientSummary(
    records: List<InvoiceRecord>,
    name: String,
    today: Date,
): PartySummary {
    val statement = ClientStatements.forClient(records, name, today)
    val newest = statement.lines.lastOrNull()?.date.orEmpty()
    return PartySummary(
        // Documents that still mean something: a settled invoice is history, but
        // it is still a document that was given to this client.
        documents = statement.lines.size,
        due = statement.balance,
        overdue = statement.lines.filter { it.isLate }.sumOf { it.balance },
        lastActivity = newest,
    )
}

private fun supplierSummary(
    records: List<InvoiceRecord>,
    purchases: List<Purchase>,
    name: String,
): PartySummary {
    val wanted = name.trim()
    val receipts = purchases.filter { it.supplierName.trim().equals(wanted, ignoreCase = true) }
    val orders = records.count { record ->
        !record.isDraft &&
            record.data.documentType == DocumentType.BON_DE_COMMANDE &&
            record.data.supplier?.name?.trim()?.equals(wanted, ignoreCase = true) == true
    }
    val newestReceipt = receipts.maxByOrNull { it.createdAt }
    return PartySummary(
        documents = receipts.size + orders,
        purchasedHt = receipts.sumOf { it.totalHt },
        receipts = receipts.size,
        lastActivity = newestReceipt?.date.orEmpty(),
    )
}
