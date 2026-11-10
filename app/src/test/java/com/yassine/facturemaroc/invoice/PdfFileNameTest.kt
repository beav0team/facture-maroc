package com.yassine.facturemaroc.invoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules a shared PDF name must satisfy on every target at once.
 *
 * A shop may type anything into the pattern: a slash, a colon, a name that is
 * just dots. Each of those is illegal somewhere between the phone, the mail
 * client and a USB stick, and a file that cannot be written is an invoice that
 * was never sent.
 */
class PdfFileNameTest {

    @Test
    fun `the default pattern is a document number with the pdf extension`() {
        assertEquals(
            "facture_F-20261110-0001.pdf",
            PdfFileName.name("", number = "F-20261110-0001"),
        )
        assertEquals(
            "facture_F-20261110-0001.pdf",
            PdfFileName.name(PdfFileName.DEFAULT_PATTERN, number = "F-20261110-0001"),
        )
    }

    @Test
    fun `the placeholders are filled from the document`() {
        val name = PdfFileName.name(
            "{type}_{date}_{number}_{client}",
            number = "F-1",
            client = "Garage Atlas",
            date = "10/11/2026",
            type = "facture",
        )

        // The slash in a date is not allowed in a name, so it becomes a dash-
        // safe underscore rather than opening a folder.
        assertEquals("facture_10_11_2026_F-1_Garage Atlas.pdf", name)
    }

    @Test
    fun `a path separator can never survive`() {
        val name = PdfFileName.name("{client}", number = "F-1", client = "../../etc/passwd")

        assertFalse(name.contains("/"))
        assertFalse(name.contains("\\"))
        assertFalse(name.startsWith("."))
        assertTrue(name.endsWith(".pdf"))
    }

    @Test
    fun `the extension is forced exactly once`() {
        assertEquals("ma facture.pdf", PdfFileName.name("ma facture.pdf", number = "F-1"))
        assertEquals("ma facture.pdf", PdfFileName.name("ma facture.PDF", number = "F-1"))
        assertEquals("ma facture.pdf", PdfFileName.sanitise("ma facture.pdf"))
        assertEquals("ma facture.pdf", PdfFileName.sanitise("ma facture"))
    }

    @Test
    fun `a pattern that sanitises to nothing still produces a file`() {
        val name = PdfFileName.name("...", number = "F-1")

        assertEquals("facture_F-1.pdf", name)
    }

    @Test
    fun `a very long pattern is trimmed rather than refused`() {
        val name = PdfFileName.name("x".repeat(200), number = "F-1")

        assertTrue(name.length <= 94)
        assertTrue(name.endsWith(".pdf"))
    }

    @Test
    fun `the receipt and statement names go through the same rules`() {
        assertEquals("recu_F-1.pdf", PdfFileName.sanitise("recu_F-1"))
        assertEquals("releve_garage_atlas.pdf", PdfFileName.sanitise("releve_garage/atlas"))
    }

    @Test
    fun `a control character cannot reach the file system`() {
        val name = PdfFileName.sanitise("facture\u0000\u0007F-1")

        assertFalse(name.any { it.code < 0x20 })
        assertTrue(name.endsWith(".pdf"))
    }
}
