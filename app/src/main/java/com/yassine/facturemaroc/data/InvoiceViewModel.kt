package com.yassine.facturemaroc.data

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yassine.facturemaroc.invoice.InvoicePdf
import com.yassine.facturemaroc.invoice.InvoiceXml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

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

    val clients = ClientStore(application)
    val savedItems = SavedItemStore(application)
    val invoices = InvoiceStore(application)

    private val _lines = MutableStateFlow<List<InvoiceLine>>(emptyList())
    val lines: StateFlow<List<InvoiceLine>> = _lines.asStateFlow()

    private val _client = MutableStateFlow(InvoiceClient(name = ""))
    val client: StateFlow<InvoiceClient> = _client.asStateFlow()

    private val _discountPercent = MutableStateFlow(0)
    val discountPercent: StateFlow<Int> = _discountPercent.asStateFlow()

    private val _paymentMethod = MutableStateFlow(PaymentMethod.CASH)
    val paymentMethod: StateFlow<PaymentMethod> = _paymentMethod.asStateFlow()

    private val _documentType = MutableStateFlow(DocumentType.FACTURE)
    val documentType: StateFlow<DocumentType> = _documentType.asStateFlow()

    private val _validityDate = MutableStateFlow<String?>(null)
    val validityDate: StateFlow<String?> = _validityDate.asStateFlow()

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
        _shop.value = ShopSettingsStore(application).read(application)
        _design.value = InvoiceDesignStore(application).read(application)
        refreshInvoice()
    }

    private fun buildInvoiceData(): InvoiceData = InvoiceData(
        number = draftNumber ?: InvoiceNumber.peek(getApplication(), _documentType.value),
        date = InvoicePdf.today(),
        client = _client.value,
        lines = _lines.value,
        discountPercent = _discountPercent.value,
        vatPercent = _shop.value.vatPercent,
        paymentMethod = _paymentMethod.value,
        documentType = _documentType.value,
        validityDate = _validityDate.value,
        supplier = _supplier.value,
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
        val existing = _lines.value.firstOrNull {
            it.description == line.description &&
                it.unit == line.unit &&
                it.unitPrice == line.unitPrice &&
                it.lineDiscountPercent == line.lineDiscountPercent &&
                it.taxExempt == line.taxExempt
        }
        _lines.value = if (existing == null) {
            _lines.value + line
        } else {
            _lines.value.map {
                if (it === existing) it.copy(quantity = it.quantity + line.quantity) else it
            }
        }
        refreshInvoice()
    }

    fun setQuantity(index: Int, quantity: Int) {
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

    fun setUnitPrice(index: Int, price: Double) {
        _lines.value = _lines.value.mapIndexed { i, line ->
            if (i == index) line.copy(unitPrice = price.coerceAtLeast(0.0)) else line
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
        _lines.value = _lines.value.mapIndexed { i, line ->
            if (i == index) line.copy(taxExempt = exempt) else line
        }
        refreshInvoice()
    }

    fun remove(index: Int) {
        _lines.value = _lines.value.filterIndexed { i, _ -> i != index }
        refreshInvoice()
    }

    fun removeLine(index: Int) = remove(index)

    fun updateLine(index: Int, line: InvoiceLine) {
        _lines.value = _lines.value.mapIndexed { i, l ->
            if (i == index) line else l
        }
        refreshInvoice()
    }

    fun setClient(client: InvoiceClient) {
        _client.value = client
        refreshInvoice()
    }

    fun clearClient() {
        _client.value = InvoiceClient(name = "")
        refreshInvoice()
    }

    /** Remembers the client for next time, but only if there is something to. */
    fun saveClient() {
        val current = _client.value
        if (current.name.isNotBlank()) clients.save(current)
    }

    fun setDiscountPercent(percent: Int) {
        _discountPercent.value = percent.coerceIn(0, 100)
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

    fun setSupplier(supplier: InvoiceClient?) {
        _supplier.value = supplier
        refreshInvoice()
    }

    fun updateShop(settings: ShopSettings) {
        ShopSettingsStore.write(getApplication<Application>(), settings)
        _shop.value = settings
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
        _paymentMethod.value = data.paymentMethod
        _documentType.value = data.documentType
        _validityDate.value = data.validityDate
        _supplier.value = data.supplier
        refreshInvoice()
    }

    private fun resetState() {
        draftNumber = null
        _lines.value = emptyList()
        _client.value = InvoiceClient(name = "")
        _discountPercent.value = 0
        _paymentMethod.value = PaymentMethod.CASH
        _documentType.value = DocumentType.FACTURE
        _validityDate.value = null
        _supplier.value = null
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
    fun buildData(): InvoiceData = InvoiceData(
        number = InvoiceNumber.consume(getApplication<Application>(), _documentType.value),
        date = InvoicePdf.today(),
        client = _client.value,
        lines = _lines.value,
        discountPercent = _discountPercent.value,
        vatPercent = _shop.value.vatPercent,
        paymentMethod = _paymentMethod.value,
        documentType = _documentType.value,
        validityDate = _validityDate.value,
        supplier = _supplier.value,
    )

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
     * Renders the invoice and hands it to the share sheet.
     *
     * The document is written to the history only when it was really produced
     * and handed over. A failure leaves the lines in place, because a shop whose
     * phone has no app that accepts a PDF should be able to press the button
     * again on a different device rather than rebuild the invoice from nothing.
     */
    suspend fun shareInvoice(): InvoicePdf.Outcome = withContext(Dispatchers.IO) {
        if (_lines.value.isEmpty()) return@withContext InvoicePdf.Outcome.NotWritten
        saveClient()
        val app = getApplication<Application>()
        val type = _documentType.value
        val before = InvoiceNumber.counters(app)[type.id] ?: 1
        val data = buildData()
        val outcome = InvoicePdf.share(app, data, _shop.value, _design.value)
        if (outcome is InvoicePdf.Outcome.Shared) {
            val now = System.currentTimeMillis()
            invoices.upsert(
                InvoiceRecord(
                    id = draftId,
                    data = data,
                    status = InvoiceStatus.ISSUED,
                    createdAt = draftCreatedAt,
                    updatedAt = now,
                ),
            )
            newDraft()
        } else {
            // The document was never handed over, so the number goes back to
            // the sequence. A PDF that could not be shared is deleted with it.
            InvoiceNumber.rewind(app, type, before)
            if (outcome is InvoicePdf.Outcome.NoReceiver) outcome.file.delete()
        }
        outcome
    }

    /** Sends a document that already exists again, without consuming a number. */
    suspend fun shareRecord(record: InvoiceRecord): InvoicePdf.Outcome = withContext(Dispatchers.IO) {
        InvoicePdf.share(
            getApplication<Application>(),
            record.data,
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
    suspend fun exportDraftXml(uri: Uri): XmlExportOutcome = withContext(Dispatchers.IO) {
        if (_lines.value.isEmpty()) return@withContext XmlExportOutcome.NothingToExport
        saveClient()
        val app = getApplication<Application>()
        val type = _documentType.value
        val before = InvoiceNumber.counters(app)[type.id] ?: 1
        val issued = buildData()
        if (!writeText(uri, InvoiceXml.build(issued, _shop.value))) {
            InvoiceNumber.rewind(app, type, before)
            return@withContext XmlExportOutcome.Failed
        }
        invoices.upsert(
            InvoiceRecord(
                id = draftId,
                data = issued,
                status = InvoiceStatus.ISSUED,
                createdAt = draftCreatedAt,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        newDraft()
        XmlExportOutcome.Saved
    }

    /** Writes a document that already exists, without touching its number. */
    suspend fun exportRecordXml(record: InvoiceRecord, uri: Uri): XmlExportOutcome =
        withContext(Dispatchers.IO) {
            if (record.data.lines.isEmpty()) return@withContext XmlExportOutcome.NothingToExport
            val xml = InvoiceXml.build(record.data, _shop.value)
            if (writeText(uri, xml)) XmlExportOutcome.Saved else XmlExportOutcome.Failed
        }

    /**
     * Writes the shop's whole data set to the file the user picked.
     *
     * The logo is read from the file the PDF renderer reads, not from the
     * unused `logoBytes` field in settings, so a restored phone prints the same
     * header as the one that made the backup.
     */
    suspend fun exportBackup(uri: Uri): BackupExportOutcome = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val logoFile = InvoiceDesignStore(app).logoFile(app)
        val logo = runCatching { if (logoFile.isFile) logoFile.readBytes() else null }.getOrNull()
        val json = Backup.build(
            shop = _shop.value,
            design = _design.value,
            logo = logo,
            clients = clients.clients.value,
            items = savedItems.items.value,
            invoices = invoices.invoices.value,
            counters = InvoiceNumber.counters(app),
            folders = savedItems.folders.value,
        )
        if (writeText(uri, json)) BackupExportOutcome.Saved else BackupExportOutcome.Failed
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
            ShopSettingsStore.write(app, backup.shop)
            InvoiceDesignStore.write(app, backup.design)
            clients.replaceAll(backup.clients)
            savedItems.replaceAll(backup.items, backup.folders)
            invoices.replaceAll(backup.invoices)
            InvoiceNumber.writeCounters(app, backup.counters)
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

    fun deleteRecord(id: String) {
        invoices.delete(id)
        if (id == draftId) newDraft()
    }

    /** Saves an issued record as a new draft, keeping the original untouched. */
    fun duplicateRecord(record: InvoiceRecord) {
        loadRecord(record.copy(id = UUID.randomUUID().toString(), status = InvoiceStatus.DRAFT))
    }
}
