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
import com.yassine.facturemaroc.invoice.InvoicePdf

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

    // Alias for compatibility
    val shopSettings: StateFlow<ShopSettings> = shop

    private val _design = MutableStateFlow(InvoiceDesign.default())
    val design: StateFlow<InvoiceDesign> = _design.asStateFlow()

    /** Combined invoice state for the builder screen. */
    private val _invoice = MutableStateFlow(buildInvoiceData())
    val invoice: StateFlow<InvoiceData> = _invoice

    private fun buildInvoiceData(): InvoiceData = InvoiceData(
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

    private fun refreshInvoice() {
        _invoice.value = buildInvoiceData()
    }

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
        refreshInvoice()
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
        refreshInvoice()
    }

    /** Changes the unit price, which is how a negotiated price is recorded. */
    fun setUnitPrice(index: Int, price: Double) {
        _lines.value = _lines.value.mapIndexed { i, line ->
            if (i == index) line.copy(unitPrice = price.coerceAtLeast(0.0)) else line
        }
        refreshInvoice()
    }

    /** Applies a discount to this line only, before the invoice-level discount. */
    fun setLineDiscount(index: Int, percent: Int) {
        _lines.value = _lines.value.mapIndexed { i, line ->
            if (i == index) line.copy(lineDiscountPercent = percent.coerceIn(0, 100)) else line
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
        _documentType.value = type
        // Reset type-specific fields when switching
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
     * Assembles the document, taking the next invoice number.
     *
     * The number is consumed here rather than when the screen opened, so a number
     * is only ever spent on a document that was really produced. The TVA rate
     * comes from the shop's own settings rather than being asked for on every
     * invoice, because it is the same number every time.
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

    /** The draft as it currently stands, for the live preview in the design screen. */
    fun previewData(): InvoiceData = InvoiceData(
        number = InvoiceNumber.peek(getApplication<Application>(), _documentType.value),
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
    fun nextNumber(): String = InvoiceNumber.peek(getApplication<Application>(), _documentType.value)

    /** Clears the draft after a document has been produced. */
    fun clear() {
        _lines.value = emptyList()
        _discountPercent.value = 0
        _paymentMethod.value = PaymentMethod.CASH
        _validityDate.value = null
        _supplier.value = null
        refreshInvoice()
    }

    /** Renders the current draft to a bitmap, for the design screen preview. */
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
     * The draft is cleared only when the document was really produced and
     * handed over. A failure leaves the lines in place, because a shop whose
     * phone has no app that accepts a PDF should be able to press the button
     * again on a different device rather than rebuild the invoice from nothing.
     */
    suspend fun shareInvoice(): InvoicePdf.Outcome = withContext(Dispatchers.IO) {
        val data = buildData()
        val outcome = InvoicePdf.share(getApplication<Application>(), data, _shop.value, _design.value)
        if (outcome is InvoicePdf.Outcome.Shared) clear()
        outcome
    }

    private companion object {
        const val MAX_LOGO_BYTES = 4 * 1024 * 1024
    }
}
