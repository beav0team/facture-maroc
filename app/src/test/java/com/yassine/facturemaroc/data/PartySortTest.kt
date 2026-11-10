package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PartySortTest {

    private fun client(name: String, role: PartyRole = PartyRole.CLIENT) =
        InvoiceClient(name = name, role = role)

    private val karim = client("Karim")
    private val atelier = client("Atelier Atlas")
    private val yasmine = client("yasmine")

    private val summaries = mapOf(
        partyKey(PartyRole.CLIENT, "Karim") to PartySummary(
            documents = 2, due = 1_812.60,
        ),
        partyKey(PartyRole.CLIENT, "Atelier Atlas") to PartySummary(
            documents = 3, due = 9_400.0, overdue = 1_200.0,
        ),
        partyKey(PartyRole.CLIENT, "yasmine") to PartySummary(
            documents = 1, due = 0.0, lastActivity = "09/11/2026",
        ),
    )

    private val all = listOf(karim, atelier, yasmine)

    @Test
    fun `the directory is alphabetical, ignoring case`() {
        assertEquals(
            listOf("Atelier Atlas", "Karim", "yasmine"),
            sortParties(all, summaries, PartySort.NAME).map { it.name },
        )
    }

    @Test
    fun `the largest debt comes first`() {
        assertEquals(
            listOf("Atelier Atlas", "Karim", "yasmine"),
            sortParties(all, summaries, PartySort.BALANCE).map { it.name },
        )
    }

    @Test
    fun `recency puts the last invoiced first and the never-invoiced last`() {
        val recent = summaries + (
            partyKey(PartyRole.CLIENT, "Karim") to PartySummary(
                due = 1_812.60, lastActivity = "10/11/2026",
            )
            )
        assertEquals(
            listOf("Karim", "yasmine", "Atelier Atlas"),
            sortParties(all, recent, PartySort.RECENT).map { it.name },
        )
    }

    @Test
    fun `owes and late are different questions`() {
        assertEquals(
            listOf("Karim", "Atelier Atlas"),
            filterParties(all, summaries, PartyFilter.OWES, PartyRole.CLIENT).map { it.name },
        )
        assertEquals(
            listOf("Atelier Atlas"),
            filterParties(all, summaries, PartyFilter.LATE, PartyRole.CLIENT).map { it.name },
        )
    }

    @Test
    fun `a supplier is never filtered out as owing money`() {
        // The chips are not drawn for suppliers, so a filter left over from the
        // client side would empty his directory for no reason.
        val supplier = client("Bois du Nord", PartyRole.SUPPLIER)
        assertEquals(
            listOf(supplier),
            filterParties(listOf(supplier), emptyMap(), PartyFilter.OWES, PartyRole.SUPPLIER),
        )
    }

    @Test
    fun `a party with no summary sorts last, not first`() {
        val stranger = client("Zineb")
        assertEquals(
            listOf("Atelier Atlas", "Karim", "yasmine", "Zineb"),
            sortParties(all + stranger, summaries, PartySort.BALANCE).map { it.name },
        )
    }
}
