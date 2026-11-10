package com.yassine.facturemaroc.data

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yassine.facturemaroc.invoice.InvoicePdf
import com.yassine.facturemaroc.invoice.ReceiptPdf
import com.yassine.facturemaroc.invoice.StatementPdf
import com.yassine.facturemaroc.invoice.InvoiceXml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private const val AUTO_BACKUP_PREFS = "auto_backup"
private const val KEY_LAST_RUN = "lastRun"

/**
 * The invoice being written, the shop it is written for, and the history of
 * every document already handed to a customer.
 *
 * The draft is saved to disk shortly after every edit. It used to live only on
 * the ViewModel, on the argument that a half-written invoice was not worth
 * keeping; a shop that takes a phone call mid-invoice and comes back to an
 * empty screen does not agree, so it is now written down.
 */
class InvoiceViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * The last storage failure, for the screens to show and then clear.
     *
     * A write that fails is not a detail: the list on screen and the file on
     * disk disagree, and the shop would only find out on the next launch, when
     * the article or the invoice is gone.
     */
    private val _writeError = MutableStateFlow<String?>(null)
    val writeError: StateFlow<String?> = _writeError.asStateFlow()

    /** Marks the message as shown, so it does not reappear on the next edit. */
    fun clearWriteError() {
        _writeError.value = null
    }

    private fun reportWriteError(message: String) {
        _writeError.value = message
    }

    val clients = ClientStore(application)
    val savedItems = SavedItemStore(
        File(application.filesDir, SavedItemStore.FILE),
        StockLedger(File(application.filesDir, StockLedger.FILE)),
    ) { reportWriteError(it) }
    val invoices = InvoiceStore(application) { reportWriteError(it) }

    /**
     * The goods receipts, and the stock movements they cause.
     *
     * Built on the same catalogue instance the rest of the app edits: a receipt
     * written through a second store would move a shelf nobody is looking at.
     */
    val purchases = PurchaseStore(application, savedItems) { reportWriteError(it) }

    /**
     * The tamper-evident copy of every exported document.
     *
     * Separate from [invoices], which is the app's own history and changes when
     * the app changes. This one holds the bytes that were actually exported, so
     * "what did we send" survives an app update and a cleared history.
     */
    val archive = InvoiceArchive(application.filesDir)

    private val _lines = MutableStateFlow<List<InvoiceLine>>(emptyList())
    val lines: StateFlow<List<InvoiceLine>> = _lines.asStateFlow()

    private val _client = MutableStateFlow(InvoiceClient(name = ""))
    val client: StateFlow<InvoiceClient> = _client.asStateFlow()

    private val _discountPercent = MutableStateFlow(0)
    val discountPercent: StateFlow<Int> = _discountPercent.asStateFlow()

    /** A discount in dirhams; it takes over from the percentage when set. */
    private val _discountFixed = MutableStateFlow(0.0)
    val discountFixed: StateFlow<Double> = _discountFixed.asStateFlow()

    private val _paymentMethod = MutableStateFlow(PaymentMethod.CASH)
    val paymentMethod: StateFlow<PaymentMethod> = _paymentMethod.asStateFlow()

    private val _documentType = MutableStateFlow(DocumentType.FACTURE)
    val documentType: StateFlow<DocumentType> = _documentType.asStateFlow()

    private val _validityDate = MutableStateFlow<String?>(null)
    val validityDate: StateFlow<String?> = _validityDate.asStateFlow()

    /** The day the money is due; null when no deadline is stated. */
    private val _dueDate = MutableStateFlow<String?>(null)
    val dueDate: StateFlow<String?> = _dueDate.asStateFlow()

    private val _referenceNumber = MutableStateFlow("")
    val referenceNumber: StateFlow<String> = _referenceNumber.asStateFlow()

    private val _incoterm = MutableStateFlow("")
    val incoterm: StateFlow<String> = _incoterm.asStateFlow()

    private val _customerNote = MutableStateFlow("")
    val customerNote: StateFlow<String> = _customerNote.asStateFlow()

    private val _termsNote = MutableStateFlow("")
    val termsNote: StateFlow<String> = _termsNote.asStateFlow()

    /** The preset behind [termsNote]; free text is [PaymentTerms.CUSTOM]. */
    private val _paymentTerms = MutableStateFlow(PaymentTerms.CUSTOM)
    val paymentTerms: StateFlow<PaymentTerms> = _paymentTerms.asStateFlow()

    private val _thankYouNote = MutableStateFlow("")
    val thankYouNote: StateFlow<String> = _thankYouNote.asStateFlow()

    private val _signature = MutableStateFlow<ImageBytes?>(null)
    val signature: StateFlow<ImageBytes?> = _signature.asStateFlow()

    private val _surchargeLabel = MutableStateFlow("Livraison")
    val surchargeLabel: StateFlow<String> = _surchargeLabel.asStateFlow()

    private val _surchargeAmount = MutableStateFlow(0.0)
    val surchargeAmount: StateFlow<Double> = _surchargeAmount.asStateFlow()

    /** A surcharge as a percentage of the discounted total. */
    /** A deposit already invoiced, deducted from what remains to pay. */
    private val _depositAmount = MutableStateFlow(0.0)
    val depositAmount: StateFlow<Double> = _depositAmount.asStateFlow()

    private val _depositReference = MutableStateFlow("")
    val depositReference: StateFlow<String> = _depositReference.asStateFlow()

    private val _surchargePercent = MutableStateFlow(0.0)
    val surchargePercent: StateFlow<Double> = _surchargePercent.asStateFlow()

    private val _supplier = MutableStateFlow<InvoiceClient?>(null)
    val supplier: StateFlow<InvoiceClient?> = _supplier.asStateFlow()

    private val _shop = MutableStateFlow(ShopSettings())
    val shop: StateFlow<ShopSettings> = _shop.asStateFlow()

    val shopSettings: StateFlow<ShopSettings> = shop

    private val _design = MutableStateFlow(InvoiceDesign.default())
    val design: StateFlow<InvoiceDesign> = _design.asStateFlow()

    /** Combined invoice state for the builder and the totals bar. */
    private val _invoice = MutableStateFlow(buildInvoiceData())
    val invoice: StateFlow<InvoiceData> = _invoice

    /** The record this draft belongs to, so edits replace it instead of piling up. */
    private var draftId: String = UUID.randomUUID().toString()
    private var draftCreatedAt: Long = System.currentTimeMillis()

    /**
     * True while a PDF is written and the shop has not said whether it went out.
     *
     * Held here rather than in the screen so a rotation cannot swallow the
     * question and leave its number spent.
     */
    private val _awaitingShare = MutableStateFlow(false)
    val awaitingShare: StateFlow<Boolean> = _awaitingShare.asStateFlow()

    /**
     * The number a saved draft is already carrying.
     *
     * A draft shows a preview of the next number, but once it is in the history
     * that number is part of what the row says. Re-peeking on every open made
     * the builder show a different number from the list as soon as another
     * document had been issued in between.
     */
    private var draftNumber: String? = null
    private var saveJob: Job? = null

    init {
        _shop.value = ShopSettingsStore(application).read()
        _design.value = InvoiceDesignStore(application).read(application)
        // The catalogue and the history are given stable ids before any screen
        // can read them. It is idempotent, so a launch after a restore simply
        // links whatever the backup could not know about.
        val migrated = savedItems.migrateIdentities(invoices.invoices.value)
        if (migrated !== invoices.invoices.value) invoices.replaceAll(migrated)
        // Quantities typed before the ledger existed become its opening lines,
        // so a later repair recomputes them instead of emptying the shelves.
        savedItems.seedLedger()
        refreshInvoice()
    }

    private fun buildInvoiceData(): InvoiceData = InvoiceData(
        number = draftNumber ?: InvoiceNumber.peek(getApplication(), _documentType.value),
        date = InvoicePdf.today(),
        client = _client.value,
        lines = _lines.value,
        discountPercent = _discountPercent.value,
        discountFixed = _discountFixed.value,
        vatPercent = _shop.value.vatPercent,
        vatNotApplicable = _shop.value.vatNotApplicable,
        paymentMethod = _paymentMethod.value,
        documentType = _documentType.value,
        validityDate = _validityDate.value,
        dueDate = _dueDate.value,
        supplier = _supplier.value,
        referenceNumber = _referenceNumber.value,
        incoterm = _incoterm.value,
        customerNote = _customerNote.value,
        termsNote = _termsNote.value,
        paymentTerms = _paymentTerms.value,
        latePenaltyClause = _shop.value.latePenaltyClause,
        thankYouNote = _thankYouNote.value,
        signature = _signature.value,
        surchargeLabel = _surchargeLabel.value,
        surchargeAmount = _surchargeAmount.value,
        surchargePercent = _surchargePercent.value,
        depositAmount = _depositAmount.value,
        depositReference = _depositReference.value,
    )

    private fun refreshInvoice() {
        _invoice.value = buildInvoiceData()
        scheduleDraftSave()
    }

    /** How many lines are on the draft, not how many units. */
    val lineCount: Int get() = _lines.value.size

    /**
     * Adds [line] to the invoice, or increments an identical line.
     *
     * Matched on everything that prints, not just the description: two lines
     * with the same name at different prices are two different sales, and
     * folding them together would quietly drop the second price.
     */
    fun addLine(line: InvoiceLine) {
        val entry = inShopPriceBasis(line)
        val existing = _lines.value.firstOrNull {
            it.description == entry.description &&
                it.unit == entry.unit &&
                it.unitPrice == entry.unitPrice &&
                it.lineDiscountPercent == entry.lineDiscountPercent &&
                it.taxExempt == entry.taxExempt &&
                it.vatPercent == entry.vatPercent
        }
        _lines.value = if (existing == null) {
            _lines.value + entry
        } else {
            _lines.value.map {
                if (it === existing) it.copy(quantity = it.quantity + entry.quantity) else it
            }
        }
        refreshInvoice()
    }

    /**
     * Reads the price of [line] the way the shop quotes it.
     *
     * When the shop works TTC, the number it types is the price with TVA
     * already inside it. The document stores and prints HT, so it is divided
     * once, here, at the door: every template, the XML and the archive then
     * keep working on a single basis and a line can never be counted twice.
     *
     * The typed TTC figure is kept on the line as well, because whether it
     * contains TVA depends on who is buying: see [PriceBasis.repriceTtcLines].
     */
    private fun inShopPriceBasis(line: InvoiceLine): InvoiceLine {
        val shop = _shop.value
        if (!shop.pricesIncludeVat || line.taxExempt) return line
        val rate = if (shop.vatNotApplicable || _client.value.vatExempt) {
            0.0
        } else {
            line.vatPercent ?: shop.vatPercent
        }
        val typed = line.unitPrice
        return line.copy(
            unitPrice = PriceBasis.excludingVat(typed, rate),
            ttcPrice = typed,
        )
    }

    fun setQuantity(index: Int, quantity: Double) {
        val current = _lines.value.getOrNull(index) ?: return
        if (quantity <= 0) {
            remove(index)
            return
        }
        _lines.value = _lines.value.mapIndexed { i, line ->
            if (i == index) line.copy(quantity = quantity) else line
        }
        refreshInvoice()
    }

    fun setLineDiscount(index: Int, percent: Int) {
        _lines.value = _lines.value.mapIndexed { i, line ->
            if (i == index) line.copy(lineDiscountPercent = percent.coerceIn(0, 100)) else line
        }
        refreshInvoice()
    }

    fun setLineTaxExempt(index: Int, exempt: Boolean) {
        val shop = _shop.value
        val rate = if (shop.vatNotApplicable || _client.value.vatExempt) 0.0 else shop.vatPercent
        _lines.value = _lines.value.mapIndexed { i, line ->
            if (i == index) PriceBasis.withExemption(line, exempt, rate) else line
        }
        refreshInvoice()
    }

    /**
     * Sets the rate of one line, or null to follow the shop setting again.
     *
     * Morocco taxes per line (CGI art. 145), so a mixed invoice is normal: a
     * 20 % article and a 10 % service on the same page. The price is re-read
     * when the shop quotes TTC — see [PriceBasis.withRate].
     */
    fun setLineVatPercent(index: Int, percent: Double?) {
        _lines.value = _lines.value.mapIndexed { i, line ->
            if (i == index) PriceBasis.withRate(line, percent) else line
        }
        refreshInvoice()
    }

    fun remove(index: Int) {
        _lines.value = _lines.value.filterIndexed { i, _ -> i != index }
        refreshInvoice()
    }

    fun removeLine(index: Int) = remove(index)

    /**
     * Replaces one line with an edited copy.
     *
     * The price on [line] is already on the document's HT basis: the line
     * editor converts it on the way out, because only the dialog knows whether
     * it displayed the shop's TTC price or an exempt one.
     */
    fun updateLine(index: Int, line: InvoiceLine) {
        _lines.value = _lines.value.mapIndexed { i, l ->
            if (i == index) line else l
        }
        refreshInvoice()
    }

    fun setClient(client: InvoiceClient) {
        _client.value = client
        repriceForClient(client)
        refreshInvoice()
    }

    fun clearClient() {
        val empty = InvoiceClient(name = "")
        _client.value = empty
        repriceForClient(empty)
        refreshInvoice()
    }

    /**
     * Re-reads a TTC price at the rate [client] pays.
     *
     * Picking a VAT-exempt client after the lines were entered used to leave
     * every line priced as if tax were inside it, so the invoice charged the
     * exempt buyer 20 % less than the number that was on the screen when the
     * line was added. The typed price is on the line, so the HT figure is
     * simply read again.
     *
     * Whether the shop currently quotes TTC has nothing to do with it: that
     * switch says how the *next* number typed into a field is to be read, not
     * what an existing line means. A line already carrying a [InvoiceLine.ttcPrice]
     * was quoted TTC whatever the switch says now, and it keeps that meaning if
     * the shop moves to HT — otherwise correcting the rate from 10 % to 20 % and
     * the basis in the same visit left a 1680 DH article priced 1527,27 HT and
     * then taxed to 1832,73 instead of staying 1680.
     */
    private fun repriceForClient(client: InvoiceClient) {
        val shop = _shop.value
        if (_lines.value.none { it.ttcPrice != null }) return
        val rate = if (shop.vatNotApplicable || client.vatExempt) 0.0 else shop.vatPercent
        _lines.value = PriceBasis.repriceTtcLines(_lines.value, rate)
    }

    /** Remembers the client for next time, but only if there is something to. */
    fun saveClient() {
        val current = _client.value
        if (current.name.isNotBlank()) clients.save(current)
    }

    fun setDiscountPercent(percent: Int) {
        _discountPercent.value = percent.coerceIn(0, 100)
        // One discount: choosing a percentage drops the amount and the other
        // way round, so the document never carries two.
        if (percent > 0) _discountFixed.value = 0.0
        refreshInvoice()
    }

    /** A discount given in dirhams, which drops any percentage set before. */
    fun setDiscountFixed(amount: Double) {
        _discountFixed.value = amount.coerceAtLeast(0.0)
        if (amount > 0.0) _discountPercent.value = 0
        refreshInvoice()
    }

    fun setPaymentMethod(method: PaymentMethod) {
        _paymentMethod.value = method
        refreshInvoice()
    }

    fun setDocumentType(type: DocumentType) {
        if (type != _documentType.value) {
            // A number belongs to one sequence: the facture preview cannot be
            // printed on a devis.
            draftNumber = null
        }
        _documentType.value = type
        if (type != DocumentType.DEVIS) _validityDate.value = null
        if (type != DocumentType.BON_DE_COMMANDE) _supplier.value = null
        refreshInvoice()
    }

    fun setValidityDate(date: String?) {
        _validityDate.value = date
        refreshInvoice()
    }

    fun setDueDate(date: String?) {
        _dueDate.value = date
        refreshInvoice()
    }

    fun setSupplier(supplier: InvoiceClient?) {
        _supplier.value = supplier
        refreshInvoice()
    }

    fun setReferenceNumber(reference: String) {
        _referenceNumber.value = reference.trim()
        refreshInvoice()
    }

    fun setIncoterm(incoterm: String) {
        _incoterm.value = incoterm.trim()
        refreshInvoice()
    }

    fun setCustomerNote(note: String) {
        _customerNote.value = note
        refreshInvoice()
    }

    fun setTermsNote(note: String) {
        // Typing the conditions by hand means they are no longer a preset, so
        // the stored term goes back to CUSTOM. A preset fills the note; the
        // shop is always free to overwrite it.
        _termsNote.value = note
        _paymentTerms.value = PaymentTerms.CUSTOM
        refreshInvoice()
    }

    /**
     * Applies a payment-term preset and the sentence it writes.
     *
     * The dialog that calls this warns before replacing a hand-typed note, so
     * the overwrite here is always one the shop asked for.
     */
    fun setPaymentTerms(terms: PaymentTerms, note: String) {
        _paymentTerms.value = terms
        _termsNote.value = note
        refreshInvoice()
    }

    fun setThankYouNote(note: String) {
        _thankYouNote.value = note
        refreshInvoice()
    }

    /** Sets or clears the signature image. Null removes it. */
    fun setSignature(bytes: ByteArray?) {
        _signature.value = bytes?.takeIf { it.isNotEmpty() }?.let(::ImageBytes)
        refreshInvoice()
    }

    /** Sets or clears the extra charge printed after the discount. */
    /** Sets or clears the deposit already received on another document. */
    fun setDeposit(amount: Double, reference: String) {
        _depositAmount.value = amount.coerceAtLeast(0.0)
        _depositReference.value = reference.trim()
        refreshInvoice()
    }

    fun setSurcharge(label: String, amount: Double) {
        _surchargeLabel.value = label.trim().ifBlank { "Livraison" }
        _surchargeAmount.value = amount.coerceAtLeast(0.0)
        if (amount > 0.0) _surchargePercent.value = 0.0
        refreshInvoice()
    }

    /**
     * The extra charge as a percentage of the discounted total.
     *
     * A shop that charges "2 % de frais de dossier" should be able to say so
     * rather than recompute the figure every time the invoice changes.
     */
    fun setSurchargePercent(label: String, percent: Double) {
        _surchargeLabel.value = label.trim().ifBlank { "Livraison" }
        _surchargePercent.value = percent.coerceIn(0.0, 100.0)
        if (percent > 0.0) _surchargeAmount.value = 0.0
        refreshInvoice()
    }

    /**
     * Saves the shop's settings and re-reads the draft at the new rate.
     *
     * Changing the TVA rate, or switching it off altogether, after the lines
     * were typed used to leave every TTC price divided by the *old* rate: a
     * draft entered at 20 % then moved to 10 % printed an HT figure that no
     * longer added up to the price the customer was quoted, and the total moved
     * by the difference. The typed price is on the line, so it is simply read
     * again under the new settings.
     */
    fun updateShop(settings: ShopSettings) {
        ShopSettingsStore.write(getApplication<Application>(), settings)
        _shop.value = settings
        repriceForClient(_client.value)
        refreshInvoice()
    }

    fun setDesign(design: InvoiceDesign) {
        InvoiceDesignStore.write(getApplication<Application>(), design)
        _design.value = design
        refreshInvoice()
    }

    /**
     * Starts a blank invoice.
     *
     * The previous draft stays in the history because it was already saved;
     * starting a new one here only detaches the builder from it.
     */
    fun newDraft() {
        saveJob?.cancel()
        draftId = UUID.randomUUID().toString()
        draftCreatedAt = System.currentTimeMillis()
        resetState()
    }

    /**
     * Opens a record in the builder.
     *
     * A draft keeps its identity, so the same history row is updated. An issued
     * document is copied into a fresh draft: it is a receipt of what was sent,
     * and editing it in place would rewrite history and reuse its number.
     *
     * The lines are re-read at today's TVA settings on the way in. The stored
     * HT figure was correct under the rate that was in force when the line was
     * typed; reopening a draft after the shop corrected its rate used to bring
     * that old figure back with the new rate applied on top, so a 1680 DH article
     * entered at 10 % and reopened at 20 % totalled 1832,73 instead of 1680.
     */
    fun loadRecord(record: InvoiceRecord) {
        saveJob?.cancel()
        draftId = if (record.isDraft) record.id else UUID.randomUUID().toString()
        draftCreatedAt = if (record.isDraft) record.createdAt else System.currentTimeMillis()
        draftNumber = if (record.isDraft) record.data.number else null
        val data = record.data
        _lines.value = data.lines
        _client.value = data.client
        _discountPercent.value = data.discountPercent
        _discountFixed.value = data.discountFixed
        _paymentMethod.value = data.paymentMethod
        _documentType.value = data.documentType
        _validityDate.value = data.validityDate
        _dueDate.value = data.dueDate
        _referenceNumber.value = data.referenceNumber
        _incoterm.value = data.incoterm
        _supplier.value = data.supplier
        _customerNote.value = data.customerNote
        _termsNote.value = data.termsNote
        _paymentTerms.value = data.paymentTerms
        _thankYouNote.value = data.thankYouNote
        _signature.value = data.signature
        _surchargeLabel.value = data.surchargeLabel
        _surchargeAmount.value = data.surchargeAmount
        _surchargePercent.value = data.surchargePercent
        _depositAmount.value = data.depositAmount
        _depositReference.value = data.depositReference
        repriceForClient(_client.value)
        refreshInvoice()
    }

    private fun resetState() {
        draftNumber = null
        _lines.value = emptyList()
        _client.value = InvoiceClient(name = "")
        _discountPercent.value = 0
        _discountFixed.value = 0.0
        _paymentMethod.value = PaymentMethod.CASH
        _documentType.value = DocumentType.FACTURE
        _validityDate.value = null
        _dueDate.value = null
        _referenceNumber.value = ""
        _incoterm.value = ""
        _supplier.value = null
        _customerNote.value = ""
        _termsNote.value = ""
        _paymentTerms.value = PaymentTerms.CUSTOM
        _thankYouNote.value = ""
        _signature.value = null
        _surchargeLabel.value = "Livraison"
        _surchargeAmount.value = 0.0
        _surchargePercent.value = 0.0
        _depositAmount.value = 0.0
        _depositReference.value = ""
        refreshInvoice()
    }

    /** Writes the draft immediately, for the moment the builder is left. */
    fun saveDraftNow() {
        saveJob?.cancel()
        persistDraft()
    }

    private fun scheduleDraftSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            withContext(Dispatchers.IO) { persistDraft() }
        }
    }

    /**
     * Writes the draft, or forgets it when there is nothing left to keep.
     *
     * An empty screen is not a draft. Deleting the row when the last line and
     * the client are gone keeps the history free of "Sans client — 0 DH" rows
     * that the shop would only have to clean up by hand.
     */
    private fun persistDraft() {
        // A save that was already in flight when the document was issued must
        // not write over it: the shop just confirmed the invoice really went
        // out, and a draft row landing afterwards would quietly take it back.
        if (invoices.invoices.value.any { it.id == draftId && it.status == InvoiceStatus.ISSUED }) {
            return
        }
        val useful = _lines.value.isNotEmpty() || _client.value.name.isNotBlank()
        if (!useful) {
            invoices.delete(draftId)
            return
        }
        val now = System.currentTimeMillis()
        invoices.upsert(
            InvoiceRecord(
                id = draftId,
                data = buildInvoiceData(),
                status = InvoiceStatus.DRAFT,
                createdAt = draftCreatedAt,
                updatedAt = now,
            ),
        )
    }

    /**
     * Assembles the document, taking the next invoice number.
     *
     * The number is consumed here rather than when the screen opened, so a
     * number is only ever spent on a document that was really produced.
     */
    /**
     * The draft's lines with a cost frozen on each article line.
     *
     * Called only when a document is really issued, never for a preview or a
     * saved draft: the cost is a fact about the day of the sale, and freezing
     * it while the shop is still typing would let an article added as a test
     * keep a cost it never sold at. A line that already carries a cost keeps
     * it — opening a saved document and sharing it again must not revalue it.
     */
    private fun issueLines(): List<InvoiceLine> {
        val method = _shop.value.costMethod
        return _lines.value.map { line ->
            if (line.unitCostHt != null) return@map line
            val id = line.itemId?.takeIf { it.isNotBlank() } ?: return@map line
            val cost = savedItems.unitCostOf(id, method) ?: return@map line
            line.copy(unitCostHt = cost)
        }
    }

    /** What one unit of [itemId] is worth under the shop's chosen method. */
    fun unitCostOf(itemId: String): Double? =
        savedItems.unitCostOf(itemId, _shop.value.costMethod)

    /** The number the next goods receipt will carry. */
    fun nextPurchaseNumber(): String = purchases.nextNumber()

    /** Receives a goods receipt: ledger, cache and file, or nothing at all. */
    fun recordPurchase(purchase: Purchase): PurchaseOutcome = purchases.add(purchase)

    /** Deletes a receipt and reverses the stock it brought in. */
    fun deletePurchase(purchase: Purchase): Boolean = purchases.delete(purchase)

    fun buildData(): InvoiceData = InvoiceData(
        number = InvoiceNumber.consume(getApplication<Application>(), _documentType.value),
        date = InvoicePdf.today(),
        client = _client.value,
        lines = issueLines(),
        discountPercent = _discountPercent.value,
        discountFixed = _discountFixed.value,
        vatPercent = _shop.value.vatPercent,
        vatNotApplicable = _shop.value.vatNotApplicable,
        paymentMethod = _paymentMethod.value,
        documentType = _documentType.value,
        validityDate = _validityDate.value,
        dueDate = _dueDate.value,
        supplier = _supplier.value,
        referenceNumber = _referenceNumber.value,
        incoterm = _incoterm.value,
        shop = snapshotOfShop(),
        customerNote = _customerNote.value,
        termsNote = _termsNote.value,
        paymentTerms = _paymentTerms.value,
        latePenaltyClause = _shop.value.latePenaltyClause,
        thankYouNote = _thankYouNote.value,
        signature = _signature.value,
        surchargeLabel = _surchargeLabel.value,
        surchargeAmount = _surchargeAmount.value,
        surchargePercent = _surchargePercent.value,
        depositAmount = _depositAmount.value,
        depositReference = _depositReference.value,
    )

    /**
     * The shop as it is at this moment, logo included.
     *
     * The logo lives in a file, not in [ShopSettings], so it is read here
     * rather than trusted from the settings object. Called only when a document
     * is really issued, and both callers already run on the IO dispatcher.
     */
    private fun snapshotOfShop(): ShopSnapshot {
        val app = getApplication<Application>()
        val logoFile = InvoiceDesignStore(app).logoFile(app)
        val logo = runCatching { if (logoFile.isFile) logoFile.readBytes() else null }.getOrNull()
        // The bytes are stored once and the document keeps only their hash; a
        // failure to write them must not block the invoice, it only leaves the
        // reprint without its old logo.
        val hash = logo?.let { LogoStore.put(app, it) }
        return ShopSnapshot.of(_shop.value, hash)
    }

    /** The draft as it currently stands, for a preview. */
    fun previewData(): InvoiceData = buildInvoiceData()

    fun nextNumber(): String = InvoiceNumber.peek(getApplication<Application>(), _documentType.value)

    /** Clears the draft after a document has been produced. */
    fun clear() {
        resetState()
    }

    suspend fun previewBitmap(widthPx: Int): Bitmap? = withContext(Dispatchers.Default) {
        InvoicePdf.preview(
            getApplication<Application>(),
            previewData(),
            _shop.value,
            _design.value,
            widthPx,
        )
    }

    /**
     * Renders a thumbnail preview of [template] with the current draft data and shop settings.
     *
     * Used by the design picker so the shop can see what each template actually looks like,
     * instead of a hand-drawn sketch. Nothing is consumed: it peeks the number and renders
     * a copy of the design carrying the requested template.
     */
    suspend fun previewBitmapFor(design: InvoiceDesign, widthPx: Int): Bitmap? =
        withContext(Dispatchers.Default) {
            InvoicePdf.preview(
                getApplication<Application>(),
                previewData(),
                _shop.value,
                design,
                widthPx,
            )
        }

    /**
     * A document that is written and open in the share sheet, waiting for the
     * shop to say whether it really went out.
     *
     * Android cannot answer that question: `startActivity` returns as soon as
     * the chooser is drawn, and a chooser that is then dismissed looks exactly
     * like one whose app sent the file. Treating the launch as delivery wiped
     * the draft, moved the shelf and burned the number for a document that was
     * never sent.
     */
    private data class PendingShare(
        val type: DocumentType,
        val counterBefore: Int,
        val data: InvoiceData,
        val file: File,
    )

    private var pendingShare: PendingShare? = null

    /**
     * Renders the invoice and hands it to the share sheet.
     *
     * Nothing is archived and no shelf moves here: this only says the PDF was
     * written and the chooser opened. The shop answers [confirmShared] or
     * [cancelShared] when it comes back. A failure leaves the lines in place,
     * because a shop whose phone has no app that accepts a PDF should be able
     * to press the button again on a different device rather than rebuild the
     * invoice from nothing.
     */
    suspend fun shareInvoice(): InvoicePdf.Outcome = withContext(Dispatchers.IO) {
        if (_lines.value.isEmpty()) return@withContext InvoicePdf.Outcome.NotWritten
        saveClient()
        val app = getApplication<Application>()
        // A share that was never answered — the process was killed, or the
        // question was lost — must not leave its number spent. Its document was
        // never sent, so the number is simply taken again below.
        pendingShare?.let { stale ->
            InvoiceNumber.rewind(app, stale.type, stale.counterBefore)
            stale.file.delete()
            pendingShare = null
            _awaitingShare.value = false
        }
        val type = _documentType.value
        val before = InvoiceNumber.counters(app)[type.id] ?: 1
        val data = buildData()
        val outcome = InvoicePdf.share(app, data, _shop.value, _design.value)
        when (outcome) {
            is InvoicePdf.Outcome.HandedOff -> {
                pendingShare = PendingShare(type, before, data, outcome.file)
                _awaitingShare.value = true
            }

            else -> {
                // The document was never handed over, so the number goes back to
                // the sequence. A PDF that could not be shared is deleted with it.
                InvoiceNumber.rewind(app, type, before)
                if (outcome is InvoicePdf.Outcome.NoReceiver) outcome.file.delete()
            }
        }
        return@withContext outcome
    }

    /**
     * The document really went out: the shelf moves, the history records it, and
     * the builder starts a fresh draft.
     */
    suspend fun confirmShared(): Boolean = withContext(Dispatchers.IO) {
        val pending = pendingShare ?: return@withContext false
        pendingShare = null
        _awaitingShare.value = false
        // Nothing may rewrite this row as a draft afterwards: the debounced
        // draft save is cancelled here and refuses to touch an issued record.
        saveJob?.cancel()
        // The shelf moves first, then the history records that it did. A record
        // saying "this moved stock" when it did not would credit the shelf on
        // deletion, so the flag is only ever written after the movement returned
        // something it actually changed.
        val moved = savedItems.applyStockMovement(pending.data).isNotEmpty()
        invoices.upsert(
            InvoiceRecord(
                id = draftId,
                data = pending.data,
                status = InvoiceStatus.ISSUED,
                createdAt = draftCreatedAt,
                updatedAt = System.currentTimeMillis(),
                stockApplied = moved,
            ),
        )
        // The PDF is the document the customer actually received, so those
        // bytes are what the archive keeps. A failure here costs the evidence,
        // never the invoice.
        runCatching { pending.file.readBytes() }.getOrNull()
            ?.let { archive.recordPdf(pending.data, it) }
        newDraft()
        true
    }

    /**
     * The share was cancelled: the number goes back into the sequence and the
     * PDF is deleted, but the draft stays exactly as it was.
     */
    suspend fun cancelShared() = withContext(Dispatchers.IO) {
        val pending = pendingShare ?: return@withContext
        pendingShare = null
        _awaitingShare.value = false
        InvoiceNumber.rewind(getApplication(), pending.type, pending.counterBefore)
        pending.file.delete()
    }

    /** Sends a document that already exists again, without consuming a number. */
    suspend fun shareRecord(record: InvoiceRecord): InvoicePdf.Outcome = withContext(Dispatchers.IO) {
        val outcome = InvoicePdf.share(
            getApplication<Application>(),
            record.data,
            _shop.value,
            _design.value,
        )
        // Re-sending keeps the same entry: the archive dedups by content hash.
        if (outcome is InvoicePdf.Outcome.HandedOff) {
            runCatching { outcome.file.readBytes() }.getOrNull()
                ?.let { archive.recordPdf(record.data, it) }
        }
        outcome
    }

    /**
     * Hands the payment reminder text to whatever messaging app the shop uses.
     *
     * Text, not a PDF: a reminder is read in a notification, and making the shop
     * retype the amount is how a wrong number reaches a customer. Returns false
     * when there is nothing to chase, so the screen can say so rather than open
     * an empty chooser.
     */
    fun shareReminder(record: InvoiceRecord): Boolean {
        val text = PaymentReminder.text(record, _shop.value) ?: return false
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, "Rappel de paiement")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            getApplication<Application>().startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    /**
     * Writes the payment receipt and hands it to the share sheet.
     *
     * A receipt is worth archiving like the invoice: it is the only proof of a
     * deposit, and the hash chain is what makes the two documents answer each
     * other if a customer ever disputes what they paid.
     */
    suspend fun shareReceipt(record: InvoiceRecord): InvoicePdf.Outcome = withContext(Dispatchers.IO) {
        val outcome = ReceiptPdf.share(
            getApplication(),
            record.data,
            _shop.value,
            _design.value,
        )
        if (outcome is InvoicePdf.Outcome.HandedOff) {
            runCatching { outcome.file.readBytes() }.getOrNull()
                ?.let { archive.recordPdf(record.data, it) }
        }
        outcome
    }

    /**
     * Writes the client statement and hands it to the share sheet.
     *
     * Nothing is archived: a statement is a report over documents that are
     * already in the archive, not a new document the shop issued. Archiving it
     * would add a hash for something that changes the moment a payment is
     * recorded, and the ledger would lie about a paper nobody received.
     *
     * The client block comes from the history rather than the saved directory,
     * so a statement can be produced for a client that was only ever typed on
     * an invoice.
     */
    suspend fun shareStatement(
        clientName: String,
        records: List<InvoiceRecord>,
    ): InvoicePdf.Outcome = withContext(Dispatchers.IO) {
        val statement = ClientStatements.forClient(records, clientName)
        if (statement.isEmpty) return@withContext InvoicePdf.Outcome.NotWritten
        val client = records
            .firstOrNull {
                !it.isDraft &&
                    it.data.client.name.trim().equals(clientName.trim(), ignoreCase = true)
            }
            ?.data?.client
            ?: InvoiceClient(name = statement.clientName)
        StatementPdf.share(
            getApplication(),
            statement,
            client,
            _shop.value,
            _design.value,
        )
    }

    /**
     * Writes the draft as XML, and keeps the document it produced.
     *
     * The number is reserved before the XML is built so the file and the
     * history carry the same one. If the write fails the counter is put back:
     * a cancelled or failed save leaves the draft exactly as it was and does
     * not leave a hole in the invoice sequence.
     */
    /**
     * Writes the shop's documents as a spreadsheet for its accountant.
     *
     * The file is built from the same issued records the list shows, so a
     * document the shop can read is a document the accountant gets: no draft
     * and no deleted row can slip in through a second query.
     */
    suspend fun exportAccountant(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val csv = runCatching {
            AccountantExport.documentsCsv(invoices.invoices.value)
        }.getOrElse { return@withContext false }
        writeText(uri, csv)
    }

    suspend fun exportDraftXml(uri: Uri): XmlExportOutcome = withContext(Dispatchers.IO) {
        if (_lines.value.isEmpty()) return@withContext XmlExportOutcome.NothingToExport
        saveClient()
        val app = getApplication<Application>()
        val type = _documentType.value
        val before = InvoiceNumber.counters(app)[type.id] ?: 1
        val issued = buildData()
        val xml = InvoiceXml.build(issued, issued.printingShop(_shop.value))
        if (!writeText(uri, xml)) {
            InvoiceNumber.rewind(app, type, before)
            return@withContext XmlExportOutcome.Failed
        }
        // Only after the file really reached the user. A full disk or a locked
        // directory must cost the archive, never the document the shop just
        // saved, so the failure is swallowed here on purpose: record() reports
        // it by returning null and verify() will show the hole afterwards.
        archive.record(issued, xml)
        // An XML export is an issue: the file left the shop, so the stock does too.
        val moved = savedItems.applyStockMovement(issued).isNotEmpty()
        invoices.upsert(
            InvoiceRecord(
                id = draftId,
                data = issued,
                status = InvoiceStatus.ISSUED,
                createdAt = draftCreatedAt,
                updatedAt = System.currentTimeMillis(),
                stockApplied = moved,
            ),
        )
        newDraft()
        XmlExportOutcome.Saved
    }

    /** Writes a document that already exists, without touching its number. */
    suspend fun exportRecordXml(record: InvoiceRecord, uri: Uri): XmlExportOutcome =
        withContext(Dispatchers.IO) {
            if (record.data.lines.isEmpty()) return@withContext XmlExportOutcome.NothingToExport
            val xml = InvoiceXml.build(record.data, record.data.printingShop(_shop.value))
            if (!writeText(uri, xml)) return@withContext XmlExportOutcome.Failed
            archive.record(record.data, xml)
            XmlExportOutcome.Saved
        }

    /**
     * Writes the shop's whole data set to the file the user picked.
     *
     * The logo is read from the file the PDF renderer reads, not from the
     * unused `logoBytes` field in settings, so a restored phone prints the same
     * header as the one that made the backup.
     *
     * The file is only written when the app could read it back: a backup over
     * the import cap is refused with a reason rather than saved and refused at
     * restore time, and a logo that alone pushes it over is dropped with a
     * message the shop can act on.
     */
    suspend fun exportBackup(uri: Uri): BackupExportOutcome = withContext(Dispatchers.IO) {
        val built = runCatching {
            buildBackup()
        }.getOrElse { error ->
            return@withContext if (error is Backup.TooLargeException) {
                BackupExportOutcome.TooLarge(error.message.orEmpty())
            } else {
                BackupExportOutcome.Failed
            }
        }
        if (!writeText(uri, built.json)) return@withContext BackupExportOutcome.Failed
        val app = getApplication<Application>()
        val hasLogo = InvoiceDesignStore(app).logoFile(app).isFile
        if (built.logoIncluded || !hasLogo) {
            BackupExportOutcome.Saved
        } else {
            BackupExportOutcome.SavedWithoutLogo
        }
    }

    /**
     * The same JSON a manual export writes.
     *
     * Shared by the export and the automatic copies so a snapshot can never
     * drift from the backup the shop would have made by hand.
     */
    private fun buildBackup(): Backup.Built {
        val app = getApplication<Application>()
        val logoFile = InvoiceDesignStore(app).logoFile(app)
        val logo = runCatching { if (logoFile.isFile) logoFile.readBytes() else null }.getOrNull()
        val records = invoices.invoices.value
        // One copy per distinct logo an issued document points at, so old
        // invoices still print their old logo on a restored phone.
        val snapshotLogos = LogoStore.hashesOf(records)
            .mapNotNull { hash -> LogoStore.read(app, hash)?.let { hash to it } }
            .toMap()
        return Backup.buildForExport(
            shop = _shop.value,
            design = _design.value,
            logo = logo,
            clients = clients.clients.value,
            items = savedItems.items.value,
            invoices = records,
            counters = InvoiceNumber.counters(app),
            folders = savedItems.folders.value,
            logos = snapshotLogos,
            prefixes = NumberingStore(app).overrides(),
            resetAnnually = NumberingStore(app).resetAnnually(),
            // The history travels with the quantities: a restored phone that
            // kept only the numbers could not answer "why?" or repair itself.
            stockEntries = savedItems.ledger.all(),
            purchases = purchases.all(),
        )
    }

    private fun snapshotsDir() = File(getApplication<Application>().filesDir, "backups")

    private fun snapshotStore() = SnapshotStore(snapshotsDir())

    private fun autoBackupPrefs() =
        getApplication<Application>().getSharedPreferences(AUTO_BACKUP_PREFS, Context.MODE_PRIVATE)

    /** True when a day has passed since the last automatic copy. */
    fun autoBackupDue(now: Long = System.currentTimeMillis()): Boolean =
        AutoBackup.due(autoBackupPrefs().getLong(KEY_LAST_RUN, 0L), now)

    /**
     * Takes the daily copy, if one is due.
     *
     * Called when the app opens. Failure is silent on purpose: the shop did not
     * ask for this at that moment, and a dialog about a backup it did not see is
     * worse than the next attempt tomorrow. The screen reports the last snapshot
     * it actually has.
     */
    suspend fun runAutoBackupIfDue(): Boolean = withContext(Dispatchers.IO) {
        if (!autoBackupDue()) return@withContext false
        runAutoBackup()
    }

    /**
     * Writes one snapshot now, locally and then to the folder if one is chosen.
     *
     * The local file is what makes the feature worth having; the folder is a
     * copy that survives a lost phone. A provider that refuses the copy must not
     * turn a good local snapshot into a failure, so the mirror is best-effort
     * and the timestamp is written either way.
     */
    suspend fun runAutoBackup(now: Long = System.currentTimeMillis()): Boolean =
        withContext(Dispatchers.IO) {
            val built = runCatching { buildBackup() }.getOrElse { return@withContext false }
            val written = runCatching {
                val store = snapshotStore()
                store.write(built.json, now)
                store.prune()
            }.isSuccess
            if (!written) return@withContext false
            runCatching {
                AutoBackupFolder(getApplication()).write(AutoBackup.mirrorName(now), built.json)
            }
            autoBackupPrefs().edit().putLong(KEY_LAST_RUN, now).apply()
            true
        }

    suspend fun autoBackupSnapshots(): List<SnapshotStore.Snapshot> = withContext(Dispatchers.IO) {
        snapshotStore().list()
    }

    suspend fun readSnapshot(snapshot: SnapshotStore.Snapshot): BackupReadOutcome =
        withContext(Dispatchers.IO) { snapshotStore().read(snapshot) }

    fun autoBackupFolderLabel(): String? =
        AutoBackupFolder(getApplication()).label()

    /**
     * Remembers [uri] as the off-device folder and proves it straight away.
     *
     * The immediate copy is not decoration: a grant that is accepted but cannot
     * be written to (a revoked cloud folder, a card removed a second later) is
     * announced now rather than on the morning the shop needs the copy.
     */
    suspend fun setAutoBackupFolder(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val folder = AutoBackupFolder(app)
        if (!folder.remember(uri)) return@withContext false
        runCatching { buildBackup() }.getOrNull()?.let { folder.writeNow(it.json) }
        true
    }

    fun forgetAutoBackupFolder() {
        AutoBackupFolder(getApplication()).forget()
    }

    /**
     * Reads and validates a backup without applying it.
     *
     * A file from another phone, a truncated download, or a JSON file that
     * merely has the right extension all end here as a message, never as a
     * half-restored shop.
     */
    suspend fun readBackup(uri: Uri): BackupReadOutcome = withContext(Dispatchers.IO) {
        val outcome = runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                Backup.read(input)
            }
        }.getOrNull()
        outcome ?: BackupReadOutcome.Failed(
            "Le fichier n'a pas pu être ouvert. Vérifiez qu'il est toujours présent, puis réessayez.",
        )
    }

    /**
     * Replaces the shop's data with a backup that was already validated.
     *
     * The logo is written first because it is the one step that talks to the
     * filesystem and can fail; the rest are in-memory stores with an atomic
     * write, so a failure cannot leave the shop with half a backup and half
     * its own data.
     */
    suspend fun restoreBackup(backup: BackupData): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val app = getApplication<Application>()
            val designStore = InvoiceDesignStore(app)
            if (backup.logo == null) {
                designStore.removeLogo(app)
            } else if (!designStore.saveLogo(app, backup.logo)) {
                throw IllegalStateException("the logo could not be written")
            }
            backup.logos.forEach { (hash, bytes) -> LogoStore.put(app, bytes) }
            ShopSettingsStore.write(app, backup.shop)
            InvoiceDesignStore.write(app, backup.design)
            clients.replaceAll(backup.clients)
            savedItems.replaceAll(backup.items, backup.folders)
            savedItems.restoreLedger(backup.stockEntries)
            purchases.replaceAll(backup.purchases)
            invoices.replaceAll(backup.invoices)
            // A backup written before ids existed restores a catalogue and a
            // history that only share labels; link them now rather than on the
            // next launch, so the first sale after a restore moves the shelf.
            val migrated = savedItems.migrateIdentities(invoices.invoices.value)
            if (migrated !== invoices.invoices.value) invoices.replaceAll(migrated)
            InvoiceNumber.writeCounters(app, backup.counters)
            NumberingStore(app).writeAll(backup.prefixes, backup.resetAnnually)
            _shop.value = backup.shop
            _design.value = backup.design
            newDraft()
            true
        }.getOrDefault(false)
    }

    /**
     * Writes [text] to the document the user picked.
     *
     * "wt" truncates first, which matters when the user picks an existing file:
     * without it, a shorter document would leave the tail of the old one behind
     * and the result would not parse.
     */
    private fun writeText(uri: Uri, text: String): Boolean {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val resolver = getApplication<Application>().contentResolver
        val stream = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: runCatching { resolver.openOutputStream(uri) }.getOrNull()
            ?: return false
        return runCatching {
            stream.use { it.write(bytes) }
            true
        }.getOrDefault(false)
    }

    /**
     * Opens a credit note that reverses [record].
     *
     * An avoir is not a new sale: it repeats the lines, prices and rates of the
     * document it corrects, and carries its number as a reference so the two
     * read together. Payment, deadline and quote validity are dropped — money
     * that came in belongs to the invoice, not to the credit.
     */
    /** Opens the invoice draft that bills [record], when it is a quote. */
    fun loadAsInvoice(record: InvoiceRecord) {
        val invoice = QuoteConversion.invoiceFrom(record) ?: return
        loadRecord(invoice)
    }

    fun loadAsAvoir(record: InvoiceRecord) {
        val avoir = CreditNote.from(record) ?: return
        loadRecord(avoir)
    }

    fun deleteRecord(id: String) {
        // An issued document is not deletable, whatever the screen offered: the
        // check lives in the model so a stale menu cannot bypass it. A
        // correction is an avoir, which is also what restores the stock — a
        // document that simply vanishes takes its movement with it and leaves
        // the shelf short for a sale that no longer exists on paper.
        val record = invoices.find(id) ?: return
        if (!record.isDeletable) return
        invoices.delete(id)
        if (id == draftId) newDraft()
    }

    /**
     * Records what the shop received against an issued document.
     *
     * The amount is clamped to the total and a zero clears the payment, so a
     * mistyped figure can never leave a document owing a negative balance. Only
     * an issued record can carry a payment: a draft has not been handed over.
     */
    fun setPayment(id: String, payment: PaymentInfo?) {
        val record = invoices.find(id) ?: return
        if (record.isDraft) return
        val amount = (payment?.paidAmount ?: 0.0).coerceIn(0.0, record.data.netToPay)
        val cleaned = if (amount <= 0.0) {
            null
        } else {
            payment!!.copy(
                paidAmount = amount,
                paidAt = payment.paidAt.trim(),
                note = payment.note.trim(),
            )
        }
        invoices.upsert(
            record.copy(
                data = record.data.copy(payment = cleaned),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Marks the whole document paid in one step, dated today unless given. */
    fun markPaid(id: String, date: String = InvoicePdf.today()) {
        val record = invoices.find(id) ?: return
        setPayment(id, PaymentInfo(paidAmount = record.data.total, paidAt = date))
    }

    /** One article's filled movements, newest first. */
    fun stockEntries(itemId: String): List<StockEntry> = savedItems.entriesFor(itemId)

    /**
     * The articles whose shown quantity disagrees with the ledger.
     *
     * Read-only by design: Settings shows this list and offers a repair, but the
     * repair only runs when the shop presses it. A check that quietly corrected
     * what it found would hide the bug that produced the drift.
     */
    suspend fun stockDrift(): List<StockDrift> = withContext(Dispatchers.IO) {
        savedItems.stockDrift()
    }

    /** Recomputes every cached quantity from the ledger; returns how many moved. */
    suspend fun repairStockCache(): Int = withContext(Dispatchers.IO) {
        savedItems.rebuildStockCache().size
    }

    /**
     * Saves a physical count and returns how many shelves it corrected.
     *
     * The expected quantity is read from the ledger, not from the cached field:
     * the count is the moment the shop can prove what is really there, and
     * counting against a number that has already drifted would turn the drift
     * into an accepted fact. A blank reason is refused by the caller; the sheet
     * will not close without one, because a correction nobody can explain is a
     * correction nobody can trust.
     */
    fun recordStocktake(counts: Map<String, Int>, reason: String): Int {
        val rows = Stocktake.rows(savedItems.items.value, counts) { item ->
            savedItems.ledger.balance(item.id)
        }
        val entries = Stocktake.entries(
            rows = rows,
            reason = reason,
            reference = "Inventaire du ${InvoicePdf.today()}",
        )
        return savedItems.recordStocktake(
            entries = entries,
            counted = rows.filter { it.difference != 0 }.associate { it.itemId to it.counted },
        )
    }

    /** Saves an issued record as a new draft, keeping the original untouched. */
    fun duplicateRecord(record: InvoiceRecord) {        loadRecord(
            record.copy(
                id = UUID.randomUUID().toString(),
                status = InvoiceStatus.DRAFT,
                // The copy has not been issued, so it has moved nothing yet.
                stockApplied = false,
            ),
        )
    }
}
