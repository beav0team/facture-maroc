package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyContactTest {

    @Test
    fun `a number written the way a receipt shows it becomes international`() {
        assertEquals("+212612345678", PartyContact.toE164("06 12 34 56 78"))
        assertEquals("+212612345678", PartyContact.toE164("0612345678"))
        assertEquals("+212612345678", PartyContact.toE164("06.12.34.56.78"))
        assertEquals("+212612345678", PartyContact.toE164("06-12-34-56-78"))
    }

    @Test
    fun `an international number is respected as typed`() {
        assertEquals("+212612345678", PartyContact.toE164("+212 612 345 678"))
        assertEquals("+212612345678", PartyContact.toE164("00212612345678"))
        // A supplier in Spain keeps his own country code: the app must not
        // rewrite him into a Moroccan number.
        assertEquals("+34612345678", PartyContact.toE164("+34 612 34 56 78"))
    }

    @Test
    fun `the trunk prefix can be left out`() {
        assertEquals("+212612345678", PartyContact.toE164("612345678"))
        assertEquals("+212712345678", PartyContact.toE164("712 345 678"))
    }

    @Test
    fun `a landline is a number too`() {
        assertEquals("+212522112233", PartyContact.toE164("05 22 11 22 33"))
        assertEquals("+212522112233", PartyContact.toE164("522112233"))
    }

    @Test
    fun `nothing usable yields nothing`() {
        assertNull(PartyContact.toE164(""))
        assertNull(PartyContact.toE164("   "))
        assertNull(PartyContact.toE164("12345"))
        assertNull(PartyContact.toE164("pas de numéro"))
        assertFalse(PartyContact.hasNumber("—"))
    }

    @Test
    fun `the dialler and WhatsApp get different shapes`() {
        assertEquals("tel:+212612345678", PartyContact.dialUri("06 12 34 56 78"))
        assertEquals("https://wa.me/212612345678", PartyContact.whatsAppUri("06 12 34 56 78"))
        assertNull(PartyContact.dialUri(""))
        assertNull(PartyContact.whatsAppUri(""))
    }

    @Test
    fun `a stored number makes the actions worth drawing`() {
        assertTrue(PartyContact.hasNumber("0612345678"))
    }
}
