package com.yassine.facturemaroc.invoice

import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.text.TextPaint

/**
 * The colours a template derives from the one accent the shop chose.
 *
 * Kept here rather than in each renderer because "10% of the accent on white"
 * is a decision about the document, not about one layout: a band, a table head
 * and a totals panel that tinted the accent differently would look like three
 * different documents stapled together.
 */
object PdfColor {

    /** [amount] of white mixed into [color]: 0 keeps it, 1 makes it white. */
    fun tint(color: Int, amount: Float): Int = mix(color, Color.WHITE, amount)

    /** [amount] of black mixed into [color]: 0 keeps it, 1 makes it black. */
    fun shade(color: Int, amount: Float): Int = mix(color, Color.BLACK, amount)

    fun mix(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val a = (from ushr shift) and 0xFF
            val b = (to ushr shift) and 0xFF
            return Math.round(a + (b - a) * t)
        }
        return (0xFF shl 24) or
            (channel(16) shl 16) or
            (channel(8) shl 8) or
            channel(0)
    }
}

/**
 * The totals block at the foot of an A4 document.
 *
 * The label and the amount live in two boxes with a gap between them, and the
 * amount is right-aligned to the paper's margin. This is the shared geometry
 * because the first version of these templates placed both right-aligned at
 * points twelve apart, so every figure printed on top of its own label —
 * `43Subtotal460,00 DH` in the extracted text. One box pair for every template
 * means the mistake cannot come back in one of them.
 */
object PdfTotals {

    const val WIDTH = 250f

    /** How wide the amount box is. Wide enough for `12 500,00 DH` at 13pt. */
    const val VALUE_WIDTH = 104f

    /** Air between the longest label and the shortest amount. */
    const val GAP = 10f

    /** Left padding inside the block. */
    const val PAD = 6f

    /** Where the two boxes sit, given the block's right edge on the paper. */
    data class Box(
        val left: Float,
        val right: Float,
        /**
         * How wide the amount is allowed to be. A template that prints the
         * total at 19pt needs more room than one that prints it at 13pt: the
         * first version of the shared geometry assumed 13pt everywhere and
         * wrapped "14 304,00 DH" onto a second line on the typographic layout.
         */
        val valueWidthPt: Float = VALUE_WIDTH,
    ) {
        val valueLeft: Float get() = right - valueWidthPt
        val labelX: Float get() = left + PAD
        val labelWidth: Int get() = (valueLeft - GAP - labelX).toInt()
        val valueWidth: Int get() = valueWidthPt.toInt()
    }

    fun box(
        right: Float,
        width: Float = WIDTH,
        valueWidth: Float = VALUE_WIDTH,
    ): Box = Box(right - width, right, valueWidth)

    fun label(canvas: Canvas, box: Box, text: String, y: Float, paint: TextPaint) {
        PdfText.draw(canvas, text, box.labelX, y, paint, box.labelWidth)
    }

    fun value(canvas: Canvas, box: Box, text: String, y: Float, paint: TextPaint) {
        PdfText.draw(
            canvas = canvas,
            text = text,
            x = box.valueLeft,
            y = y,
            paint = paint,
            width = box.valueWidth,
            align = Layout.Alignment.ALIGN_OPPOSITE,
        )
    }

    fun row(
        canvas: Canvas,
        box: Box,
        name: String,
        amount: String,
        y: Float,
        labelPaint: TextPaint,
        valuePaint: TextPaint,
    ) {
        label(canvas, box, name, y, labelPaint)
        value(canvas, box, amount, y, valuePaint)
    }
}
