package com.yassine.facturemaroc.data

import java.util.Date

/**
 * One document as it appears on a client statement.
 *
 * [dueDate] is null when none was stated, which is not the same as "due today":
 * a cash sale nobody put a deadline on can never turn late, and the page leaves
 * the column empty rather than inventing a date.
 */
data class StatementLine(
    val number: String,
    val date: String,
    val dueDate: String?,
    val total: Double,
    val paid: Double,
    /** What this line adds to the debt; negative on a credit note. */
    val balance: Double,
    /** An avoir: money the shop gave back, so it reduces what is owed. */
    val isCredit: Boolean,
    /** Whether the deadline passed while the line was still unpaid. */
    val isLate: Boolean,
)

/**
 * Everything one client owes, document by document.
 *
 * The statement is a view of records that already exist, never a new
 * accounting object: it is recomputed from the history each time it is asked
 * for, so it cannot disagree with the invoices it lists. Credit notes are
 * subtracted instead of being listed as a separate kind of debt — a customer
 * handed an avoir of 500 DH on a 2 000 DH invoice owes 1 500, not 2 000 and a
 * credit of 500 that nobody carries forward in their head.
 *
 * Quotes, proformas, purchase orders, deposits and rectifying invoices are
 * excluded. None of them is a debt: a devis is a price the shop offered, a bon
 * de commande is something the shop is buying, an acompte is already paid, and
 * a régularisation corrects a document the statement already lists. Including
 * one would either invent money owed or count the same sale twice.
 */
data class ClientStatement(
    val clientName: String,
    val lines: List<StatementLine>,
) {
    /** Documents that charge money, to the centime. */
    val billed: Double get() = money(lines.filterNot { it.isCredit }.sumOf { it.total })

    /** Avoirs, as a positive figure: the block prints it as a subtraction. */
    val credited: Double get() = money(lines.filter { it.isCredit }.sumOf { it.total })

    val paid: Double get() = money(lines.filterNot { it.isCredit }.sumOf { it.paid })

    /**
     * What is still owed: the sum of what every document still asks for.
     *
     * Not `billed - credited - paid`, which reads the same only when no document
     * carries a deposit. A final invoice already deducts the acompte received on
     * its own document — that acompte is not on the statement, since an acompte
     * is not a debt — so totalling the documents is the only figure that agrees
     * with the line above it and with the paper the customer holds. Never
     * floored at zero: a client in credit must show it rather than a zero that
     * hides the avoir.
     */
    val balance: Double get() = money(lines.sumOf { it.balance })

    /** Nothing to state: the caller must not print an empty page. */
    val isEmpty: Boolean get() = lines.isEmpty()
}

/**
 * Builds statements out of the shop's history.
 *
 * The client is matched on the name, ignoring case and surrounding spaces,
 * because that is how the rest of the app identifies a client: there is no
 * stable client id yet, and rejecting "  société btp nord " as a different
 * customer would split one client's debt in two.
 */
object ClientStatements {

    fun forClient(
        records: List<InvoiceRecord>,
        clientName: String,
        today: Date = Date(),
    ): ClientStatement {
        val wanted = clientName.trim()
        val matching = records.filter { record ->
            !record.isDraft &&
                record.data.documentType in STATEMENT_DOCUMENTS &&
                record.data.client.name.trim().equals(wanted, ignoreCase = true)
        }
        val lines = matching
            // Oldest first: a statement is read like an account, and the
            // balance columns only make sense once the earlier invoices are
            // above the later payments. Records arrive newest-updated first, so
            // the document date decides instead.
            .sortedBy { PaymentDue.parse(it.data.date)?.time ?: Long.MAX_VALUE }
            .map { record ->
                val data = record.data
                val credit = data.documentType == DocumentType.AVOIR
                val total = money(data.total)
                // The deposit was money the client really paid, on its own
                // document. Counting it here is what keeps "facturé − payé"
                // equal to the balance printed under it.
                val deposit = if (credit) 0.0 else money(data.deposit)
                val paid = if (credit) 0.0 else money(data.paidAmount + deposit)
                StatementLine(
                    number = data.number,
                    date = data.date,
                    dueDate = data.dueDatePrinted,
                    total = total,
                    paid = paid,
                    // The deposit the customer already paid on another document
                    // is not a debt either, so the line shows what is really
                    // left on this one.
                    balance = if (credit) money(-total) else money(data.balanceDue),
                    isCredit = credit,
                    // An avoir owes nothing, so it is never late: calling a
                    // credit note overdue would chase a customer for money the
                    // shop owes them.
                    isLate = !credit &&
                        PaymentDue.isOverdue(data.dueDate, data.paymentState, today),
                )
            }
        return ClientStatement(clientName = wanted, lines = lines)
    }

    /**
     * Whether a client has anything worth stating.
     *
     * The client card asks this before offering the menu item: a client with
     * only a devis would otherwise get a button that produces nothing.
     */
    fun hasStatement(records: List<InvoiceRecord>, clientName: String): Boolean =
        forClient(records, clientName).lines.isNotEmpty()
}

/**
 * The documents a statement lists: money billed and money given back.
 *
 * Kept here rather than reusing [BILLED_DOCUMENTS] because the statement also
 * carries the avoir, and because an acompte — which the monthly report leaves
 * out for the same reason — is not a debt either way.
 */
private val STATEMENT_DOCUMENTS = setOf(
    DocumentType.FACTURE,
    DocumentType.TICKET,
    DocumentType.SIMPLIFIEE,
    DocumentType.EXPORT,
    DocumentType.AVOIR,
)

/** Half-up rounding to two decimals, as everywhere else in the app. */
private fun money(value: Double): Double = Math.round(value * 100.0) / 100.0

/**
 * What the whole shop is waiting to be paid.
 *
 * The home screen used to open on "Facturé ce mois-ci", which answers an
 * accountant's question. A shop opening the app wants the other one: how much
 * is still out there, and how much of it is late. Built from the same client
 * statements as everything else, so the figure on the card is the sum of the
 * figures the statements print — never a second arithmetic that can drift.
 */
data class ShopOutstanding(
    /** Money still owed, net of credit notes. Negative when the shop owes more. */
    val due: Double,
    /** The part of [due] whose deadline has passed. */
    val overdue: Double,
    /** Documents still waiting for money, late or not. */
    val documents: Int,
)

fun List<InvoiceRecord>.outstanding(today: Date = Date()): ShopOutstanding {
    // Grouped by name because that is how the app identifies a client; a client
    // with an invoice and an avoir nets them off on his own statement, which is
    // what the customer experiences too.
    val names = asSequence()
        .filter { !it.isDraft && it.data.documentType in STATEMENT_DOCUMENTS }
        .map { it.data.client.name.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()

    val statements = names.map { ClientStatements.forClient(this, it, today) }
    return ShopOutstanding(
        due = statements.sumOf { it.balance },
        overdue = statements.sumOf { statement ->
            statement.lines.filter { it.isLate }.sumOf { it.balance }
        },
        documents = statements.sumOf { statement ->
            // A credit note is not a document waiting to be paid, even though
            // it moves the balance.
            statement.lines.count { !it.isCredit && it.balance > 0.0 }
        },
    )
}
