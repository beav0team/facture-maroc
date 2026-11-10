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
    /**
     * A discount given in dirhams rather than in percent, taken off after the
     * lines. When set it takes over from [discountPercent]: a shop says either
     * "5 % off" or "200 DH off", not both.
     */
    val discountFixed: Double = 0.0,
    /** TVA. Morocco's standard rate is 20; 0 is legitimate for some sales. */
    val vatPercent: Double = 20.0,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val documentType: DocumentType = DocumentType.FACTURE,
    /** Required for Devis — the date until which the quote is valid. */
    val validityDate: String? = null,
    /**
     * The day the money is due, `dd/MM/yyyy`.
     *
     * Moroccan invoices are usually paid "comptant" or at 30/60 days, and a
     * customer who has the deadline on the paper queries it far less often.
     * Null on a cash sale with no deadline stated, and on every document
     * written before this field existed.
     */
    val dueDate: String? = null,
    /** For Bon de commande — the supplier instead of a client. */
    val supplier: InvoiceClient? = null,
    /** For avoir / régularisation / acompte — the invoice this relates to. */
    val referenceNumber: String = "",
    /** For export — the Incoterms, printed only when filled. */
    val incoterm: String = "",
    /** Auto-entrepreneur: TVA non applicable, whatever the configured rate. */
    val vatNotApplicable: Boolean = false,
    /**
     * The shop's details as they were when this document was issued.
     *
     * Null on a draft: a draft is written for today's shop, so it follows the
     * live settings until it is issued. From then on the copy wins, because an
     * old invoice reprinted after an address change must still say what it
     * said when the customer received it.
     */
    val shop: ShopSnapshot? = null,
    /** Free text printed under the totals — a note for this client. */
    val customerNote: String = "",
    /** Payment or delivery conditions, printed under the note. */
    val termsNote: String = "",
    /**
     * The preset behind [termsNote]: comptant, 30/60 jours, fin de mois, or
     * free text. [PaymentTerms.CUSTOM] is the honest default for every document
     * written before this field existed.
     */
    val paymentTerms: PaymentTerms = PaymentTerms.CUSTOM,
    /**
     * The shop's late-payment clause, copied from settings when the document is
     * built so a reprint keeps what the customer read.
     *
     * Empty means the shop cleared it, and then nothing is printed. The text is
     * a model clause the shop is told to have checked; the app does not claim
     * it is legally binding.
     */
    val latePenaltyClause: String = "",
    /** Replaces the stock thank-you line at the foot of the document. */
    val thankYouNote: String = "",
    /** The shop's signature or stamp, drawn at the end of the document. */
    val signature: ImageBytes? = null,
    /** Label of an extra charge printed after the discount, e.g. "Livraison". */
    val surchargeLabel: String = "Livraison",
    /** The charge itself, HT, added to the taxable base. 0 means none. */
    val surchargeAmount: Double = 0.0,
    /**
     * A charge given as a percentage of the discounted total instead of an
     * amount, e.g. 2 % for packaging. Takes over from [surchargeAmount].
     */
    val surchargePercent: Double = 0.0,
    /**
     * A deposit already invoiced and paid on another document, TTC.
     *
     * It is not a discount and it does not touch the taxable base: the deposit
     * document already carried its own VAT, and deducting it here only says
     * what the customer still owes. 0 means none.
     */
    val depositAmount: Double = 0.0,
    /** The number of the deposit document, printed with the deduction. */
    val depositReference: String = "",
    /**
     * The payments received, oldest first.
     *
     * Empty on an unpaid document. The old single `"payment"` object is read
     * back as a one-entry list, so every document written before payments had
     * a history still opens with its money intact.
     */
    val payments: List<PaymentInfo> = emptyList(),
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
        get() = if (discountFixed > 0.0) {
            // Never more than the goods are worth: a discount larger than the
            // invoice is a typing mistake, not a payment.
            money(discountFixed.coerceAtMost(subtotal).coerceAtLeast(0.0))
        } else {
            money(subtotal * discountPercent.coerceIn(0, 100) / 100.0)
        }

    /** Whether the page prints a discount row at all. */
    val showsDiscount: Boolean get() = discountAmount > 0.0

    /**
     * Whether the discount was given in dirhams.
     *
     * Such a discount has no rate, and printing one back out — amount divided
     * by base — would state a percentage the shop never gave.
     */
    val discountInAmount: Boolean get() = discountFixed > 0.0

    /** The same discount without rounding, for the same reason as [exactSubtotal]. */
    private val exactDiscount: Double
        get() = if (discountFixed > 0.0) {
            discountFixed.coerceAtMost(exactSubtotal).coerceAtLeast(0.0)
        } else {
            exactSubtotal * discountPercent.coerceIn(0, 100) / 100.0
        }

    /** Total HT: what the goods come to before TVA. */
    val totalExcludingVat: Double
        get() = money(subtotal - discountAmount)

    /** Delivery or another charge, HT, after the discount and before TVA. */
    val surcharge: Double get() = money(exactSurcharge)

    /** The same charge without rounding, which is what [total] is built from. */
    private val exactSurcharge: Double
        get() = if (surchargePercent > 0.0) {
            (exactSubtotal - exactDiscount).coerceAtLeast(0.0) *
                surchargePercent.coerceIn(0.0, 100.0) / 100.0
        } else {
            surchargeAmount.coerceAtLeast(0.0)
        }

    /** The effective VAT: 0 for an auto-entrepreneur or an exempt client. */
    val effectiveVatPercent: Double
        get() = if (vatNotApplicable || client.vatExempt) 0.0 else vatPercent

    /**
     * The rate a given line pays on this document.
     *
     * An exempt line is outside the tax, and so is every line when the seller
     * is an auto-entrepreneur or the buyer is exempt. Otherwise the line's own
     * rate wins, falling back on the document's.
     */
    fun rateOf(line: InvoiceLine): Double = when {
        line.taxExempt || vatNotApplicable || client.vatExempt -> 0.0
        else -> (line.vatPercent ?: vatPercent).coerceAtLeast(0.0)
    }

    /**
     * The rate the delivery-type surcharge is taxed at.
     *
     * A charge is not a line, so it has no rate of its own. It follows the
     * document's rate, except when every *taxed* line pays the same other rate —
     * a 10 % invoice with 20 % delivery would be a document nobody can
     * reconcile. Exempt lines are ignored here: a delivery charged on an export
     * is still taxed at the document's rate.
     */
    val surchargeVatPercent: Double
        get() {
            if (vatNotApplicable || client.vatExempt) return 0.0
            val taxedRates = lines.map { rateOf(it) }.filter { it > 0.0 }.distinct()
            return if (taxedRates.size == 1) taxedRates.first() else vatPercent.coerceAtLeast(0.0)
        }

    /** The deadline as the page prints it, or null when none is stated. */
    val dueDatePrinted: String? get() = dueDate?.trim()?.takeIf { it.isNotEmpty() }

    /** Whether this document carries a reference to another invoice. */
    val showReferenceField: Boolean get() = documentType.isReferenceDocument

    /** Whether this document prints an Incoterms line (export only). */
    val showIncoterm: Boolean get() = documentType == DocumentType.EXPORT

    /** The part of the invoice that carries TVA. Exempt lines never do. */
    val taxableSubtotal: Double
        get() = money(lines.filterNot { it.taxExempt }.sumOf { money(it.total) })

    /**
     * Every rate's exact net: the lines at that rate, after the document
     * discount, plus the surcharge on the rate it follows.
     */
    private fun exactRateNets(): Map<Double, Double> {
        // The discount is spread over the rates in proportion to what each
        // rate is worth, so an exempt line cannot reduce the tax a taxed line
        // owes. Computed from the amount, because a fixed discount has no
        // percentage to apply.
        val keep = if (exactSubtotal <= 0.0) {
            1.0
        } else {
            (exactSubtotal - exactDiscount) / exactSubtotal
        }
        val nets = LinkedHashMap<Double, Double>()
        lines.forEach { line ->
            val rate = rateOf(line)
            nets[rate] = (nets[rate] ?: 0.0) + line.total * keep
        }
        val charge = exactSurcharge
        if (charge > 0.0) {
            val rate = surchargeVatPercent
            nets[rate] = (nets[rate] ?: 0.0) + charge
        }
        return nets
    }

    /** TVA actually charged, before rounding, summed over every rate. */
    private val exactVat: Double
        get() = exactRateNets().entries.sumOf { (rate, net) -> net * rate / 100.0 }

    /**
     * The document broken down by rate: base and tax, highest rate first.
     *
     * This is the block a mixed-rate invoice has to show (CGI art. 145), and
     * the source of the UBL tax subtotals. The bases add up to
     * [totalExcludingVat] + [surcharge] and the taxes add up to [vatAmount]:
     * whatever a rounded group loses is put back on the largest one, so the
     * recap can never contradict the figures above it.
     */
    val vatBreakdown: List<VatRecap>
        get() {
            val raw = exactRateNets().entries.sortedByDescending { it.key }
            if (raw.isEmpty()) return emptyList()
            val bases = raw.map { (rate, net) -> rate to money(net) }.toMutableList()
            val baseDelta = money(money(totalExcludingVat + surcharge) - bases.sumOf { it.second })
            if (baseDelta != 0.0) {
                val index = bases.indices.maxByOrNull { bases[it].second } ?: 0
                bases[index] = bases[index].first to money(bases[index].second + baseDelta)
            }
            val amounts = bases.map { (rate, base) -> money(base * rate / 100.0) }.toMutableList()
            val amountDelta = money(vatAmount - amounts.sum())
            if (amountDelta != 0.0) {
                val index = amounts.indices.maxByOrNull { amounts[it] } ?: 0
                amounts[index] = money(amounts[index] + amountDelta)
            }
            return bases.mapIndexed { i, (rate, base) -> VatRecap(rate, base, amounts[i]) }
        }

    /** The rates actually charged: what the totals block prints, one row each. */
    val vatRows: List<VatRecap> get() = vatBreakdown.filter { it.rate > 0.0 && it.base != 0.0 }

    /**
     * The rows the page prints under "Total HT".
     *
     * A document with no taxed rate still says something: "TVA non applicable"
     * for an auto-entrepreneur, "TVA 0 %" otherwise. Returning that here keeps
     * the six templates from each inventing their own wording.
     */
    val vatPrintRows: List<VatRecap>
        get() = vatRows.ifEmpty {
            if (vatNotApplicable) emptyList()
            else listOf(VatRecap(effectiveVatPercent, vatBase, vatAmount))
        }

    /** How many lines the VAT recap occupies, for pagination. */
    val vatRowCount: Int get() = vatPrintRows.size.coerceAtLeast(1)

    /** The base a tax office reads: every taxed rate's base, to the centime. */
    val vatBase: Double get() = money(vatBreakdown.filter { it.rate > 0.0 }.sumOf { it.base })

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
        get() = money(total - totalExcludingVat - surcharge)

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
            exactSubtotal - exactDiscount + exactSurcharge + exactVat,
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

    /**
     * The shop details to draw: the issue-time copy, or [live] for a draft.
     *
     * Every renderer and the XML writer read the shop through this, so a
     * reprint cannot mix an old document with today's identifiers.
     */
    fun printingShop(live: ShopSettings, logoBytes: ByteArray? = null): ShopSettings =
        shop?.toSettings(logoBytes ?: shop.logo?.bytes) ?: live

    /** Whether the free-text block has anything to print. */
    val hasNotes: Boolean
        get() = customerNote.isNotBlank() || termsNote.isNotBlank() ||
            printsLateClause

    /**
     * Whether this document is paid at the counter.
     *
     * An explicit preset decides. A document with no preset at all — every
     * document written before the field existed — is cash when its method is
     * cash, it states no deadline and its terms note is empty. A typed
     * condition is treated as deferred, unless the shop actually wrote
     * "comptant": that is the word that makes a clause about late interest
     * absurd on the page.
     */
    val isCashTerms: Boolean
        get() = when (paymentTerms) {
            PaymentTerms.CASH -> true
            PaymentTerms.DAYS30, PaymentTerms.DAYS60, PaymentTerms.END_OF_MONTH -> false
            PaymentTerms.CUSTOM -> {
                val text = termsNote.trim().lowercase()
                when {
                    text.isEmpty() ->
                        paymentMethod == PaymentMethod.CASH && dueDatePrinted == null
                    text.contains("comptant") -> true
                    else -> false
                }
            }
        }

    /**
     * Whether the legal late-payment clause is printed.
     *
     * Only on a document that is not paid at the counter, and only when the
     * shop filled the clause in. A cash invoice with a paragraph about
     * collection fees on it is a document the customer has a right to query.
     */
    val printsLateClause: Boolean
        get() = !isCashTerms && latePenaltyClause.isNotBlank()

    /** Whether a signature or stamp image was captured for this document. */
    val hasSignature: Boolean get() = signature != null

    /** The deposit actually applied: never more than the document is worth. */
    val deposit: Double get() = money(depositAmount.coerceIn(0.0, total))

    /** The figure the customer still has to pay after the deposit. */
    val netToPay: Double get() = money(total - deposit)

    /** The most recent payment, for the lines that name "the" payment. */
    val lastPayment: PaymentInfo? get() = payments.lastOrNull()

    /** How many payments settle this document; 0 when nothing was received. */
    val paymentCount: Int get() = payments.size

    /**
     * What has been received against [netToPay], never more than it is worth.
     *
     * The sum of the history, not one stored figure: a document paid in three
     * goes reports what the three add up to, and removing one entry takes it
     * back out.
     */
    val paidAmount: Double
        get() = money(payments.sumOf { it.paidAmount }.coerceIn(0.0, netToPay))

    /** What is still owed; 0 once the document is settled. */
    val balanceDue: Double get() = money(netToPay - paidAmount)

    /**
     * Derived, never stored: the amounts decide, not a flag that can drift.
     *
     * A document whose whole price was covered by a deposit is settled even
     * though no payment was recorded against it — that money arrived on the
     * deposit document, which is where it belongs.
     */
    val paymentState: PaymentState
        get() = when {
            total > 0.0 && balanceDue <= 0.0 -> PaymentState.PAID
            paidAmount > 0.0 -> PaymentState.PARTIAL
            else -> PaymentState.UNPAID
        }

    /** True when every dirham of the document has been received. */
    val isSettled: Boolean get() = total > 0.0 && paymentState == PaymentState.PAID

    /**
     * The line at the foot of the page.
     *
     * A shop that wrote its own thank-you note gets it; otherwise the template's
     * stock line, which is why the default is passed in rather than fixed here.
     */
    fun footer(default: String): String = thankYouNote.trim().ifBlank { default }
}

