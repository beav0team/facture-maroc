package com.yassine.facturemaroc.data

/**
 * What a scanner hands back, and what is worth keeping of it.
 *
 * Kept apart from the camera for the usual reason: the camera needs a phone and
 * a lens, this needs a string. The trimming is not cosmetic — a decoded value
 * can carry a trailing newline from a Code 128 label, and an item saved with
 * one can never be found again by typing the digits that are printed on it.
 */
object Barcode {

    /**
     * [raw] with control characters dropped and surrounding space trimmed.
     *
     * Internal spaces are kept: a Code 128 label may legitimately read
     * "AB 123", and silently gluing it into "AB123" would store a code that
     * does not match the sticker on the shelf.
     */
    fun clean(raw: String): String = raw.filter { it.code >= 32 }.trim()
}
