package com.yassine.facturemaroc.invoice

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.yassine.facturemaroc.data.ClientStatement
import com.yassine.facturemaroc.data.InvoiceClient
import com.yassine.facturemaroc.data.InvoiceDesign
import com.yassine.facturemaroc.data.ShopSettings
import com.yassine.facturemaroc.data.StatementLine
import java.io.File

/**
 * The sheet a shop hands a client who wants to know where the account stands.
 *
 * It is a report, not a legal document: no new invoice number is consumed, no
 * entry is written to the history, and the shop details are the live ones
 * because nothing here was "issued". It is drawn like [ReceiptPdf] rather than
 * with an invoice template, for the same reason — the six templates are
 * answerable for invoice content, and a statement that inherited their totals
 * block would print an article list that a statement does not have.
 *
 * A4 portrait, one row per document, with the column headers repeated on every
 * sheet so page two can be read on its own. It returns null when there is
 * nothing to state, because a blank statement with a client's name on it is a
 * document that says less than handing over nothing.
 */
object StatementPdf {

    /** A4 portrait at 72dpi, the size every template uses. */
    private const val PAGE_W = 595
    private const val PAGE_H = 842

    private const val MARGIN = 32f

    /**
     * Rows per sheet.
     *
     * Lower than what would physically fit, because the totals block has to sit
     * under the last row of the last sheet: a statement whose balance is
     * stranded on a page of its own has no balance a customer can read.
     */
    private const val ROWS_PER_PAGE = 22

    private const val ROW_H = 15f

    // Column geometry, left to right. The three money columns are right-aligned
    // to the same right edge so a column of figures can be compared by eye.
    private const val COL_NUM = MARGIN
    private const val W_NUM = 112
    private const val COL_DATE = 144f
    private const val W_DATE = 54
    private const val COL_DUE = 198f
    private const val W_DUE = 62
    private const val COL_TOTAL = 260f
    private const val W_TOTAL = 72
    private const val COL_PAID = 332f
    private const val W_PAID = 70
    private const val COL_BALANCE = 402f
    private const val W_BALANCE = 86
    private const val COL_LATE = 488f
    private const val W_LATE = 75

    private const val LATE_COLOR = 0xFFB3261E.toInt()

    const val TAG = "StatementPdf"

