package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the payment code says, in words a customer could read.
 *
 * It is plain text and never a URL: the shop chooses the bank account, and a
 * link in a QR would be a payment instruction the app invented. These tests
 * pin the content, so a scanner always shows the same five facts.
 */
class PaymentQrTextTest {

    private val shop = ShopSettings(
        name = "Garage Atlas",
        bankName = "Attijariwafa Bank",
        bankRib = "011 780 0000 1234 5678 9012 34",
        bankHolder = "Garage Atlas SARL",
    )

    private val data = InvoiceData(
        number = "F-20261110-0007",
        date = "10/11/2026",
        client = InvoiceClient(name = "Client"),
        lines = listOf(InvoiceLine("Chaise", 2.0, 950.0)),
    )

    @Test
    fun `no RIB means no code`() {
        assertNull(PaymentQrText.of(data, ShopSettings(bankName = "Sans RIB")))
    }

    @Test
    fun `the payload carries the document, the bank, the RIB and the amount due`() {
        val text = PaymentQrText.of(data, shop)!!.normaliseSpaces()

        assertTrue(text.contains("F-20261110-0007"))
        assertTrue(text.contains("Attijariwafa Bank"))
        assertTrue(text.contains("011780000012345678901234"))
        assertTrue(text.contains("Garage Atlas SARL"))
        assertTrue(text.contains("2 280,00 DH"))
    }

    @Test
    fun `the code is plain text, never a URL`() {
        val text = PaymentQrText.of(data, shop)!!

        assertFalse(text.contains("http://"))
        assertFalse(text.contains("https://"))
        assertFalse(text.contains("www."))
    }

    @Test
    fun `a partial payment makes the balance the amount to pay`() {
        val partial = data.copy(payments = listOf(PaymentInfo(paidAmount = 1_000.0)))

        assertEquals(1_280.0, partial.balanceDue, 0.001)
        assertTrue(PaymentQrText.of(partial, shop)!!.normaliseSpaces().contains("1 280,00 DH"))
    }

    /** French formatting uses a narrow no-break space before the centimes. */
    private fun String.normaliseSpaces(): String = replace('\u202F', ' ').replace('\u00A0', ' ')

    @Test
    fun `only the RIB is enough to pay, so only it is required`() {
        val text = PaymentQrText.of(data, ShopSettings(bankRib = "011780000012345678901234"))!!

        assertTrue(text.contains("RIB : 011780000012345678901234"))
        assertFalse(text.contains("Banque :"))
    }
}
