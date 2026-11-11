package com.yassine.facturemaroc.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The catalogue file: what happens when it will not parse, and when it cannot
 * be written.
 *
 * Both used to fail in the same silent way — the shop saw an empty catalogue or
 * a saved article, and only a relaunch told them otherwise.
 */
class SavedItemStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun catalogue(): File = File(temp.root, SavedItemStore.FILE)

    private fun open(errors: MutableList<String> = mutableListOf()) = SavedItemStore(
        catalogue(),
        StockLedger(File(temp.root, StockLedger.FILE)),
    ) { errors += it }

    @Test
    fun `a catalogue that will not parse is kept before it can be overwritten`() {
        val broken = "{ \"items\": [ this is not json"
        catalogue().writeText(broken)

        val store = open()

        assertTrue(store.items.value.isEmpty())
        val copy = File(temp.root, "${SavedItemStore.FILE}-corrompu.json")
        assertTrue("the unreadable catalogue must be kept", copy.isFile)
        assertEquals(broken, copy.readText())
        // The live file is left alone until a save replaces it, so nothing is
        // destroyed by the mere act of opening the app.
        assertEquals(broken, catalogue().readText())
    }

    @Test
    fun `the quarantine copy is written once, not on every launch`() {
        catalogue().writeText("not json at all")

        open()
        val copy = File(temp.root, "${SavedItemStore.FILE}-corrompu.json")
        val first = copy.readText()

        // A second launch sees a different broken file and must not replace the
        // copy: the first one is the most likely to still hold every article.
        catalogue().writeText("also not json")
        open()

        assertEquals(first, copy.readText())
    }

    @Test
    fun `a valid catalogue still loads`() {
        catalogue().writeText(
            """
            {
              "version": 3,
              "folders": ["Mobilier"],
              "items": [
                { "id": "a1", "label": "Chaise", "price": 250.0, "folder": "Mobilier" }
              ]
            }
            """.trimIndent(),
        )

        val store = open()

        assertEquals(1, store.items.value.size)
        assertEquals("Chaise", store.items.value.first().label)
        assertTrue(store.folders.value.contains("Mobilier"))
    }

    @Test
    fun `a tier price is written in the shop's basis under its new key`() {
        val store = open()
        store.save(
            SavedItem(
                label = "Câble",
                price = 120.0,
                priceTiers = listOf(PriceTier(fromQuantity = 10, unitPrice = 100.0)),
            ),
        )

        val item = JSONObject(catalogue().readText()).getJSONArray("items").getJSONObject(0)
        val tier = item.getJSONArray("priceTiers").getJSONObject(0)

        // The tier follows the article price, so it is stored as typed, with no
        // TVA arithmetic of its own — that is what keeps it honest for a shop
        // quoting TTC.
        assertEquals(100.0, tier.getDouble("unitPrice"), 0.001)
        assertFalse("the old name must not be written any more", tier.has("unitPriceHt"))

        val reopened = open()
        assertEquals(100.0, reopened.items.value.single().priceTiers.single().unitPrice, 0.001)
    }

    @Test
    fun `a catalogue written before the basis change still reads its tiers`() {
        // The key every catalogue in the wild carries today.
        catalogue().writeText(
            """
            {
              "version": 3,
              "folders": [],
              "items": [
                {
                  "id": "a1",
                  "label": "Câble",
                  "price": 120.0,
                  "priceTiers": [{ "fromQuantity": 10, "unitPriceHt": 100.0 }]
                }
              ]
            }
            """.trimIndent(),
        )

        val store = open()

        assertEquals(100.0, store.items.value.single().priceTiers.single().unitPrice, 0.001)
    }

    @Test
    fun `the whole catalogue's costs come from one pass and agree with one article`() {
        val ledger = StockLedger(File(temp.root, StockLedger.FILE))
        ledger.append(StockEntry("a", delta = 10, StockReason.PURCHASE, unitCost = 30.0))
        ledger.append(StockEntry("b", delta = 5, StockReason.PURCHASE, unitCost = 70.0))
        val store = SavedItemStore(catalogue(), ledger)

        val costs = store.unitCosts(CostMethod.CMP)

        assertEquals(30.0, costs.getValue("a"), 0.001)
        assertEquals(70.0, costs.getValue("b"), 0.001)
        // The bulk map and the single-article read must not be two answers.
        assertEquals(store.unitCostOf("a", CostMethod.CMP)!!, costs.getValue("a"), 0.001)
    }

    @Test
    fun `no valuation means no costs at all`() {
        val ledger = StockLedger(File(temp.root, StockLedger.FILE))
        ledger.append(StockEntry("a", delta = 10, StockReason.PURCHASE, unitCost = 30.0))
        val store = SavedItemStore(catalogue(), ledger)

        assertTrue(store.unitCosts(CostMethod.NONE).isEmpty())
    }

    @Test
    fun `a write that fails is reported and does not change the screen`() {
        val errors = mutableListOf<String>()
        val store = open(errors)

        // A directory where the temporary file belongs makes the write throw,
        // which is what a full disk looks like from here.
        File(temp.root, "${SavedItemStore.FILE}.tmp").mkdirs()

        store.save(SavedItem(label = "Chaise", price = 250.0))

        assertTrue("the list must not show an article that was not written", store.items.value.isEmpty())
        assertEquals(1, errors.size)
        assertFalse("nothing may claim the catalogue was saved", catalogue().isFile)
    }

    @Test
    fun `a deleted article can be put back without moving its stock again`() {
        // Undo restores through `restore`, not `save`: `save` reads a row it has
        // never seen as an opening movement, and the ledger still holds the
        // entries the article had, so the quantity would double.
        val store = open()
        val chair = SavedItem(label = "Chaise", price = 350.0, stock = 4)
        store.save(chair)
        val saved = store.items.value.single()
        val balanceBefore = store.ledger.balance(saved.id)

        store.delete(saved)
        assertTrue(store.items.value.isEmpty())

        store.restore(saved)

        assertEquals(listOf(saved), store.items.value)
        assertEquals(balanceBefore, store.ledger.balance(saved.id))
    }

    @Test
    fun `restoring an article that is already there changes nothing`() {
        val store = open()
        val chair = SavedItem(label = "Chaise", price = 350.0, stock = 4)
        store.save(chair)
        val saved = store.items.value.single()

        store.restore(saved)

        assertEquals(1, store.items.value.size)
        assertEquals(4, store.items.value.single().stock)
    }

    @Test
    fun `a folder moves with everything it holds`() {
        val store = open()
        store.createFolder("Mobilier")
        store.createFolder("Chaises", "Mobilier")
        store.save(SavedItem(label = "Chaise", price = 350.0, folder = "Mobilier/Chaises"))
        store.createFolder("Ventes")

        val moved = store.moveFolder("Mobilier", "Ventes")

        assertEquals("Ventes/Mobilier", moved)
        assertEquals(
            setOf("Ventes", "Ventes/Mobilier", "Ventes/Mobilier/Chaises"),
            store.folders.value.toSet(),
        )
        // The article travelled with its shelf, keeping its id.
        val chair = store.items.value.single()
        assertEquals("Ventes/Mobilier/Chaises", chair.folder)
    }

    @Test
    fun `a folder cannot be moved inside itself`() {
        val store = open()
        store.createFolder("Mobilier")
        store.createFolder("Chaises", "Mobilier")

        assertNull(store.moveFolder("Mobilier", "Mobilier/Chaises"))
        assertNull(store.copyFolder("Mobilier", "Mobilier/Chaises"))
        assertEquals(setOf("Mobilier", "Mobilier/Chaises"), store.folders.value.toSet())
    }

    @Test
    fun `a folder cannot be moved onto a name that is taken`() {
        val store = open()
        store.createFolder("Mobilier")
        store.createFolder("Ventes")
        store.createFolder("Mobilier", "Ventes")

        assertNull(store.moveFolder("Mobilier", "Ventes"))
    }

    @Test
    fun `copying a folder duplicates its tree and its articles with new identities`() {
        val store = open()
        store.createFolder("13")
        store.createFolder("175 70R13", "13")
        store.save(
            SavedItem(label = "Michelin 175/70 R13", price = 650.0, stock = 4, folder = "13/175 70R13"),
        )
        val original = store.items.value.single()

        val copied = store.copyFolder("13", "")

        assertEquals("13 (copie)", copied)
        assertEquals(2, store.items.value.size)
        val copy = store.items.value.first { it.id != original.id }
        assertNotEquals(original.id, copy.id)
        assertEquals("Michelin 175/70 R13 (copie)", copy.label)
        assertEquals("13 (copie)/175 70R13", copy.folder)
        // The quantity came with the copy, and its opening movement too, so the
        // journal and the cached figure agree on the copy.
        assertEquals(4, copy.stock)
        assertEquals(4, store.ledger.balance(copy.id))
    }

    @Test
    fun `copying the same folder twice numbers the copies`() {
        val store = open()
        store.createFolder("13")
        store.save(SavedItem(label = "Pneu", price = 500.0, folder = "13"))

        store.copyFolder("13", "")
        // The first copy owns "13 (copie)", so the second one must not overwrite
        // it: it becomes "13 (copie 2)".
        assertEquals("13 (copie 2)", store.copyFolder("13", ""))
        assertEquals(3, store.items.value.size)
        assertEquals(3, store.items.value.map { it.label }.distinct().size)
    }
}
