package com.yassine.facturemaroc.data

import com.yassine.facturemaroc.invoice.InvoiceValidation
import com.yassine.facturemaroc.invoice.ValidationCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VAT per line — CGI art. 145.
 *
 * A Moroccan invoice may mix rates on one page, so the arithmetic has to be
 * done per rate and then printed per rate. These tests pin both halves: the
 * figures a line produces, and the recap the page and the XML export read.
 */
class VatRateTest {

    private fun invoice(
        lines: List<InvoiceLine>,
        discount: Int = 0,
        surcharge: Double = 0.0,
        vat: Double = 20.0,
        shopVatOff: Boolean = false,
        clientExempt: Boolean = false,
    ) = InvoiceData(
        number = "F-20261110-0001",
        date = "10/11/2026",
        client = InvoiceClient(name = "Client", vatExempt = clientExempt),
        lines = lines,
        discountPercent = discount,
        vatPercent = vat,
        surchargeAmount = surcharge,
        vatNotApplicable = shopVatOff,
    )

    // ---- a document at one rate is exactly what it was before ---------------

    @Test
    fun `a document at one rate keeps its single VAT row`() {
        val data = invoice(listOf(InvoiceLine("Chaise", 1.0, 100.0)))

        assertEquals(100.0, data.totalExcludingVat, 0.0)
        assertEquals(20.0, data.vatAmount, 0.0)
        assertEquals(120.0, data.total, 0.0)
        assertEquals(1, data.vatPrintRows.size)
        assertEquals(20.0, data.vatPrintRows.single().rate, 0.0)
        assertEquals(20.0, data.vatPrintRows.single().amount, 0.0)
    }

    // ---- two rates on one invoice ------------------------------------------

    @Test
    fun `a 20 and a 10 percent line are taxed separately and recapped`() {
        val data = invoice(
            lines = listOf(
                InvoiceLine("Chaise", 1.0, 100.0),
                InvoiceLine("Montage", 1.0, 100.0, vatPercent = 10.0),
            ),
        )

        assertEquals(200.0, data.totalExcludingVat, 0.0)
        assertEquals(30.0, data.vatAmount, 0.0)
        assertEquals(230.0, data.total, 0.0)

        // Highest rate first, one row each: the order the page prints.
        assertEquals(listOf(20.0, 10.0), data.vatPrintRows.map { it.rate })
        assertEquals(listOf(20.0, 10.0), data.vatPrintRows.map { it.amount })
        assertEquals(listOf(100.0, 100.0), data.vatPrintRows.map { it.base })
    }

    @Test
    fun `the rate of a line is the document's until it is set`() {
        val data = invoice(
            lines = listOf(
                InvoiceLine("Chaise", 1.0, 100.0),
                InvoiceLine("Montage", 1.0, 100.0, vatPercent = 10.0),
            ),
        )

        assertEquals(20.0, data.rateOf(data.lines[0]), 0.0)
        assertEquals(10.0, data.rateOf(data.lines[1]), 0.0)
    }

    @Test
    fun `a legacy 7 percent line is still charged 7 percent`() {
        // 7 % and 14 % were removed on 1 January 2026, but documents written
        // before that must keep adding up the way they were signed.
        val data = invoice(
            lines = listOf(InvoiceLine("Ancien taux", 1.0, 100.0, vatPercent = 7.0)),
        )

        assertEquals(7.0, data.vatAmount, 0.0)
        assertEquals(107.0, data.total, 0.0)
        assertEquals(7.0, data.vatPrintRows.single().rate, 0.0)
    }

