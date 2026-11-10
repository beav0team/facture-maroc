package com.yassine.facturemaroc.data

import java.io.File

/** One article whose cached quantity disagrees with what the ledger says. */
data class StockDrift(
    val item: SavedItem,
    /** The `stock` field the screens have been showing. */
    val cached: Int,
    /** What the sum of the ledger says it should be. */
    val ledger: Int,
) {
    val difference: Int get() = cached - ledger
}

/**
 * The append-only history of every stock movement, one JSON object per line.
 *
 * `SavedItem.stock` is now a cache. It is what the list draws without re-reading
 * history, and it is written by the same call that appends the entry, but the
 * ledger is the truth: [rebuild] can recompute every quantity from it, and
 * [drift] says where the cache and the history disagree without touching either.
 *
 * A JSONL file rather than one JSON array for a reason that matters when a
 * phone dies: appending one line cannot destroy the lines before it, while
 * rewriting an array can. A trailing half-written line is skipped on read — the
 * movement it described never happened from the shop's point of view.
 *
 * Kept free of Android and `Context` so the arithmetic is tested on the JVM with
 * an ordinary temporary file.
 */
class StockLedger(private val file: File) {

    private val entries: MutableList<StockEntry> = read()

    /** Every entry, oldest first, as it was appended. */
    fun all(): List<StockEntry> = entries.toList()

    /** One article's history, newest first — the order the screen reads in. */
    fun entriesFor(itemId: String): List<StockEntry> =
        entries.asReversed().filter { it.itemId == itemId }

    /** The quantity one article should have, or 0 when it has no history. */
    fun balance(itemId: String): Int =
        entries.sumOf { if (it.itemId == itemId) it.delta else 0 }

    /** Appends one movement. Returns false when the file could not be written. */
    fun append(entry: StockEntry): Boolean {
        val written = runCatching {
            file.parentFile?.mkdirs()
            file.appendText(entry.toLine() + "\n")
        }.isSuccess
        // Memory follows the file, never the other way round: a failed append
        // must not make the session believe a movement that will vanish on the
        // next launch.
        if (written) entries.add(entry)
        return written
    }

    fun appendAll(movements: List<StockEntry>): Int =
        movements.count { append(it) }

    /**
     * Replaces the whole ledger, for a backup restore.
     */
    fun replaceAll(replacement: List<StockEntry>) {
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(replacement.joinToString("") { it.toLine() + "\n" })
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
        }
        entries.clear()
        entries.addAll(replacement)
    }

    /** The cached quantities that the ledger does not support. */
    fun drift(items: List<SavedItem>): List<StockDrift> = items.mapNotNull { item ->
        val cached = item.stock ?: return@mapNotNull null
        val expected = balance(item.id)
        if (cached == expected) null else StockDrift(item, cached, expected)
    }

    /**
     * [items] with every tracked quantity recomputed from the ledger.
     *
     * A service keeps its null stock: there is no shelf to recompute. An article
     * with no ledger line becomes zero, which is why [seed] exists — the startup
     * migration writes one opening entry for every quantity that predates the
     * ledger, so "no history" really does mean "nothing on the shelf".
     */
    fun rebuild(items: List<SavedItem>): List<SavedItem> = items.map { item ->
        if (item.stock == null) item else item.copy(stock = balance(item.id))
    }

    /**
     * Writes the opening quantity of articles that have no history at all.
     *
     * The catalogue already holds quantities typed before this ledger existed.
     * Without an opening line they would be invisible to a rebuild and the first
     * repair would empty every shelf. Only articles with a non-zero quantity get
     * one, and only when they have no entry yet, so running this on every launch
     * is a no-op after the first.
     */
    fun seed(items: List<SavedItem>): Int {
        val known = entries.map { it.itemId }.toSet()
        var written = 0
        items.forEach { item ->
            val stock = item.stock ?: return@forEach
            if (item.id.isBlank() || item.id in known || stock == 0) return@forEach
            if (append(StockEntry(item.id, stock, StockReason.ADJUSTMENT, note = "Stock initial"))) {
                written++
            }
        }
        return written
    }

    private fun read(): MutableList<StockEntry> {
        if (!file.isFile) return mutableListOf()
        val text = runCatching { file.readText() }.getOrElse { return mutableListOf() }
        return text.lineSequence()
            .mapNotNull(StockEntry::parse)
            .toMutableList()
    }

    companion object {
        /** The one ledger file in `filesDir`. */
        const val FILE = "stock_ledger.jsonl"
    }
}
