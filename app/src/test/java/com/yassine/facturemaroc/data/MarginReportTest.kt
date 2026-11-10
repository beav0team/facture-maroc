package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Margin arithmetic: what it counts, what it deliberately leaves out, and the
 * month it belongs to.
 */
class MarginReportTest {

    private fun line(
        description: String = "Produit",
        quantity: Double = 1.0,
        unitPrice: Double = 100.0,
        vat: Double? = null,
        discountPercent: Int = 0,
        discountFixed: Double = 0.0,
        cost: Double? = null,
    ) = InvoiceLine(
        description = description,
        quantity = quantity,
        unitPrice = unitPrice,
        vatPercent = vat,
        lineDiscountPercent = discountPercent,
        lineDiscountFixed = discountFixed,
        unitCostHt = cost,
    )

    private fun data(
        lines: List<InvoiceLine>,
        number: String = "F-20261110-0001",
        date: String = "10/11/2026",
        type: DocumentType = DocumentType.FACTURE,
        discountPercent: Int = 0,
        discountFixed: Double = 0.0,
    ) = InvoiceData(
        number = number,
        date = date,
        client = InvoiceClient(name = "Client"),
        lines = lines,
        vatPercent = 20.0,
        documentType = type,
        discountPercent = discountPercent,
        discountFixed = discountFixed,
    )