    @Test
    fun `an exempt line sits beside a taxed one without touching it`() {
        val data = invoice(
            lines = listOf(
                InvoiceLine("Chaise", 1.0, 100.0),
                InvoiceLine("Export", 1.0, 100.0, taxExempt = true),
            ),
        )

        assertEquals(200.0, data.totalExcludingVat, 0.0)
        assertEquals(20.0, data.vatAmount, 0.0)
        assertEquals(220.0, data.total, 0.0)
        // The recap only lists what was actually charged; the exempt line is
        // declared by its own "TVA exonérée" label.
        assertEquals(listOf(20.0), data.vatPrintRows.map { it.rate })
        assertEquals(100.0, data.vatBase, 0.0)
    }

    // ---- exemptions overrule a line rate -----------------------------------

    @Test
    fun `an auto-entrepreneur charges nothing whatever the lines say`() {
        val data = invoice(
            lines = listOf(InvoiceLine("Chaise", 1.0, 100.0, vatPercent = 10.0)),
            shopVatOff = true,
        )

        assertEquals(0.0, data.vatAmount, 0.0)
        assertEquals(100.0, data.total, 0.0)
        assertTrue(data.vatPrintRows.isEmpty())
    }

    @Test
    fun `a VAT-exempt client charges nothing whatever the lines say`() {
        val data = invoice(
            lines = listOf(InvoiceLine("Chaise", 1.0, 100.0)),
            clientExempt = true,
        )

        assertEquals(0.0, data.vatAmount, 0.0)
        assertEquals(100.0, data.total, 0.0)
        // Not "non applicable": the shop is a normal taxpayer, this buyer is
        // just outside the tax. The page prints a 0 % row.
        assertEquals(1, data.vatPrintRows.size)
        assertEquals(0.0, data.vatPrintRows.single().rate, 0.0)
    }

    // ---- discount and surcharge across rates --------------------------------

    @Test
    fun `a global discount is spread over every rate`() {
        val data = invoice(
            lines = listOf(
                InvoiceLine("Chaise", 1.0, 100.0),
                InvoiceLine("Montage", 1.0, 100.0, vatPercent = 10.0),
            ),
            discount = 10,
        )

        // 200 − 20 = 180 HT, split 90 / 90 by rate: 18 + 9 = 27 of TVA.
        assertEquals(180.0, data.totalExcludingVat, 0.0)
        assertEquals(27.0, data.vatAmount, 0.0)
        assertEquals(207.0, data.total, 0.0)
        assertEquals(listOf(90.0, 90.0), data.vatPrintRows.map { it.base })
        assertEquals(listOf(18.0, 9.0), data.vatPrintRows.map { it.amount })
    }

    @Test
    fun `a surcharge follows the only taxed rate on the document`() {
        val data = invoice(
            lines = listOf(InvoiceLine("Montage", 1.0, 100.0, vatPercent = 10.0)),
            surcharge = 50.0,
        )

        // 100 + 50 at 10 %, not 50 taxed at the shop's 20 % default.
        assertEquals(150.0, data.vatBase, 0.0)
        assertEquals(15.0, data.vatAmount, 0.0)
        assertEquals(165.0, data.total, 0.0)
    }

    @Test
    fun `a surcharge on an export is still taxed at the document rate`() {
        val data = invoice(
            lines = listOf(InvoiceLine("Export", 1.0, 100.0, taxExempt = true)),
            surcharge = 100.0,
        )

        assertEquals(100.0, data.vatBase, 0.0)
        assertEquals(20.0, data.vatAmount, 0.0)
        assertEquals(220.0, data.total, 0.0)
    }

    @Test
    fun `a mixed invoice taxes the surcharge at the shop rate`() {
        val data = invoice(
            lines = listOf(
                InvoiceLine("Chaise", 1.0, 100.0),
                InvoiceLine("Montage", 1.0, 100.0, vatPercent = 10.0),
            ),
            surcharge = 50.0,
        )

        // 20 + 10 on the goods, 10 on the delivery at the document's 20 %.
        assertEquals(40.0, data.vatAmount, 0.0)
        assertEquals(290.0, data.total, 0.0)
    }

    // ---- the invariants the page relies on ---------------------------------

