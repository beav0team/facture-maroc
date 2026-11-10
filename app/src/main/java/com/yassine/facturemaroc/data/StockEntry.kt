package com.yassine.facturemaroc.data

import org.json.JSONObject

/**
 * Why stock moved.
 *
 * Stored as a short id rather than the French label, so a translation or a
 * wording change never rewrites history: the ledger is evidence, and the label
 * is presentation. [label] is what the history screen prints.
 */
enum class StockReason(val id: String, val label: String) {
    /** A document that sold goods took them off the shelf. */
    INVOICE("invoice", "Facture"),

    /** A credit note put goods back. */
    CREDIT("credit", "Avoir"),

    /** Goods received from a supplier. */
    PURCHASE("purchase", "Achat"),

    /** A physical count found a different number on the shelf. */
    STOCKTAKE("stocktake", "Inventaire"),

    /** A manual correction, including the opening quantity of a new article. */
    ADJUSTMENT("adjustment", "Ajustement"),
    ;

    companion object {
        /** An unknown id (a file written by a later build) reads as a correction. */
        fun fromId(id: String?): StockReason =
            entries.firstOrNull { it.id == id } ?: ADJUSTMENT
    }
}

/**
 * One line of the stock ledger: a change, never a value.
 *
 * The old model kept only `SavedItem.stock`, so every sale overwrote the number
 * that was there and the shop could not answer "why is it three?". A ledger of
 * deltas can: the balance is their sum, and each one says what document or
 * count moved it.
 *
 * [delta] is signed — negative when goods left, positive when they came back.
 * [reference] is the document number ("F-20261110-0001"), the stocktake label,
 * or empty for a correction. [at] is epoch milliseconds, kept raw so the history
 * can be sorted and the file stays readable; the screen formats it in French.
 */
data class StockEntry(
    val itemId: String,
    val delta: Int,
    val reason: StockReason,
    val reference: String = "",
    val note: String = "",
    val at: Long = System.currentTimeMillis(),
) {
    /** The line as it is written in the JSONL file, one per line. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("itemId", itemId)
        put("delta", delta)
        put("reason", reason.id)
        put("reference", reference)
        put("note", note)
        put("at", at)
    }

    /** A missing reason is not guessed from the sign: it reads as a correction. */
    fun toLine(): String = toJson().toString()

    companion object {
        /**
         * Reads one line, or null when it is not a complete entry.
         *
         * Null is a normal answer, not an error: a phone that died mid-append
         * leaves half a line at the end of the file, and the only safe response
         * is to ignore that fragment and keep every line before it.
         */
        fun parse(line: String): StockEntry? {
            val text = line.trim()
            if (text.isEmpty()) return null
            return runCatching {
                val obj = JSONObject(text)
                val itemId = obj.optString("itemId", "").trim()
                if (itemId.isEmpty()) return null
                val delta = obj.optInt("delta", 0)
                if (delta == 0) return null
                StockEntry(
                    itemId = itemId,
                    delta = delta,
                    reason = StockReason.fromId(obj.optString("reason", "")),
                    reference = obj.optString("reference", ""),
                    note = obj.optString("note", ""),
                    at = obj.optLong("at", 0L),
                )
            }.getOrNull()
        }
    }
}
