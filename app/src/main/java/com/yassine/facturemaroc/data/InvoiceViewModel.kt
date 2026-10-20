package com.yassine.facturemaroc.data

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * The invoice being written, and the shop and design it will be printed with.
 *
 * Held on a ViewModel so a rotation does not lose a half-built invoice, which is
 * the one thing a person would be genuinely annoyed to lose mid-sentence with a
 * customer. It is not held across process death: an unsaved draft is not worth
 * writing to disk, and [InvoiceLine] deliberately copies its fields rather than
 * holding catalogue references, so a restored one can never be stale.
 */
class InvoiceViewModel(application: Application) : AndroidViewModel(application) {

    val clients = ClientStore(application)
    val savedItems = SavedItemStore(application)

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

    private val _design = MutableStateFlow(InvoiceDesign.default())
    val design: StateFlow<InvoiceDesign> = _design.asStateFlow()

    /** How many lines are on the draft, which is what the top bar badge shows. */
    val lineCount: Int get() = _lines.value.sumOf { it.quantity }

    /**
     * Adds [line] to the invoice, or increments quantity of an identical line.
     *
     * Matched on description alone, because that is what decides whether the
     * customer is getting the same item again: two lines for the same item at
     * different prices are a mistake to fix by hand.
     */
    fun addLine(line: InvoiceLine) {
        val existing = _lines.value.firstOrNull { it.description == line.description }
        _lines.value = if (existing == null) {
            _lines.value + line
        } else {
            _lines.value.map {
                if (it === existing) it.copy(quantity = it.quantity + 1) else it
            }
        }
    }

    fun setQuantity(index: Int, quantity: Int) {
        val current = _lines.value.getOrNull(index) ?: return
        // A quantity of zero is a removal, not an invoice line with nothing on
        // it, so it is taken as the instruction it means.
        if (quantity <= 0) {
            remove(index)
            return
        }
        _lines.value = _lines.value.mapIndexed { i, line ->
            if (i == index) line.copy(quantity = quantity) else line
        }
    }

    /** Changes the unit price, which is how a negotiated price is recorded. */
    fun setUnitPrice(index: Int, price: Double) {
        _lines.value = _lines.value.mapIndexed { i, line ->
            if (i == index) line.copy(unitPrice = price.coerceAtLeast(0.0)) else line
        }
    }

    /** Applies a discount to this line only, before the invoice-level discount. */
    fun setLineDiscount(index: Int, percent: Int) {
        _lines.value = _lines.value.mapIndexed { i, line ->
            if (i == index) line.copy(lineDiscountPercent = percent.coerceIn(0, 100)) else line
        }
    }

    fun remove(index: Int) {
        _lines.value = _lines.value.filterIndexed { i, _ -> i != index }
    }

    fun setClient(client: InvoiceClient) {
        _client.value = client
    }

    /** Remembers the client for next time, but only if there is something to. */
    fun saveClient() {
        val current = _client.value
        if (current.name.isNotBlank()) clients.save(current)
    }

    fun setDiscount(percent: Int) {
        _discountPercent.value = percent.coerceIn(0, 100)
    }

    fun setPaymentMethod(method: PaymentMethod) {
        _paymentMethod.value = method
    }

    fun setDocumentType(type: DocumentType) {
        _documentType.value = type
        // Reset type-specific fields when switching
        if (type != DocumentType.DEVIS) _validityDate.value = null
        if (type != DocumentType.BON_DE_COMMANDE) _supplier.value = null
    }

    fun setValidityDate(date: String?) {
        _validityDate.value = date
    }

    fun setSupplier(supplier: InvoiceClient?) {
        _supplier.value = supplier
    }

    fun updateShop(settings: ShopSettings) {
        ShopSettingsStore.write(getApplication(), settings)
        _shop.value = settings
    }

    fun updateDesign(design: InvoiceDesign) {
        InvoiceDesignStore.write(getApplication(), design)
        _design.value = design
    }

    /**
     * Reads the picked logo and stores it.
     *
     * The bytes are copied into the app's own files directory rather than keeping
     * the URI, because the grant behind a photo-picker URI does not outlive the
     * process. Holding the URI would mean the logo silently disappeared from the
     * next invoice.
     */
    suspend fun chooseLogo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val bytes = runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext false

        // A screenshot or a photo can be several megabytes. The logo is drawn at
        // 40pt on the page, so decoding a huge original onto a PDF is memory
        // spent for no visible gain.
        if (bytes.size > MAX_LOGO_BYTES) return@withContext false

        val stored = InvoiceDesignStore.saveLogo(getApplication(), bytes)
        if (stored) _design.value = InvoiceDesignStore.read(getApplication())
        stored
    }

    fun removeLogo() {
        InvoiceDesignStore.removeLogo(getApplication())
        _design.value = InvoiceDesignStore.read(getApplication())
    }

    /**
     * Assembles the document, taking the next invoice number.
     *
     * The number is consumed here rather than when the screen opened, so a number
     * is only ever spent on a document that was really produced. The TVA rate
     * comes from the shop's own settings rather than being asked for on every
     * invoice, because it is the same number every time.
     */
    fun buildData(): InvoiceData = InvoiceData(
        number = InvoiceNumber.consume(getApplication(), _documentType.value),
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

    /** The draft as it currently stands, for the live preview in the design screen. */
    fun previewData(): InvoiceData = InvoiceData(
        number = InvoiceNumber.peek(getApplication(), _documentType.value),
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

    /** Peek at the next invoice number without consuming it. */
    fun nextNumber(): String = InvoiceNumber.peek(getApplication(), _documentType.value)

    /** Clears the draft after a document has been produced. */
    fun clear() {
        _lines.value = emptyList()
        _discountPercent.value = 0
        _paymentMethod.value = PaymentMethod.CASH
        _validityDate.value = null
        _supplier.value = null
    }

    /** Renders the current draft to a bitmap, for the design screen preview. */
    suspend fun previewBitmap(widthPx: Int): Bitmap? = withContext(Dispatchers.Default) {
        InvoicePdf.preview(
            getApplication(),
            previewData(),
            _shop.value,
            _design.value,
            widthPx,
        )
    }

    /**
     * Renders the invoice and hands it to the share sheet.
     *
     * The draft is cleared only when the document was really produced and
     * handed over. A failure leaves the lines in place, because a shop whose
     * phone has no app that accepts a PDF should be able to press the button
     * again on a different device rather than rebuild the invoice from nothing.
     */
    suspend fun shareInvoice(): InvoicePdf.Outcome = withContext(Dispatchers.IO) {
        val data = buildData()
        val outcome = InvoicePdf.share(getApplication(), data, _shop.value, _design.value)
        if (outcome is InvoicePdf.Outcome.Shared) clear()
        outcome
    }

    private companion object {
        const val MAX_LOGO_BYTES = 4 * 1024 * 1024
    }
}
