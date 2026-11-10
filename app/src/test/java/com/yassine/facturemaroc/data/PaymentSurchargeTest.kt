package com.yassine.facturemaroc.data

import com.yassine.facturemaroc.invoice.InvoiceValidation
import com.yassine.facturemaroc.invoice.ValidationCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentSurchargeTest {

    private fun invoice(
        lines: List<InvoiceLine> = listOf(InvoiceLine("Prestation", 1.0, 100.0)),
        surcharge: Double = 0.0,
        payment: PaymentInfo? = null,
    ) = InvoiceData(
        number = "F-20261110-0001",
        date = "10/11/2026",
        client = InvoiceClient("Client"),
        lines = lines,
        surchargeAmount = surcharge,
        payments = listOfNotNull(payment),
    )

    @Test
    fun `a surcharge is taxed and sits after the discount`() {
        val data = invoice(
            lines = listOf(
                InvoiceLine("A", 1.0, 100.0),
                InvoiceLine("B", 1.0, 100.0),
            ),
            surcharge = 50.0,
        ).copy(discountPercent = 10)

        // 200 − 20 (discount) + 50 (delivery) = 230 HT, TVA 46, total 276.
        assertEquals(200.0, data.subtotal, 0.0)
        assertEquals(20.0, data.discountAmount, 0.0)
        assertEquals(180.0, data.totalExcludingVat, 0.0)
        assertEquals(50.0, data.surcharge, 0.0)
        assertEquals(230.0, data.vatBase, 0.0)
        assertEquals(276.0, data.total, 0.0)
        // The tax is on the discounted goods plus the delivery, not on the
        // difference between the two printed totals.
        assertEquals(46.0, data.vatAmount, 0.0)
    }

    @Test
    fun `a surcharge is taxed even when the lines are exempt`() {
        // The delivery is a taxed service even when the goods are exported.
        val data = invoice(
            lines = listOf(InvoiceLine("Export", 1.0, 100.0, taxExempt = true)),
            surcharge = 100.0,
        )

        assertEquals(100.0, data.vatBase, 0.0)
        assertEquals(20.0, data.vatAmount, 0.0)
        assertEquals(220.0, data.total, 0.0)
    }

    @Test
    fun `a negative surcharge is ignored rather than reducing the total`() {
        val data = invoice(surcharge = -50.0)

        assertEquals(0.0, data.surcharge, 0.0)
        assertEquals(120.0, data.total, 0.0)
    }

    @Test
    fun `validation accepts the same arithmetic and rejects a negative charge`() {
        val valid = invoice(surcharge = 50.0).copy(discountPercent = 10)
        assertTrue(
            InvoiceValidation.validate(valid).none { it.code == ValidationCode.TOTAL_MISMATCH },
        )

        val invalid = invoice(surcharge = -1.0)
        assertTrue(
            InvoiceValidation.validate(invalid).any { it.code == ValidationCode.INVALID_SURCHARGE },
        )
    }

    @Test
    fun `payment state is derived from the amount, not stored`() {
        val base = invoice()

        assertEquals(PaymentState.UNPAID, base.paymentState)
        assertEquals(0.0, base.paidAmount, 0.0)

        val partial = base.copy(payments = listOf(PaymentInfo(paidAmount = 50.0, paidAt = "10/11/2026")))
        assertEquals(PaymentState.PARTIAL, partial.paymentState)
        assertEquals(50.0, partial.paidAmount, 0.0)
        assertEquals(70.0, partial.balanceDue, 0.0)

        val paid = base.copy(payments = listOf(PaymentInfo(paidAmount = 120.0, paidAt = "10/11/2026")))
        assertEquals(PaymentState.PAID, paid.paymentState)
        assertEquals(0.0, paid.balanceDue, 0.0)
        assertTrue(paid.isSettled)
    }

    @Test
    fun `an overpayment is clamped instead of owing a negative balance`() {
        val paid = invoice(payment = PaymentInfo(paidAmount = 999.0, paidAt = "10/11/2026"))

        assertEquals(120.0, paid.paidAmount, 0.0)
        assertEquals(0.0, paid.balanceDue, 0.0)
        assertEquals(PaymentState.PAID, paid.paymentState)
    }

    @Test
    fun `a zero payment reads back as unpaid, and JSON keeps the note`() {
        val zero = invoice(payment = PaymentInfo(paidAmount = 0.0, paidAt = "10/11/2026"))
        assertEquals(PaymentState.UNPAID, zero.paymentState)

        val partial = invoice(
            surcharge = 30.0,
            payment = PaymentInfo(paidAmount = 40.0, paidAt = "09/11/2026", note = "Chèque 123"),
        )
        val restored = InvoiceJson.dataFromJson(InvoiceJson.dataToJson(partial))

        assertEquals(partial, restored)
        assertEquals(30.0, restored.surcharge, 0.0)
        assertEquals(40.0, restored.lastPayment!!.paidAmount, 0.0)
        assertEquals("Chèque 123", restored.lastPayment!!.note)
        assertEquals(PaymentState.PARTIAL, restored.paymentState)
    }

    @Test
    fun `an old document without surcharge or payment still reads`() {
        val legacy = org.json.JSONObject(
            """
            {
              "number": "F-20260101-0001",
              "date": "01/01/2026",
              "client": { "name": "Client" },
              "lines": [ { "description": "Pneu", "quantity": 1, "unitPrice": 500 } ],
              "discountPercent": 0,
              "vatPercent": 20,
              "paymentMethod": "cash",
              "documentType": "facture"
            }
            """.trimIndent(),
        )

        val data = InvoiceJson.dataFromJson(legacy)

        assertEquals(0.0, data.surcharge, 0.0)
        assertTrue(data.payments.isEmpty())
        assertEquals("Livraison", data.surchargeLabel)
        assertEquals(600.0, data.total, 0.0)
    }
}
