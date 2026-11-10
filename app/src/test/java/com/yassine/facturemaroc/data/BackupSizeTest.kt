package com.yassine.facturemaroc.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How big a full shop really is.
 *
 * The import cap used to be a round number nobody had measured: 8 Mo. Five
 * thousand issued documents of two lines each weigh 7,8 Mo — the shop this app
 * is written for, at the top of its range, fitted with less than 3 % to spare.
 * This test is what keeps the constant honest the day someone adds a field to
 * every record: it builds that shop and asks the backup builder to carry it.
 */
class BackupSizeTest {

    private fun record(index: Int) = InvoiceRecord(
        id = "record-$index",
        data = InvoiceData(
            number = "F-2026%04d-0001".format(index),
            date = "21/10/2026",
            client = InvoiceClient(
                name = "Société Exemple $index",
                address = "12 rue des Fleurs, Casablanca",
                ice = "009876543000012",
                phone = "0600000000",
            ),
            lines = listOf(
                InvoiceLine("Chaise de bureau", 2.0, 950.0, unit = "unité"),
                InvoiceLine("Montage et livraison", 1.0, 300.0),
            ),
            dueDate = "21/11/2026",
            payments = listOf(PaymentInfo(paidAmount = 500.0, paidAt = "12/11/2026")),
        ),
        status = InvoiceStatus.ISSUED,
        createdAt = index.toLong(),
        updatedAt = index.toLong(),
    )

    @Test
    fun `five thousand documents still fit a backup`() {
        val json = Backup.build(
            shop = ShopSettings(name = "Atelier", vatPercent = 20.0),
            design = InvoiceDesign(),
            logo = null,
            clients = emptyList(),
            items = emptyList(),
            invoices = (1..5_000).map(::record),
            counters = emptyMap(),
            exportedAt = 1_234L,
        )

        val size = json.toByteArray(Charsets.UTF_8).size
        assertTrue(
            "5 000 documents weigh $size bytes, the cap is ${Backup.MAX_BYTES}",
            size <= Backup.MAX_BYTES,
        )
    }
}
