package com.yassine.facturemaroc.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether a name is already taken, which decides if a save replaces or adds.
 *
 * The rule is out of the screens on purpose: it used to be written inline as
 * `previous == null && …`, so a *rename* onto a name the catalogue already held
 * skipped the check and replaced that record — an article and the quantity on
 * its shelf, or a client and its ICE.
 */
class NameClashTest {

    private fun item(label: String, stock: Int? = null) =
        SavedItem(label = label, price = 100.0, stock = stock)

    private fun client(name: String, role: PartyRole = PartyRole.CLIENT) =
        InvoiceClient(name = name, role = role)

    // --- articles ---------------------------------------------------------

    @Test
    fun `a new article may not take a label that already exists`() {
        val items = listOf(item("Montage"), item("Équilibrage"))

        assertTrue(items.labelTaken("Équilibrage"))
        // Case and surrounding space are not a different article.
        assertTrue(items.labelTaken("  équilibrage  "))
        assertFalse(items.labelTaken("Vidange"))
    }

    @Test
    fun `renaming an article onto an existing label is a clash`() {
        val items = listOf(item("Montage"), item("Équilibrage"))

        assertTrue(items.labelTaken("Équilibrage", excluding = items[0]))
    }

    @Test
    fun `an article keeping its own name is not a clash with itself`() {
        val items = listOf(item("Montage"), item("Équilibrage"))

        assertFalse(items.labelTaken("Montage", excluding = items[0]))
        // Neither is a change of case on itself.
        assertFalse(items.labelTaken("montage", excluding = items[0]))
    }

    // --- clients and suppliers -------------------------------------------

    @Test
    fun `a client may not take a name the directory already holds`() {
        val clients = listOf(client("Garage Atlas"))

        assertTrue(clients.nameTaken("garage atlas", PartyRole.CLIENT))
        assertFalse(clients.nameTaken("Garage Atlas", PartyRole.SUPPLIER))
    }

    @Test
    fun `renaming a client onto an existing name is a clash`() {
        val clients = listOf(client("Garage Atlas"), client("Garage Rif"))

        assertTrue(clients.nameTaken("Garage Rif", PartyRole.CLIENT, excluding = clients[0]))
        assertFalse(clients.nameTaken("Garage Atlas", PartyRole.CLIENT, excluding = clients[0]))
    }

    @Test
    fun `a garage can be a client and a supplier under the same name`() {
        val clients = listOf(client("Garage Atlas"), client("Garage Atlas", PartyRole.SUPPLIER))

        // The two records say different things about the relationship, which is
        // why the role is part of the key: one does not block the other.
        assertTrue(clients.nameTaken("Garage Atlas", PartyRole.CLIENT))
        assertTrue(clients.nameTaken("Garage Atlas", PartyRole.SUPPLIER))

        // Editing the supplier is not a clash with itself, even though a client
        // of the same name sits next to it.
        assertFalse(clients.nameTaken("Garage Atlas", PartyRole.SUPPLIER, excluding = clients[1]))
    }
}
