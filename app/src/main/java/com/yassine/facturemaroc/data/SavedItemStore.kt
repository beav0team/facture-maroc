package com.yassine.facturemaroc.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * The shop's own items, saved once and reused on every invoice.
 *
 * A JSON file for the same reason as [ClientStore]: an item is a label and a
 * price, a shop has tens of them, and pulling in a database for that would
 * be machinery in place of a list. Written to a temporary file and renamed over
 * the live one, so a phone that dies mid-write leaves the old list or the new
 * one and never a half-file that will not parse on the next launch.
 *
 * Folders are stored beside the items rather than derived from them, so an
 * empty folder survives until the shop deletes it. They nest: a folder is a
 * path ("Mobilier/Chaises"), which is what lets a shop put its chairs
 * under Mobilier without a table of nodes or an id on every item. The file has
 * been version 3 since the inventory arrived: version 2 added folders, version
 * 3 added the barcode, the purchase price and the quantity on the shelf. Both
 * older shapes still load — a version 1 file is a bare array of items and is
 * read as "everything in Sans dossier", and a version 2 item is read as a
 * product whose stock is not counted, which is exactly what it was.
 */
class SavedItemStore(
    context: Context,
    /**
     * The movement history, injected so the repair check can share the same
     * instance the store writes through. A second reader on the same file would
     * show a stale balance the moment a sale was recorded.
     */
    val ledger: StockLedger = StockLedger(File(context.filesDir, StockLedger.FILE)),
) : ItemShelf {

    private val file = File(context.filesDir, FILE)
    private val _items = MutableStateFlow<List<SavedItem>>(emptyList())
    val items: StateFlow<List<SavedItem>> = _items.asStateFlow()

    private val _folders = MutableStateFlow<List<String>>(emptyList())
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    init {
        val content = load()
        _items.value = content.items
        _folders.value = content.folders
    }

    /**
     * Adds an item, or updates the one already saved under the same label.
     *
     * Matched on the label alone, ignoring case and surrounding space, because
     * that is what a person means when they retype "Main-d'œuvre" rather than
     * adding it a second time at a different price. The price is not part of the
     * key: raising a price means editing the item that is already there.
     */
    fun save(item: SavedItem) {
        if (item.label.isBlank()) return
        val clean = item.copy(
            label = item.label.trim(),
            folder = item.folder.trim(),
        )
        val existing = _items.value.toMutableList()
        val index = indexOfIdentity(existing, clean)
        val previous = existing.getOrNull(index)
        // Identity belongs to the row, not to the form that edited it. A blank
        // id means "I do not know which article this is", which is exactly what
        // a screen built from an old item looks like, and the row it matched
        // keeps its id. A brand-new row takes the id it was handed or gets one.
        val id = previous?.id?.takeIf { it.isNotBlank() }
            ?: clean.id.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()
        val saved = clean.copy(id = id)
        if (index >= 0) {
            existing[index] = saved
        } else {
            existing.add(saved)
        }
        persist(existing, _folders.value)
        // Editing the quantity in the form is a movement like any other. Writing
        // it only into the cache would make the next repair "correct" a number
        // the shop typed on purpose, so it is written into history as well.
        val delta = when {
            previous == null -> saved.stock ?: 0
            previous.stock == null -> saved.stock ?: 0
            saved.stock == null -> 0
            else -> saved.stock - previous.stock
        }
        if (delta != 0) {
            ledger.append(
                StockEntry(
                    itemId = id,
                    delta = delta,
                    reason = StockReason.ADJUSTMENT,
                    note = if (previous == null) "Stock initial" else "Correction manuelle",
                    // Changing how many are on the shelf is not changing what
                    // they cost: the correction carries the price the shop just
                    // typed, or the last cost the ledger knows, instead of
                    // resetting the average to zero and reporting a margin of
                    // 100 % afterwards.
                    unitCost = saved.purchasePrice.takeIf { it > 0.0 }
                        ?: ledger.lastCost(id)
                        ?: 0.0,
                ),
            )
        }
    }

    fun delete(item: SavedItem) {
        val key = item.label.trim().lowercase()
        persist(_items.value.filterNot { it.label.trim().lowercase() == key }, _folders.value)
    }

    /** Replaces the whole list, for a backup restore. */
    fun replaceAll(items: List<SavedItem>, folders: List<String> = emptyList()) {
        persist(
            items
                .filter { it.label.isNotBlank() && it.price > 0.0 }
                .distinctBy { it.label.trim().lowercase() },
            folders,
        )
    }

    /**
     * Gives old articles their id and links old invoice lines, once.
     *
     * The file is copied to `saved_items.json.avant-migration` before the first
     * rewrite, because this is the only operation that changes every row of the
     * catalogue at once. The copy is kept only when the migration really changes
     * something, so a shop that has already migrated is not given a new file
     * (and a restore cannot make the "before" copy describe the new file).
     *
     * Returns the records, linked where they could be. The caller writes them
     * back through [InvoiceStore], which owns that file.
     */
    fun migrateIdentities(records: List<InvoiceRecord>): List<InvoiceRecord> {
        val plan = ItemMigration.plan(_items.value, records)
        if (!plan.changed) return records
        runCatching {
            val copy = File(file.parentFile, "$FILE.avant-migration")
            if (file.isFile && !copy.exists()) file.copyTo(copy, overwrite = false)
        }
        persist(plan.items, _folders.value)
        return plan.records
    }

    /**
     * Creates an empty folder inside [parent] and returns its path.
     *
     * Returns null when the name is blank, contains the separator, or is
     * already taken by a sibling. Folders nest, so a name is only unique among
     * its siblings: "Chaises" may exist under Mobilier and under Vêtements.
     */
    fun createFolder(name: String, parent: String = ""): String? {
        val clean = name.trim()
        if (clean.isEmpty() || clean.contains(SEPARATOR)) return null
        val home = parent.trim()
        if (home.isNotEmpty() && _folders.value.none { it.equals(home, ignoreCase = true) }) {
            return null
        }
        val path = join(home, clean)
        if (_folders.value.any { it.equals(path, ignoreCase = true) }) return null
        persist(_items.value, _folders.value + path)
        return path
    }

    /**
     * Renames the last segment of [path] and returns the new path.
     *
     * Everything below it follows: sub-folders keep their place in the tree and
     * every article keeps its folder, because renaming a shelf must not spill
     * its contents. Returns null when the new name is blank, contains the
     * separator, or names a sibling that already exists.
     */
    fun renameFolder(path: String, newName: String): String? {
        val clean = newName.trim()
        if (clean.isEmpty() || clean.contains(SEPARATOR)) return null
        val known = _folders.value.firstOrNull { it.equals(path, ignoreCase = true) } ?: return null
        val parent = parentOf(known)
        val renamed = join(parent, clean)
        if (_folders.value.any { it.equals(renamed, ignoreCase = true) }) return null
        persist(
            _items.value.map { item -> item.copy(folder = rebase(item.folder, known, renamed)) },
            _folders.value.map { folder -> rebase(folder, known, renamed) },
        )
        return renamed
    }

    /**
     * Deletes [path] and everything below it, but keeps the articles.
     *
     * Deleting a folder that quietly deleted the work inside it would be the
     * most expensive mistake this screen could make, so every article in the
     * subtree moves up to [parentOf] the deleted folder — into "Sans dossier"
     * when it was at the top — and stays on every invoice it is already on.
     * Sub-folders go with it: leaving a tree of empty shelves behind a delete
     * would need a second delete for each of them.
     */
    fun deleteFolder(path: String) {
        val known = _folders.value.firstOrNull { it.equals(path, ignoreCase = true) } ?: return
        val home = parentOf(known)
        val items = _items.value.map { item ->
            if (isIn(item.folder, known)) item.copy(folder = home) else item
        }
        val folders = _folders.value.filterNot { isIn(it, known) }
        persist(items, folders)
    }

    /**
     * Items whose label, barcode, unit or folder contains [query].
     *
     * Called on every keystroke against tens of items, so it filters the
     * in-memory list rather than re-reading the file. [folder] limits the
     * result to that folder **and everything below it** — a search inside
     * Mobilier that ignored Mobilier/Chaises would tell a shop its chairs do
     * not exist. `""` means "Sans dossier" and `null` means all.
     */
    fun search(query: String, folder: String? = null): List<SavedItem> {
        val scoped = if (folder == null) _items.value else itemsIn(folder, _items.value, deep = true)
        val q = query.trim().lowercase()
        if (q.isEmpty()) return scoped
        // Every word has to appear somewhere, so "ali pose" finds
        // "Aligner une roue" as well as "Pose de film", and a mistyped word
        // narrows the list instead of emptying it.
        return scoped.filter { matches(it, q) }
    }

    /**
     * Applies what [data] sold, and returns the items whose stock moved.
     *
     * Called once, when a document becomes Émise, and never when a draft is
     * saved — a half-written invoice is not a sale. [reversing] puts the
     * quantities back, which is what deleting an issued document does.
     *
     * Every movement is appended to [ledger] before the cached quantity is
     * rewritten, and each entry carries the document number: the shelf can now
     * answer "why does it say three?" instead of merely saying three.
     *
     * Everything is computed by [StockMovement] before this touches a file: if
     * the arithmetic is wrong, a test says so, and no screen can be the only
     * place the rule lives.
     */
    fun applyStockMovement(data: InvoiceData, reversing: Boolean = false): List<SavedItem> {
        val moves = StockMovement.moves(_items.value, data, reversing)
        if (moves.isEmpty()) return emptyList()
        moves.forEach { move ->
            ledger.append(
                StockEntry(
                    itemId = move.item.id,
                    delta = move.units,
                    // The sign, not the document type, says what happened: a
                    // reversal of a facture is a credit and a reversal of an
                    // avoir is a sale, and the history should say so.
                    reason = if (move.units > 0) StockReason.CREDIT else StockReason.INVOICE,
                    reference = data.number,
                ),
            )
        }
        persist(StockMovement.apply(_items.value, data, reversing), _folders.value)
        return moves.map { move ->
            move.item.copy(stock = (move.item.stock ?: 0) + move.units)
        }
    }

    /** One article's movements, newest first, for its history screen. */
    fun entriesFor(itemId: String): List<StockEntry> = ledger.entriesFor(itemId)

    /**
     * What one unit of [itemId] is worth under [method], or null when unknown.
     *
     * Read from the ledger, not from the article's purchase price: the price
     * says what the last delivery cost, while the cost says what the shelf is
     * worth after every delivery and sale that came before it. An issued line
     * freezes this number, so a later purchase can never rewrite an old
     * invoice's margin.
     */
    fun unitCostOf(itemId: String, method: CostMethod): Double? =
        StockCost.unitCost(ledger.entriesChronological(itemId), method)

    /** The catalogue as a receipt reads it. */
    override fun catalogue(): List<SavedItem> = _items.value

    /** Writes the catalogue back after a receipt moved a quantity. */
    override fun writeCatalogue(items: List<SavedItem>) = persist(items, _folders.value)

    /**
     * Writes an opening entry for every quantity that predates the ledger.
     *
     * Called once at startup, after ids exist. Without it a repair would read
     * "no history" as "nothing on the shelf" and empty the catalogue.
     */
    fun seedLedger(): Int = ledger.seed(_items.value)

    /** Where the cached quantities and the ledger disagree; read-only. */
    fun stockDrift(): List<StockDrift> = ledger.drift(_items.value)

    /**
     * Rewrites every cached quantity from the ledger and returns what changed.
     *
     * Only ever called after the shop has seen [stockDrift]: a repair that ran
     * by itself would hide the very disagreement that shows a bug exists.
     */
    fun rebuildStockCache(): List<StockDrift> {
        val drift = ledger.drift(_items.value)
        if (drift.isEmpty()) return emptyList()
        persist(ledger.rebuild(_items.value), _folders.value)
        return drift
    }

    /**
     * Installs a ledger from a backup.
     *
     * A backup written before the ledger existed has none, so the restored
     * articles' quantities become opening entries: they are real, they were
     * typed by the shop, and a repair must keep them.
     */
    fun restoreLedger(entries: List<StockEntry>) {
        ledger.replaceAll(entries)
        if (entries.isEmpty()) ledger.seed(_items.value)
    }

    /**
     * Writes a validated stocktake: the differences into the ledger, the counted
     * numbers into the cache.
     *
     * The counted quantity is written as-is rather than as `expected + delta`,
     * because it is what the shop physically saw. The two agree by construction —
     * [Stocktake.entries] derives the delta from the same rows — but writing the
     * physical number keeps a rounding bug from silently becoming a new shelf
     * quantity.
     *
     * Returns the number of articles corrected.
     */
    fun recordStocktake(entries: List<StockEntry>, counted: Map<String, Int>): Int {
        if (entries.isEmpty()) return 0
        val written = ledger.appendAll(entries)
        if (written == 0) return 0
        persist(
            _items.value.map { item ->
                val count = counted[item.id]
                if (item.stock == null || count == null) item else item.copy(stock = count)
            },
            _folders.value,
        )
        return written
    }

    private fun sorted(list: List<SavedItem>): List<SavedItem> =
        list.sortedBy { it.label.trim().lowercase() }

    private fun cleanFolders(folders: List<String>, items: List<SavedItem>): List<String> =
        withAncestors(folders + items.map { it.folder })

    private fun load(): Content {
        if (!file.isFile) return Content(emptyList(), emptyList())
        return runCatching {
            val text = file.readText().trim()
            if (text.startsWith("[")) {
                // Version 1 had no folders at all.
                val array = JSONArray(text)
                val items = (0 until array.length())
                    .mapNotNull { index -> array.optJSONObject(index)?.toItem() }
                Content(sorted(items), cleanFolders(emptyList(), items))
            } else {
                val root = JSONObject(text)
                val itemsArray = root.optJSONArray("items") ?: JSONArray()
                val items = (0 until itemsArray.length())
                    .mapNotNull { index -> itemsArray.optJSONObject(index)?.toItem() }
                val foldersArray = root.optJSONArray("folders") ?: JSONArray()
                val folders = (0 until foldersArray.length())
                    .mapNotNull { index ->
                        foldersArray.optString(index, "").trim().takeIf { it.isNotEmpty() }
                    }
                Content(sorted(items), cleanFolders(folders, items))
            }
        }.getOrElse { Content(emptyList(), emptyList()) }
    }

    private fun persist(items: List<SavedItem>, folders: List<String>) {
        // An id is assigned at the door and never re-rolled: a blank one would
        // mean a rename could adopt the identity of another article.
        val identified = items.map { item ->
            if (item.id.isBlank()) item.copy(id = UUID.randomUUID().toString()) else item
        }
        val cleanItems = sorted(identified)
        _items.value = cleanItems
        _folders.value = cleanFolders(folders, cleanItems)
        val payload = JSONObject().apply {
            put("version", 3)
            put(
                "folders",
                JSONArray().apply { _folders.value.forEach { put(it) } },
            )
            put(
                "items",
                JSONArray().apply { cleanItems.forEach { put(it.toJson()) } },
            )
        }
        runCatching {
            val temp = File(file.parentFile, "$FILE.tmp")
            temp.writeText(payload.toString())
            // The rename is the atomic step: it is what makes a half-written file
            // impossible rather than merely unlikely.
            if (!temp.renameTo(file)) {
                file.writeText(payload.toString())
                temp.delete()
            }
        }
    }

    private fun SavedItem.toJson() = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("price", price)
        put("unit", unit)
        put("folder", folder)
        // Written on every save and read as "absent" by older files, so a
        // version 2 file stays a valid catalogue of products with no quantities.
        put("barcode", barcode)
        put("purchasePrice", purchasePrice)
        if (stock != null) put("stock", stock)
        if (reorderThreshold != null) put("reorderThreshold", reorderThreshold)
        // Price tiers: written as an array of {fromQuantity, unitPriceHt}.
        // Absent on older files, which means the single price applies.
        if (priceTiers.isNotEmpty()) {
            put(
                "priceTiers",
                JSONArray().apply {
                    priceTiers.forEach { tier ->
                        put(
                            JSONObject().apply {
                                put("fromQuantity", tier.fromQuantity)
                                put("unitPriceHt", tier.unitPriceHt)
                            },
                        )
                    }
                },
            )
        }
        // Attributes: written as an array of {key, value}. Absent on older
        // files, which means no attributes.
        if (attributes.isNotEmpty()) {
            put(
                "attributes",
                JSONArray().apply {
                    attributes.forEach { attr ->
                        put(
                            JSONObject().apply {
                                put("key", attr.key)
                                put("value", attr.value)
                            },
                        )
                    }
                },
            )
        }
    }

    private fun JSONObject.toItem() = SavedItem(
        // Absent on every item written before ids existed; the store assigns
        // one before the file is written again.
        id = optString("id", "").trim(),
        label = optString("label", ""),
        price = optDouble("price", 0.0),
        unit = optString("unit", ""),
        folder = optString("folder", ""),
        barcode = optString("barcode", ""),
        purchasePrice = optDouble("purchasePrice", 0.0).let { if (it.isNaN()) 0.0 else it },
        // No "stock" key means an article saved before the inventory existed,
        // which was a catalogue entry and not a counted product.
        stock = if (has("stock") && !isNull("stock")) optInt("stock", 0) else null,
        // No key means the shop never set one, which is not the same as zero.
        reorderThreshold = if (has("reorderThreshold") && !isNull("reorderThreshold")) {
            optInt("reorderThreshold", 0).coerceAtLeast(0)
        } else {
            null
        },
        // Price tiers: absent on older files means the single price applies.
        priceTiers = if (has("priceTiers") && !isNull("priceTiers")) {
            val array = optJSONArray("priceTiers")
            if (array == null) emptyList() else {
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let { tier ->
                        PriceTier(
                            fromQuantity = tier.optInt("fromQuantity", 0),
                            unitPriceHt = tier.optDouble("unitPriceHt", 0.0)
                                .let { if (it.isNaN() || it.isInfinite()) 0.0 else it },
                        )
                    }
                }
            }
        } else {
            emptyList()
        },
        // Attributes: absent on older files means none.
        attributes = if (has("attributes") && !isNull("attributes")) {
            val array = optJSONArray("attributes")
            if (array == null) emptyList() else {
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let { attr ->
                        ItemAttribute(
                            key = attr.optString("key", ""),
                            value = attr.optString("value", ""),
                        )
                    }
                }
            }
        } else {
            emptyList()
        },
    ).takeIf {
        // An item with no price is not an item: it would sit on the list unable
        // to be added, because adding it would put a zero on the invoice.
        it.label.isNotBlank() && it.price > 0.0
    }

    private data class Content(
        val items: List<SavedItem>,
        val folders: List<String>,
    )

    companion object {
        private const val FILE = "saved_items.json"

        /**
         * What separates a folder from its parent inside a stored path.
         *
         * A path rather than a list of ids: folders nest, and "Mobilier/Chaises" is
         * a name a person can read in the JSON file, sorts next to
         * "Mobilier/Chaises/Rotin", and needs no table of nodes to resolve. It also
         * leaves the file shape alone, so every catalogue written by the flat
         * version still loads.
         */
        const val SEPARATOR = "/"
        private const val SEPARATOR_DISPLAY = " › "

        /** The folder one level up; `""` for a top-level folder. */
        fun parentOf(path: String): String = path.substringBeforeLast(SEPARATOR, "")

        /** The folder's own name, without the path that leads to it. */
        fun nameOf(path: String): String = path.substringAfterLast(SEPARATOR)

        /**
         * Where [item] belongs in [items]: by id first, then by label.
         *
         * Id first is what makes a rename keep its history. The screen sends the
         * edited article with the id it already had and the new label; matching
         * on the label alone would add a second row and leave the shelf, the
         * movements and the reorder threshold on a ghost.
         *
         * A blank id falls back to the old label rule, which is how an article
         * saved by a screen that did not know about ids still replaces itself
         * instead of duplicating.
         */
        fun indexOfIdentity(items: List<SavedItem>, item: SavedItem): Int {
            val id = item.id.trim()
            if (id.isNotEmpty()) {
                val byId = items.indexOfFirst { it.id == id }
                if (byId >= 0) return byId
            }
            val label = item.label.trim().lowercase()
            return items.indexOfFirst { it.label.trim().lowercase() == label }
        }

        /** [name] placed inside [parent]. */
        fun join(parent: String, name: String): String =
            if (parent.isBlank()) name else "$parent$SEPARATOR$name"

        /** How a path is drawn in the interface: `Mobilier › Chaises`. */
        fun display(path: String): String =
            path.split(SEPARATOR).filter { it.isNotEmpty() }.joinToString(SEPARATOR_DISPLAY)

        /** Whether [folder] is [path] itself or lives somewhere below it. */
        fun isIn(folder: String, path: String): Boolean =
            folder.equals(path, ignoreCase = true) ||
                (path.isNotEmpty() && folder.startsWith("$path$SEPARATOR", ignoreCase = true))

        /** [folder] with the part named by [from] replaced by [to]. */
        fun rebase(folder: String, from: String, to: String): String =
            if (!isIn(folder, from)) {
                folder
            } else {
                to + folder.substring(from.length)
            }

        /**
         * The direct sub-folders of [parent], sorted.
         *
         * Direct, not every descendant: a grid of tiles is one level at a time,
         * the way a file manager draws it.
         */
        fun children(parent: String, folders: List<String>): List<String> =
            folders
                .filter { parentOf(it).equals(parent, ignoreCase = true) }
                .sortedBy { nameOf(it).lowercase() }

        /**
         * Every folder plus the folders that contain it.
         *
         * Needed because a stored path is the only place a sub-folder is
         * recorded: a file that mentions "Mobilier/Chaises" without mentioning
         * "Mobilier" would hold a shelf nobody can reach, since the grid only
         * draws children of the folder it is standing in.
         */
        fun withAncestors(folders: List<String>): List<String> {
            val seen = LinkedHashMap<String, String>()
            folders.map { it.trim() }.filter { it.isNotEmpty() }.forEach { path ->
                var current = ""
                path.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.forEach { segment ->
                    // Built on the spelling already chosen for the parent, so
                    // "Mobilier" followed by "mobilier/Chaises" yields "Mobilier/Chaises"
                    // rather than a path whose first half disagrees with the
                    // tile the shop taps. First spelling wins, so one shelf
                    // cannot become two tiles.
                    val candidate = join(current, segment)
                    current = seen[candidate.lowercase()] ?: candidate.also {
                        seen[it.lowercase()] = it
                    }
                }
            }
            return seen.values.sortedBy { it.lowercase() }
        }

        /**
         * The articles in [path], or in it and everything below when [deep].
         *
         * `""` is "Sans dossier", which has no sub-folders of its own, so the
         * two readings are the same there.
         */
        fun itemsIn(
            path: String,
            items: List<SavedItem>,
            deep: Boolean = false,
        ): List<SavedItem> = items.filter { item ->
            val folder = item.folder.trim()
            if (path.isEmpty()) folder.isEmpty() else folder.equals(path, true) || (deep && isIn(folder, path))
        }

        /**
         * Whether [item] answers [query].
         *
         * The rule lives here, in one place, because it is used by two screens
         * and a second copy has already drifted once: the Articles list
         * filtered on label, unit and folder and quietly forgot the barcode, so
         * a shop that typed the digits from a box was told the article did not
         * exist.
         *
         * Every word has to appear somewhere, so "ali pose" finds "Poser une
         * étagère" as well as "Pose de film", and a mistyped word narrows the list
         * instead of emptying it. Digits are matched as text, which is what a
         * barcode is. The folder is matched as its full path, so searching
         * "Mobilier" finds what is filed under Mobilier/Chaises too.
         *
         * A pure rule over two values, and reachable from a test without a
         * Context, because that is where it should have been checked the first
         * time.
         */
        fun matches(item: SavedItem, query: String): Boolean {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return true
            val haystack = "${item.label} ${item.unit} ${item.folder} ${item.barcode}".lowercase()
            return q.split(' ').all { haystack.contains(it) }
        }
    }
}

/**
 * Whether [label] already names an article other than [excluding].
 *
 * The label is the catalogue's key: [SavedItemStore.save] replaces by it, and an
 * invoice line is matched to the shelf by it. Two articles therefore cannot
 * share one — a rename onto a taken name would silently replace the article
 * that holds it, and the quantity on its shelf with it.
 *
 * [excluding] is the article being edited, which is not a clash with itself.
 * Kept out of the screen, where it was `previous == null &&`, so the rule can be
 * tested without Compose.
 */
fun List<SavedItem>.labelTaken(label: String, excluding: SavedItem? = null): Boolean {
    val target = label.trim()
    val kept = excluding?.label?.trim()
    return any {
        val current = it.label.trim()
        current.equals(target, ignoreCase = true) &&
            !(kept != null && current.equals(kept, ignoreCase = true))
    }
}
