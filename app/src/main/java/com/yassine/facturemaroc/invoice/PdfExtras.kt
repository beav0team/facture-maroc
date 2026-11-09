package com.yassine.facturemaroc.invoice

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.TextPaint
import com.yassine.facturemaroc.data.InvoiceData

/**
 * The free-text notes and the signature, drawn the same way on every layout.
 *
 * These are the only parts of a document that the shop writes itself, so they
 * get one implementation rather than six: a note that wraps differently on
 * Classique and Maroc would be a second place to fix the same bug, and a
 * signature that is cropped on one template is a stamp the customer cannot
 * read.
 *
 * Both are optional. A document with neither draws exactly what it drew before
 * these existed, which is why every function here returns the y it was given.
 */
object PdfExtras {

    /** The signature box, in points, on the A4 layouts. */
    const val SIGNATURE_W = 150f
    const val SIGNATURE_H = 46f

    private const val CAPTION_H = 11f

    /** How much paper the notes need, including the gap after them. */
    fun notesHeight(
        data: InvoiceData,
        label: TextPaint,
        body: TextPaint,
        width: Int,
        maxLines: Int = 3,
    ): Float {
        if (!data.hasNotes || width <= 0) return 0f
        var h = 0f
        val customer = data.customerNote.trim()
        if (customer.isNotEmpty()) {
            h += PdfText.height("NOTE", label, width, 1) + 1f
            h += PdfText.height(customer, body, width, maxLines) + 8f
        }
        val terms = data.termsNote.trim()
        if (terms.isNotEmpty()) {
            h += PdfText.height("CONDITIONS", label, width, 1) + 1f
            h += PdfText.height(terms, body, width, maxLines) + 8f
        }
        return h
    }

    /** Draws the notes at [x], [y] and returns the y after them. */
    fun drawNotes(
        canvas: Canvas,
        data: InvoiceData,
        x: Float,
        y: Float,
        width: Int,
        label: TextPaint,
        body: TextPaint,
        maxLines: Int = 3,
    ): Float {
        if (!data.hasNotes || width <= 0) return y
        var cursor = y

        val customer = data.customerNote.trim()
        if (customer.isNotEmpty()) {
            PdfText.draw(canvas, "NOTE", x, cursor, label, width, maxLines = 1)
            cursor += PdfText.height("NOTE", label, width, 1) + 1f
            PdfText.draw(canvas, customer, x, cursor, body, width, maxLines = maxLines)
            cursor += PdfText.height(customer, body, width, maxLines) + 8f
        }

        val terms = data.termsNote.trim()
        if (terms.isNotEmpty()) {
            PdfText.draw(canvas, "CONDITIONS", x, cursor, label, width, maxLines = 1)
            cursor += PdfText.height("CONDITIONS", label, width, 1) + 1f
            PdfText.draw(canvas, terms, x, cursor, body, width, maxLines = maxLines)
            cursor += PdfText.height(terms, body, width, maxLines) + 8f
        }

        return cursor
    }

    /** How much paper the signature needs, caption included; 0 when absent. */
    fun signatureHeight(data: InvoiceData): Float =
        if (data.hasSignature) SIGNATURE_H + CAPTION_H + 8f else 0f

    /**
     * Draws the signature image fitted inside a [width] × [height] box.
     *
     * The image is never stretched and never cropped: a photographed stamp
     * arrives at whatever aspect ratio the phone camera produced, and a
     * squashed signature on a legal document reads as a forged one.
     * [drawCaption] is off where the template already prints its own label.
     */
    fun drawSignature(
        canvas: Canvas,
        data: InvoiceData,
        x: Float,
        y: Float,
        width: Float = SIGNATURE_W,
        height: Float = SIGNATURE_H,
        caption: TextPaint? = null,
        drawCaption: Boolean = true,
        captionText: String = "Signature",
    ): Float {
        val image = decode(data) ?: return y
        val scale = kotlin.math.min(width / image.width, height / image.height)
        val w = image.width * scale
        val h = image.height * scale
        val target = RectF(
            x + (width - w) / 2f,
            y + (height - h) / 2f,
            x + (width + w) / 2f,
            y + (height + h) / 2f,
        )
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(image, null, target, paint)
        if (drawCaption && caption != null) {
            PdfText.draw(
                canvas, captionText, x, y + height + 1f, caption, width.toInt(),
                Layout.Alignment.ALIGN_CENTER, maxLines = 1,
            )
        }
        return y + height + if (drawCaption) CAPTION_H + 4f else 0f
    }

    private fun decode(data: InvoiceData): Bitmap? {
        val bytes = data.signature?.bytes ?: return null
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
    }
}
