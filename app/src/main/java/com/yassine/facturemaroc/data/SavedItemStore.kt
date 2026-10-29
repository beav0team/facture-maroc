package com.yassine.facturemaroc.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
 * path ("Pneus/Hiver"), which is what lets a tyre shop put its winter stock
 * under Pneus without a table of nodes or an id on every item. The file has
 * been version 3 since the inventory arrived: version 2 added folders, version
 * 3 added the barcode, the purchase price and the quantity on the shelf. Both
 * older shapes still load — a version 1 file is a bare array of items and is
 * read as "everything in Sans dossier", and a version 2 item is read as a
 * product whose stock is not counted, which is exactly what it was.
 */
class SavedItemStore(context: Context) {

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
        val key = clean.label.lowercase()
        val existing = _items.value.toMutableList()
        val index = existing.indexOfFirst { it.label.trim().lowercase() == key }
        if (index >= 0) {
            existing[index] = clean
        } else {
            existing.add(clean)
        }
        persist(existing, _folders.value)
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
     * Creates an empty folder inside [parent] and returns its path.
     *
     * Returns null when the name is blank, contains the separator, or is
     * already taken by a sibling. Folders nest, so a name is only unique among
     * its siblings: "Hiver" may exist under Pneus and under Vêtements.
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
     * Pneus that ignored Pneus/Hiver would tell a shop its winter stock does
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
     * Everything is computed by [StockMovement] before this touches a file: if
     * the arithmetic is wrong, a test says so, and no screen can be the only
     * place the rule lives.
     */
    fun applyStockMovement(data: InvoiceData, reversing: Boolean = false): List<SavedItem> {
        val moved = StockMovement.changed(_items.value, data, reversing)
        if (moved.isEmpty()) return emptyList()
        persist(StockMovement.apply(_items.value, data, reversing), _folders.value)
        return moved
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
        val cleanItems = sorted(items)
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
        put("label", label)
        put("price", price)
        put("unit", unit)
        put("folder", folder)
        // Written on every save and read as "absent" by older files, so a
        // version 2 file stays a valid catalogue of products with no quantities.
        put("barcode", barcode)
        put("purchasePrice", purchasePrice)
        if (stock != null) put("stock", stock)
    }

    private fun JSONObject.toItem() = SavedItem(
        label = optString("label", ""),
        price = optDouble("price", 0.0),
        unit = optString("unit", ""),
        folder = optString("folder", ""),
        barcode = optString("barcode", ""),
        purchasePrice = optDouble("purchasePrice", 0.0).let { if (it.isNaN()) 0.0 else it },
        // No "stock" key means an article saved before the inventory existed,
        // which was a catalogue entry and not a counted product.
        stock = if (has("stock") && !isNull("stock")) optInt("stock", 0) else null,
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
         * A path rather than a list of ids: folders nest, and "Pneus/Hiver" is
         * a name a person can read in the JSON file, sorts next to
         * "Pneus/Hiver clouté", and needs no table of nodes to resolve. It also
         * leaves the file shape alone, so every catalogue written by the flat
         * version still loads.
         */
        const val SEPARATOR = "/"
        private const val SEPARATOR_DISPLAY = " › "

        /** The folder one level up; `""` for a top-level folder. */
        fun parentOf(path: String): String = path.substringBeforeLast(SEPARATOR, "")

        /** The folder's own name, without the path that leads to it. */
        fun nameOf(path: String): String = path.substringAfterLast(SEPARATOR)

        /** [name] placed inside [parent]. */
        fun join(parent: String, name: String): String =
            if (parent.isBlank()) name else "$parent$SEPARATOR$name"

        /** How a path is drawn in the interface: `Pneus › Hiver`. */
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
         * recorded: a file that mentions "Pneus/Hiver" without mentioning
         * "Pneus" would hold a shelf nobody can reach, since the grid only
         * draws children of the folder it is standing in.
         */
        fun withAncestors(folders: List<String>): List<String> {
            val seen = LinkedHashMap<String, String>()
            folders.map { it.trim() }.filter { it.isNotEmpty() }.forEach { path ->
                var current = ""
                path.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.forEach { segment ->
                    // Built on the spelling already chosen for the parent, so
                    // "Pneus" followed by "pneus/Hiver" yields "Pneus/Hiver"
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
         * Every word has to appear somewhere, so "ali pose" finds "Aligner une
         * roue" as well as "Pose de film", and a mistyped word narrows the list
         * instead of emptying it. Digits are matched as text, which is what a
         * barcode is. The folder is matched as its full path, so searching
         * "Pneus" finds what is filed under Pneus/Hiver too.
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
