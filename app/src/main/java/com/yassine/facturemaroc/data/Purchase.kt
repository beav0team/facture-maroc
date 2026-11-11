package com.yassine.facturemaroc.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

/**
 * One article on a goods receipt: what came in and what it cost.
 *
 * [itemId] is the link back to the catalogue and is required: a receipt that
 * names no article would add stock to nothing, and a label alone would let a
 * typo create a phantom shelf. The label is copied anyway, so the receipt still
 * reads after the article is renamed or deleted.
 *
 * [quantity] is whole units, like every stock movement: a supplier delivers
 * three chairs, not 2,5 of them. [unitCostHt] is what one unit cost before tax,
 * which is the number a supplier's invoice shows and the number the costing
 * methods average.
 */
data class PurchaseLine(
    val itemId: String,
    val label: String,
    val quantity: Int,
    val unitCostHt: Double,
) {
    /** What this line cost, HT. */
    val total: Double get() = quantity * unitCostHt

    fun toJson(): JSONObject = JSONObject().apply {
        put("itemId", itemId)
        put("label", label)
        put("quantity", quantity)
        put("unitCostHt", unitCostHt)
    }

    companion object {
        fun fromJson(obj: JSONObject): PurchaseLine? {
            val itemId = obj.optString("itemId", "").trim()
            if (itemId.isEmpty()) return null
            val quantity = obj.optInt("quantity", 0)
            if (quantity <= 0) return null
            return PurchaseLine(
                itemId = itemId,
                label = obj.optString("label", ""),
                quantity = quantity,
                unitCostHt = obj.optDouble("unitCostHt", 0.0)
                    .let { if (it.isNaN() || it.isInfinite() || it < 0.0) 0.0 else it },
            )
        }
    }
}

/**
 * A goods receipt — a supplier delivered articles.
 *
 * Deliberately not a [DocumentType]: a bon de réception is a purchase, not a
 * sale, and it never reaches a customer. It carries its own number series
 * (`BR-yyyyMMdd-0001`), its own file and its own screen, because mixing it into
 * the invoice history would put a supplier's delivery in the list of documents
 * the shop handed out.
 *
 * [supplierName] and [supplierIce] are copied rather than referenced: a
 * receipt is evidence of what was received that day, and renaming or deleting
 * the supplier afterwards must not rewrite it. [date] is dd/MM/yyyy, the shape
 * the rest of the app prints.
 */
data class Purchase(
    val id: String,
    val number: String,
    val date: String,
    val supplierName: String = "",
    val supplierIce: String = "",
    val lines: List<PurchaseLine> = emptyList(),
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * Stable identity of the supplier, when one was picked from the directory.
     *
     * [supplierName] stays what the paper says: a receipt is evidence, and the
     * name printed on it must not follow a later rename. The id is only the
     * link back to the party for lists and totals.
     */
    val supplierId: String = "",
) {
    /** The whole receipt, HT. */
    val totalHt: Double get() = lines.sumOf { it.total }

    /** How many units it brought in. */
    val unitCount: Int get() = lines.sumOf { it.quantity }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("number", number)
        put("date", date)
        put("supplierName", supplierName)
        if (supplierId.isNotBlank()) put("supplierId", supplierId)
        put("supplierIce", supplierIce)
        put("lines", JSONArray().apply { lines.forEach { put(it.toJson()) } })
        put("note", note)
        put("createdAt", createdAt)
    }

    companion object {
        /**
         * Reads a receipt, or null when it has no identity or no line.
         *
         * A receipt with no line moved no stock, so keeping it would show the
         * shop a delivery that changed nothing — the same rule the JSONL ledger
         * applies to a torn trailing line.
         */
        fun fromJson(obj: JSONObject): Purchase? {
            val number = obj.optString("number", "").trim()
            if (number.isEmpty()) return null
            val array = obj.optJSONArray("lines") ?: JSONArray()
            val lines = (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let(PurchaseLine::fromJson)
            }
            if (lines.isEmpty()) return null
            return Purchase(
                id = obj.optString("id", "").ifBlank { number },
                number = number,
                date = obj.optString("date", ""),
                supplierName = obj.optString("supplierName", ""),
                supplierIce = obj.optString("supplierIce", ""),
                lines = lines,
                note = obj.optString("note", ""),
                createdAt = obj.optLong("createdAt", 0L),
                supplierId = obj.optString("supplierId", "").trim(),
            )
        }
    }
}

