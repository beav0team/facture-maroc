package com.yassine.facturemaroc.data

import com.yassine.facturemaroc.invoice.InvoiceValidation
import com.yassine.facturemaroc.invoice.ValidationCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A deposit already invoiced reduces what is left to pay, not what is taxed.
 *
 * The deposit document carried its own VAT when it was issued; deducting it
 * again here would under-declare the tax on this invoice, so it sits after the
 * total and only moves the balance.
 */
class DepositTest {

    private fun invoice(
        lines: List<InvoiceLine> = listOf(InvoiceLine("Chaise", 1, 1_000.0)),
        deposit: Double = 0.0,
        reference: String = "",
        paid: Double = 0.0,
    ) = InvoiceData(
        number = "F-20261110-0001",
        date = "10/11/2026",
        client = InvoiceClient("Client"),
        lines = lines,
        depositAmount = deposit,
        depositReference = reference,
        payment = if (paid > 0.0) PaymentInfo(paidAmount = paid) else null,
    )

    @Test
    fun `a deposit reduces the balance without touching the VAT`() {
        val data = invoice(deposit = 240.0, reference = "A-20261101-0001")

        assertEquals(1_200.0, data.total, 0.001)
        assertEquals(240.0, data.deposit, 0.001)
        assertEquals(960.0, data.netToPay, 0.001)
        assertEquals(960.0, data.balanceDue, 0.001)
        // The tax is the tax on the invoice, not on what remains.
        assertEquals(200.0, data.vatAmount, 0.001)
        assertEquals(1_000.0, data.vatBase, 0.001)
    }

    @Test
    fun `a deposit larger than the invoice is capped`() {
        val data = invoice(deposit = 5_000.0)

        assertEquals(1_200.0, data.deposit, 0.001)
        assertEquals(0.0, data.balanceDue, 0.001)
        assertEquals(PaymentState.PAID, data.paymentState)
    }

    @Test
    fun `a settled deposit leaves nothing to chase`() {
        val data = invoice(deposit = 1_200.0)

        assertEquals(0.0, data.netToPay, 0.001)
        assertTrue(data.isSettled)
    }

    @Test
    fun `payments are clamped to what the deposit left`() {
        val data = invoice(deposit = 240.0, paid = 5_000.0)

        assertEquals(960.0, data.paidAmount, 0.001)
        assertEquals(0.0, data.balanceDue, 0.001)
        assertEquals(PaymentState.PAID, data.paymentState)
    }

    @Test
    fun `a partial payment after a deposit is partial`() {
        val data = invoice(deposit = 240.0, paid = 400.0)

        assertEquals(PaymentState.PARTIAL, data.paymentState)
        assertEquals(560.0, data.balanceDue, 0.001)
    }

    @Test
    fun `a deposit survives a JSON round trip`() {
        val data = invoice(deposit = 240.0, reference = "A-20261101-0001")

        val restored = InvoiceJson.dataFromJson(InvoiceJson.dataToJson(data))

        assertEquals(240.0, restored.depositAmount, 0.001)
        assertEquals("A-20261101-0001", restored.depositReference)
        assertEquals(data.balanceDue, restored.balanceDue, 0.001)
    }

    @Test
    fun `a deposit above the total is a warning, not a block`() {
        val issues = InvoiceValidation.validate(invoice(deposit = 5_000.0))

        assertTrue(issues.any { it.code == ValidationCode.DEPOSIT_ABOVE_TOTAL })
        assertTrue(issues.none { it.code == ValidationCode.DEPOSIT_ABOVE_TOTAL && it.severity.name == "BLOCKING" })
    }

    @Test
    fun `a negative deposit is reported`() {
        val issues = InvoiceValidation.validate(invoice(deposit = -10.0))

        assertTrue(issues.any { it.code == ValidationCode.INVALID_DEPOSIT })
    }

    @Test
    fun `the statement shows what is left on the line, not the full total`() {
        val record = InvoiceRecord(
            id = "r1",
            data = invoice(deposit = 240.0),
            status = InvoiceStatus.ISSUED,
            createdAt = 0L,
            updatedAt = 0L,
        )

        val statement = ClientStatements.forClient(listOf(record), "Client")

        assertEquals(960.0, statement.lines.single().balance, 0.001)
    }
}
