package com.yassine.facturemaroc.invoice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import com.yassine.facturemaroc.data.InvoiceDesign
import com.yassine.facturemaroc.data.InvoiceDesignStore

object PdfText {

    /**
     * A text paint for one of the templates.
     *
     * Always left-aligned. Alignment is decided by the [Layout.Alignment] passed
     * to [draw] and [layout], never by the paint, because a paint that carries
     * its own alignment silently overrides the layout's — so a right-aligned
     * paint left to itself positions text against the wrong edge and runs it off
     * the paper. One authority for alignment, and it is the layout's.
     */
    fun paint(
        size: Float,
        color: Int,
        bold: Boolean = false,
    ): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textAlign = Paint.Align.LEFT
    }

    /**
     * Draws [text] in a box [width] wide at ([x], [y]), which is the top of the
     * text rather than its baseline, so rows can be laid out by height.
     *
     * The paint's own textAlign is ignored. A TextPaint that carries an
     * alignment takes precedence over the one given to StaticLayout, so the two
     * can disagree — and when they do, text is positioned against the wrong edge
     * and runs off the paper. On an 80mm ticket that showed as "Total TTC"
     * printed as "TC" against the left edge and the amount in words missing its
     * first few words. [paint] therefore only ever builds left-aligned paints,
     * and alignment lives here alone.
     */
    fun draw(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        paint: TextPaint,
        width: Int,
        align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        maxLines: Int = Int.MAX_VALUE,
    ) {
        if (text.isBlank() || width <= 0) return
        val layout = layout(text, paint, width, align, maxLines)
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    /** How tall [text] will be when drawn into [width]. */
    fun height(text: String, paint: TextPaint, width: Int, maxLines: Int = Int.MAX_VALUE): Int {
        if (text.isBlank() || width <= 0) return 0
        return layout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, maxLines).height
    }

    private fun layout(
        text: String,
        paint: TextPaint,
        width: Int,
        align: Layout.Alignment,
        maxLines: Int,
    ): StaticLayout {
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(align)
            .setTextDirection(TextDirectionHeuristics.LOCALE)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
    }

    /**
     * A dirham amount, the way it is written on an invoice.
     *
     * French formatting throughout: a space for the thousands separator and a
     * comma for the decimal point, because that is how the figure is read aloud
     * and written on the same document the amount in words appears on.
     */
    fun money(value: Double): String =
        String.format(java.util.Locale.FRANCE, "%,.2f", value) + " DH"

    /**
     * Draws [logo] fitted inside a box [box] tall, with its left edge at [x] and
     * its vertical centre at [centerY].
     *
     * Returns the x the next element may start at, so a caller can lay a title
     * out beside the logo without measuring the image itself. A logo is fitted
     * by its height and width both, and never cropped: shop logos arrive at every
     * aspect ratio there is, and clipping one is how a logo ends up with a slice
     * of itself missing on a customer's invoice.
     */
    fun drawLogo(
        canvas: Canvas,
        logo: android.graphics.Bitmap,
        x: Float,
        centerY: Float,
        box: Float,
    ): Float {
        if (logo.width <= 0 || logo.height <= 0) return x
        val scale = kotlin.math.min(box / logo.height, box / logo.width)
        val w = logo.width * scale
        val h = logo.height * scale
        val top = centerY - h / 2f
        val target = RectF(x, top, x + w, top + h)
        canvas.drawBitmap(logo, null, target, null)
        return target.right
    }

    /**
     * Reads the shop logo off disk.
     *
     * Returns null rather than throwing when there is no logo or it cannot be
     * decoded, because a missing logo must leave the invoice without one rather
     * than fail to be produced. A file that is not a decodable image is treated
     * the same way as no file at all.
     */
    fun loadShopLogo(context: Context, design: InvoiceDesign): Bitmap? {
        if (!design.logoEnabled) return null
        val file = InvoiceDesignStore(context).logoFile(context)
        if (!file.isFile) return null
        return runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
    }
}
