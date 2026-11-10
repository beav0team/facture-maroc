package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tyre shop demo file that ships with the project.
 *
 * It exists to be restored on a phone in front of a shopkeeper, so it is tested
 * like a real backup rather than trusted because it was generated: every folder
 * must have its parent, every article must sit in a folder that exists, and every
 * stock movement must point at an article. A demo that half-restores is worse
 * than no demo.
 */
class TyreDemoBackupTest {

    private val backup: BackupData by lazy {
        val json = javaClass.classLoader!!.getResourceAsStream("demo-pneus.json")!!
        (Backup.read(json) as BackupReadOutcome.Ready).backup
    }

    @Test
    fun `the file restores and covers rims 13 to 19`() {
        val folders = backup.folders.toSet()

        (13..19).forEach { rim ->
            assertTrue("rayon $rim absent", rim.toString() in folders)
        }

        // Three levels: ray, size, brand.
        val sizes = backup.folders.filter { it.count { c -> c == '/' } == 1 }
        val brands = backup.folders.filter { it.count { c -> c == '/' } == 2 }
        assertTrue("aucune taille", sizes.size >= 20)
        assertTrue("aucune marque", brands.size >= 60)
        sizes.forEach { path ->
            assertTrue("rayon manquant pour $path", path.substringBefore('/') in folders)
        }
        brands.forEach { path ->
            assertTrue("taille manquante pour $path", path.substringBeforeLast('/') in folders)
        }
    }

    @Test
    fun `every tyre has a price, a cost and a brand folder`() {
        val tyres = backup.items.filter { it.unit == "pneu" }
        assertTrue("le fichier doit contenir des pneus", tyres.size >= 100)

        tyres.forEach { tyre ->
            assertTrue("${tyre.label} sans prix", tyre.price > 0.0)
            assertTrue("${tyre.label} sans coût", tyre.purchasePrice > 0.0)
            assertTrue("${tyre.label} hors dossier", tyre.folder.isNotBlank())
            assertTrue(
                "${tyre.label} rangé dans un dossier inconnu : ${tyre.folder}",
                tyre.folder in backup.folders,
            )
            // The shop sells TTC; a cost below the sale price is what makes the
            // margin meaningful in the demo.
            assertTrue("${tyre.label} vendu à perte", tyre.purchasePrice < tyre.price)
        }
    }

    @Test
    fun `the opening stock is in the journal, not only in the cached field`() {
        val ids = backup.items.map { it.id }.toSet()
        val entries = backup.stockEntries

        assertTrue("journal vide", entries.size >= 90)
        entries.forEach { entry ->
            assertTrue("mouvement orphelin : ${entry.itemId}", entry.itemId in ids)
            assertEquals(StockReason.ADJUSTMENT, entry.reason)
        }
        // The journal and the cached quantity must agree, or the first
        // "Vérifier le stock" would report a drift on every article.
        backup.items.filter { (it.stock ?: 0) > 0 }.forEach { item ->
            val fromJournal = entries.filter { it.itemId == item.id }.sumOf { it.delta }
            assertEquals("stock incohérent pour ${item.label}", item.stock, fromJournal)
        }
    }

    @Test
    fun `the shop quotes prices including VAT, as a tyre counter does`() {
        assertTrue(backup.shop.pricesIncludeVat)
        assertEquals(20.0, backup.shop.vatPercent, 0.001)
    }

    @Test
    fun `the demo has documents to look at`() {
        assertTrue(backup.invoices.size >= 3)
        val ids = backup.items.map { it.id }.toSet()
        backup.invoices.flatMap { it.data.lines }
            .filter { !it.itemId.isNullOrBlank() }
            .forEach { line ->
                assertTrue("ligne vers un article inconnu : ${line.description}", line.itemId in ids)
            }
    }
}
