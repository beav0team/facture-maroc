package com.yassine.facturemaroc.invoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The totals block's geometry, as arithmetic rather than as a drawing.
 *
 * The bug this guards against was not subtle once it happened: the label and
 * the amount were both right-aligned to points twelve apart, so every figure
 * printed on top of its own label — `43Subtotal460,00 DH` in the extracted
 * text of a real invoice. A test cannot see the overlap, but it can state the
 * rule that prevents it, and every template now shares this one box pair.
 */
class PdfTotalsTest {

    @Test
    fun `the block sits flush with the right margin`() {
        val box = PdfTotals.box(545f)

        assertEquals(545f - PdfTotals.WIDTH, box.left, 0.001f)
        assertEquals(545f, box.right, 0.001f)
    }

    @Test
    fun `the amount box is the width it promises`() {
        val box = PdfTotals.box(545f)

        assertEquals(545f - PdfTotals.VALUE_WIDTH, box.valueLeft, 0.001f)
        assertEquals(PdfTotals.VALUE_WIDTH.toInt(), box.valueWidth)
    }

    @Test
    fun `the label box ends before the amount box starts`() {
        val box = PdfTotals.box(545f)
        val labelRight = box.labelX + box.labelWidth

        // One point of slack: labelWidth is truncated to an Int, so the box can
        // fall a fraction short of the gap but can never cross it.
        assertTrue(
            "label ends at $labelRight, amount starts at ${box.valueLeft}",
            labelRight <= box.valueLeft - PdfTotals.GAP + 1f,
        )
    }

    @Test
    fun `the label box never leaves the block on the left`() {
        val box = PdfTotals.box(545f)

        assertEquals(box.left + PdfTotals.PAD, box.labelX, 0.001f)
        assertTrue(box.labelWidth > 0)
    }

    @Test
    fun `a narrower block still keeps the two boxes apart`() {
        val box = PdfTotals.box(545f, width = 200f)

        assertTrue(box.labelWidth > 0)
        assertTrue(box.labelX + box.labelWidth <= box.valueLeft - PdfTotals.GAP + 1f)
    }

    @Test
    fun `a wider amount box takes its room from the label, not the margin`() {
        val box = PdfTotals.box(545f, valueWidth = 150f)

        assertEquals(545f - 150f, box.valueLeft, 0.001f)
        assertEquals(545f, box.right, 0.001f)
        assertEquals(150, box.valueWidth)
        assertTrue(box.labelWidth > 0)
        assertTrue(box.labelX + box.labelWidth <= box.valueLeft - PdfTotals.GAP + 1f)
    }

    @Test
    fun `the amount fits a wide figure`() {
        // "12 500,00 DH" at 13pt is the widest thing this box ever holds. At 104
        // points it has about 20 points to spare, which is the margin the first
        // version of these templates did not have.
        val box = PdfTotals.box(545f)

        assertTrue(box.valueWidth >= 100)
    }
}
