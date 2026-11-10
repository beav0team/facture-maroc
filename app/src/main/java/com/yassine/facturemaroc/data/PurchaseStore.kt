package com.yassine.facturemaroc.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date

/**
 * The shop's goods receipts, newest first.
 *
 * A JSON file rather than the ledger, because a receipt is a document with a
 * number, a supplier and a date that the shop may want to read again; the
 * ledger holds only the movements it caused. Written to a temporary file and
 * renamed over the live one, so a phone that dies mid-write leaves the old list
 * or the new one and never a half-file.
 *
 * The store owns the whole operation, not just the file: receiving appends the
 * ledger entries and moves the cached quantities, deleting appends the reverse
 * entries. That is what keeps a cancelled receipt from leaving phantom stock —
 * a deletion that only removed the row would leave the shelf holding goods
 * nobody can account for.
 *
 * Free of `Context` so the arithmetic and the file round trip are tested on the
 * JVM with an ordinary temporary folder.
 */
class PurchaseStore(
    private val file: File,
    private val ledger: StockLedger,
    private val shelf: ItemShelf,
    /**
     * Told when a write fails, so a receipt that could not be saved is reported
     * instead of looking saved until the next launch.
     */
    private val onWriteError: (String) -> Unit = {},
) {

    constructor(
        context: Context,
        savedItems: SavedItemStore,
        onWriteError: (String) -> Unit = {},
    ) : this(
        File(context.filesDir, FILE),
        savedItems.ledger,
        savedItems,
        onWriteError,
    )

    private val _purchases = MutableStateFlow(read())
    val purchases: StateFlow<List<Purchase>> = _purchases.asStateFlow()

    /** Every receipt, newest first. */
    fun all(): List<Purchase> = _purchases.value

    /** The number the next receipt will carry, read from what already exists. */
    fun nextNumber(date: Date = Date()): String = PurchaseNumber.peek(_purchases.value, date)

    /**
     * Receives [purchase]: ledger first, then the cache, then the receipt.
     *
     * Every line is checked before anything is written. A receipt naming an
     * article that no longer exists is refused rather than silently dropped:
     * dropping the line would record a delivery that arrived and vanished, and
     * accepting it would credit stock to a shelf nobody can open.
     */
    fun add(purchase: Purchase): PurchaseOutcome {
        if (purchase.lines.isEmpty()) {
            return PurchaseOutcome.Invalid("Un bon de réception doit contenir au moins un article.")
        }
        val items = shelf.catalogue()
        val known = items.map { it.id }.toSet()
        purchase.lines.forEach { line ->
            if (line.itemId.isBlank() || line.itemId !in known) {
                return PurchaseOutcome.UnknownItem(line.itemId, line.label)
            }
        }
        // The ledger is the truth: nothing moves on the shelf until every line
        // is in it. A partial write would make the cache and history disagree
        // on the first receipt of a busy morning.
        if (ledger.appendAll(PurchaseStock.entries(purchase)) != purchase.lines.size) {
            return PurchaseOutcome.WriteFailed
        }
        shelf.writeCatalogue(PurchaseStock.apply(items, purchase))
        persist(listOf(purchase) + _purchases.value)
        return PurchaseOutcome.Saved
    }

    /**
     * Deletes [purchase] and reverses what it brought in.
     *
     * The stock returns to the ledger as a signed movement, not as a quiet
     * subtraction: the history keeps the receipt number and says it was
     * cancelled, exactly like a credit note corrects an invoice.
     */
    fun delete(purchase: Purchase): Boolean {
        val remaining = _purchases.value.filterNot { it.id == purchase.id }
        if (remaining.size == _purchases.value.size) return false
        ledger.appendAll(PurchaseStock.entries(purchase, reversing = true))
        shelf.writeCatalogue(PurchaseStock.apply(shelf.catalogue(), purchase, reversing = true))
        persist(remaining)
        return true
    }

    /**
     * Replaces the whole list, for a backup restore.
     *
     * The ledger is restored separately and already holds the movements these
     * receipts produced; writing them again here would double every delivery.
     */
    fun replaceAll(purchases: List<Purchase>) {
        persist(purchases)
    }

    private fun persist(list: List<Purchase>): Boolean {
        val clean = list
            .filter { it.lines.isNotEmpty() && it.number.isNotBlank() }
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
        val payload = JSONObject().apply {
            put("version", 1)
            put(
                "purchases",
                JSONArray().apply { clean.forEach { put(it.toJson()) } },
            )
        }
        val written = runCatching {
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(payload.toString())
            if (!temp.renameTo(file)) {
                file.writeText(payload.toString())
                temp.delete()
            }
        }.onFailure {
            android.util.Log.e(TAG, "could not write the receipts", it)
        }.isSuccess
        if (!written) {
            onWriteError("Les réceptions n'ont pas pu être enregistrées. Vérifiez l'espace libre.")
            return false
        }
        _purchases.value = clean
        return true
    }

    private fun read(): List<Purchase> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val root = JSONObject(file.readText())
            val array = root.optJSONArray("purchases") ?: JSONArray()
            (0 until array.length())
                .mapNotNull { index -> array.optJSONObject(index)?.let(Purchase::fromJson) }
                .sortedByDescending { it.createdAt }
        }.getOrElse { emptyList() }
    }

    companion object {
        private const val TAG = "PurchaseStore"

        const val FILE = "purchases.json"
    }
}
