package com.yassine.facturemaroc.data

import java.util.Date

/**
 * How urgent a row in the history is.
 *
 * The screen decides colour from this, never from the document type: a green
 * "Émise" badge used to read as "paid" when the customer had not paid anything.
 * Only one tone can apply to a row, so a document cannot be both "late" and
 * "paid" in the same list.
 */
enum class StatusTone {
    /** Nothing to do yet: a draft, or an invoice whose deadline is ahead. */
    NEUTRAL,
    /** Money is owed but not late. */
    DUE,
    /** Money is owed and the deadline has passed. */
    OVERDUE,
    /** Settled. */
    PAID,
    /** Not issued yet. */
    DRAFT,
}

/**
 * The single line that replaces the stack of status pills.
 *
 * Three pills per row (type, late, balance) did not fit at 360 dp: they wrapped
 * inside their own shape and squeezed the client's name to a few characters.
 * One short line says the most urgent true thing, in the order
 * late > balance > paid > draft.
 */
data class DocumentStatusLine(val text: String, val tone: StatusTone)

object DocumentStatus {

    fun of(record: InvoiceRecord, today: Date = Date()): DocumentStatusLine {
        val data = record.data

        if (record.isDraft) return DocumentStatusLine("Brouillon", StatusTone.DRAFT)

        // A credit note gives money back. It is never late and never "due"; the
        // row already prints the amount with a minus sign in the error colour,
        // so a second label would be noise.
        if (data.isCredit) return DocumentStatusLine("", StatusTone.NEUTRAL)

        val overdueDays = PaymentDue.daysUntil(data.dueDatePrinted, today)
            ?.takeIf { data.paymentState != PaymentState.PAID && it < 0 }
            ?.let { -it }

        if (overdueDays != null) {
            return DocumentStatusLine("$overdueDays j de retard", StatusTone.OVERDUE)
        }

        return when (data.paymentState) {
            PaymentState.PAID -> {
                val paidAt = data.lastPayment?.paidAt.orEmpty().trim()
                DocumentStatusLine(
                    if (paidAt.isEmpty()) "Payée" else "Payée le ${shortDate(paidAt)}",
                    StatusTone.PAID,
                )
            }

            PaymentState.PARTIAL -> DocumentStatusLine(
                "${Money.dirhams(data.balanceDue)} dû",
                StatusTone.DUE,
            )

            PaymentState.UNPAID -> {
                val due = data.dueDatePrinted
                if (due.isNullOrBlank()) {
                    DocumentStatusLine("Impayée", StatusTone.NEUTRAL)
                } else {
                    DocumentStatusLine("Échéance ${shortDate(due)}", StatusTone.NEUTRAL)
                }
            }
        }
    }

    /** `12/11` — the year is already on the document line above. */
    private fun shortDate(text: String): String =
        text.trim().split('/').takeIf { it.size == 3 }?.let { "${it[0]}/${it[1]}" } ?: text.trim()
}
