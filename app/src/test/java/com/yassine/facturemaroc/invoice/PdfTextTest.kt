package com.yassine.facturemaroc.invoice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The small pieces of text the templates share.
 *
 * `oneline` exists because a shop's address is typed with a line break in it,
 * and every template puts it into a slot one line tall: StaticLayout honours
 * the break, so the second line fell outside the header and the city was
 * replaced by an ellipsis on a real invoice.
 */
class PdfTextTest {

    @Test
    fun `a stored address becomes one line`() {
        assertEquals(
            "12 rue des Fleurs, 20250 Casablanca",
            PdfText.oneline("12 rue des Fleurs\n20250 Casablanca"),
        )
    }

    @Test
    fun `windows line breaks and stray spaces are collapsed too`() {
        assertEquals(
            "A, B, C",
            PdfText.oneline("  A \r\n B \n\n  C  "),
        )
    }

    @Test
    fun `a single-line address is left alone`() {
        assertEquals("12 rue des Fleurs", PdfText.oneline("12 rue des Fleurs"))
    }

    /** French formatting separates thousands with a space, which may be a
     *  no-break one depending on the platform — compare it as a plain space. */
    private fun visible(text: String): String =
        text.replace('\u00A0', ' ').replace('\u202F', ' ')

    @Test
    fun `money uses a space for thousands and a comma for centimes`() {
        assertEquals("4 947,60 DH", visible(PdfText.money(4947.6)))
        assertEquals("1 200,00 DH", visible(PdfText.money(1200.0)))
    }

    @Test
    fun `a whole percentage has no decimal part`() {
        assertEquals("20", PdfText.percent(20.0))
        assertEquals("5,5", PdfText.percent(5.5))
    }
}
