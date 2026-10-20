package com.yassine.facturemaroc.data

/**
 * A whole invoice, and every figure derived from it.
 *
 * The arithmetic lives on the object rather than in the builder screen so the
 * four templates cannot each round a total their own way. Rounding is explicit
 * and to two decimals at every step: French invoices quote centimes, and a
 * total that differs from the sum of its lines by a rounding step is a document
 * a customer can legitimately query.
 */
data class InvoiceData(
    val number: String,
    val date: String,
    val client: InvoiceClient,
    val lines: List<InvoiceLine>,
    val discountPercent: Int = 0,
    /** TVA. Morocco's standard rate is 20; 0 is legitimate for some sales. */
    val vatPercent: Double = 20.0,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val documentType: DocumentType = DocumentType.FACTURE,
    /** Required for Devis — the date until which the quote is valid. */
    val validityDate: String? = null,
    /** For Bon de commande — the supplier instead of a client. */
    val supplier: InvoiceClient? = null,
) {
    val subtotal: Double get() = lines.sumOf { it.total }

    val discountAmount: Double
        get() = money(subtotal * discountPercent.coerceIn(0, 100) / 100.0)

    /** Total HT: what the goods come to before TVA. */
    val totalExcludingVat: Double
        get() = money(subtotal - discountAmount)

    /** Effective VAT: 0 if client is exempt, otherwise the set rate. */
    val effectiveVatPercent: Double
        get() = if (client.vatExempt) 0.0 else vatPercent

    val vatAmount: Double
        get() = money(totalExcludingVat * effectiveVatPercent.coerceAtLeast(0.0) / 100.0)

    /** Total TTC: what the customer pays. The amount spelled out in letters. */
    val total: Double
        get() = money(totalExcludingVat + vatAmount)

    /** The amount in words, the line that makes the document hard to alter. */
    val amountInWords: String get() = AmountWords.sentence(total)

    /** Header text for the document: "FACTURE", "DEVIS", "BON DE COMMANDE". */
    val header: String get() = documentType.label

    /** Whether this document shows payment method (Facture only). */
    val showPaymentMethod: Boolean get() = documentType == DocumentType.FACTURE

    /** Whether this document shows a signature block (Devis only). */
    val showSignatureBlock: Boolean get() = documentType == DocumentType.DEVIS

    /** Whether this document is a quote with validity date. */
    val isDevis: Boolean get() = documentType == DocumentType.DEVIS
}

/** Half-up rounding to two decimals. */
private fun money(value: Double): Double = Math.round(value * 100.0) / 100.0

/** Invoice numbers, in the form Moroccan shops use: `F-20261001-0042`. */
object InvoiceNumber {

    private const val PREFS = "invoice_counter"

    /** The next number for a given document type, without consuming it. */
    fun peek(context: android.content.Context, type: DocumentType = DocumentType.FACTURE): String =
        format(context, type, nextValue(context, type))

    /** The number for a document that is really being written, and takes it. */
    fun consume(context: android.content.Context, type: DocumentType = DocumentType.FACTURE): String {
        val value = nextValue(context, type)
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putInt(keyFor(type), value + 1)
            .apply()
        return format(context, type, value)
    }

    private fun nextValue(context: android.content.Context, type: DocumentType): Int =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getInt(keyFor(type), 1)

    private fun keyFor(type: DocumentType): String = "next_${type.id}"

    private fun format(context: android.content.Context, type: DocumentType, value: Int): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        return "${type.prefix}-$stamp-${java.lang.String.format(java.util.Locale.US, "%04d", value)}"
    }
}
