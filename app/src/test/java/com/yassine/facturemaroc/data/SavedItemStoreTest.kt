package com.yassine.facturemaroc.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
