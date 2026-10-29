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
    /**
     * Total HT as the page adds it up: every line rounded to the centime, then
     * summed.
     *
     * A line can carry a third decimal — `99,99 TTC` is `83,325 HT` — and three
     * of them summed before rounding give `249,98` while the three lines printed
     * beside it read `83,33` each. That centime is a total a customer can query
     * and an importer rejects. Rounding each line first makes the printed column
     * add up; [total] still comes from the exact figures, so the price the shop
     * typed is the price the customer pays.
     */
    val subtotal: Double get() = money(lines.sumOf { money(it.total) })

    /** The same sum without rounding, kept only so [total] can stay exact. */
    private val exactSubtotal: Double get() = lines.sumOf { it.total }

    val discountAmount: Double
        get() = money(subtotal * discountPercent.coerceIn(0, 100) / 100.0)

    /** The same discount without rounding, for the same reason as [exactSubtotal]. */
    private val exactDiscount: Double
        get() = exactSubtotal * discountPercent.coerceIn(0, 100) / 100.0

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
        get() = money(lines.filterNot { it.taxExempt }.sumOf { money(it.total) })

    /** The same part without rounding, for [exactTaxableNet]. */
    private val exactTaxable: Double
        get() = lines.filterNot { it.taxExempt }.sumOf { it.total }

    /**
     * The TVA base, on the rounded figures the page prints.
     *
     * The invoice-level discount is spread over every line in proportion to
     * what it is worth, so an exempt line cannot accidentally reduce the tax
     * that a taxed line owes.
     */
    private val taxableNet: Double
        get() = if (subtotal == 0.0) {
            0.0
        } else {
            taxableSubtotal - discountAmount * (taxableSubtotal / subtotal)
        }

    /**
     * The same base before rounding, which is what [total] is built from.
     *
     * Kept unrounded because rounding it first is what turned `99,99` into
     * `100,00`: the TTC figure has to come back exactly as it was typed.
     */
    private val exactTaxableNet: Double
        get() = if (exactSubtotal == 0.0) {
            0.0
        } else {
            exactTaxable - exactDiscount * (exactTaxable / exactSubtotal)
        }

    /** The base a tax office reads: the same figure, to the centime. */
    val vatBase: Double get() = money(taxableNet)

    /**
     * TVA actually charged.
     *
     * Derived from the total rather than computed from [vatBase], so that
     * `HT + TVA = TTC` to the centime on every document. A shop that quotes
     * `99,99 TTC` must see 99,99 on the invoice, and the tax is what makes up
     * the difference — rounding it independently produces a total a customer
     * never agreed to.
     */
    val vatAmount: Double
        get() = money(total - totalExcludingVat)

    /**
     * Total TTC: what the customer pays. The amount spelled out in letters.
     *
     * Built from the exact figures for the same reason as [vatAmount]: a price
     * typed TTC and divided by `1 + taux` must return exactly the price that was
     * typed. [subtotal] is the rounded view of the same lines, and the centimes
     * the two disagree on land in [vatAmount] — which is where an accountant
     * expects an arrondi to sit.
     */
    val total: Double
        get() = money(
            exactSubtotal - exactDiscount +
                exactTaxableNet * effectiveVatPercent.coerceAtLeast(0.0) / 100.0,
        )

    /** The amount in words, the line that makes the document hard to alter. */
    val amountInWords: String get() = AmountWords.sentence(total, documentType)

    /** Header text for the document: "FACTURE", "DEVIS", "BON DE COMMANDE". */
    val header: String get() = documentType.label

    /**
     * The party the document is addressed to.
     *
     * A bon de commande is written by the shop *to* a supplier, so the supplier
     * takes the place the client occupies on every other document. The client
     * field is not printed on it at all: the shop is the client.
     */
    val party: InvoiceClient
        get() = if (documentType == DocumentType.BON_DE_COMMANDE) {
            // Never the client: on an order form the client field holds the
            // shop, and printing the shop's own name and ICE under
            // "Fournisseur" would address the order to its author.
            supplier ?: InvoiceClient(name = "")
        } else {
            client
        }

    /** What the party block is headed: "Client" or "Fournisseur". */
    val partyLabel: String
        get() = if (documentType == DocumentType.BON_DE_COMMANDE) "Fournisseur" else "Client"

    /**
     * The party's name as printed.
     *
     * A sale with no identified buyer is still a sale, and "Client de passage"
     * is the term the shop already uses in its history. A bon de commande with
     * no supplier says nothing rather than inventing one, because naming a
     * supplier that did not supply anything is worse than a blank line.
     */
    val partyName: String
        get() = party.name.trim().ifBlank {
            if (documentType == DocumentType.BON_DE_COMMANDE) "" else "Client de passage"
        }

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
