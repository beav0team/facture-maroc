package com.yassine.facturemaroc.invoice

import android.content.Context
import android.content.ClipData
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
import com.yassine.facturemaroc.data.InvoiceData
import com.yassine.facturemaroc.data.InvoiceDesign
import com.yassine.facturemaroc.data.InvoiceTemplate
import com.yassine.facturemaroc.data.ShopSettings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders an invoice onto one or more pages of PDF.
 *
 * A template is this interface and nothing else. The four layouts differ in where
 * they put things, not in what they are required to say, so every template
 * receives the same [InvoiceData], the same shop details and the same design
 * settings and is answerable for the drawing only.
 *
 * It draws on a [PageSurface] rather than a canvas because a long invoice does
 * not fit on one A4: the renderer, which is the only code that knows where a row
 * ends, asks for the next page itself. A template that never asks stays a
 * one-page document, which is what the 80mm ticket is.
 */
interface PdfRenderer {
    fun draw(
        context: Context,
        surface: PageSurface,
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

    fun createPdf(
        context: Context,
        data: InvoiceData,
        shop: ShopSettings,
        design: InvoiceDesign,
    ): File? = try {
        val template = design.template
        val height = if (template.isRoll) {
            TicketRenderer.height(data, shop, design)
        } else {
            PAGE_H
        }
        val width = if (template.isRoll) TicketRenderer.WIDTH.toInt() else PAGE_W

        val dir = java.io.File(context.cacheDir, "invoices").apply { mkdirs() }
        val file = java.io.File(dir, "facture_${data.number}.pdf")
        var pages = 0
        file.outputStream().use { out ->
            val doc = PdfDocument()
            val pager = DocumentPager(doc, width, height)
            rendererFor(template).draw(context, pager, data, shop, design)
            pager.finish()
            pages = pager.pageCount
            doc.writeTo(out)
            doc.close()
        }
        android.util.Log.i(TAG, "pdf written ${file.name} ${file.length()} bytes ${width}x$height ${template.id} $pages page(s)")
        file
    } catch (e: Exception) {
        android.util.Log.w(TAG, "could not write the invoice PDF", e)
        null
    }

    /**
     * Builds the PDF and hands it to the share sheet, in one pass.
     *
     * This does not say the file was sent. `startActivity` returns as soon as
     * the chooser is drawn, and a chooser that is dismissed straight after
     * looks exactly like one whose app delivered the file, so the caller has to
     * ask the shop before it treats the document as issued. The number it
     * reserved is put back when the shop says no.
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
            val chooser = Intent.createChooser(send, "Share invoice")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            Outcome.HandedOff(file)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "could not hand the invoice to another app", e)
            Outcome.NoReceiver(file)
        }
    }

    /** What came of [share], with the reason in each failure case. */
    sealed interface Outcome {
        /**
         * The PDF was written and the chooser opened.
         *
         * Deliberately not called "sent": the file exists and is in front of the
         * user, and whether it reached anyone is a question only the shop can
         * answer.
         */
        data class HandedOff(val file: File) : Outcome

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
    ): Bitmap? {
        val template = design.template
        val scale = widthPx.toDouble() /
            (if (template.isRoll) TicketRenderer.WIDTH.toDouble() else PAGE_W.toDouble())
        val height = if (template.isRoll) {
            (TicketRenderer.height(data, shop, design) * scale).toInt()
        } else {
            (PAGE_H * scale).toInt()
        }

        // A rendering fault has to come back as "no preview", not as an
        // exception: this runs on a background dispatcher with nothing to catch
        // it, so throwing here kills the screen that was merely drawing a
        // mock-up. The log line is what makes the blank preview diagnosable.
        return try {
            val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
            }
            val canvas = Canvas(bitmap)
            canvas.scale(scale.toFloat(), scale.toFloat())
            // One page only: a renderer that asks for a second sheet is told no
            // and stops, so the preview shows the first page of the real
            // document instead of stacking page two on top of page one.
            rendererFor(template).draw(context, PreviewSurface(canvas), data, shop, design)
            bitmap
        } catch (e: Exception) {
            android.util.Log.w("InvoicePdf", "preview failed for ${template.id}", e)
            null
        }
    }

    private fun rendererFor(template: InvoiceTemplate): PdfRenderer = when (template) {
        InvoiceTemplate.CLASSIQUE -> ClassiqueRenderer()
        InvoiceTemplate.MODERNE -> ModerneRenderer()
        InvoiceTemplate.MAROC -> MarocRenderer()
        InvoiceTemplate.EPURE -> EpureRenderer()
        InvoiceTemplate.BANDEAU -> BandeauRenderer()
        InvoiceTemplate.TICKET -> TicketRenderer()
    }

    fun today(): String = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date())
}

const val TAG = "InvoicePdf"
