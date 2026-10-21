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
 * empty folder survives until the shop deletes it. The file has been version 2
 * since folders arrived; a version 1 file is a bare array of items and is read
 * as "everything in Sans dossier", which is exactly what those items were.
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

    /** Creates an empty folder. Returns false when the name is blank or taken. */
    fun createFolder(name: String): Boolean {
        val clean = name.trim()
        if (clean.isBlank()) return false
        if (_folders.value.any { it.equals(clean, ignoreCase = true) }) return false
        persist(_items.value, _folders.value + clean)
        return true
    }

    /**
     * Renames a folder and moves its articles with it.
     *
     * Refused when the new name is blank or already belongs to another folder,
     * so two folders can never end up with the same label.
     */
    fun renameFolder(oldName: String, newName: String): Boolean {
        val clean = newName.trim()
        if (clean.isBlank()) return false
        if (clean.equals(oldName, ignoreCase = true)) return false
        if (_folders.value.any { !it.equals(oldName, true) && it.equals(clean, true) }) return false
        val items = _items.value.map { item ->
            if (item.folder.equals(oldName, ignoreCase = true)) item.copy(folder = clean) else item
        }
        val folders = _folders.value.map { if (it.equals(oldName, true)) clean else it }
        persist(items, folders)
        return true
    }

    /**
     * Deletes a folder but keeps its articles.
     *
     * Deleting a folder that quietly deleted the work inside it would be the
     * most expensive mistake this screen could make, so the items move to
     * "Sans dossier" and stay on every invoice they are already on.
     */
    fun deleteFolder(name: String) {
        val items = _items.value.map { item ->
            if (item.folder.equals(name, ignoreCase = true)) item.copy(folder = "") else item
        }
        persist(items, _folders.value.filterNot { it.equals(name, ignoreCase = true) })
    }

    /**
     * Items whose label, unit or folder contains [query].
     *
     * Called on every keystroke against tens of items, so it filters the
     * in-memory list rather than re-reading the file. [folder] limits the
     * result to one folder; `""` means "Sans dossier" and `null` means all.
     */
    fun search(query: String, folder: String? = null): List<SavedItem> {
        val scoped = if (folder == null) {
            _items.value
        } else {
            _items.value.filter { it.folder.equals(folder, ignoreCase = true) }
        }
        val q = query.trim().lowercase()
        if (q.isEmpty()) return scoped
        // Each word has to appear somewhere, so "ali pose" finds
        // "Aligner une roue" as well as "Pose de film", and a mistyped word
        // narrows the list instead of emptying it.
        return scoped.filter { item ->
            val haystack = "${item.label} ${item.unit} ${item.folder}".lowercase()
            q.split(' ').all { haystack.contains(it) }
        }
    }

    private fun sorted(list: List<SavedItem>): List<SavedItem> =
        list.sortedBy { it.label.trim().lowercase() }

    private fun cleanFolders(folders: List<String>, items: List<SavedItem>): List<String> =
        (folders + items.map { it.folder })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

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
            put("version", 2)
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
    }

    private fun JSONObject.toItem() = SavedItem(
        label = optString("label", ""),
        price = optDouble("price", 0.0),
        unit = optString("unit", ""),
        folder = optString("folder", ""),
    ).takeIf {
        // An item with no price is not an item: it would sit on the list unable
        // to be added, because adding it would put a zero on the invoice.
        it.label.isNotBlank() && it.price > 0.0
    }

    private data class Content(
        val items: List<SavedItem>,
        val folders: List<String>,
    )

    private companion object {
        const val FILE = "saved_items.json"
    }
}
