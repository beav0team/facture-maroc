package com.yassine.facturemaroc.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Several payments against one document.
 *
 * The bug these pin: the record stored a single figure, so a second partial
 * payment overwrote the first and the receipt printed one cumulative amount the
 * customer had never handed over in one go.
 */
class PaymentsTest {

    private fun invoice(
        payments: List<PaymentInfo> = emptyList(),
        deposit: Double = 0.0,
        type: DocumentType = DocumentType.FACTURE,
    ) = InvoiceData(
        number = "F-20261110-0001",
        date = "10/11/2026",
        documentType = type,
        // No VAT: these tests are about the payment arithmetic, and a rate here
        // would only add a multiplication between the assertion and the number.
        vatPercent = 0.0,
        client = InvoiceClient("Client"),
        lines = listOf(InvoiceLine("Prestation", 1.0, 1_000.0)),
        depositAmount = deposit,
        payments = payments,
    )

    private fun payment(amount: Double, day: String = "10/11/2026") =
        PaymentInfo(paidAmount = amount, paidAt = day)

    @Test
    fun `two partial payments add up instead of replacing each other`() {
        val data = invoice(listOf(payment(400.0, "01/11/2026"), payment(350.0, "05/11/2026")))

        assertEquals(750.0, data.paidAmount, 0.001)
        assertEquals(250.0, data.balanceDue, 0.001)
        assertEquals(PaymentState.PARTIAL, data.paymentState)
        assertEquals(2, data.paymentCount)
    }

    @Test
    fun `the whole history settles the document`() {
        val data = invoice(listOf(payment(600.0), payment(400.0, "12/11/2026")))

        assertEquals(1_000.0, data.paidAmount, 0.001)
        assertEquals(0.0, data.balanceDue, 0.001)
        assertEquals(PaymentState.PAID, data.paymentState)
    }

    @Test
    fun `the last entry is the one a single-figure line names`() {
        val first = payment(400.0, "01/11/2026")
        val second = payment(350.0, "05/11/2026")
        val data = invoice(listOf(first, second))

        assertEquals(second, data.lastPayment)
        assertEquals("05/11/2026", data.lastPayment?.paidAt)
    }

    @Test
    fun `an unpaid document has no history at all`() {
        val data = invoice()

        assertTrue(data.payments.isEmpty())
        assertNull(data.lastPayment)
        assertEquals(PaymentState.UNPAID, data.paymentState)
    }

    @Test
    fun `a history longer than the balance never owes less than zero`() {
        // Two entries of 700 on a 1 000 document: the record can only have been
        // built by hand, and the arithmetic must still not report -400.
        val data = invoice(listOf(payment(700.0), payment(700.0)))

        assertEquals(1_000.0, data.paidAmount, 0.001)
        assertEquals(0.0, data.balanceDue, 0.001)
        assertEquals(PaymentState.PAID, data.paymentState)
    }

    @Test
    fun `a deposit reduces the balance, not the history`() {
        val data = invoice(listOf(payment(400.0)), deposit = 500.0)

        assertEquals(400.0, data.paidAmount, 0.001)
        assertEquals(100.0, data.balanceDue, 0.001)
        assertEquals(PaymentState.PARTIAL, data.paymentState)
    }

    @Test
    fun `the whole history survives a JSON round trip`() {
        val data = invoice(
            listOf(
                PaymentInfo(paidAmount = 400.0, paidAt = "01/11/2026", note = "Espèces"),
                PaymentInfo(
                    paidAmount = 350.0,
                    paidAt = "05/11/2026",
                    method = PaymentMethod.CHECK,
                    note = "Chèque 456",
                ),
            ),
        )

        val restored = InvoiceJson.dataFromJson(InvoiceJson.dataToJson(data))

        assertEquals(2, restored.payments.size)
        assertEquals(750.0, restored.paidAmount, 0.001)
        assertEquals("Chèque 456", restored.lastPayment?.note)
        assertEquals(PaymentMethod.CHECK, restored.lastPayment?.method)
    }

    @Test
    fun `an old single payment object reads back as a one-entry history`() {
        // The shape every document issued before payments had a history carries.
        val legacy = JSONObject(
            """
            {
              "number": "F-20261110-0002",
              "date": "10/11/2026",
              "documentType": "facture",
              "client": { "name": "Client" },
              "lines": [{ "description": "Prestation", "quantity": 1.0, "unitPrice": 1000.0 }],
              "payment": { "paidAmount": 250.0, "paidAt": "09/11/2026", "note": "Virement" }
            }
            """.trimIndent(),
        )

        val data = InvoiceJson.dataFromJson(legacy)

        assertEquals(1, data.payments.size)
        assertEquals(250.0, data.paidAmount, 0.001)
        assertEquals("09/11/2026", data.lastPayment?.paidAt)
        assertEquals("Virement", data.lastPayment?.note)
    }

    @Test
    fun `a document with no payment key reads back empty`() {
        val bare = JSONObject(
            """
            {
              "number": "F-20261110-0003",
              "date": "10/11/2026",
              "documentType": "facture",
              "client": { "name": "Client" },
              "lines": [{ "description": "Prestation", "quantity": 1.0, "unitPrice": 1000.0 }]
            }
            """.trimIndent(),
        )

        assertTrue(InvoiceJson.dataFromJson(bare).payments.isEmpty())
    }

    @Test
    fun `only a document that asks to be paid can take a payment`() {
        assertTrue(DocumentType.FACTURE.canBePaid)
        assertTrue(DocumentType.TICKET.canBePaid)
        assertTrue(DocumentType.SIMPLIFIEE.canBePaid)
        assertTrue(DocumentType.EXPORT.canBePaid)

        assertFalse(DocumentType.DEVIS.canBePaid)
        assertFalse(DocumentType.BON_DE_COMMANDE.canBePaid)
        assertFalse(DocumentType.PROFORMA.canBePaid)
        assertFalse(DocumentType.AVOIR.canBePaid)
    }

    @Test
    fun `a credit note carries no payment of its own`() {
        val record = InvoiceRecord(
            id = "r1",
            data = invoice(listOf(payment(400.0))),
            status = InvoiceStatus.ISSUED,
            createdAt = 0L,
            updatedAt = 0L,
        )

        val avoir = CreditNote.from(record)

        assertTrue(avoir!!.data.payments.isEmpty())
        assertEquals(0.0, avoir.data.paidAmount, 0.001)
        assertEquals(1_000.0, avoir.data.balanceDue, 0.001)
    }
}
