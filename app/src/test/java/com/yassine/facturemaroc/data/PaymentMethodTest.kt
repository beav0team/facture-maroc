package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * How each payment was actually made.
 *
 * The document carries the method the shop expected; the payment carries the
 * one that happened. A customer who says "je paie en espèces" after a virement
 * invoice must leave a paper that says espèces, and a payment written before
 * this field existed must not invent one.
 */
class PaymentMethodTest {

    private fun invoice(payment: PaymentInfo?) = InvoiceData(
        number = "F-20261110-0001",
        date = "10/11/2026",
        client = InvoiceClient("Client"),
        lines = listOf(InvoiceLine("Prestation", 1, 100.0)),
        paymentMethod = PaymentMethod.TRANSFER,
        payment = payment,
    )

    @Test
    fun `a payment keeps the method it was received with`() {
        val data = invoice(PaymentInfo(paidAmount = 120.0, method = PaymentMethod.CASH))

        assertEquals(PaymentMethod.CASH, data.payment!!.method)
    }

    @Test
    fun `a payment without its own method follows the document`() {
        val data = invoice(PaymentInfo(paidAmount = 120.0))

        assertNull(data.payment!!.method)
        // The document is what the paper prints in that case.
        assertEquals(PaymentMethod.TRANSFER, data.paymentMethod)
    }

    @Test
    fun `the payment method survives a JSON round trip`() {
        val restored = InvoiceJson.dataFromJson(
            InvoiceJson.dataToJson(invoice(PaymentInfo(paidAmount = 120.0, method = PaymentMethod.CARD))),
        )

        assertEquals(PaymentMethod.CARD, restored.payment!!.method)
    }

    @Test
    fun `an older payment restores with no method of its own`() {
        val restored = InvoiceJson.dataFromJson(
            InvoiceJson.dataToJson(invoice(PaymentInfo(paidAmount = 120.0))),
        )

        assertNull(restored.payment!!.method)
    }

    @Test
    fun `an amount in words has no legal opening of its own`() {
        assertEquals(
            "CINQ CENTS DIRHAMS ET QUINZE CENTIMES",
            AmountWords.amount(500.15),
        )
        assertEquals("UN DIRHAM", AmountWords.amount(1.0))
        assertEquals("ZÉRO DIRHAMS", AmountWords.amount(0.0))
    }
}