    private fun record(
        data: InvoiceData,
        status: InvoiceStatus = InvoiceStatus.ISSUED,
        id: String = data.number,
    ) = InvoiceRecord(
        id = id,
        data = data,
        status = status,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `mixed rates and discounts still add up`() {
        val document = data(
            listOf(
                // 2 × 100 at 20 %, −10 % on the line = 180, cost 2 × 60 = 120.
                line(quantity = 2.0, unitPrice = 100.0, vat = 20.0, discountPercent = 10, cost = 60.0),
                // 1 × 250 at 10 %, −50 DH on the line = 200, cost 150.
                line(quantity = 1.0, unitPrice = 250.0, vat = 10.0, discountFixed = 50.0, cost = 150.0),
            ),
        )

        val totals = MarginReport.forDocument(document)

        // Revenue is the sum of the line totals: the VAT rate never enters it.
        assertEquals(380.0, totals.revenueHt, 0.001)
        assertEquals(270.0, totals.cost, 0.001)
        assertEquals(110.0, totals.margin, 0.001)
        assertEquals(28.95, totals.marginPercent ?: 0.0, 0.01)
        assertEquals(2, totals.linesWithCost)
        assertEquals(0, totals.linesWithoutCost)
    }

    @Test
    fun `a line without a cost is excluded and counted`() {
        val totals = MarginReport.forDocument(
            data(
                listOf(
                    line(quantity = 1.0, unitPrice = 100.0, cost = 40.0),
                    line(quantity = 1.0, unitPrice = 200.0, cost = null),
                ),
            ),
        )

        // The unknown line still sold — revenue counts it — but it is never
        // assumed to cost zero, which would report a 200 DH profit that is not
        // known to exist.
        assertEquals(300.0, totals.revenueHt, 0.001)
        assertEquals(40.0, totals.cost, 0.001)
        assertEquals(1, totals.linesWithoutCost)
        assertEquals(1, totals.linesWithCost)
        assertEquals(50.0, totals.shareWithoutCost, 0.01)
    }

    @Test
    fun `a service with no cost reports nothing rather than a full margin`() {
        val totals = MarginReport.forDocument(
            data(listOf(line(description = "Main-d'œuvre", unitPrice = 300.0, cost = null))),
        )

        assertEquals(300.0, totals.revenueHt, 0.001)
        assertEquals(0.0, totals.cost, 0.001)
        assertEquals(0, totals.linesWithCost)
        assertEquals(1, totals.linesWithoutCost)
        // The arithmetic still yields a margin; the screen is what says the
        // number rests on a line whose cost is unknown.
        assertEquals(100.0, totals.shareWithoutCost, 0.001)
    }

    @Test
    fun `a document discount comes off the margin`() {
        // 10 % off the document is 10 % off what the two lines sold for, and the
        // goods still cost what they cost: reporting 400 of revenue here was
        // reporting money the shop never collected.
        val document = data(
            listOf(
                line(quantity = 1.0, unitPrice = 250.0, cost = 100.0),
                line(quantity = 1.0, unitPrice = 150.0, cost = 100.0),
            ),
            discountPercent = 10,
        )

        val totals = MarginReport.forDocument(document)

        assertEquals(360.0, totals.revenueHt, 0.001)
        assertEquals(200.0, totals.cost, 0.001)
        assertEquals(160.0, totals.margin, 0.001)
    }

    @Test
    fun `a fixed discount is spread over the lines, not taken off one`() {
        // A -100 DH document discount has no article to belong to; the margin
        // spreads it in proportion, so neither line reports a windfall.
        val document = data(
            listOf(
                line(quantity = 1.0, unitPrice = 300.0, cost = 100.0),
                line(quantity = 1.0, unitPrice = 100.0, cost = 50.0),
            ),
            discountFixed = 100.0,
        )

        val totals = MarginReport.forDocument(document)

        assertEquals(300.0, totals.revenueHt, 0.001)
        assertEquals(150.0, totals.cost, 0.001)
        assertEquals(150.0, totals.margin, 0.001)
    }

    @Test
    fun `the per-article report applies the document discount too`() {
        val records = listOf(
            record(
                data(
                    listOf(
                        line(description = "Chaise", unitPrice = 200.0, cost = 80.0),
                        line(description = "Table", unitPrice = 200.0, cost = 120.0),
                    ),
                    discountPercent = 25,
                ),
            ),
        )

        val rows = MarginReport.byItem(records)

        // 400 - 25 % = 300, split in proportion: 150 each on a base of 200.
        assertEquals(150.0, rows.first { it.label == "Chaise" }.totals.revenueHt, 0.001)
        assertEquals(150.0, rows.first { it.label == "Table" }.totals.revenueHt, 0.001)
    }

    @Test
    fun `an empty document has no percentage`() {
        val totals = MarginReport.forDocument(data(emptyList()))
        assertEquals(0.0, totals.revenueHt, 0.0)
        assertNull(totals.marginPercent)
    }

    @Test
    fun `the month groups by date and takes the credit notes off`() {
        val records = listOf(
            record(data(listOf(line(unitPrice = 1000.0, cost = 600.0)))),
            // Same month, a credit note that cancels half of it.
            record(
                data(
                    listOf(line(unitPrice = 500.0, cost = 300.0)),
                    number = "A-20261110-0001",
                    type = DocumentType.AVOIR,
                ),
            ),
            // Next month must not leak in.
            record(
                data(
                    listOf(line(unitPrice = 900.0, cost = 100.0)),
                    number = "F-20261201-0001",
                    date = "01/12/2026",
                ),
            ),
        )

        val totals = MarginReport.forMonth(records, "11/2026")

        assertEquals(500.0, totals.revenueHt, 0.001)
        assertEquals(300.0, totals.cost, 0.001)
        assertEquals(200.0, totals.margin, 0.001)
        assertEquals(40.0, totals.marginPercent ?: 0.0, 0.01)
    }

    @Test
    fun `a draft is not margin`() {
        val draft = record(
            data(listOf(line(unitPrice = 1000.0, cost = 500.0))),
            status = InvoiceStatus.DRAFT,
        )
        assertNull(MarginReport.forRecord(draft))
        assertEquals(0.0, MarginReport.forMonth(listOf(draft), "11/2026").revenueHt, 0.0)
    }

    @Test
    fun `per item and per label both find the sales`() {
        val records = listOf(
            record(
                data(
                    listOf(
                        InvoiceLine("Chaise", 1.0, 300.0, itemId = "chair", unitCostHt = 200.0),
                        // A line written before ids existed: the label is the
                        // only link, and it still earns a row.
                        InvoiceLine("Tabouret", 2.0, 100.0, unitCostHt = 60.0),
                    ),
                ),
            ),
        )

        val rows = MarginReport.byItem(records)

        assertEquals(2, rows.size)
        assertEquals(100.0, MarginReport.forItem(records, "chair").margin, 0.001)
        val labeled = rows.first { it.label == "Tabouret" }
        assertEquals(80.0, labeled.totals.margin, 0.001)
    }

    @Test
    fun `a frozen cost survives a restore and a later price change`() {
        val issued = record(
            data(listOf(InvoiceLine("Chaise", 2.0, 300.0, itemId = "chair", unitCostHt = 150.0))),
        )
        // A purchase entered afterwards changes the item's price, not the
        // invoice: the line keeps the cost it was issued with.
        val restored = InvoiceRecord(
            id = issued.id,
            data = InvoiceJson.dataFromJson(InvoiceJson.dataToJson(issued.data)),
            status = issued.status,
            createdAt = 0L,
            updatedAt = 0L,
        )

        assertEquals(150.0, restored.data.lines.single().unitCostHt ?: 0.0, 0.001)
        assertEquals(300.0, MarginReport.forRecord(restored)?.margin ?: 0.0, 0.001)
    }

    @Test
    fun `the totals combine and subtract without losing the counts`() {
        val a = MarginTotals(revenueHt = 100.0, cost = 40.0, linesWithCost = 1)
        val b = MarginTotals(revenueHt = 50.0, cost = 10.0, linesWithoutCost = 1)

        assertEquals(150.0, (a + b).revenueHt, 0.001)
        assertEquals(2, (a + b).lines)
        assertEquals(100.0, (a + b - b).revenueHt, 0.001)
        assertTrue((a + b).marginPercent!! > 60.0)
    }
}
