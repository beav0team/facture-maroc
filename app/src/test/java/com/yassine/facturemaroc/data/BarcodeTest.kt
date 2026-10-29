package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What is kept of a decoded code.
 *
 * Short, but the failure it prevents is permanent: an item saved with a stray
 * newline can never be found again by typing the digits printed on the box.
 */
class BarcodeTest {

    @Test
    fun `a trailing newline from the label is dropped`() {
        assertEquals("6111234500017", Barcode.clean("6111234500017\n"))
    }

    @Test
    fun `surrounding space is trimmed`() {
        assertEquals("6111234500017", Barcode.clean("  6111234500017  "))
    }

    @Test
    fun `internal spaces are kept, because a Code 128 label may carry them`() {
        assertEquals("AB 123", Barcode.clean("AB 123"))
    }

    @Test
    fun `leading zeroes survive, because they are part of the code`() {
        assertEquals("06111234500017", Barcode.clean("06111234500017"))
    }

    @Test
    fun `a value made only of control characters becomes empty`() {
        assertEquals("", Barcode.clean("\n\r\t"))
    }
}
