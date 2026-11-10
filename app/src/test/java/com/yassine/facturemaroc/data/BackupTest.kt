package com.yassine.facturemaroc.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class BackupTest {

    private val shop = ShopSettings(
        name = "Atelier A&B",
        address = "12 rue des Fleurs, Casablanca",
        phone = "0522334455",
        email = "atelier@exemple.ma",
        ice = "001234567000089",
        ifNumber = "12345678",
        rc = "98765",
        patente = "654321",
        cnss = "1234567",
        vatPercent = 20.0,
    )

    private val design = InvoiceDesign(template = InvoiceTemplate.MAROC, logoEnabled = false)
    private val logo = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)
    private val clients = listOf(
        InvoiceClient(
            name = "Société Exemple",
            address = "Casablanca",
            ice = "009876543000012",
            phone = "0600000000",
            vatExempt = true,
        ),
        InvoiceClient(name = "Client de passage"),
        InvoiceClient(
            name = "Grossiste Atlas Fournitures",
            address = "Avenue des Fès, Casablanca",
            ice = "007711223000045",
            role = PartyRole.SUPPLIER,
        ),
    )
    private val items = listOf(
        SavedItem("Main-d'œuvre", 250.0, "heure", folder = "Prestations"),
        SavedItem("Livraison", 50.0, folder = "Services"),
    )
    private val folders = listOf("Prestations", "Services", "Dossier vide")
    private val invoices = listOf(
        InvoiceRecord(
            id = "record-1",
            data = InvoiceData(
                number = "F-20261021-0001",
                date = "21/10/2026",
                client = clients.first(),
                lines = listOf(
                    InvoiceLine("Prestation", 2.0, 350.0, unit = "heure", taxExempt = true),
                ),
                discountPercent = 10,
                paymentMethod = PaymentMethod.TRANSFER,
                documentType = DocumentType.FACTURE,
            ),
            status = InvoiceStatus.ISSUED,
            createdAt = 1_000L,
            updatedAt = 2_000L,
        ),
        InvoiceRecord(
            id = "record-2",
            data = InvoiceData(
                number = "D-20261021-0001",
                date = "21/10/2026",
                client = InvoiceClient(name = ""),
                lines = emptyList(),
                documentType = DocumentType.DEVIS,
                validityDate = "31/12/2026",
            ),
            status = InvoiceStatus.DRAFT,
            createdAt = 3_000L,
            updatedAt = 4_000L,
        ),
    )

    private val counters = mapOf(
        "facture" to 7,
        "devis" to 3,
        "bon_commande" to 1,
        "ticket" to 2,
        "avoir" to 1,
        "proforma" to 1,
        "acompte" to 1,
        "regul" to 1,
        "simplifiee" to 1,
        "export" to 1,
    )

    private fun fullBackup(): String = Backup.build(
        shop = shop,
        design = design,
        logo = logo,
        clients = clients,
        items = items,
        invoices = invoices,
        counters = counters,
        folders = folders,
        prefixes = mapOf("facture" to "FA", "devis" to "DEV"),
        resetAnnually = true,
        exportedAt = 1_234L,
    )

    private fun export(
        logo: ByteArray?,
        invoices: List<InvoiceRecord> = this.invoices,
    ): Backup.Built = Backup.buildForExport(
        shop = shop,
        design = design,
        logo = logo,
        clients = clients,
        items = items,
        invoices = invoices,
        counters = counters,
        folders = folders,
        exportedAt = 1_234L,
    )

    private fun mutated(block: JSONObject.() -> Unit): String {
        val root = JSONObject(fullBackup())
        root.block()
        return root.toString()
    }

    @Test
    fun `a backup restores exactly what was saved`() {
        val restored = Backup.parse(fullBackup())

        assertEquals(shop, restored.shop)
        assertEquals(design, restored.design)
        assertArrayEquals(logo, restored.logo)
        assertEquals(clients, restored.clients)
        assertEquals(items, restored.items)
        assertEquals(folders, restored.folders)
        assertEquals(invoices, restored.invoices)
        assertEquals(counters, restored.counters)
        assertEquals(mapOf("facture" to "FA", "devis" to "DEV"), restored.prefixes)
        assertTrue(restored.resetAnnually)
        assertEquals(1_234L, restored.exportedAt)
    }

    @Test
    fun `a backup from before numbering restores the defaults`() {
        val json = mutated { remove("numbering") }

        val restored = Backup.parse(json)

        assertTrue(restored.prefixes.isEmpty())
        assertFalse(restored.resetAnnually)
    }

    @Test
    fun `a prefix that cannot be a filename is not restored`() {
        val json = mutated {
            getJSONObject("numbering")
                .getJSONObject("prefixes")
                .put("facture", "../F")
        }

        val restored = Backup.parse(json)

        assertEquals(mapOf("devis" to "DEV"), restored.prefixes)
    }

    @Test
    fun `a hidden article field survives the round trip`() {
        val hidden = shop.copy(
            showItemBarcode = false,
            showItemPurchasePrice = false,
            showItemUnit = false,
            showItemStock = false,
            showItemFolder = false,
        )
        val json = Backup.build(
            shop = hidden,
            design = design,
            logo = logo,
            clients = clients,
            items = items,
            invoices = invoices,
            counters = counters,
            folders = folders,
            exportedAt = 1_234L,
        )

        val restored = Backup.parse(json)

        assertFalse(restored.shop.showItemBarcode)
        assertFalse(restored.shop.showItemPurchasePrice)
        assertFalse(restored.shop.showItemUnit)
        assertFalse(restored.shop.showItemStock)
        assertFalse(restored.shop.showItemFolder)
    }

    @Test
    fun `a backup from before the article switches restores every field visible`() {
        val json = mutated {
            val saved = getJSONObject("shop")
            saved.remove("showItemBarcode")
            saved.remove("showItemPurchasePrice")
            saved.remove("showItemUnit")
            saved.remove("showItemStock")
            saved.remove("showItemFolder")
        }

        val restored = Backup.parse(json)

        // The file was written by a version whose every field was on screen, so
        // restoring it with fields hidden would be a change nobody asked for.
        assertTrue(restored.shop.showItemBarcode)
        assertTrue(restored.shop.showItemPurchasePrice)
        assertTrue(restored.shop.showItemUnit)
        assertTrue(restored.shop.showItemStock)
        assertTrue(restored.shop.showItemFolder)
    }

    @Test
    fun `a backup from before folders still restores its articles`() {
        val json = mutated {
            remove("folders")
            val saved = getJSONArray("items")
            (0 until saved.length()).forEach { index ->
                saved.getJSONObject(index).remove("folder")
            }
        }

        val restored = Backup.parse(json)

        assertTrue(restored.folders.isEmpty())
        assertEquals(listOf("Main-d'œuvre", "Livraison"), restored.items.map { it.label })
        assertTrue(restored.items.all { it.folder.isEmpty() })
    }

    @Test
    fun `a backup without a logo restores no logo`() {
        val restored = Backup.parse(
            Backup.build(shop, design, null, clients, items, invoices, counters),
        )

        assertNull(restored.logo)
    }

    @Test
    fun `an empty shop is a valid backup, not an error`() {
        val restored = Backup.parse(
            Backup.build(
                ShopSettings(),
                InvoiceDesign.default(),
                null,
                emptyList(),
                emptyList(),
                emptyList(),
                mapOf(),
            ),
        )

        assertTrue(restored.clients.isEmpty())
        assertTrue(restored.items.isEmpty())
        assertTrue(restored.folders.isEmpty())
        assertTrue(restored.invoices.isEmpty())
        assertEquals(1, restored.counters["facture"])
    }

    @Test
    fun `a file from another app is refused`() {
        val error = assertThrows(BackupException::class.java) {
            Backup.parse(mutated { put("app", "autre-application") })
        }

        assertTrue(error.message!!.contains("Facture Maroc"))
    }

    @Test
    fun `a plain json file is refused`() {
        val error = assertThrows(BackupException::class.java) {
            Backup.parse(mutated { put("format", "invoice") })
        }

        assertTrue(error.message!!.contains("sauvegarde"))
    }

    @Test
    fun `a backup from a newer version is refused instead of half-read`() {
        val error = assertThrows(BackupException::class.java) {
            Backup.parse(mutated { put("version", Backup.VERSION + 1) })
        }

        assertTrue(error.message!!.contains("plus récente"))
    }

    @Test
    fun `a file with no version is refused`() {
        val error = assertThrows(BackupException::class.java) {
            Backup.parse(mutated { remove("version") })
        }

        assertTrue(error.message!!.contains("version"))
    }

    @Test
    fun `a file without shop settings is refused`() {
        val error = assertThrows(BackupException::class.java) {
            Backup.parse(mutated { remove("shop") })
        }

        assertTrue(error.message!!.contains("paramètres du magasin"))
    }

    @Test
    fun `a corrupt file says so instead of throwing a parser error`() {
        val error = assertThrows(BackupException::class.java) {
            Backup.parse("{not json")
        }

        assertTrue(error.message!!.contains("JSON"))
    }

    @Test
    fun `a malformed list element is skipped, not allowed to poison the restore`() {
        val json = mutated {
            put(
                "clients",
                JSONArray()
                    .put("junk")
                    .put(InvoiceJson.clientToJson(InvoiceClient(name = "Valide"))),
            )
        }

        val restored = Backup.parse(json)

        assertEquals(listOf("Valide"), restored.clients.map { it.name })
    }

    @Test
    fun `the file name is dated and recognisable`() {
        val name = Backup.fileName(now = 1_000_000_000_000L)

        assertTrue(Regex("""facture-maroc-sauvegarde-\d{8}-\d{4}\.json""").matches(name))
    }

    @Test
    fun `article ids, thresholds, line links and the ledger survive the round trip`() {
        val linkedItems = listOf(
            SavedItem(
                label = "Chaise",
                price = 100.0,
                stock = 3,
                reorderThreshold = 2,
                id = "chair",
            ),
        )
        val linkedRecord = InvoiceRecord(
            id = "record-linked",
            data = InvoiceData(
                number = "F-2",
                date = "10/11/2026",
                client = InvoiceClient(name = "Client"),
                lines = listOf(
                    InvoiceLine("Chaise", 1.0, 100.0, itemId = "chair"),
                ),
            ),
            status = InvoiceStatus.ISSUED,
            createdAt = 3L,
            updatedAt = 4L,
        )
        val ledger = listOf(
            StockEntry("chair", 4, StockReason.ADJUSTMENT, note = "Stock initial", at = 10L),
            StockEntry("chair", -1, StockReason.INVOICE, reference = "F-2", at = 20L),
        )
        val json = Backup.build(
            shop = shop,
            design = design,
            logo = null,
            clients = clients,
            items = linkedItems,
            invoices = listOf(linkedRecord),
            counters = counters,
            stockEntries = ledger,
            exportedAt = 1_234L,
        )

        val restored = Backup.parse(json)

        assertEquals(linkedItems, restored.items)
        assertEquals("chair", restored.invoices.single().data.lines.single().itemId)
        assertEquals(2, restored.items.single().reorderThreshold)
        assertEquals(ledger, restored.stockEntries)
    }

    @Test
    fun `an older backup without a ledger restores an empty history`() {
        val json = mutated { remove("stockLedger") }

        assertTrue(Backup.parse(json).stockEntries.isEmpty())
    }

    @Test
    fun `a backup read from a stream is the same as one read from a string`() {
        val outcome = Backup.read(ByteArrayInputStream(fullBackup().toByteArray()))

        val restored = (outcome as BackupReadOutcome.Ready).backup
        assertEquals(items, restored.items)
        assertEquals(folders, restored.folders)
        assertEquals(counters, restored.counters)
    }

    @Test
    fun `an oversized file is refused while reading, not parsed first`() {
        val tooBig = ByteArray(Backup.MAX_BYTES + 1) { ' '.code.toByte() }

        val outcome = Backup.read(ByteArrayInputStream(tooBig))

        assertTrue(outcome is BackupReadOutcome.Failed)
        assertTrue((outcome as BackupReadOutcome.Failed).message.contains("8 Mo"))
    }

    @Test
    fun `a logo that would push the file over the cap is left out`() {
        // The picker allows a 4 MiB logo, which Base64 turns into 5.6 MB. With a
        // few hundred documents carrying real descriptions, the file crosses the
        // 8 MB the importer accepts — a backup that exists and can never be
        // restored. Everything the shop cannot retype survives without it.
        val huge = ByteArray(4 * 1024 * 1024) { 0x42 }
        val fat = InvoiceRecord(
            id = "fat",
            data = InvoiceData(
                number = "F-FAT",
                date = "21/10/2026",
                client = InvoiceClient(name = "Client"),
                lines = listOf(InvoiceLine("L".repeat(20_000), quantity = 1.0, unitPrice = 100.0)),
            ),
            status = InvoiceStatus.ISSUED,
            createdAt = 0,
            updatedAt = 0,
        )

        val built = export(logo = huge, invoices = List(150) { fat })

        assertFalse(built.logoIncluded)
        assertTrue(built.json.toByteArray(Charsets.UTF_8).size <= Backup.MAX_BYTES)
        val restored = Backup.parse(built.json)
        assertNull(restored.logo)
        assertEquals(150, restored.invoices.size)
    }

    @Test
    fun `a logo that fits is kept, and the file is still readable`() {
        val built = export(logo = logo)

        assertTrue(built.logoIncluded)
        assertArrayEquals(logo, Backup.parse(built.json).logo)
    }

    @Test
    fun `a backup the app could not restore is refused instead of written`() {
        // 60 documents of ~200 kB of remarks: over the cap even without a logo.
        val fat = InvoiceRecord(
            id = "fat",
            data = InvoiceData(
                number = "F-1",
                date = "21/10/2026",
                client = InvoiceClient(name = "Client"),
                lines = listOf(InvoiceLine("L".repeat(200_000), quantity = 1.0, unitPrice = 100.0)),
            ),
            status = InvoiceStatus.ISSUED,
            createdAt = 0,
            updatedAt = 0,
        )

        val error = runCatching { export(logo = null, invoices = List(60) { fat }) }.exceptionOrNull()

        assertTrue(error is Backup.TooLargeException)
    }
}