    /**
     * Writes the statement and hands it to the share sheet.
     *
     * Shares [InvoicePdf.Outcome] so the client card can report a failure with
     * exactly the words it already uses for an invoice or a receipt.
     */
    fun share(
        context: Context,
        statement: ClientStatement,
        client: InvoiceClient = InvoiceClient(name = statement.clientName),
        shop: ShopSettings,
        design: InvoiceDesign,
    ): InvoicePdf.Outcome {
        val file = create(context, statement, client, shop, design)
            ?: return InvoicePdf.Outcome.NotWritten
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, "statement", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "Partager le relevé")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            InvoicePdf.Outcome.HandedOff(file)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "could not hand the statement to another app", e)
            InvoicePdf.Outcome.NoReceiver(file)
        }
    }

    fun create(
        context: Context,
        statement: ClientStatement,
        client: InvoiceClient = InvoiceClient(name = statement.clientName),
        shop: ShopSettings,
        design: InvoiceDesign,
    ): File? {
        if (statement.isEmpty) return null
        return try {
            val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
            val file = File(dir, "releve_${slug(statement.clientName)}.pdf")
            val pages = statement.lines.chunked(ROWS_PER_PAGE)

            file.outputStream().use { out ->
                val doc = PdfDocument()
                pages.forEachIndexed { index, rows ->
                    val page = doc.startPage(
                        PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, index + 1).create(),
                    )
                    drawPage(
                        canvas = page.canvas,
                        statement = statement,
                        client = client,
                        shop = shop,
                        design = design,
                        rows = rows,
                        pageIndex = index,
                        pageCount = pages.size,
                        isLast = index == pages.lastIndex,
                    )
                    doc.finishPage(page)
                }
                doc.writeTo(out)
                doc.close()
            }
            android.util.Log.i(
                TAG,
                "statement written ${file.name} ${file.length()} bytes ${pages.size} page(s)",
            )
            file
        } catch (e: Exception) {
            android.util.Log.w(TAG, "could not write the statement PDF", e)
            null
        }
    }

    private fun drawPage(
        canvas: Canvas,
        statement: ClientStatement,
        client: InvoiceClient,
        shop: ShopSettings,
        design: InvoiceDesign,
        rows: List<StatementLine>,
        pageIndex: Int,
        pageCount: Int,
        isLast: Boolean,
    ) {
        canvas.drawColor(Color.WHITE)
        val width = (PAGE_W - MARGIN * 2).toInt()
        val right = PAGE_W - MARGIN

        val accent = design.accentColor
        val title = PdfText.paint(18f, Color.BLACK, bold = true)
        val name = PdfText.paint(13f, Color.BLACK, bold = true)
        val body = PdfText.paint(9f, Color.BLACK)
        val small = PdfText.paint(8f, Color.GRAY)
        val label = PdfText.paint(7.5f, Color.GRAY, bold = true)
        val cell = PdfText.paint(8.5f, Color.BLACK)
        val latePaint = PdfText.paint(7f, LATE_COLOR, bold = true)

        var y = MARGIN

        // ---- who is issuing, in the same shape as the receipt
        PdfText.draw(canvas, shop.name.ifBlank { "—" }, MARGIN, y, name, width, maxLines = 2)
        y += PdfText.height(shop.name, name, width, 2) + 2f
        val contact = buildList {
            if (shop.address.isNotBlank()) add(PdfText.oneline(shop.address))
            if (design.showLegalIdentifiers) {
                if (shop.ice.isNotBlank()) add("ICE : ${shop.ice}")
                if (shop.ifNumber.isNotBlank()) add("IF : ${shop.ifNumber}")
                if (shop.rc.isNotBlank()) add("RC : ${shop.rc}")
            }
        }.joinToString(" · ")
        if (contact.isNotEmpty()) {
            PdfText.draw(canvas, contact, MARGIN, y, small, width, maxLines = 2)
            y += PdfText.height(contact, small, width, 2) + 4f
        }

        canvas.drawLine(
            MARGIN, y, right, y,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent
                strokeWidth = 2.5f
            },
        )
        y += 12f

        // ---- what this is, and for whom
        PdfText.draw(canvas, "RELEVÉ CLIENT", MARGIN, y, title, (width * 0.6f).toInt(), maxLines = 1)
        if (pageCount > 1) {
            PdfText.draw(
                canvas, "Page ${pageIndex + 1} / $pageCount", MARGIN, y + 5f, small,
                width, android.text.Layout.Alignment.ALIGN_OPPOSITE, maxLines = 1,
            )
        }
        y += PdfText.height("RELEVÉ CLIENT", title, width, 1) + 6f

        PdfText.draw(
            canvas, client.name.trim().ifBlank { statement.clientName }, MARGIN, y, name,
            (width * 0.65f).toInt(), maxLines = 2,
        )
        y += PdfText.height(client.name, name, (width * 0.65f).toInt(), 2) + 1f
        if (client.ice.isNotBlank()) {
            val ice = "ICE : ${client.ice}"
            PdfText.draw(canvas, ice, MARGIN, y, small, width, maxLines = 1)
            y += PdfText.height(ice, small, width, 1) + 2f
        }
        if (client.address.isNotBlank()) {
            val address = PdfText.oneline(client.address)
            PdfText.draw(canvas, address, MARGIN, y, small, width, maxLines = 1)
            y += PdfText.height(address, small, width, 1) + 2f
        }
        y += 8f

        // ---- column headings, repeated on every sheet
        PdfText.draw(canvas, "N°", COL_NUM, y, label, W_NUM, maxLines = 1)
        PdfText.draw(canvas, "Date", COL_DATE, y, label, W_DATE, maxLines = 1)
        PdfText.draw(canvas, "Échéance", COL_DUE, y, label, W_DUE, maxLines = 1)
        rightAlign(canvas, "Total", COL_TOTAL, y, label, W_TOTAL)
        rightAlign(canvas, "Payé", COL_PAID, y, label, W_PAID)
        rightAlign(canvas, "Reste", COL_BALANCE, y, label, W_BALANCE)
        y += 10f
        canvas.drawLine(
            MARGIN, y, right, y,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = 0.8f
                color = Color.LTGRAY
            },
        )
        y += 4f

        // ---- one row per document
        rows.forEach { line ->
            val rowY = y
            PdfText.draw(canvas, line.number, COL_NUM, rowY, cell, W_NUM, maxLines = 1)
            PdfText.draw(canvas, line.date, COL_DATE, rowY, cell, W_DATE, maxLines = 1)
            PdfText.draw(
                canvas, line.dueDate.orEmpty(), COL_DUE, rowY, cell, W_DUE, maxLines = 1,
            )
            rightAlign(canvas, PdfText.money(if (line.isCredit) -line.total else line.total), COL_TOTAL, rowY, cell, W_TOTAL)
            rightAlign(canvas, PdfText.money(line.paid), COL_PAID, rowY, cell, W_PAID)
            rightAlign(canvas, PdfText.money(line.balance), COL_BALANCE, rowY, cell, W_BALANCE)
            if (line.isLate) {
                PdfText.draw(canvas, "En retard", COL_LATE, rowY + 1f, latePaint, W_LATE, maxLines = 1)
            }
            y += ROW_H
        }

        // ---- totals, under the last row of the last sheet
        if (isLast) {
            y += 8f
            canvas.drawLine(
                COL_TOTAL - 40f, y, right, y,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    strokeWidth = 0.8f
                    color = Color.LTGRAY
                },
            )
            y += 8f
            totalsRow(canvas, "Total facturé", PdfText.money(statement.billed), y, body, body)
            y += 16f
            val avoirs = if (statement.credited > 0.0) -statement.credited else 0.0
            totalsRow(canvas, "Avoirs", PdfText.money(avoirs), y, body, body)
            y += 16f
            totalsRow(canvas, "Total payé", PdfText.money(statement.paid), y, body, body)
            y += 16f
            canvas.drawLine(
                COL_TOTAL - 40f, y, right, y,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    strokeWidth = 0.8f
                    color = Color.LTGRAY
                },
            )
            y += 6f
            val big = PdfText.paint(11f, accent, bold = true)
            totalsRow(canvas, "Solde dû", PdfText.money(statement.balance), y, big, big)
            y += 18f
            if (statement.balance < 0.0) {
                PdfText.draw(
                    canvas, "Le client est créditeur de ce montant.", MARGIN, y, small, width,
                    maxLines = 1,
                )
                y += 10f
            }
        }

        // ---- footer
        val footer = "${shop.name.ifBlank { "—" }} · Relevé établi le ${InvoicePdf.today()}"
        PdfText.draw(
            canvas, footer, MARGIN, PAGE_H - MARGIN, small, width,
            android.text.Layout.Alignment.ALIGN_CENTER, maxLines = 1,
        )
    }

    /**
     * One line of the totals block: label on the left, figure against the right
     * margin. The value width is fixed rather than measured so the figures line
     * up with each other whatever their length.
     */
    private fun totalsRow(
        canvas: Canvas,
        label: String,
        value: String,
        y: Float,
        labelPaint: android.text.TextPaint,
        valuePaint: android.text.TextPaint,
    ) {
        val right = PAGE_W - MARGIN
        PdfText.draw(canvas, label, right - 220f, y, labelPaint, 120, maxLines = 1)
        rightAlign(canvas, value, right - 100f, y, valuePaint, 100)
    }

    /** Draws [text] ending at the right edge of a [width]-wide column. */
    private fun rightAlign(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        paint: android.text.TextPaint,
        width: Int,
    ) {
        PdfText.draw(
            canvas, text, x, y, paint, width,
            android.text.Layout.Alignment.ALIGN_OPPOSITE, maxLines = 1,
        )
    }

    /** A file name a shop can find in its download folder. */
    private fun slug(name: String): String {
        val clean = name.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(40)
        return clean.ifBlank { "client" }
    }
}
