package com.yassine.facturemaroc.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A month of documents as a spreadsheet an accountant can open.
 *
 * The app is the shop's book, not the accountant's: the person who closes the
 * year works in Excel, and asking them to retype forty invoices is how figures
 * get transposed. This writes the same numbers the paper carries — HT, TVA,
 * TTC, paid, still owed — one row per non-draft document, so the two can be
 * checked line by line.
 *
 * Semicolons separate the columns and amounts use a comma for the decimal,
 * because that is what a French Excel reads without a dialog. Fields are quoted
 * only when they have to be, so a client named `Dupont; Frères` cannot shift
 * every value one column to the right.
 */
object AccountantExport {

    /** The columns, in the order the shop's accountant expects them. */
    val HEADER: List<String> = listOf(
        "Date",
        "Numéro",
        "Type",
        "Client",
        "ICE",
        "Total HT",
        "TVA",
        "Total TTC",
        "Payé",
        "Reste",
        "Statut",
    )

    /**
     * One row per issued document, oldest first.
     *
     * Drafts are left out: an accountant reading a document that was never
     * handed over would book a sale the shop did not make. A credit note is
     * kept with its own type, so the year's total is the sum of the column —
     * the shop does not have to know which lines to subtract by hand.
     */
    fun documentsCsv(records: List<InvoiceRecord>): String {
        val rows = records
            .filterNot { it.isDraft }
            .sortedWith(compareBy({ it.data.date }, { it.data.number }))
            .map { record -> row(record) }

        return buildString {
            appendLine(HEADER.joinToString(";") { escape(it) })
            rows.forEach { fields ->
                appendLine(fields.joinToString(";") { escape(it) })
            }
        }
    }

    /**
     * The month's VAT per rate, as a second sheet.
     *
     * Kept separate from the document list because the two have different
     * shapes: one row per document cannot also be one row per rate without
     * repeating the document. An accountant importing the file gets a table
     * whose columns mean one thing.
     */
    fun vatCsv(records: List<InvoiceRecord>, monthStamp: String): String {
        val rows = records.vatByRateInMonth(monthStamp)
        return buildString {
            appendLine(listOf("Taux", "Base HT", "TVA").joinToString(";") { escape(it) })
            rows.forEach { recap ->
                appendLine(
                    listOf(
                        escape(trimRate(recap.rate)),
                        money(recap.base),
                        money(recap.amount),
                    ).joinToString(";"),
                )
            }
        }
    }

    /** `comptable-2026-11-10.csv`, unambiguously dated and safe as a file name. */
    fun fileName(date: Date = Date()): String =
        "comptable-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)}.csv"

    private fun row(record: InvoiceRecord): List<String> {
        val data = record.data
        return listOf(
            data.date,
            data.number,
            data.documentType.label,
            data.client.name,
            data.client.ice,
            money(data.totalExcludingVat),
            money(data.vatAmount),
            money(data.total),
            money(data.paidAmount),
            money(data.balanceDue),
            data.paymentState.label,
        )
    }

    /**
     * A number as a spreadsheet reads it: `1234,56`, never `1 234,56`.
     *
     * The thousands separator is left out on purpose — a narrow non-breaking
     * space in a CSV is a character some importers keep, and the cell then
     * silently becomes text.
     */
    private fun money(value: Double): String =
        String.format(Locale.FRANCE, "%.2f", if (value.isNaN() || value.isInfinite()) 0.0 else value)

    /**
     * Quotes a field only when it holds the delimiter, a quote or a newline.
     *
     * Doubling the quote is the CSV rule, and applying it only where needed
     * keeps the file readable when opened in a text editor.
     */
    private fun escape(value: String): String =
        if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