/**
 * One rate's share of a document: the net it was charged on, and the tax.
 *
 * Printed as its own row in the totals block and exported as one UBL
 * `TaxSubtotal`, which is what "break the VAT down line by line" means once the
 * lines have been added up.
 */
data class VatRecap(
    val rate: Double,
    /** Net HT taxed at [rate], discount applied, surcharge included. */
    val base: Double,
    /** Tax owed at [rate], to the centime. */
    val amount: Double,
)

/** Half-up rounding to two decimals. */
private fun money(value: Double): Double = Math.round(value * 100.0) / 100.0

/**
 * Invoice numbers, in the form Moroccan shops use: `F-20261001-0042`.
 *
 * The printed shape is decided by [format] alone, so a test can pin the
 * padding and the dashes without Android. The counter and its year live in a
 * preferences file; the prefix and the annual-reset switch live in
 * [NumberingStore], a separate file, so editing a prefix can never disturb a
 * sequence.
 */
object InvoiceNumber {

    private const val PREFS = "invoice_counter"

    /** The next number for a given document type, without consuming it. */
    fun peek(context: android.content.Context, type: DocumentType = DocumentType.FACTURE): String {
        val now = java.util.Date()
        return format(prefixFor(context, type), now, nextValue(context, type, now))
    }

    /** The number for a document that is really being written, and takes it. */
    fun consume(context: android.content.Context, type: DocumentType = DocumentType.FACTURE): String {
        val now = java.util.Date()
        val value = nextValue(context, type, now)
        // The year is stamped with every reservation, so a counter taken in
        // January is never mistaken for one taken last December by a later
        // rewind or restore.
        writeCounter(context, type, value + 1, yearOf(now))
        return format(prefixFor(context, type), now, value)
    }

