package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The accountant's CSV and the month's VAT table.
 *
 * Both are read by someone who will not open the app: the export has to be a
 * file Excel parses without a dialog, and the VAT table has to agree with the
 * paper down to the centime. That is why the arithmetic is checked here rather
 * than by eyeballing a screen.
 */
class AccountantExportTest {

    private fun record(
        id: String,
        type: DocumentType = DocumentType.FACTURE,
        date: String = "10/11/2026",
        status: InvoiceStatus = InvoiceStatus.ISSUED,
        lines: List<InvoiceLine> = listOf(InvoiceLine("Chaise", 1.0, 100.0)),
        client: InvoiceClient = InvoiceClient(name = "Client"),
    ) = InvoiceRecord(
        id = id,
        data = InvoiceData(
            number = "F-$id",
            date = date,
            client = client,
            lines = lines,
            documentType = type,
        ),
        status = status,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun `the export has a header and one row per issued document`() {
        val csv = AccountantExport.documentsCsv(
            listOf(
                record("1"),
                record("2", type = DocumentType.TICKET),
                record("3", status = InvoiceStatus.DRAFT),
            ),
        )

        val lines = csv.trim().lines()
        assertEquals(3, lines.size) // header + two issued documents
        assertEquals(AccountantExport.HEADER.joinToString(";"), lines[0])
        assertTrue(lines.none { it.contains("F-3") })
    }

    @Test
    fun `amounts use a French decimal and never a thousands space`() {
        val csv = AccountantExport.documentsCsv(listOf(record("1")))

        val row = csv.trim().lines()[1]
        // 100 HT + 20 % = 120 TTC, written the way Excel FR reads it.
        assertTrue(row.contains("100,00"))
        assertTrue(row.contains("120,00"))
        assertFalse(row.contains("1 000"))
    }

    @Test
    fun `a client name with a semicolon cannot shift the columns`() {
        val csv = AccountantExport.documentsCsv(
            listOf(record("1", client = InvoiceClient(name = "Dupont; Frères"))),
        )

        assertTrue(csv.contains("\"Dupont; Frères\""))
    }

    @Test
    fun `a quote with a quote character is doubled, not removed`() {
        val csv = AccountantExport.documentsCsv(
            listOf(record("1", client = InvoiceClient(name = "L'Atelier \"Nord\""))),
        )

        assertTrue(csv.contains("\"\"Nord\"\""))
    }

    @Test
    fun `a credit note keeps its own type so the total is a plain sum`() {
        val csv = AccountantExport.documentsCsv(
            listOf(record("1"), record("2", type = DocumentType.AVOIR)),
        )

        val rows = csv.trim().lines()
        assertEquals(3, rows.size)
        assertTrue(rows[1].contains("Facture"))
        assertTrue(rows[2].contains("Avoir"))
    }

    @Test
    fun `the file name is dated and ends in csv`() {
        val name = AccountantExport.fileName()

        assertTrue(name.startsWith("comptable-"))
        assertTrue(name.endsWith(".csv"))
    }

    @Test
    fun `VAT collected for the month is net of credit notes`() {
        val records = listOf(
            record(
                "1",
                date = "10/11/2026",
                lines = listOf(InvoiceLine("Chaise", 1.0, 100.0)),
            ),
            record(
                "2",
                type = DocumentType.AVOIR,
                date = "11/11/2026",
                lines = listOf(InvoiceLine("Chaise", 1.0, 50.0)),
            ),
        )

        // 20 on the sale, 10 on the credit note.
        assertEquals(10.0, records.vatCollectedInMonth("11/2026"), 0.001)
    }

    @Test
    fun `the VAT table keeps a rate even when its net is zero`() {
        val records = listOf(
            record(
                "1",
                date = "10/11/2026",
                lines = listOf(InvoiceLine("Service", 1.0, 200.0, vatPercent = 10.0)),
            ),
            record(
                "2",
                type = DocumentType.AVOIR,
                date = "11/11/2026",
                lines = listOf(InvoiceLine("Service", 1.0, 200.0, vatPercent = 10.0)),
            ),
        )

        val rows = records.vatByRateInMonth("11/2026")

        assertEquals(1, rows.size)
        assertEquals(10.0, rows.single().rate, 0.001)
        assertEquals(0.0, rows.single().amount, 0.001)
    }

    @Test
    fun `the VAT table adds both rates of a mixed month`() {
        val records = listOf(
            record(
                "1",
                date = "10/11/2026",
                lines = listOf(
                    InvoiceLine("Article", 1.0, 100.0),
                    InvoiceLine("Service", 1.0, 200.0, vatPercent = 10.0),
                ),
            ),
        )

        val rows = records.vatByRateInMonth("11/2026")

        assertEquals(2, rows.size)
        assertEquals(20.0, rows.first { it.rate == 20.0 }.amount, 0.001)
        assertEquals(20.0, rows.first { it.rate == 10.0 }.amount, 0.001)
    }
}
