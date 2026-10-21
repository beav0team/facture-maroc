package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
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
}
