package com.yassine.facturemaroc.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Whether a record is still being written or was really handed to a customer. */
enum class InvoiceStatus(val id: String, val label: String) {
    DRAFT("draft", "Brouillon"),
    ISSUED("issued", "Émise"),
    ;

    companion object {
        fun fromId(id: String?): InvoiceStatus =
            entries.firstOrNull { it.id == id } ?: DRAFT
    }
}

/**
 * One document kept in the shop's history.
 *
 * The whole [InvoiceData] is stored, not a reference to the draft: a record is
 * what the customer received, and it must not change because a price list or a
 * shop setting changed afterwards.
 */
data class InvoiceRecord(
    val id: String,
    val data: InvoiceData,
    val status: InvoiceStatus,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * Whether issuing this document moved stock, so deleting it may move it back.
     *
     * False for anything saved before the inventory existed: those documents
     * never took a tyre off a shelf, and crediting them on deletion would raise
     * the count of a product that was never counted. The alternative — deducing
     * it from the document type — would be right for every document written by
     * this build and wrong for every document written by the last one.
     */
    val stockApplied: Boolean = false,
) {
    /**
     * What the history list calls it.
     *
     * The party the document is addressed to, which is the supplier on a bon
     * de commande — the list used to read `client.name` and titled every order
     * form with the shop's own name, because on that document the shop *is*
     * the client. A blank party falls back to the document's type rather than
     * to an empty row.
     */
    val title: String
        get() = data.partyName.trim().ifBlank { data.documentType.label }

    val isDraft: Boolean get() = status == InvoiceStatus.DRAFT
}

/**
 * Every invoice the shop has written, persisted as one JSON file.
 *
 * Same pattern as the other stores: a list of small documents, written to a
 * temporary file and renamed over the live one so a phone that dies mid-write
 * leaves the old list or the new one, never a half file that will not parse on
 * the next launch.
 *
 * The file is the app's own data. It is not the source of truth for a document
 * that was already shared — the PDF the customer received is — so a corrupted
 * file costs the history, never the ability to invoice.
 */
class InvoiceStore(context: Context) {

    private val file = File(context.filesDir, FILE)
    private val _invoices = MutableStateFlow(load())
    val invoices: StateFlow<List<InvoiceRecord>> = _invoices.asStateFlow()

    /** Adds a record, or replaces the one with the same id. */
    fun upsert(record: InvoiceRecord) {
        val without = _invoices.value.filterNot { it.id == record.id }
        persist((without + record).sortedByDescending { it.updatedAt })
    }

    fun delete(id: String) {
        persist(_invoices.value.filterNot { it.id == id })
    }

    fun find(id: String): InvoiceRecord? = _invoices.value.firstOrNull { it.id == id }

    /** Replaces the whole history, for a backup restore. */
    fun replaceAll(records: List<InvoiceRecord>) {
        persist(records.sortedByDescending { it.updatedAt })
    }

    private fun persist(list: List<InvoiceRecord>) {
        _invoices.value = list
        val payload = JSONObject().apply {
            put("version", 1)
            put(
                "invoices",
                JSONArray().apply { list.forEach { put(InvoiceJson.recordToJson(it)) } },
            )
        }
        runCatching {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(payload.toString())
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }.onFailure {
            android.util.Log.w("InvoiceStore", "could not save the invoice history", it)
        }
    }

    private fun load(): List<InvoiceRecord> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val root = JSONObject(file.readText())
            val array = root.optJSONArray("invoices") ?: JSONArray()
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let(InvoiceJson::recordFromJson)
            }.sortedByDescending { it.updatedAt }
        }.getOrElse {
            android.util.Log.w("InvoiceStore", "the invoice history would not parse", it)
            emptyList()
        }
    }

    companion object {
        private const val FILE = "invoices.json"
    }
}
