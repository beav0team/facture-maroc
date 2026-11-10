package com.yassine.facturemaroc.data

import com.yassine.facturemaroc.invoice.InvoiceValidation
import com.yassine.facturemaroc.invoice.ValidationCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Discounts and the surcharge in the two shapes a shop actually uses.
 *
 * One rule runs through all of it: a document carries *one* discount and *one*
 * surcharge, and the amount form takes over from the percentage form rather
 * than adding to it. Two figures that look additive would otherwise be summed
 * on the page and the customer would be charged neither of them correctly.
 */
class DiscountSurchargeTest {

    private fun invoice(
        vararg lines: InvoiceLine,
        discount: Int = 0,
        discountFixed: Double = 0.0,
        surcharge: Double = 0.0,
        surchargePercent: Double = 0.0,
        vat: Double = 20.0,
    ) = InvoiceData(
        number = "F-20261110-0001",
        date = "10/11/2026",
        client = InvoiceClient(name = "Client"),
        lines = lines.toList(),
        discountPercent = discount,
        discountFixed = discountFixed,
        surchargeAmount = surcharge,
        surchargePercent = surchargePercent,
        vatPercent = vat,
    )

    @Test
    fun `a discount in dirhams comes off the total before TVA`() {
        val data = invoice(
            InvoiceLine("Table", 2, 100.0),
            discountFixed = 50.0,
        )

        assertEquals(50.0, data.discountAmount, 0.001)
        assertEquals(150.0, data.totalExcludingVat, 0.001)
        assertEquals(150.0, data.vatBase, 0.001)
        assertEquals(30.0, data.vatAmount, 0.001)
        assertEquals(180.0, data.total, 0.001)
    }

    @Test
    fun `a discount larger than the goods never produces a credit`() {
        val data = invoice(
            InvoiceLine("Table", 1, 200.0),
            discountFixed = 500.0,
        )

        assertEquals(200.0, data.discountAmount, 0.001)
        assertEquals(0.0, data.totalExcludingVat, 0.001)
        assertEquals(0.0, data.total, 0.001)
    }

    @Test
    fun `the amount discount takes over from the percentage`() {
        val data = invoice(
            InvoiceLine("Table", 2, 100.0),
            discount = 10,
            discountFixed = 50.0,
        )

        // One discount, not 10 % plus 50 DH.
        assertEquals(50.0, data.discountAmount, 0.001)
        assertEquals(150.0, data.totalExcludingVat, 0.001)
    }

    @Test
    fun `a fixed discount is still shared so an exempt line cannot reduce the tax`() {
        val data = invoice(
            InvoiceLine("Conseil", 1, 100.0, taxExempt = true),
            InvoiceLine("Produit", 1, 200.0),
            discountFixed = 30.0,
        )

        // 300 − 30 = 270. The 30 is shared 10/20, so the taxed base is 180
        // and the tax 36 — the same answer as 10 % of the whole.
        assertEquals(180.0, data.vatBase, 0.001)
        assertEquals(36.0, data.vatAmount, 0.001)
        assertEquals(306.0, data.total, 0.001)
    }

    @Test
    fun `a line discount in dirhams reduces that line only`() {
        val discounted = InvoiceLine("Chaise", quantity = 2, unitPrice = 950.0, lineDiscountFixed = 100.0)
        val data = invoice(discounted, InvoiceLine("Livraison", 1, 100.0))

        assertEquals(1_800.0, discounted.total, 0.001)
        assertEquals(1_900.0, data.subtotal, 0.001)
        assertEquals(2_280.0, data.total, 0.001)
    }

    @Test
    fun `a line discount larger than the line stops at zero`() {
        val line = InvoiceLine("Chaise", quantity = 1, unitPrice = 950.0, lineDiscountFixed = 1_200.0)

        assertEquals(0.0, line.total, 0.001)
    }

    @Test
    fun `a line amount discount takes over from its percentage`() {
        val line = InvoiceLine(
            "Chaise",
            quantity = 1,
            unitPrice = 950.0,
            lineDiscountPercent = 50,
            lineDiscountFixed = 100.0,
        )

        assertEquals(850.0, line.total, 0.001)
    }

