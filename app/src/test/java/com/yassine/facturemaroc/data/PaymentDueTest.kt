package com.yassine.facturemaroc.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The payment deadline and the day it turns late.
 *
 * The whole point of a deadline is the day after it: calling a customer late
 * on the day the invoice says they may still pay is a mistake that costs a
 * client, so the boundary is tested explicitly.
 */
class PaymentDueTest {

    private fun day(text: String): Date =
        SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).parse(text)!!

    private fun invoice(dueDate: String?, paid: Double = 0.0) = InvoiceData(
        number = "F-20261110-0001",
        date = "10/11/2026",
        client = InvoiceClient("Client"),
        lines = listOf(InvoiceLine("Prestation", 1, 100.0)),
        dueDate = dueDate,
        payment = if (paid > 0.0) PaymentInfo(paidAmount = paid) else null,
    )

    @Test
    fun `a deadline is not late on the day itself`() {
        val data = invoice("30/11/2026")

        assertFalse(PaymentDue.isOverdue(data.dueDate, data.paymentState, day("30/11/2026")))
        assertTrue(PaymentDue.isOverdue(data.dueDate, data.paymentState, day("01/12/2026")))
    }

    @Test
    fun `a settled document is never late`() {
        val data = invoice("30/11/2026", paid = 120.0)

        assertEquals(PaymentState.PAID, data.paymentState)
        assertFalse(PaymentDue.isOverdue(data.dueDate, data.paymentState, day("01/01/2027")))
    }

    @Test
    fun `a partially paid document can be late`() {
        val data = invoice("30/11/2026", paid = 50.0)

        assertEquals(PaymentState.PARTIAL, data.paymentState)
        assertTrue(PaymentDue.isOverdue(data.dueDate, data.paymentState, day("01/12/2026")))
    }

    @Test
    fun `no deadline means nothing to be late for`() {
        val data = invoice(null)

        assertNull(data.dueDatePrinted)
        assertFalse(PaymentDue.isOverdue(data.dueDate, data.paymentState, day("01/01/2030")))
    }

    @Test
    fun `the clock time of day never decides lateness`() {
        val lateOnTheSameDay = PaymentDue.isOverdue(
            "01/12/2026",
            PaymentState.UNPAID,
            day("01/12/2026"),
        )

        assertFalse(lateOnTheSameDay)
    }

    @Test
    fun `days until counts calendar days and goes negative after the deadline`() {
        assertEquals(20, PaymentDue.daysUntil("30/11/2026", day("10/11/2026")))
        assertEquals(0, PaymentDue.daysUntil("30/11/2026", day("30/11/2026")))
        assertEquals(-2, PaymentDue.daysUntil("30/11/2026", day("02/12/2026")))
    }

    @Test
    fun `a nonsense date is not a deadline`() {
        val data = invoice("31/02/2026")

        assertTrue(PaymentDue.isOverdue(data.dueDate, data.paymentState, day("10/03/2026")).not())
    }

    @Test
    fun `a deadline survives a JSON round trip`() {
        val data = invoice("30/11/2026")

        val restored = InvoiceJson.dataFromJson(InvoiceJson.dataToJson(data))

        assertEquals("30/11/2026", restored.dueDate)
        assertEquals("30/11/2026", restored.dueDatePrinted)
    }

    @Test
    fun `the builders of an older file have no deadline`() {
        val restored = InvoiceJson.dataFromJson(
            InvoiceJson.dataToJson(invoice(null)),
        )

        assertNull(restored.dueDate)
    }
}
