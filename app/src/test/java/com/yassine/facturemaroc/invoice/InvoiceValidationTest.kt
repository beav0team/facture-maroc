package com.yassine.facturemaroc.invoice

import com.yassine.facturemaroc.data.InvoiceClient
import com.yassine.facturemaroc.data.InvoiceData
import com.yassine.facturemaroc.data.InvoiceLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceValidationTest {

    private fun invoice(
        lines: List<InvoiceLine> = listOf(InvoiceLine("Produit", 1, 100.0)),
        number: String = "F-20261021-0001",
        discount: Int = 0,
        vat: Double = 20.0,
    ) = InvoiceData(
        number = number,
        date = "21/10/2026",
        client = InvoiceClient(name = "Client"),
        lines = lines,
        discountPercent = discount,
        vatPercent = vat,
    )

    private fun codes(data: InvoiceData) = InvoiceValidation.validate(data).map { it.code }

    @Test
    fun `a normal invoice has nothing to report`() {
        assertTrue(InvoiceValidation.validate(invoice()).isEmpty())
    }

    @Test
    fun `an invoice without lines cannot be sent`() {
        assertTrue(codes(invoice(lines = emptyList())).contains(ValidationCode.NO_LINES))
    }

    @Test
    fun `a line with no name is named by its position`() {
        val issues = InvoiceValidation.validate(
            invoice(lines = listOf(InvoiceLine("  ", 1, 10.0))),
        )

        assertEquals(ValidationCode.LINE_WITHOUT_DESCRIPTION, issues.single().code)
        assertTrue(issues.single().message.contains("Ligne 1"))
    }

    @Test
    fun `a zero quantity is refused`() {
        assertTrue(
            codes(invoice(lines = listOf(InvoiceLine("Produit", 0, 10.0))))
                .contains(ValidationCode.NEGATIVE_QUANTITY),
        )
    }

    @Test
    fun `a negative price is refused`() {
        assertTrue(
            codes(invoice(lines = listOf(InvoiceLine("Produit", 1, -5.0))))
                .contains(ValidationCode.INVALID_PRICE),
        )
    }

    @Test
    fun `a discount over one hundred percent is refused`() {
        assertTrue(
            codes(invoice(lines = listOf(InvoiceLine("Produit", 1, 10.0, lineDiscountPercent = 150))))
                .contains(ValidationCode.DISCOUNT_OUT_OF_RANGE),
        )
    }

    @Test
    fun `a TVA rate outside zero to one hundred is refused`() {
        assertTrue(codes(invoice(vat = 120.0)).contains(ValidationCode.VAT_RATE_OUT_OF_RANGE))
    }

    @Test
    fun `a document without a number is refused`() {
        assertTrue(codes(invoice(number = " ")).contains(ValidationCode.MISSING_DOCUMENT_NUMBER))
    }

    @Test
    fun `the totals are re-added from the lines, not trusted`() {
        val data = invoice(
            lines = listOf(
                InvoiceLine("A", 2, 50.0, lineDiscountPercent = 50),
                InvoiceLine("B", 1, 100.0),
            ),
            discount = 10,
        )

        assertTrue(InvoiceValidation.validate(data).none { it.code == ValidationCode.TOTAL_MISMATCH })
    }

    @Test
    fun `three TTC lines that round to a different sum are not accused`() {
        // 99,99 TTC is 83,325 HT: the printed subtotal is 249,99 while the exact
        // sum is 249,975. The validator has to re-add the way the page does, or
        // it would block every invoice with a price like this.
        val line = InvoiceLine("Pose", 1, 99.99 / 1.2)
        val data = invoice(lines = listOf(line, line, line))

        assertTrue(InvoiceValidation.validate(data).none { it.code == ValidationCode.TOTAL_MISMATCH })
    }

    @Test
    fun `an exempt line and a discount still re-add to the stored total`() {
        val data = invoice(
            lines = listOf(
                InvoiceLine("Conseil", 1, 100.0, taxExempt = true),
                InvoiceLine("Produit", 1, 200.0),
            ),
            discount = 10,
        )

        assertTrue(InvoiceValidation.validate(data).none { it.code == ValidationCode.TOTAL_MISMATCH })
    }
}
