package com.yassine.facturemaroc.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Payment terms as a structure, and the late-payment clause that hangs off it.
 *
 * The rule that matters is the one that keeps a collection clause off a cash
 * invoice: a shop that sells for cash should not hand out a document saying
 * what will happen "passé le délai de paiement".
 */
class PaymentTermsTest {

    private val clause = ShopSettings.DEFAULT_LATE_PENALTY_CLAUSE

    private fun invoice(
        terms: PaymentTerms = PaymentTerms.CUSTOM,
        note: String = "",
        lateClause: String = clause,
    ): InvoiceData = InvoiceData(
        number = "F-20261110-0001",
        date = "10/11/2026",
        client = InvoiceClient(name = "Client"),
        lines = listOf(InvoiceLine("Article", 1.0, 100.0)),
        paymentTerms = terms,
        termsNote = note,
        latePenaltyClause = lateClause,
    )

    @Test
    fun `every preset but free text writes its own sentence`() {
        PaymentTerms.entries.filter { it != PaymentTerms.CUSTOM }.forEach { terms ->
            assertTrue("${terms.id} has no sentence", terms.note.isNotBlank())
            assertEquals(terms, PaymentTerms.fromNote(terms.note))
        }
        assertEquals("", PaymentTerms.CUSTOM.note)
    }

    @Test
    fun `an edited sentence is free text again`() {
        val edited = PaymentTerms.DAYS30.note + " Sauf accord du fournisseur."
        assertEquals(PaymentTerms.CUSTOM, PaymentTerms.fromNote(edited))
    }

    @Test
    fun `an unknown stored id reads back as free text, never as cash`() {
        assertEquals(PaymentTerms.CUSTOM, PaymentTerms.fromId("quelque-chose"))
        assertEquals(PaymentTerms.CUSTOM, PaymentTerms.fromId(null))
        assertEquals(PaymentTerms.DAYS60, PaymentTerms.fromId("60j"))
    }

    @Test
    fun `a cash sale never prints the late clause`() {
        val cash = invoice(terms = PaymentTerms.CASH, note = PaymentTerms.CASH.note)
        assertTrue(cash.isCashTerms)
        assertFalse(cash.printsLateClause)
    }

    @Test
    fun `a deferred term prints the clause when the shop filled it`() {
        val deferred = invoice(terms = PaymentTerms.DAYS30, note = PaymentTerms.DAYS30.note)
        assertFalse(deferred.isCashTerms)
        assertTrue(deferred.printsLateClause)
    }

    @Test
    fun `a cleared clause prints nothing even on a deferred document`() {
        val deferred = invoice(terms = PaymentTerms.DAYS60, lateClause = "")
        assertFalse(deferred.printsLateClause)
        assertFalse(deferred.hasNotes)
    }

    @Test
    fun `the clause is part of the notes block`() {
        val deferred = invoice(terms = PaymentTerms.DAYS30)
        assertTrue(deferred.hasNotes)
    }

    @Test
    fun `a document written before presets is cash when nothing says otherwise`() {
        val legacyCash = invoice(note = "")
        assertTrue(legacyCash.isCashTerms)

        val legacyDeferred = invoice(note = "Paiement à 45 jours.")
        assertFalse(legacyDeferred.isCashTerms)
    }

    @Test
    fun `a typed sentence that says comptant is still cash`() {
        assertTrue(invoice(note = "Paiement comptant à la livraison.").isCashTerms)
    }

    @Test
    fun `a cash method with a deadline is not a cash sale`() {
        val withDeadline = invoice(note = "").copy(dueDate = "10/12/2026")
        assertFalse(withDeadline.isCashTerms)
        assertTrue(withDeadline.printsLateClause)
    }

    @Test
    fun `terms and the clause survive a JSON round trip`() {
        val original = invoice(terms = PaymentTerms.END_OF_MONTH, note = "Paiement en fin de mois.")
        val restored = InvoiceJson.dataFromJson(InvoiceJson.dataToJson(original))

        assertEquals(PaymentTerms.END_OF_MONTH, restored.paymentTerms)
        assertEquals(clause, restored.latePenaltyClause)
    }

    @Test
    fun `an old file with a preset sentence derives the preset`() {
        val legacy = JSONObject()
            .put("number", "F-1")
            .put("date", "01/01/2026")
            .put("client", JSONObject().put("name", "Client"))
            .put("lines", org.json.JSONArray())
            .put("termsNote", PaymentTerms.DAYS30.note)

        val restored = InvoiceJson.dataFromJson(legacy)

        assertEquals(PaymentTerms.DAYS30, restored.paymentTerms)
        assertEquals("", restored.latePenaltyClause)
    }

    @Test
    fun `a RIB is read through its spaces and flagged only when it is the wrong length`() {
        val grouped = ShopSettings(bankRib = "011 780 0000 1234 5678 9012 34")
        assertEquals(24, grouped.bankRibDigits.length)
        assertFalse(grouped.bankRibLooksWrong)

        val short = ShopSettings(bankRib = "011 780")
        assertTrue(short.bankRibLooksWrong)

        assertFalse(ShopSettings(bankRib = "").bankRibLooksWrong)
    }
}
