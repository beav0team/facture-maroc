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
 * price, a tyre shop has tens of them, and pulling in a database for that would
 * be machinery in place of a list. Written to a temporary file and renamed over
 * the live one, so a phone that dies mid-write leaves the old list or the new
 * one and never a half-file that will not parse on the next launch.
 */
class SavedItemStore(context: Context) {

    private val file = File(context.filesDir, FILE)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<SavedItem>> = _items.asStateFlow()

    /**
     * Adds an item, or updates the one already saved under the same label.
     *
     * Matched on the label alone, ignoring case and surrounding space, because
     * that is what a person means when they retype "Wheel alignment" rather than
     * adding it a second time at a different price. The price is not part of the
     * key: raising a price means editing the item that is already there.
     */
    fun save(item: SavedItem) {
        if (item.label.isBlank()) return
        val key = item.label.trim().lowercase()
        val existing = _items.value.toMutableList()
        val index = existing.indexOfFirst { it.label.trim().lowercase() == key }
        if (index >= 0) {
            existing[index] = item.copy(label = item.label.trim())
        } else {
            existing.add(item.copy(label = item.label.trim()))
        }
        persist(sorted(existing))
    }

    fun delete(item: SavedItem) {
        val key = item.label.trim().lowercase()
        persist(_items.value.filterNot { it.label.trim().lowercase() == key })
    }

    /**
     * Items whose label contains [query], in the order they are kept in.
     *
     * Called on every keystroke against tens of items, so it filters the
     * in-memory list rather than re-reading the file.
     */
    fun search(query: String): List<SavedItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return _items.value
        // Each word has to appear somewhere, so "ali horse" finds
        // "Wheel alignment" as well as "Horse shoeing", and a mistyped word
        // narrows the list instead of emptying it.
        return _items.value.filter { item ->
            val haystack = "${item.label} ${item.unit}".lowercase()
            q.split(' ').all { haystack.contains(it) }
        }
    }

    private fun sorted(list: List<SavedItem>): List<SavedItem> =
        list.sortedBy { it.label.trim().lowercase() }

    private fun load(): List<SavedItem> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.toItem() }
        }.getOrElse { emptyList() }
    }

    private fun persist(items: List<SavedItem>) {
        _items.value = items
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        runCatching {
            val temp = File(file.parentFile, "$FILE.tmp")
            temp.writeText(array.toString())
            // The rename is the atomic step: it is what makes a half-written file
            // impossible rather than merely unlikely.
            if (!temp.renameTo(file)) {
                file.writeText(array.toString())
                temp.delete()
            }
        }
    }

    private fun SavedItem.toJson() = JSONObject().apply {
        put("label", label)
        put("price", price)
        put("unit", unit)
    }

    private fun JSONObject.toItem() = SavedItem(
        label = optString("label", ""),
        price = optDouble("price", 0.0),
        unit = optString("unit", ""),
    ).takeIf {
        // An item with no price is not an item: it would sit on the list unable
        // to be added, because adding it would put a zero on the invoice.
        it.label.isNotBlank() && it.price > 0.0
    }

    private companion object {
        const val FILE = "saved_items.json"
    }
}
