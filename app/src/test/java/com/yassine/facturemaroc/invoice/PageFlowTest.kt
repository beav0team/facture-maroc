package com.yassine.facturemaroc.invoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic behind a page break.
 *
 * The renderers themselves cannot be tested off a device, because PdfDocument
 * is part of the framework. What can be tested is the decision they all share:
 * which row lands on which sheet. This is the part that was wrong before — not
 * by a number, but by never being asked — so a twenty-five line invoice lost
 * its totals off the bottom of the page.
 */
class PageFlowTest {

    private fun layout(
        heights: List<Float>,
        firstY: Float = 300f,
        tailHeight: Float = 100f,
    ) = PageFlow.layout(
        heights = heights,
        firstY = firstY,
        resumeY = 72f,
        bottom = 790f,
        tailHeight = tailHeight,
    )

    @Test
    fun `a short invoice stays on one page and keeps its tail`() {
        // 18 rows end at 660, and the 100pt tail still fits under 790.
        val result = layout(List(18) { 20f })
        assertEquals(1, result.pages.size)
        assertEquals(1, result.totalPages)
        assertFalse(result.tailOnNewPage)
    }

    @Test
    fun `forty rows become two pages`() {
        val result = layout(List(40) { 20f })
        assertEquals(2, result.pages.size)
        assertEquals(40, result.rows.size)
    }

    @Test
    fun `every row appears exactly once and in order`() {
        val heights = List(97) { 10f + (it % 7) * 4f }
        val result = layout(heights)
        assertEquals((0 until heights.size).toList(), result.rows)
    }

    @Test
    fun `no page is empty`() {
        val result = layout(List(57) { 25f })
        assertTrue(result.pages.all { !it.isEmpty() })
    }

    @Test
    fun `a row that fits exactly at the bottom stays on the page`() {
        // One 20pt row from 770 ends at 790, which is the last usable point;
        // the second row has nowhere to go but a fresh sheet.
        val result = layout(listOf(20f, 20f), firstY = 770f)
        assertEquals(0, result.pages.first().last)
        assertEquals(2, result.pages.size)
    }

    @Test
    fun `continuous pages fill from the resume line, not from the first header`() {
        // 24 rows fit under a full header, 35 under a short one, so 40 rows
        // need exactly two pages and leave room for the totals.
        val result = layout(List(40) { 20f })
        assertEquals(0..23, result.pages[0])
        assertEquals(24..39, result.pages[1])
        assertFalse(result.tailOnNewPage)
    }

    @Test
    fun `the totals move to their own sheet when they no longer fit`() {
        val result = layout(List(40) { 20f }, tailHeight = 500f)
        assertEquals(2, result.pages.size)
        assertTrue(result.tailOnNewPage)
        assertEquals(3, result.totalPages)
    }

    @Test
    fun `an empty table still needs a page for its totals`() {
        val result = layout(emptyList())
        assertEquals(1, result.pages.size)
        assertEquals(0, result.rows.size)
        assertFalse(result.tailOnNewPage)
    }

    @Test
    fun `an empty table whose words do not fit asks for a second page`() {
        val result = layout(emptyList(), tailHeight = 900f)
        assertEquals(1, result.pages.size)
        assertTrue(result.tailOnNewPage)
    }

    @Test
    fun `a row taller than the page is placed rather than dropped`() {
        val result = layout(listOf(20f, 2000f, 20f))
        assertEquals(3, result.rows.size)
        assertEquals(listOf(0, 1, 2), result.rows)
        assertEquals(3, result.pages.size)
    }

    @Test
    fun `a single overflowing row is never silently skipped`() {
        val result = layout(listOf(3000f))
        assertEquals(1, result.pages.size)
        assertEquals(listOf(0), result.rows)
    }

    @Test
    fun `pages grow by one whenever the sheet runs out`() {
        val result = layout(List(200) { 20f })
        assertTrue(result.pages.size >= 5)
        assertEquals(200, result.rows.size)
        assertEquals((0 until 200).toList(), result.rows)
    }
}
