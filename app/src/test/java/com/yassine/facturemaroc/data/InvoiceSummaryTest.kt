package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceSummaryTest {

    private fun record(
        id: String,
        type: DocumentType,
        date: String = "21/10/2026",
        status: InvoiceStatus = InvoiceStatus.ISSUED,
        total: Double = 100.0,
    ) = InvoiceRecord(
        id = id,
        data = InvoiceData(
            number = "X-$id",
            date = date,
            client = InvoiceClient(name = "Client"),
            lines = listOf(InvoiceLine("Ligne", 1, total)),
            documentType = type,
        ),
        status = status,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun `a quote is not billed revenue`() {
        val records = listOf(
            record("facture", DocumentType.FACTURE, total = 200.0),
            record("devis", DocumentType.DEVIS, total = 900.0),
        )

        val billed = records.billedInMonth("10/2026")

        assertEquals(listOf("facture"), billed.map { it.id })
        assertEquals(240.0, billed.sumOf { it.data.total }, 0.001)
    }

    @Test
    fun `a purchase order is not billed revenue`() {
        val records = listOf(
            record("bon", DocumentType.BON_DE_COMMANDE),
            record("facture", DocumentType.FACTURE),
        )

        assertEquals(listOf("facture"), records.billedInMonth("10/2026").map { it.id })
    }

    @Test
    fun `a draft is not billed until it is issued`() {
        val records = listOf(
            record("brouillon", DocumentType.FACTURE, status = InvoiceStatus.DRAFT),
            record("emise", DocumentType.FACTURE),
        )

        assertEquals(listOf("emise"), records.billedInMonth("10/2026").map { it.id })
    }

    @Test
    fun `a ticket is a simplified invoice and counts`() {
        val records = listOf(record("ticket", DocumentType.TICKET, total = 75.0))

        assertEquals(listOf("ticket"), records.billedInMonth("10/2026").map { it.id })
    }

    @Test
    fun `a document from another month does not count`() {
        val records = listOf(
            record("septembre", DocumentType.FACTURE, date = "30/09/2026"),
            record("octobre", DocumentType.FACTURE, date = "01/10/2026"),
        )

        assertEquals(listOf("octobre"), records.billedInMonth("10/2026").map { it.id })
    }

    @Test
    fun `a simplified invoice and an export are invoices, not quotes`() {
        // They differ by heading, not by nature: the shop billed them.
        val records = listOf(
            record("simplifiee", DocumentType.SIMPLIFIEE),
            record("export", DocumentType.EXPORT),
            record("devis", DocumentType.DEVIS, total = 900.0),
        )

        assertEquals(
            listOf("simplifiee", "export"),
            records.billedInMonth("10/2026").map { it.id },
        )
    }

    @Test
    fun `a deposit and an adjustment are not counted twice`() {
        // Both carry "annule et remplace la facture N° …", and the app cannot
        // tell whether the invoice they adjust is in this month.
        val records = listOf(
            record("acompte", DocumentType.ACOMPTE),
            record("regularisation", DocumentType.REGULARISATION),
        )

        assertTrue(records.billedInMonth("10/2026").isEmpty())
    }

    @Test
    fun `a credit note comes off the month total and is not counted as an invoice`() {
        val records = listOf(
            record("facture", DocumentType.FACTURE, total = 500.0),
            record("avoir", DocumentType.AVOIR, total = 500.0),
        )

        // 500 HT + 20 % is 600 TTC on both sides, so the month is worth nothing.
        assertEquals(0.0, records.billedTotalInMonth("10/2026"), 0.001)
        assertEquals(listOf("facture"), records.billedInMonth("10/2026").map { it.id })
        assertEquals(listOf("avoir"), records.creditedInMonth("10/2026").map { it.id })
    }

    @Test
    fun `a credit note from another month leaves this month alone`() {
        val records = listOf(
            record("facture", DocumentType.FACTURE, total = 100.0),
            record("avoir", DocumentType.AVOIR, date = "30/09/2026"),
        )

        assertEquals(120.0, records.billedTotalInMonth("10/2026"), 0.001)
    }
}