    @Test
    fun `a surcharge given in percent is taxed and sits after the discount`() {
        val data = invoice(
            InvoiceLine("Table", 2, 100.0),
            surchargePercent = 5.0,
        )

        assertEquals(10.0, data.surcharge, 0.001)
        // totalExcludingVat is the lines after the discount; the surcharge is
        // its own printed row and is added on top of it.
        assertEquals(200.0, data.totalExcludingVat, 0.001)
        assertEquals(252.0, data.total, 0.001)
    }

    @Test
    fun `the percentage surcharge is computed after the discount`() {
        val data = invoice(
            InvoiceLine("Table", 2, 100.0),
            discount = 20,
            surchargePercent = 10.0,
        )

        // 200 − 40 = 160, then 10 % of 160 = 16, so 176 HT and 211,20 TTC.
        assertEquals(16.0, data.surcharge, 0.001)
        assertEquals(160.0, data.totalExcludingVat, 0.001)
        assertEquals(211.2, data.total, 0.001)
    }

    @Test
    fun `the percentage surcharge takes over from the amount`() {
        val data = invoice(
            InvoiceLine("Table", 2, 100.0),
            surcharge = 100.0,
            surchargePercent = 5.0,
        )

        assertEquals(10.0, data.surcharge, 0.001)
    }

    @Test
    fun `the recap still adds up with a fixed discount and a percentage surcharge`() {
        val data = invoice(
            InvoiceLine("Chaise", 2, 950.0, lineDiscountFixed = 100.0),
            InvoiceLine("Montage", 3, 450.0, vatPercent = 10.0),
            InvoiceLine("Conseil", 1, 300.0, taxExempt = true),
            discountFixed = 200.0,
            surchargePercent = 2.5,
        )

        assertEquals(
            data.totalExcludingVat + data.surcharge,
            data.vatBreakdown.sumOf { it.base },
            0.001,
        )
        assertEquals(data.vatAmount, data.vatBreakdown.sumOf { it.amount }, 0.001)
        assertEquals(
            data.total,
            data.totalExcludingVat + data.surcharge + data.vatAmount,
            0.001,
        )
    }

    @Test
    fun `fixed discounts and a percentage surcharge survive a JSON round trip`() {
        val data = invoice(
            InvoiceLine("Chaise", 2, 950.0, lineDiscountFixed = 100.0),
            discountFixed = 150.0,
            surchargePercent = 3.0,
        )

        val restored = InvoiceJson.dataFromJson(InvoiceJson.dataToJson(data))

        assertEquals(150.0, restored.discountFixed, 0.001)
        assertEquals(3.0, restored.surchargePercent, 0.001)
        assertEquals(100.0, restored.lines.single().lineDiscountFixed, 0.001)
        assertEquals(data.total, restored.total, 0.001)
    }

    @Test
    fun `a negative amount discount is reported, not silently applied`() {
        val data = invoice(InvoiceLine("Table", 1, 100.0), discountFixed = -50.0)

        val issues = InvoiceValidation.validate(data)

        assertTrue(issues.any { it.code == ValidationCode.DISCOUNT_OUT_OF_RANGE })
        assertEquals(0.0, data.discountAmount, 0.001)
    }

    @Test
    fun `a surcharge percentage above one hundred is reported`() {
        val data = invoice(InvoiceLine("Table", 1, 100.0), surchargePercent = 150.0)

        val issues = InvoiceValidation.validate(data)

        assertTrue(issues.any { it.code == ValidationCode.INVALID_SURCHARGE })
    }

    @Test
    fun `a discount in dirhams is printed without inventing a rate`() {
        val fixed = invoice(InvoiceLine("Table", 1, 200.0), discountFixed = 50.0)
        val percent = invoice(InvoiceLine("Table", 1, 200.0), discount = 5)

        assertEquals(true, fixed.showsDiscount)
        assertEquals(true, fixed.discountInAmount)
        assertEquals(true, percent.showsDiscount)
        assertEquals(false, percent.discountInAmount)

        val none = invoice(InvoiceLine("Table", 1, 200.0))
        assertEquals(false, none.showsDiscount)
    }
}
