package com.yassine.facturemaroc

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yassine.facturemaroc.data.ItemField
import com.yassine.facturemaroc.data.SavedItem
import com.yassine.facturemaroc.ui.FactureMarocTheme
import com.yassine.facturemaroc.ui.ItemEditorDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The article form when a shop has switched optional fields off.
 *
 * The rule under test is not "the field is hidden" — that is cosmetic — but
 * "a hidden field keeps what the article already had". The JVM tests cover the
 * data layer; only a real composition can prove the dialog actually passes the
 * old values back instead of the blank ones it never asked for.
 */
@RunWith(AndroidJUnit4::class)
class ItemEditorFieldsTest {

    @get:Rule
    val compose = createComposeRule()

    private val article = SavedItem(
        label = "Chaise en rotin",
        price = 950.0,
        unit = "unité",
        folder = "Mobilier",
        barcode = "6111234500017",
        purchasePrice = 700.0,
        stock = 12,
    )

    private fun edit(fields: Set<ItemField>, onSave: (SavedItem) -> Unit) {
        compose.setContent {
            FactureMarocTheme {
                ItemEditorDialog(
                    initial = article,
                    onSave = onSave,
                    onDismiss = {},
                    folders = listOf("Mobilier", "Mobilier/Chaises"),
                    fields = fields,
                )
            }
        }
    }

    @Test
    fun hidingStockAndFolderNeverErasesThem() {
        var saved: SavedItem? = null
        edit(ItemField.entries.toSet() - ItemField.STOCK - ItemField.FOLDER) { saved = it }

        compose.onNodeWithText("En stock").assertDoesNotExist()
        compose.onNodeWithText("Dossier").assertDoesNotExist()
        compose.onNodeWithText("Enregistrer").performClick()

        compose.runOnIdle {
            assertEquals(12, saved?.stock)
            assertEquals("Mobilier", saved?.folder)
        }
    }

    @Test
    fun hidingUnitAndBarcodeKeepsThemOnTheArticle() {
        var saved: SavedItem? = null
        edit(ItemField.entries.toSet() - ItemField.UNIT - ItemField.BARCODE) { saved = it }

        compose.onNodeWithText("Unité").assertDoesNotExist()
        compose.onNodeWithText("Code-barres (facultatif)").assertDoesNotExist()
        compose.onNodeWithText("Enregistrer").performClick()

        compose.runOnIdle {
            assertEquals("unité", saved?.unit)
            assertEquals("6111234500017", saved?.barcode)
        }
    }

    @Test
    fun theStepperChangesTheQuantityWithoutTheKeyboard() {
        var saved: SavedItem? = null
        edit(ItemField.entries.toSet()) { saved = it }

        compose.onNodeWithText("12").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ajouter un").performClick()
        compose.onNodeWithText("Enregistrer").performClick()

        compose.runOnIdle { assertEquals(13, saved?.stock) }
    }
}