    /**
     * The next value a type will get, after [resetAnnually] has had its say.
     *
     * A counter stamped with another year starts again at 1 when the switch is
     * on. A counter with no year at all — written before this field existed, or
     * by a build that did not stamp it — is kept: an unknown year is not a
     * year that changed, and resetting it would reissue numbers on a shop that
     * never asked.
     */
    fun effectiveNext(
        stored: Int,
        storedYear: Int,
        currentYear: Int,
        resetAnnually: Boolean,
    ): Int = if (resetAnnually && storedYear > 0 && storedYear != currentYear) {
        1
    } else {
        stored.coerceAtLeast(1)
    }

    private fun nextValue(context: android.content.Context, type: DocumentType, now: java.util.Date): Int {
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        return effectiveNext(
            stored = prefs.getInt(keyFor(type), 1),
            storedYear = prefs.getInt(yearKeyFor(type), 0),
            currentYear = yearOf(now),
            resetAnnually = NumberingStore(context).resetAnnually(),
        )
    }

    /** Every counter as the next value, for a backup. */
    fun counters(context: android.content.Context): Map<String, Int> {
        val now = java.util.Date()
        return DocumentType.entries.associate { it.id to nextValue(context, it, now) }
    }

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
        writeCounter(context, type, nextValue, yearOf(java.util.Date()))
    }

    /**
     * Restores the counters from a backup.
     *
     * A counter is never allowed below 1: a restored zero would format the next
     * document as `-0000`, which is not a number a shop can hand to a customer.
     * A missing type is reset to 1, which is what a fresh install has. Every
     * counter is stamped with the current year, so a restore of last year's
     * file cannot make the sequence restart in the middle of this year.
     */
    fun writeCounters(context: android.content.Context, counters: Map<String, Int>) {
        val year = yearOf(java.util.Date())
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .apply {
                DocumentType.entries.forEach { type ->
                    putInt(keyFor(type), (counters[type.id] ?: 1).coerceAtLeast(1))
                    putInt(yearKeyFor(type), year)
                }
            }
            .commit()
    }

    private fun writeCounter(context: android.content.Context, type: DocumentType, value: Int, year: Int) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putInt(keyFor(type), value.coerceAtLeast(1))
            .putInt(yearKeyFor(type), year)
            .commit()
    }

    private fun prefixFor(context: android.content.Context, type: DocumentType): String =
        NumberingStore(context).prefix(type)

    /**
     * The printed number: `PREFIX-yyyyMMdd-0000`.
     *
     * Pure, so the padding and the dashes are tested on the JVM. A prefix that
     * already ends in `-` does not get a second one; a blank prefix prints the
     * date and the sequence alone rather than a dangling dash.
     */
    fun format(prefix: String, date: java.util.Date, value: Int): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(date)
        val number = java.lang.String.format(
            java.util.Locale.US,
            "%04d",
            value.coerceAtLeast(1),
        )
        val clean = prefix.trim().trimEnd('-')
        return if (clean.isEmpty()) "$stamp-$number" else "$clean-$stamp-$number"
    }

    /** The calendar year of [date], as stamped beside a counter. */
    fun yearOf(date: java.util.Date): Int =
        java.text.SimpleDateFormat("yyyy", java.util.Locale.US).format(date).toInt()

    private fun keyFor(type: DocumentType): String = "next_${type.id}"

    private fun yearKeyFor(type: DocumentType): String = "year_${type.id}"
}
