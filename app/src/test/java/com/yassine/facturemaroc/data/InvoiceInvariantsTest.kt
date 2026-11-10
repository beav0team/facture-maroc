package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic every document must satisfy, whatever mix of features it uses.
 *
 * These are not examples of a particular invoice: they are the equations an
 * accountant will check on any invoice — HT + TVA = TTC, the recap adds up to
 * the tax line, the lines add up to the subtotal. A change to the totals code
 * that breaks one of them must fail here rather than on a customer's desk.
 */
class InvoiceInvariantsTest {

    private fun document(
        lines: List<InvoiceLine>,
        discountFixed: Double = 0.0,
        discountPercent: Int = 0,
        surchargeAmount: Double = 0.0,
        surchargePercent: Double = 0.0,
        vatPercent: Double = 20.0,
        vatNotApplicable: Boolean = false,
        deposit: Double = 0.0,
        client: InvoiceClient = InvoiceClient("Client"),
    ) = InvoiceData(
        number = "F-20261110-0001",
        date = "10/11/2026",
        client = client,
        lines = lines,
        discountFixed = discountFixed,
        discountPercent = discountPercent,
        vatPercent = vatPercent,
        vatNotApplicable = vatNotApplicable,
        surchargeAmount = surchargeAmount,
        surchargePercent = surchargePercent,
        depositAmount = deposit,
    )

    /** A deliberately awkward mix: two rates, a decimal quantity, a line discount. */
    private val mixed = listOf(
        InvoiceLine("Câble", 2.5, 80.0, vatPercent = 20.0),
        InvoiceLine("Service", 1.5, 200.0, vatPercent = 10.0),
        InvoiceLine("Dossier", 1.0, 50.0, lineDiscountFixed = 10.0),
    )

    private fun assertMoneyInvariants(data: InvoiceData) {
        // HT + TVA = TTC, to the centime each component is printed with.
        assertEquals(
            data.total,
            data.totalExcludingVat + data.surcharge + data.vatAmount,
            0.011,
        )
        // The recap is the tax: it cannot disagree with the line above it.
        assertEquals(data.vatAmount, data.vatBreakdown.sumOf { it.amount }, 0.001)
        // The recap's bases are the whole taxable base, surcharge included.
        assertEquals(
            data.totalExcludingVat + data.surcharge,
            data.vatBreakdown.sumOf { it.base },
            0.001,
        )
        // The printed lines add up to the printed subtotal.
        assertEquals(data.subtotal, data.lines.sumOf { money2(it.total) }, 0.001)
        // A discount never makes the goods negative.
        assertTrue(data.discountAmount <= data.subtotal + 0.001)
    }

    private fun money2(value: Double): Double = Math.round(value * 100.0) / 100.0

    @Test
    fun `a mixed-rate document holds the totals together`() {
        assertMoneyInvariants(document(mixed))
    }

    @Test
    fun `a fixed discount and a percentage surcharge hold together`() {
        assertMoneyInvariants(document(mixed, discountFixed = 25.0, surchargePercent = 2.0))
    }

    @Test
    fun `an exempt document has no tax but still adds up`() {
        val data = document(mixed, vatNotApplicable = true)
        assertMoneyInvariants(data)
        assertEquals(0.0, data.vatAmount, 0.001)
        assertEquals(data.total, data.totalExcludingVat + data.surcharge, 0.001)
    }

    @Test
    fun `a deposit changes the balance and never the tax`() {
        val without = document(mixed)
        val with = document(mixed, deposit = 100.0)

        assertEquals(without.total, with.total, 0.001)
        assertEquals(without.vatAmount, with.vatAmount, 0.001)
        assertEquals(with.total - 100.0, with.netToPay, 0.001)
        assertMoneyInvariants(with)
    }

    @Test
    fun `a fixed discount takes over from a percentage`() {
        val data = document(mixed, discountFixed = 40.0, discountPercent = 50)

        // 40 DH, not 50 % of the lines: one discount per document.
        assertEquals(40.0, data.discountAmount, 0.001)
        assertEquals(data.subtotal - 40.0, data.totalExcludingVat, 0.001)
    }

    @Test
    fun `a credit note repeats the invoice it corrects`() {
        val invoice = InvoiceRecord(
            id = "r1",
            data = document(mixed, discountFixed = 25.0, surchargePercent = 2.0),
            status = InvoiceStatus.ISSUED,
            createdAt = 0,
            updatedAt = 0,
        )

        val credit = CreditNote.from(invoice)!!

        assertEquals(invoice.data.total, credit.data.total, 0.001)
        assertEquals(invoice.data.vatAmount, credit.data.vatAmount, 0.001)
        assertEquals(invoice.data.number, credit.data.referenceNumber)
        assertEquals(0.0, credit.data.paidAmount, 0.001)
    }

    @Test
    fun `a document with no line is still well formed`() {
        val data = document(emptyList())

        assertEquals(0.0, data.total, 0.001)
        assertEquals(0.0, data.vatAmount, 0.001)
        assertTrue(data.vatBreakdown.isEmpty() || data.vatBreakdown.all { it.amount == 0.0 })
    }
}
