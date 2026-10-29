package com.yassine.facturemaroc.invoice

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.text.Layout

/**
 * The sheet a renderer draws on.
 *
 * A template draws straight onto this rather than onto a canvas, because a
 * table of thirty lines does not fit on one A4: [newPage] is how a renderer
 * asks for the next sheet in the middle of its own drawing, which is the only
 * place that knows where a row ends.
 */
interface PageSurface {

    val canvas: Canvas

    /**
     * Ends the current page and starts the next one.
     *
     * Returns false when the surface has no pages to give — the design screen
     * previews the first page of a bitmap, and a preview that silently overdrew
     * page two on top of page one would show a document nobody will ever
     * receive. A renderer that gets false must stop drawing.
     */
    fun newPage(): Boolean
}

/** A real PDF: pages are added to the document as the renderer asks for them. */
class DocumentPager(
    private val doc: PdfDocument,
    private val width: Int,
    private val height: Int,
) : PageSurface {

    private var page: PdfDocument.Page = doc.startPage(info(1))
    private var number = 1

    override val canvas: Canvas get() = page.canvas

    /** How many pages have been started, for the log line and for tests. */
    val pageCount: Int get() = number

    override fun newPage(): Boolean {
        doc.finishPage(page)
        number += 1
        page = doc.startPage(info(number))
        return true
    }

    /** Closes the page still open. Must be called before the document is written. */
    fun finish() {
        doc.finishPage(page)
    }

    private fun info(pageNumber: Int): PdfDocument.PageInfo =
        PdfDocument.PageInfo.Builder(width, height, pageNumber).create()
}

/** The design-screen preview: one page, and a renderer that asks for more is stopped. */
class PreviewSurface(override val canvas: Canvas) : PageSurface {
    override fun newPage(): Boolean = false
}

/**
 * The A4 sheet, in the points [PdfDocument] works in.
 *
 * Shared by the three A4 templates so they cannot drift apart on where the
 * paper ends. A table that stops at a different y in each layout is how one of
 * them quietly runs its totals off the bottom while the others are fine.
 */
object A4 {
    const val WIDTH = 595f
    const val HEIGHT = 842f
    const val MARGIN = 40f

    /** The lowest y a row may reach: everything below this belongs to the footer. */
    const val BOTTOM = HEIGHT - 52f

    /**
     * Where the first row starts on a continuation page.
     *
     * A second sheet repeats only the document's identity and the column
     * titles — the shop block and the client block are not repeated, because
     * they are already on page one and the paper is needed for articles.
     *
     * The value is the row start itself, not the top of the header: every
     * template draws its continuation masthead and table head above it, so a
     * figure that only roughly matched would put the first row of page two
     * under the column titles.
     */
    const val RESUME_Y = 82f
}

/**
 * Where the rows of a table break across pages.
 *
 * Kept apart from the drawing on purpose. Which row lands on which page is
 * arithmetic, and arithmetic is the part that can be checked by a test: a
 * renderer that fills a canvas cannot, because PdfDocument only works on a
 * device. What went wrong before this existed was not a wrong number here — it
 * was that nobody ever asked the question, so rows thirty and beyond were drawn
 * below the paper and the totals were clipped off the invoice.
 */
object PageFlow {

    /**
     * The pages of one table.
     *
     * [pages] holds the row indices drawn on each page, in order, and
     * [tailOnNewPage] says the totals had to move to a page of their own
     * because what was left of the last one could not hold them.
     */
    data class Layout(
        val pages: List<IntRange>,
        val tailOnNewPage: Boolean,
    ) {
        /** Every page the document will have, totals page included. */
        val totalPages: Int get() = pages.size + if (tailOnNewPage) 1 else 0

        /** Every row that will be drawn, once, in order. */
        val rows: List<Int> get() = pages.flatMap { it.toList() }
    }

    /**
     * Splits [heights] into pages.
     *
     * [firstY] is where the first row starts on page one, after the full header;
     * [resumeY] is where a row starts on a continuation page, after the reduced
     * header and the repeated column titles. [bottom] is the lowest y a row may
     * reach, and [tailHeight] is what the totals block will need after the last
     * row.
     *
     * A row taller than a whole page is still placed, alone, rather than
     * dropped: losing an article from an invoice is worse than an ugly page, and
     * a row that long is a description that has already gone wrong.
     */
    fun layout(
        heights: List<Float>,
        firstY: Float,
        resumeY: Float,
        bottom: Float,
        tailHeight: Float,
    ): Layout {
        if (heights.isEmpty()) {
            return Layout(listOf(IntRange.EMPTY), firstY + tailHeight > bottom)
        }

        val starts = mutableListOf(0)
        var y = firstY
        heights.forEachIndexed { index, height ->
            if (index > 0 && y + height > bottom) {
                starts += index
                y = resumeY
            }
            y += height
        }

        val pages = starts.mapIndexed { index, start ->
            start until starts.getOrElse(index + 1) { heights.size }
        }

        // Where the last row ends determines whether the totals still fit.
        val lastPageStart = if (pages.size == 1) firstY else resumeY
        val lastPageRows = pages.last()
        val lastRowEnd = lastPageStart + lastPageRows.sumOf { heights[it].toDouble() }.toFloat()

        return Layout(pages, lastRowEnd + tailHeight > bottom)
    }
}

/**
 * The line at the foot of a page: `Page 2/3`, and nothing at all on a one-page
 * document, where it would only be noise.
 */
object PdfFooter {

    fun draw(
        canvas: Canvas,
        margin: Float,
        contentWidth: Float,
        pageHeight: Float,
        page: Int,
        total: Int,
    ) {
        if (total <= 1) return
        val paint = PdfText.paint(8f, Color.GRAY)
        PdfText.draw(
            canvas = canvas,
            text = "Page $page/$total",
            x = margin,
            y = pageHeight - 30f,
            paint = paint,
            width = contentWidth.toInt(),
            align = Layout.Alignment.ALIGN_OPPOSITE,
        )
    }
}
