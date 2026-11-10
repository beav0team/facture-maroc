package com.yassine.facturemaroc.data

/**
 * What the shop has received against one document.
 *
 * Kept on the record, not in the issue-time [ShopSnapshot]: the shop's details
 * are evidence that must not move, while a payment is a later fact about the
 * same document. An unpaid document carries no [PaymentInfo] at all, so every
 * document written before this existed reads back exactly as it was.
 */
data class PaymentInfo(
    /** Amount received, in dirhams. Clamped to the document total when read. */
    val paidAmount: Double = 0.0,
    /** The day the money came in, `dd/MM/yyyy`. Empty when only a part was paid. */
    val paidAt: String = "",
    /** Free reference: cheque number, transfer id, remark. */
    val note: String = "",
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
