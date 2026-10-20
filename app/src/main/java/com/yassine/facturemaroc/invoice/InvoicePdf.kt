package com.yassine.facturemaroc.invoice

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders one invoice onto one page of PDF.
 *
 * A template is this interface and nothing else. The four layouts differ in where
 * they put things, not in what they are required to say, so every template
 * receives the same [InvoiceData], the same shop details and the same design
 * settings and is answerable for the drawing only.
 */
interface PdfRenderer {
    fun draw(
        context: Context,
        canvas: Canvas,
        data: InvoiceData,
        shop: ShopSettings,
        design: InvoiceDesign,
    )
}

/**
 * Turns an invoice into a PDF file and hands it to whatever the shop wants to
 * send it with.
 *
 * Android's own [PdfDocument] is used rather than a PDF library. It is part of
 * the framework, so it costs nothing in size and needs no dependency, and it
 * writes a real PDF that any printer or browser will open.
 */
object InvoicePdf {

    /** A4 at 72dpi, which is what [PdfDocument] works in. */
    private const val PAGE_W = 595
    private const val PAGE_H = 842

    /** A receipt roll: 80mm wide, and as tall as the sale needs. */
    private const val TICKET_W = 226

    fun createPdf(
        context: Context,
        data: InvoiceData,
        shop: ShopSettings,
        design: InvoiceDesign,
    ): File? = try {
        val template = design.template
        val height = if (template.isRoll) ticketHeight(data, shop, design) else PAGE_H
        val width = if (template.isRoll) TICKET_W else PAGE_W

        val dir = java.io.File(context.cacheDir, "invoices").apply { mkdirs() }
        val file = java.io.File(dir, "facture_${data.number}.pdf")
        file.outputStream().use { out ->
            val doc = PdfDocument()
            val page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
            rendererFor(template).draw(context, page.canvas, data, shop, design)
            doc.finishPage(page)
            doc.writeTo(out)
            doc.close()
        }
        android.util.Log.i(TAG, "pdf written ${file.name} ${file.length()} bytes ${width}x$height ${template.id}")
        file
    } catch (e: Exception) {
        // Logged rather than swallowed: this is the document a customer receives,
        // and a silent failure here is a failure at the counter with no reason.
        android.util.Log.w(TAG, "could not write the invoice PDF", e)
        null
    }

    /**
     * Builds the PDF and hands it to the share sheet, in one pass.
     *
     * A single call because building the document consumes an invoice number, so
     * checking whether it works and then building it again would skip a number
     * in the sequence for no reason.
     */
    fun share(
        context: Context,
        data: InvoiceData,
        shop: ShopSettings,
        design: InvoiceDesign,
    ): Outcome {
        val file = createPdf(context, data, shop, design) ?: return Outcome.NotWritten
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, "invoice", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // The flag goes on the chooser, not on the intent it wraps.
            //
            // The chooser is started from the Application context, because the PDF
            // is built off the main thread where there is no Activity to hand, and
            // Android refuses a bare startActivity from a non-Activity context. A
            // flag on the wrapped SEND intent is carried into the chooser as an
            // extra rather than as a launch flag, so the chooser itself is still
            // launched without one and the share fails on every device — with the
            // document written correctly and nothing to show for it.
            val chooser = Intent.createChooser(send, "Share invoice")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            Outcome.Shared(file)
        } catch (e: Exception) {
            // Usually nothing on the device that accepts a PDF. Said out loud
            // rather than failing silently at the counter, and the file is still
            // on disk so it can be sent later from the file manager.
            android.util.Log.w(TAG, "could not hand the invoice to another app", e)
            Outcome.NoReceiver(file)
        }
    }

    /** What came of [share], with the reason in each failure case. */
    sealed interface Outcome {
        data class Shared(val file: File) : Outcome

        /** The PDF would not build. Nothing was written. */
        data object NotWritten : Outcome

        /** The PDF was written, but no app on this device can receive it. */
        data class NoReceiver(val file: File) : Outcome
    }

    /**
     * Renders to a bitmap, for the live preview in the design screen.
     *
     * The preview draws the same code the saved file does at a reduced scale, so
     * what the shop approves on screen is the document that comes out of the
     * printer. A mock-up built from Compose would drift from the PDF the first
     * time a template was edited.
     */
    fun preview(
        context: Context,
        data: InvoiceData,
        shop: ShopSettings,
        design: InvoiceDesign,
        widthPx: Int,
    ): Bitmap? = try {
        val template = design.template
        val height = if (template.isRoll) ticketHeight(data, shop, design) else PAGE_H
        val width = if (template.isRoll) TICKET_W else PAGE_W

        val scale = widthPx.toFloat() / width
        val bitmap = Bitmap.createBitmap(
            widthPx,
            (height * scale).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.scale(scale, scale)
        rendererFor(template).draw(context, canvas, data, shop, design)
        canvas.restore()
        android.util.Log.i(TAG, "preview ${template.id} ${widthPx}x${bitmap.height}")
        bitmap
    } catch (e: Exception) {
        // Logged rather than swallowed. A blank preview with nothing on the
        // screen is undiagnosable without this, and this is the one place a
        // rendering fault shows up before it reaches a customer's invoice.
        android.util.Log.w(TAG, "preview failed for ${design.templateId}", e)
        null
    }

    private const val TAG = "InvoicePdf"

    private fun rendererFor(template: InvoiceTemplate): PdfRenderer = when (template) {
        InvoiceTemplate.CLASSIQUE -> ClassiqueRenderer()
        InvoiceTemplate.MODERNE -> ModerneRenderer()
        InvoiceTemplate.MAROC -> MarocRenderer()
        InvoiceTemplate.TICKET -> TicketRenderer()
    }

    /**
     * How tall a till roll needs to be for this sale.
     *
     * A roll is cut to fit, so the height is worked out from the content rather
     * than fixed: the header, a fixed allowance per line, the totals block and a
     * foot. Overrunning it would silently drop the amount in words off the
     * bottom of the receipt, which is the one line that must not be lost.
     */
    private fun ticketHeight(data: InvoiceData, shop: ShopSettings, design: InvoiceDesign): Int {
        var h = 150f
        h += shop.legalIdentifierList.size * 12f
        data.lines.forEach { h += if (it.description.length > 30) 26f else 16f }
        h += 110f
        if (design.logoEnabled) h += 40f
        return h.toInt().coerceAtLeast(240)
    }

    /** The date as a Moroccan invoice prints it. */
    fun today(): String =
        SimpleDateFormat("dd/MM/yyyy", Locale.US).format(java.util.Date())
}
