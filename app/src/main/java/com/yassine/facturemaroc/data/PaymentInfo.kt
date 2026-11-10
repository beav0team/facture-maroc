package com.yassine.facturemaroc.data

/**
 * One payment received against one document.
 *
 * A document can be settled in several goes — 1 000 DH on account, the rest
 * next month — so [InvoiceData.payments] holds a list of these and the document
 * keeps the whole history rather than only the last figure. Each entry is the
 * amount that arrived on one day, so each can be receipted on its own.
 *
 * Kept on the record, not in the issue-time [ShopSnapshot]: the shop's details
 * are evidence that must not move, while a payment is a later fact about the
 * same document.
 */
data class PaymentInfo(
    /** Amount received, in dirhams. Clamped to the document total when read. */
    val paidAmount: Double = 0.0,
    /** The day the money came in, `dd/MM/yyyy`. Empty when only a part was paid. */
    val paidAt: String = "",
    /** Free reference: cheque number, transfer id, remark. */
    val note: String = "",
    /**
     * How this payment was made, when it differs from what the document says.
     *
     * A customer can say "espèces" when the invoice was written "virement", and
     * the paper must record what actually happened. Null means "as the document
     * says" — every payment written before this field existed.
     */
    val method: PaymentMethod? = null,
) {
    val isEmpty: Boolean get() = paidAmount <= 0.0
}

/** Payment state, derived from the amount and the document total. */
enum class PaymentState(val id: String, val label: String) {
    UNPAID("unpaid", "Impayée"),
    PARTIAL("partial", "Partielle"),
    PAID("paid", "Payée"),
    ;

    companion object {
        fun fromId(id: String?): PaymentState = entries.firstOrNull { it.id == id } ?: UNPAID
    }
}
