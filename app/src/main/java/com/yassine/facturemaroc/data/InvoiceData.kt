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
    /** For avoir / régularisation / acompte — the invoice this relates to. */
    val referenceNumber: String = "",
    /** For export — the Incoterms, printed only when filled. */
    val incoterm: String = "",
    /** Auto-entrepreneur: TVA non applicable, whatever the configured rate. */
    val vatNotApplicable: Boolean = false,
) {
    val subtotal: Double get() = lines.sumOf { it.total }

    val discountAmount: Double
        get() = money(subtotal * discountPercent.coerceIn(0, 100) / 100.0)

    /** Total HT: what the goods come to before TVA. */
    val totalExcludingVat: Double
        get() = money(subtotal - discountAmount)

    /** Effective VAT: 0 for an auto-entrepreneur or an exempt client. */
    val effectiveVatPercent: Double
        get() = if (vatNotApplicable || client.vatExempt) 0.0 else vatPercent

    /** Whether this document carries a reference to another invoice. */
    val showReferenceField: Boolean get() = documentType.isReferenceDocument

    /** Whether this document prints an Incoterms line (export only). */
    val showIncoterm: Boolean get() = documentType == DocumentType.EXPORT

    /** The part of the invoice that carries TVA. Exempt lines never do. */
    val taxableSubtotal: Double
        get() = money(lines.filterNot { it.taxExempt }.sumOf { it.total })

    /**
     * The TVA base after the invoice discount is shared out.
     *
     * The invoice-level discount is spread over every line in proportion to
     * what it is worth, so an exempt line cannot accidentally reduce the tax
     * that a taxed line owes. When no line is exempt this is exactly
     * [totalExcludingVat], which is the behaviour every existing invoice has.
     */
    val vatBase: Double
        get() = if (subtotal == 0.0) {
            0.0
        } else {
            money(taxableSubtotal - discountAmount * (taxableSubtotal / subtotal))
        }

    val vatAmount: Double
        get() = money(vatBase * effectiveVatPercent.coerceAtLeast(0.0) / 100.0)

    /** Total TTC: what the customer pays. The amount spelled out in letters. */
    val total: Double
        get() = money(totalExcludingVat + vatAmount)

    /** The amount in words, the line that makes the document hard to alter. */
    val amountInWords: String get() = AmountWords.sentence(total, documentType)

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
            .commit()
        return format(context, type, value)
    }

    private fun nextValue(context: android.content.Context, type: DocumentType): Int =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getInt(keyFor(type), 1)

    /** Every counter as the next value, for a backup. */
    fun counters(context: android.content.Context): Map<String, Int> =
        DocumentType.entries.associate { it.id to nextValue(context, it) }

    /**
     * Puts a counter back after a document could not be produced.
     *
     * A number is only reserved while a PDF or XML is being written, so a
     * failed share or a cancelled save must not leave a hole in the sequence.
     * The caller passes the value it read before reserving; the app is
     * single-user and single-threaded here, so nothing else can have taken a
     * number in between.
     */
    fun rewind(context: android.content.Context, type: DocumentType, nextValue: Int) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putInt(keyFor(type), nextValue.coerceAtLeast(1))
            .commit()
    }

    /**
     * Restores the counters from a backup.
     *
     * A counter is never allowed below 1: a restored zero would format the next
     * document as `-0000`, which is not a number a shop can hand to a customer.
     * A missing type is reset to 1, which is what a fresh install has.
     */
    fun writeCounters(context: android.content.Context, counters: Map<String, Int>) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .apply {
                DocumentType.entries.forEach { type ->
                    putInt(keyFor(type), (counters[type.id] ?: 1).coerceAtLeast(1))
                }
            }
            .commit()
    }

    private fun keyFor(type: DocumentType): String = "next_${type.id}"

    private fun format(context: android.content.Context, type: DocumentType, value: Int): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        return "${type.prefix}-$stamp-${java.lang.String.format(java.util.Locale.US, "%04d", value)}"
    }
}
