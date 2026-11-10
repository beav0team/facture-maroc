package com.yassine.facturemaroc.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a client statement counts, and what it must never count.
 *
 * The figures here are the ones a shop reads out to a customer on the phone, so
 * the interesting cases are the exclusions (a devis is not a debt), the credit
 * note that has to pull the balance down, and the late column that must follow
 * the deadline rather than the day the invoice was written.
 */
class ClientStatementTest {

    private fun day(text: String): Date =
        SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).parse(text)!!

    private fun record(
        id: String,
        type: DocumentType = DocumentType.FACTURE,
        name: String = "Société BTP Nord",
        total: Double = 100.0,
        paid: Double = 0.0,
        date: String = "01/11/2026",
        dueDate: String? = null,
        status: InvoiceStatus = InvoiceStatus.ISSUED,
    ) = InvoiceRecord(
        id = id,
        data = InvoiceData(
            number = "X-$id",
            date = date,
            client = InvoiceClient(name = name, ice = "009876543000012"),
            // VAT at zero keeps the test about the statement, not about tax:
            // every figure below is the number written in the record.
            lines = listOf(InvoiceLine("Ligne", 1.0, total, vatPercent = 0.0)),
            documentType = type,
            dueDate = dueDate,
            payment = if (paid > 0.0) PaymentInfo(paidAmount = paid) else null,
        ),
        status = status,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun `a draft is not stated`() {
        val records = listOf(
            record("brouillon", total = 400.0, status = InvoiceStatus.DRAFT),
            record("emise", total = 100.0),
        )

        val statement = ClientStatements.forClient(records, "Société BTP Nord")

        assertEquals(listOf("X-emise"), statement.lines.map { it.number })
        assertEquals(100.0, statement.billed, 0.001)
    }

    @Test
    fun `a quote is not a debt`() {
        val records = listOf(
            record("devis", type = DocumentType.DEVIS, total = 900.0),
            record("facture", total = 200.0),
        )

        val statement = ClientStatements.forClient(records, "Société BTP Nord")

        assertEquals(listOf("X-facture"), statement.lines.map { it.number })
        assertEquals(200.0, statement.billed, 0.001)
        assertEquals(200.0, statement.balance, 0.001)
    }

    @Test
    fun `a proforma and a purchase order are not debts`() {
        val records = listOf(
            record("proforma", type = DocumentType.PROFORMA, total = 300.0),
            record("bon", type = DocumentType.BON_DE_COMMANDE, total = 500.0),
        )

        val statement = ClientStatements.forClient(records, "Société BTP Nord")

        assertTrue(statement.isEmpty)
        assertEquals(0.0, statement.billed, 0.001)
    }

    @Test
    fun `a credit note subtracts from the billed total and from the balance`() {
        val records = listOf(
            record("facture", total = 2_000.0),
            record("avoir", type = DocumentType.AVOIR, total = 500.0, date = "02/11/2026"),
        )

        val statement = ClientStatements.forClient(records, "Société BTP Nord")

        assertEquals(2, statement.lines.size)
        assertEquals(2_000.0, statement.billed, 0.001)
        assertEquals(500.0, statement.credited, 0.001)
        assertEquals(1_500.0, statement.balance, 0.001)
        assertEquals(-500.0, statement.lines.last().balance, 0.001)
        assertTrue(statement.lines.last().isCredit)
    }

    @Test
    fun `an unpaid invoice is owed in full`() {
        val statement = ClientStatements.forClient(
            listOf(record("facture", total = 1_200.0)),
            "Société BTP Nord",
        )

        val line = statement.lines.single()
        assertEquals(0.0, line.paid, 0.001)
        assertEquals(1_200.0, line.balance, 0.001)
        assertEquals(1_200.0, statement.balance, 0.001)
    }

    @Test
    fun `a partial payment leaves the rest due`() {
        val statement = ClientStatements.forClient(
            listOf(record("facture", total = 1_200.0, paid = 400.0)),
            "Société BTP Nord",
        )

        val line = statement.lines.single()
        assertEquals(400.0, line.paid, 0.001)
        assertEquals(800.0, line.balance, 0.001)
        assertEquals(400.0, statement.paid, 0.001)
        assertEquals(800.0, statement.balance, 0.001)
    }

    @Test
    fun `a settled invoice owes nothing`() {
        val statement = ClientStatements.forClient(
            listOf(record("facture", total = 1_200.0, paid = 1_200.0)),
            "Société BTP Nord",
        )

        val line = statement.lines.single()
        assertEquals(1_200.0, line.paid, 0.001)
        assertEquals(0.0, line.balance, 0.001)
        assertEquals(0.0, statement.balance, 0.001)
    }

    @Test
    fun `lateness follows the due date and not the invoice date`() {
        // The invoice is written in October and is due at the end of November:
        // in the middle of November it is not late, however old the document
        // already looks.
        val records = listOf(
            record("en-cours", date = "01/10/2026", dueDate = "30/11/2026"),
            record("echue", date = "01/11/2026", dueDate = "05/11/2026"),
            record("reglee", date = "01/10/2026", dueDate = "05/10/2026", paid = 100.0),
        )

        val statement = ClientStatements.forClient(
            records,
            "Société BTP Nord",
            today = day("15/11/2026"),
        )
        val byNumber = statement.lines.associateBy { it.number }

        assertFalse(byNumber.getValue("X-en-cours").isLate)
        assertTrue(byNumber.getValue("X-echue").isLate)
        // A settled document is never late, whatever its deadline was.
        assertFalse(byNumber.getValue("X-reglee").isLate)
    }

    @Test
    fun `a credit note is never late`() {
        val statement = ClientStatements.forClient(
            listOf(record("avoir", type = DocumentType.AVOIR, dueDate = "01/01/2026")),
            "Société BTP Nord",
            today = day("15/11/2026"),
        )

        assertFalse(statement.lines.single().isLate)
    }

    @Test
    fun `the client match ignores case and surrounding spaces`() {
        val records = listOf(
            record("facture", name = "  société btp nord ", total = 300.0),
        )

        val statement = ClientStatements.forClient(records, "SOCIÉTÉ BTP Nord")

        assertEquals(1, statement.lines.size)
        assertEquals(300.0, statement.billed, 0.001)
        assertEquals("SOCIÉTÉ BTP Nord", statement.clientName)
    }

    @Test
    fun `another client is not in this statement`() {
        val records = listOf(
            record("notre", name = "Société BTP Nord", total = 300.0),
            record("autre", name = "Garage Atlas", total = 900.0),
        )

        val statement = ClientStatements.forClient(records, "Société BTP Nord")

        assertEquals(listOf("X-notre"), statement.lines.map { it.number })
        assertEquals(300.0, statement.billed, 0.001)
    }

    @Test
    fun `an unknown client gets an empty statement`() {
        val statement = ClientStatements.forClient(
            listOf(record("facture", name = "Garage Atlas")),
            "Client inconnu",
        )

        assertTrue(statement.isEmpty)
        assertEquals(0.0, statement.billed, 0.001)
        assertEquals(0.0, statement.credited, 0.001)
        assertEquals(0.0, statement.paid, 0.001)
        assertEquals(0.0, statement.balance, 0.001)
        assertFalse(ClientStatements.hasStatement(listOf(record("f")), "Client inconnu"))
    }

    @Test
    fun `every figure is rounded to two decimals`() {
        // Three lines at a third of a dirham each: each line itself carries a
        // repeating decimal, and a statement that printed it would show a
        // balance nobody can type into a till.
        val records = listOf(
            record("a", total = 33.333),
            record("b", total = 33.333),
            record("c", total = 33.333),
        )

        val statement = ClientStatements.forClient(records, "Société BTP Nord")

        assertEquals(99.99, statement.billed, 0.0001)
        assertEquals(99.99, statement.balance, 0.0001)
        statement.lines.forEach {
            assertEquals(33.33, it.total, 0.0001)
            assertEquals(it.total, it.balance, 0.0)
        }
    }
}
