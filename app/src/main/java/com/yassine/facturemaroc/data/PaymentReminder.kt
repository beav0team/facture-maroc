package com.yassine.facturemaroc.data

/**
 * A short payment reminder the shop can send by SMS or WhatsApp.
 *
 * The app does not send anything itself — there is no network permission and
 * no account — so this produces the text and hands it to whichever app the shop
 * already uses. That is also why the wording is plain: a reminder that arrives
 * on a customer's phone has to read like a sentence from the shop, not like a
 * system notification.
 *
 * A reminder is only offered for a document that is really owed: a paid
 * document, a draft, a quote, an order or a credit note has nothing to chase.
 */
object PaymentReminder {

    /** Documents that are never chased: they bill nothing or owe nothing. */
    private val NEVER = setOf(
        DocumentType.DEVIS,
        DocumentType.PROFORMA,
        DocumentType.BON_DE_COMMANDE,
        DocumentType.AVOIR,
    )

    /**
     * The text, or null when there is nothing to remind.
     *
     * [shop] is the live shop rather than the document snapshot: a reminder is a
     * new message sent today, so it must carry the phone number the customer
     * can actually call, not the one printed on the paper months ago.
     */
    fun text(record: InvoiceRecord, shop: ShopSettings, today: String? = null): String? {
        if (record.isDraft) return null
        val data = record.data
        if (data.documentType in NEVER) return null
        if (data.paymentState == PaymentState.PAID) return null
        if (data.balanceDue <= 0.0) return null

        val client = data.client.name.trim().ifBlank { "Madame, Monsieur" }
        val due = data.dueDatePrinted
        val overdue = due != null && PaymentDue.isOverdue(due, data.paymentState)

        return buildString {
            append("Bonjour ")
            append(client)
            append(",\n\n")
            if (data.paidAmount > 0.0) {
                append("Nous avons bien reçu ")
                append(money(data.paidAmount))
                append(" sur la facture ")
            } else {
                append("Petit rappel : la facture ")
            }
            append(data.number)
            append(" du ")
            append(data.date)
            append(" reste à régler : ")
            append(money(data.balanceDue))
            append(".\n\n")
            when {
                overdue -> {
                    append("L'échéance du ")
                    append(due)
                    append(" est dépassée. ")
                }
                due != null -> {
                    append("Échéance : ")
                    append(due)
                    append(". ")
                }
            }
            append("Merci de votre confiance.")
            val name = shop.name.trim()
            if (name.isNotEmpty()) {
                append("\n")
                append(name)
                val phone = shop.phone.trim()
                if (phone.isNotEmpty()) {
                    append(" — ")
                    append(phone)
                }
            }
        }
    }

    /** Whether [text] is worth offering; the screen uses this to hide the button. */
    fun available(record: InvoiceRecord): Boolean =
        !record.isDraft &&
            record.data.documentType !in NEVER &&
            record.data.paymentState != PaymentState.PAID &&
            record.data.balanceDue > 0.0

    /**
     * An amount in the same `1 234,50 DH` shape the documents print.
     *
     * Kept here rather than borrowed from the PDF layer so the message and the
     * paper cannot drift: a customer comparing the two must not see two
     * spellings of the same number.
     */
    private fun money(value: Double): String =
        String.format(java.util.Locale.FRANCE, "%,.2f", value)
            .replace('\u202F', ' ')
            .replace('\u00A0', ' ') +
            " DH"
}
