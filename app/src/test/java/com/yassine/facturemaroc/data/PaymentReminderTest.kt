package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The payment reminder: a sentence a shop sends, not a system message.
 *
 * The tests pin the two things a customer will call about — the amount still
 * owed and the deadline — and the cases where sending nothing is the right
 * answer.
 */
class PaymentReminderTest {

    private fun record(
        type: DocumentType = DocumentType.FACTURE,
        status: InvoiceStatus = InvoiceStatus.ISSUED,
        dueDate: String? = null,
        paid: Double = 0.0,
        client: String = "Karim",
    ) = InvoiceRecord(
        id = "r1",
        data = InvoiceData(
            number = "F-20261110-0001",
            date = "10/11/2026",
            client = InvoiceClient(name = client),
            lines = listOf(InvoiceLine("Chaise", 1.0, 1_000.0)),
            documentType = type,
            dueDate = dueDate,
            payment = if (paid > 0.0) PaymentInfo(paidAmount = paid) else null,
        ),
        status = status,
        createdAt = 0,
        updatedAt = 0,
    )

    private val shop = ShopSettings(name = "Atelier Nord", phone = "0600000000")

    @Test
    fun `an unpaid invoice names the balance and the shop`() {
        val text = PaymentReminder.text(record(), shop)

        assertTrue(text!!.contains("1 200,00 DH"))
        assertTrue(text.contains("F-20261110-0001"))
        assertTrue(text.contains("Atelier Nord"))
        assertTrue(text.contains("0600000000"))
        assertTrue(text.startsWith("Bonjour Karim"))
    }

    @Test
    fun `a partial payment is acknowledged, then what is left`() {
        val text = PaymentReminder.text(record(paid = 200.0), shop)!!

        assertTrue(text.contains("bien reçu"))
        assertTrue(text.contains("200,00 DH"))
        assertTrue(text.contains("1 000,00 DH"))
    }

    @Test
    fun `a deadline is printed, and an overdue one says so`() {
        val due = PaymentReminder.text(record(dueDate = "31/12/2026"), shop)!!

        assertTrue(due.contains("Échéance : 31/12/2026"))
    }

    @Test
    fun `nothing is sent for a paid document, a draft, a quote or an avoir`() {
        assertNull(PaymentReminder.text(record(paid = 1_200.0), shop))
        assertNull(PaymentReminder.text(record(status = InvoiceStatus.DRAFT), shop))
        assertNull(PaymentReminder.text(record(type = DocumentType.DEVIS), shop))
        assertNull(PaymentReminder.text(record(type = DocumentType.AVOIR), shop))
    }

    @Test
    fun `an unknown client still gets a salutation`() {
        val text = PaymentReminder.text(record(client = "  "), shop)!!

        assertTrue(text.startsWith("Bonjour Madame, Monsieur"))
    }

    @Test
    fun `the button is offered exactly when a reminder exists`() {
        assertTrue(PaymentReminder.available(record()))
        assertFalse(PaymentReminder.available(record(paid = 1_200.0)))
        assertFalse(PaymentReminder.available(record(status = InvoiceStatus.DRAFT)))
        assertFalse(PaymentReminder.available(record(type = DocumentType.DEVIS)))
    }

    @Test
    fun `a shop with no phone still signs the message`() {
        val text = PaymentReminder.text(record(), ShopSettings(name = "Atelier Nord"))!!

        assertTrue(text.contains("Atelier Nord"))
        assertFalse(text.contains("—"))
    }
}