/**
 * The receipt series: `BR-yyyyMMdd-0001`.
 *
 * A parallel series rather than a new [DocumentType], because the invoice
 * counter is a legal sequence of documents handed to customers and a goods
 * receipt is a purchase record. The number is derived from the receipts that
 * exist for that day, so no extra counter has to be backed up, restored or
 * rewound: what is on disk is what the next number follows.
 */
object PurchaseNumber {

    const val PREFIX = "BR"

    /** The printed number, using the invoice numbering machinery. */
    fun format(date: Date, value: Int): String = InvoiceNumber.format(PREFIX, date, value)

    /**
     * The next sequence for [date], one past the highest already used that day.
     *
     * Deleting the last receipt of the day makes its number free again. That is
     * safe — nothing on paper still holds it — and it keeps a shop from staring
     * at `BR-20261110-0004` after it corrected the afternoon's entries.
     */
    fun nextValue(purchases: List<Purchase>, date: Date): Int {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(date)
        val highest = purchases.mapNotNull { purchase ->
            val parts = purchase.number.split('-')
            if (parts.size < 2 || parts[parts.size - 2] != stamp) return@mapNotNull null
            parts.last().toIntOrNull()
        }.maxOrNull() ?: 0
        return highest + 1
    }

    /** The number the next receipt would carry, without writing anything. */
    fun peek(purchases: List<Purchase>, date: Date = Date()): String =
        format(date, nextValue(purchases, date))
}

/** What receiving a goods receipt did to the catalogue. */
sealed interface PurchaseOutcome {
    data object Saved : PurchaseOutcome

    /** A line names an article the catalogue does not hold. */
    data class UnknownItem(val itemId: String, val label: String) : PurchaseOutcome

    /** The receipt has no usable line, or a quantity that is not a quantity. */
    data class Invalid(val message: String) : PurchaseOutcome

    /** The ledger or the file could not be written. */
    data object WriteFailed : PurchaseOutcome
}

/**
 * What a receipt does to the ledger and the catalogue.
 *
 * Pure, like [StockMovement] and [Stocktake]: the arithmetic a shop will
 * question — "I received three, why does it say five?" — is checked by a test
 * instead of by re-reading a screen. The receipt is never written into
 * `SavedItem.stock` directly; it appends [StockEntry] lines, and the cache
 * follows exactly as it does after a sale.
 */
object PurchaseStock {

    /**
     * One purchase entry per line, signed as given.
     *
     * [reversing] writes the same quantities back out, for deleting a receipt:
     * the shop corrected the delivery, and the shelf must return to what it
     * would have been had the receipt never been entered. The reference stays
     * the receipt number so the history still names the document that was
     * cancelled.
     */
    fun entries(purchase: Purchase, reversing: Boolean = false): List<StockEntry> =
        purchase.lines.map { line ->
            StockEntry(
                itemId = line.itemId,
                delta = if (reversing) -line.quantity else line.quantity,
                reason = StockReason.PURCHASE,
                reference = purchase.number,
                note = purchase.supplierName.trim(),
                unitCost = line.unitCostHt,
                at = System.currentTimeMillis(),
            )
        }

    /**
     * [items] after a receipt (or its reversal).
     *
     * Two effects, and only two: the cached quantity moves by what was
     * received, and the article's purchase price becomes the cost of this
     * delivery. The price is the shop's best knowledge of what the article
     * costs today, and the item editor already shows it — leaving it at an old
     * figure while the receipt says otherwise is what made the margin lie.
     */
    fun apply(
        items: List<SavedItem>,
        purchase: Purchase,
        reversing: Boolean = false,
    ): List<SavedItem> {
        val received = LinkedHashMap<String, Int>()
        val latestCost = LinkedHashMap<String, Double>()
        purchase.lines.forEach { line ->
            received.merge(line.itemId, line.quantity, Int::plus)
            // Last line wins: a receipt that lists the same article twice at
            // two prices says the later one is the current price.
            if (line.unitCostHt > 0.0) latestCost[line.itemId] = line.unitCostHt
        }
        return items.map { item ->
            val units = received[item.id] ?: return@map item
            val stock = item.stock ?: return@map item
            val next = if (reversing) stock - units else stock + units
            // A reversal restores the quantity but never the old price: the
            // delivery was cancelled, not the knowledge of what the article
            // costs.
            val cost = if (reversing) null else latestCost[item.id]
            item.copy(
                stock = next,
                purchasePrice = cost ?: item.purchasePrice,
            )
        }
    }
}

/** The catalogue as the receipt code reads and writes it. */
interface ItemShelf {
    fun catalogue(): List<SavedItem>
    fun writeCatalogue(items: List<SavedItem>)
}
