package com.yassine.facturemaroc.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class BackupTest {

    private val shop = ShopSettings(
        name = "Atelier A&B",
        address = "12 rue des Fleurs, Casablanca",
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
                    InvoiceLine("Prestation", 2, 350.0, unit = "heure", taxExempt = true),
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
        assertEquals(1_234L, restored.exportedAt)
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
            Backup.parse(mutated { put("app", "tyre-finder") })
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
}
