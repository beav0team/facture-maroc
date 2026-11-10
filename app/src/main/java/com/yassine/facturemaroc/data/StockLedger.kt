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

    /** Positions (1-based) of interior lines that could not be parsed. */
    private val droppedLines = mutableListOf<Int>()

    /** Whether the file ended in a half-written line, which is normal. */
    private var tornTail = false

    private val entries: MutableList<StockEntry> = read()

    /**
     * Lines the history could not read, as 1-based positions.
     *
     * Empty on a healthy file. A non-empty list is a hole in the history: the
     * balance is computed from what survived, so the shelf reads lower than it
     * really is. The app used to drop such a line without a word, which is how a
     * repair could quietly lower a stock count.
     */
    val unreadableLines: List<Int> get() = droppedLines.toList()

    /** Whether the last line was torn, the one thing a dying phone explains. */
    val lastLineWasTorn: Boolean get() = tornTail

    /** Every entry, oldest first, as it was appended. */
    fun all(): List<StockEntry> = entries.toList()

    /** One article's history, newest first — the order the screen reads in. */
    fun entriesFor(itemId: String): List<StockEntry> =
        entries.asReversed().filter { it.itemId == itemId }

    /**
     * One article's history in the order it happened.
     *
     * The order the costing methods replay in: a sale consumes what was on the
     * shelf before it, so reading the file backwards would value every delivery
     * after it had already been sold.
     */
    fun entriesChronological(itemId: String): List<StockEntry> =
        entries.filter { it.itemId == itemId }

    /**
     * Every article's history in one pass, each in the order it happened.
     *
     * What a screen that needs a cost for the whole catalogue must call: asking
     * [entriesChronological] once per article walks the entire file once per
     * article, which on a busy shelf is a scan per card and a second scan per
     * line at issue time.
     */
    fun byItemChronological(): Map<String, List<StockEntry>> =
        entries.groupBy { it.itemId }

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
            // The purchase price the shop already typed is the opening cost: an
            // article that predates the ledger has no delivery to average, and
            // starting at zero would report a margin of 100 % until the next
            // purchase corrected it.
            if (
                append(
                    StockEntry(
                        item.id,
                        stock,
                        StockReason.ADJUSTMENT,
                        note = "Stock initial",
                        unitCost = item.purchasePrice.coerceAtLeast(0.0),
                    ),
                )
            ) {
                written++
            }
        }
        return written
    }

    /**
     * The most recent inbound cost for [itemId], or null when none is known.
     *
     * Used by a manual quantity correction: the shop is changing how many are
     * on the shelf, not what they cost, so the correction carries the last cost
     * the ledger knows instead of resetting the average to zero.
     */
    fun lastCost(itemId: String): Double? =
        entries.lastOrNull { it.itemId == itemId && it.delta > 0 && it.unitCost > 0.0 }?.unitCost

    private fun read(): MutableList<StockEntry> {
        if (!file.isFile) return mutableListOf()
        val text = runCatching { file.readText() }.getOrElse {
            android.util.Log.e(TAG, "the ledger could not be read", it)
            return mutableListOf()
        }
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        val parsed = mutableListOf<StockEntry>()
        lines.forEachIndexed { index, line ->
            val entry = StockEntry.parse(line)
            if (entry != null) {
                parsed += entry
                return@forEachIndexed
            }
            // Only the last line can be torn: it was being appended when the
            // process stopped, so the movement it describes never finished and
            // dropping it loses nothing. Any other unreadable line is a hole in
            // the history and is reported rather than skipped in silence.
            if (index == lines.lastIndex) {
                tornTail = true
                android.util.Log.w(TAG, "dropped a torn last ledger line")
            } else {
                droppedLines += index + 1
                android.util.Log.e(TAG, "unreadable ledger line ${index + 1}")
            }
        }
        return parsed
    }

    companion object {
        private const val TAG = "StockLedger"

        /** The one ledger file in `filesDir`. */
        const val FILE = "stock_ledger.jsonl"
    }
}