    @Test
    fun `the recap always adds up to the printed totals`() {
        val cases = listOf(
            invoice(listOf(InvoiceLine("A", 1.0, 99.99), InvoiceLine("B", 1.0, 33.33, vatPercent = 10.0))),
            invoice(
                listOf(InvoiceLine("A", 3.0, 19.99), InvoiceLine("B", 2.0, 7.45, vatPercent = 10.0)),
                discount = 7,
                surcharge = 24.5,
            ),
            invoice(
                listOf(
                    InvoiceLine("A", 1.0, 0.05),
                    InvoiceLine("B", 1.0, 0.05, vatPercent = 10.0),
                ),
            ),
            invoice(
                listOf(
                    InvoiceLine("A", 7.0, 1.11),
                    InvoiceLine("B", 3.0, 2.22, vatPercent = 14.0),
                    InvoiceLine("C", 1.0, 5.0, taxExempt = true),
                ),
                discount = 3,
                surcharge = 9.99,
            ),
        )

        cases.forEach { data ->
            val bases = data.vatBreakdown.sumOf { it.base }
            val taxes = data.vatBreakdown.sumOf { it.amount }
            assertEquals(data.totalExcludingVat + data.surcharge, bases, 0.005)
            assertEquals(data.vatAmount, taxes, 0.005)
            assertEquals(
                data.totalExcludingVat + data.surcharge + data.vatAmount,
                data.total,
                0.011,
            )
        }
    }

    @Test
    fun `a mixed invoice passes validation`() {
        val data = invoice(
            lines = listOf(
                InvoiceLine("Chaise", 1.0, 100.0),
                InvoiceLine("Montage", 1.0, 100.0, vatPercent = 10.0),
            ),
            discount = 5,
            surcharge = 12.0,
        )

        val issues = InvoiceValidation.validate(data).map { it.code }

        assertTrue(issues.none { it == ValidationCode.TOTAL_MISMATCH })
    }

    @Test
    fun `a line rate outside zero to a hundred is refused`() {
        val data = invoice(listOf(InvoiceLine("Chaise", 1.0, 100.0, vatPercent = 140.0)))

        assertTrue(
            InvoiceValidation.validate(data)
                .any { it.code == ValidationCode.INVALID_LINE_VAT_RATE },
        )
    }

    // ---- persistence --------------------------------------------------------

    @Test
    fun `a line rate survives the round trip through JSON`() {
        val data = invoice(
            lines = listOf(
                InvoiceLine("Chaise", 1.0, 100.0),
                InvoiceLine("Montage", 1.0, 100.0, vatPercent = 10.0),
            ),
        )

        val restored = InvoiceJson.dataFromJson(InvoiceJson.dataToJson(data))

        assertEquals(null, restored.lines[0].vatPercent)
        assertEquals(10.0, restored.lines[1].vatPercent!!, 0.0)
        assertEquals(data.total, restored.total, 0.0)
        assertEquals(data.vatAmount, restored.vatAmount, 0.0)
    }

    @Test
    fun `a line without its own rate is written without the field`() {
        val json = InvoiceJson.lineToJson(InvoiceLine("Chaise", 1.0, 100.0))

        assertFalse(json.has("vatPercent"))
    }

    // ---- what the paper says ------------------------------------------------

    @Test
    fun `an explicit rate is named on the line`() {
        assertEquals(
            "Montage · TVA 10\u00A0%",
            InvoiceLine("Montage", 1.0, 100.0, vatPercent = 10.0).lineLabel,
        )
        // A line following the document says nothing: the totals block already
        // says it, once.
        assertEquals("Chaise", InvoiceLine("Chaise", 1.0, 100.0).lineLabel)
    }

    @Test
    fun `a zero rate line is announced as zero`() {
        assertEquals(
            "Cadeau · TVA 0\u00A0%",
            InvoiceLine("Cadeau", 1.0, 0.0, vatPercent = 0.0).lineLabel,
        )
    }
}
