package com.yassine.facturemaroc.data

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopSnapshotTest {

    private val shop = ShopSettings(
        name = "Garage Atlas",
        address = "12 rue des Fleurs, Casablanca",
        phone = "0522334455",
        email = "contact@atlas.ma",
        ice = "001234567000089",
        ifNumber = "12345678",
        rc = "98765",
        patente = "654321",
        cnss = "1234567",
        vatPercent = 20.0,
        vatNotApplicable = false,
        pricesIncludeVat = true,
    )

    private val logo = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    @Test
    fun `a snapshot keeps what the document printed, not what the shop says now`() {
        val issued = InvoiceData(
            number = "F-20260101-0001",
            date = "01/01/2026",
            client = InvoiceClient(name = "Client"),
            lines = listOf(InvoiceLine("Montage", 1, 100.0)),
            shop = ShopSnapshot.of(shop, logo),
        )

        // The shop moves and re-registers: the old invoice must not follow.
        val moved = shop.copy(
            name = "Garage Atlas Nouveau",
            address = "Ailleurs",
            ice = "999999999000099",
            vatPercent = 10.0,
        )

        val printed = issued.printingShop(moved)

        assertEquals("Garage Atlas", printed.name)
        assertEquals("12 rue des Fleurs, Casablanca", printed.address)
        assertEquals("001234567000089", printed.ice)
        assertEquals(20.0, printed.vatPercent, 0.0)
        assertArrayEquals(logo, printed.logoBytes)
    }

    @Test
    fun `a draft with no snapshot follows the live shop`() {
        val draft = InvoiceData(
            number = "",
            date = "01/01/2026",
            client = InvoiceClient(name = ""),
            lines = emptyList(),
        )

        assertNull(draft.shop)
        assertEquals("Garage Atlas", draft.printingShop(shop).name)
    }

    @Test
    fun `a snapshot survives the JSON round trip, logo included`() {
        val data = InvoiceData(
            number = "F-20260101-0007",
            date = "01/01/2026",
            client = InvoiceClient(name = "Client"),
            lines = listOf(InvoiceLine("Pneu", 2, 500.0)),
            shop = ShopSnapshot.of(shop, logo),
            customerNote = "Merci de vérifier la marchandise.",
            termsNote = "Paiement comptant.",
            thankYouNote = "À bientôt !",
            signature = ImageBytes(byteArrayOf(1, 2, 3, 4)),
        )

        val restored = InvoiceJson.dataFromJson(InvoiceJson.dataToJson(data))

        assertEquals(data, restored)
        assertEquals("Merci de vérifier la marchandise.", restored.customerNote)
        assertEquals("Paiement comptant.", restored.termsNote)
        assertEquals("À bientôt !", restored.thankYouNote)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), restored.signature!!.bytes)
        assertArrayEquals(logo, restored.shop!!.logo!!.bytes)
    }

    @Test
    fun `a record written before snapshots existed still reads, with no snapshot`() {
        // The exact shape the previous version wrote: no shop, no notes, no
        // signature. Reading it must not invent any of them.
        val legacy = JSONObject(
            """
            {
              "number": "F-20260101-0001",
              "date": "01/01/2026",
              "client": { "name": "Client" },
              "lines": [ { "description": "Pneu", "quantity": 1, "unitPrice": 500 } ],
              "discountPercent": 0,
              "vatPercent": 20,
              "paymentMethod": "cash",
              "documentType": "facture",
              "validityDate": null,
              "supplier": null,
              "referenceNumber": "",
              "incoterm": "",
              "vatNotApplicable": false
            }
            """.trimIndent(),
        )

        val data = InvoiceJson.dataFromJson(legacy)

        assertNull(data.shop)
        assertNull(data.signature)
        assertEquals("", data.customerNote)
        assertEquals("", data.termsNote)
        assertEquals("", data.thankYouNote)
        assertEquals(500.0, data.lines.first().unitPrice, 0.0)
    }

    @Test
    fun `the footer falls back to the template line when no note was written`() {
        val data = InvoiceData(
            number = "F-20260101-0001",
            date = "01/01/2026",
            client = InvoiceClient(name = "Client"),
            lines = emptyList(),
        )

        assertEquals("Merci de votre confiance", data.footer("Merci de votre confiance"))
        assertEquals("Merci !", data.copy(thankYouNote = "Merci !").footer("Merci de votre confiance"))
        assertEquals(
            "Merci de votre confiance",
            data.copy(thankYouNote = "   ").footer("Merci de votre confiance"),
        )
    }

    @Test
    fun `image bytes compare by content, so two identical documents are equal`() {
        val a = ImageBytes(byteArrayOf(1, 2, 3))
        val b = ImageBytes(byteArrayOf(1, 2, 3))
        val c = ImageBytes(byteArrayOf(1, 2, 4))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)

        val first = InvoiceData(
            number = "N",
            date = "D",
            client = InvoiceClient(name = "C"),
            lines = emptyList(),
            signature = ImageBytes(byteArrayOf(1, 2, 3)),
        )
        val second = first.copy(signature = ImageBytes(byteArrayOf(1, 2, 3)))

        assertEquals(first, second)
        assertTrue(first.hasSignature)
    }
}
