package com.yassine.facturemaroc.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the search box answers.
 *
 * A shop types a name it half remembers, a measurement, a supplier's word or the
 * digits from a box. All of them have to work, because a search that silently
 * ignores one of those is a search the shop stops believing: the Articles list
 * once filtered on the label, the unit and the folder and forgot the barcode,
 * and a scanned code came back as "no such article".
 */
class SavedItemSearchTest {

    private val chair = SavedItem(
        label = "Chaise en rotin 60x60 cm",
        price = 850.0,
        unit = "unité",
        folder = "Mobilier",
        barcode = "6111234500017",
        stock = 12,
    )

    @Test
    fun `the digits under the bars find the article`() {
        assertTrue(SavedItemStore.matches(chair, "6111234500017"))
    }

    @Test
    fun `part of a barcode is enough`() {
        assertTrue(SavedItemStore.matches(chair, "4500017"))
    }

    @Test
    fun `a measurement written in one word finds it`() {
        assertTrue(SavedItemStore.matches(chair, "60x60"))
        assertTrue(SavedItemStore.matches(chair, "cm"))
    }

    @Test
    fun `case and surrounding space do not matter`() {
        assertTrue(SavedItemStore.matches(chair, "  CHAISE  "))
    }

    @Test
    fun `every word has to appear, in any order`() {
        assertTrue(SavedItemStore.matches(chair, "chaise 60x60"))
        assertTrue(SavedItemStore.matches(chair, "60x60 chaise"))
        assertFalse(SavedItemStore.matches(chair, "chaise 80x80"))
    }

    @Test
    fun `the folder is searchable`() {
        assertTrue(SavedItemStore.matches(chair, "mobilier"))
    }

    @Test
    fun `an article with no barcode does not match one`() {
        val service = SavedItem(label = "Montage", price = 50.0, unit = "unité")

        assertFalse(SavedItemStore.matches(service, "6111234500017"))
    }

    @Test
    fun `a blank query accepts everything`() {
        assertTrue(SavedItemStore.matches(chair, ""))
        assertTrue(SavedItemStore.matches(chair, "   "))
    }

    @Test
    fun `a stored code with a leading zero is still found without it`() {
        // EAN-13 and UPC-A codes describe the same product family, and the
        // match is a substring: typing the digits you can read finds the code
        // that carries them, with or without a leading zero.
        val item = chair.copy(barcode = "06111234500017")

        assertTrue(SavedItemStore.matches(item, "06111234500017"))
        assertTrue(SavedItemStore.matches(item, "6111234500017"))
    }

    @Test
    fun `a code that shares no digits with the item finds nothing`() {
        assertFalse(SavedItemStore.matches(chair, "0000000000000"))
    }
}
