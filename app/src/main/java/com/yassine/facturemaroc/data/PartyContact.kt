package com.yassine.facturemaroc.data

/**
 * What a stored phone number turns into when the shop taps it.
 *
 * Numbers are typed the way they are said — `06 12 34 56 78`, `+212 612 34 56
 * 78`, `00212612345678` — and three of those are the same phone. Nothing in the
 * app stored a canonical form, so a `tel:` link built from the raw field would
 * have dialled a number the phone does not recognise, and `wa.me` would have
 * opened an empty chat.
 *
 * Deliberately free of `Intent`: the URIs are strings, so the rules are tested
 * on the JVM and only the launching lives in the UI.
 */
object PartyContact {

    /** The country this app is sold in; the only one with a local-dialling rule. */
    private const val MOROCCO = "212"

    /** Shortest thing worth dialling: an extension is not a contact. */
    private const val MIN_DIGITS = 6

    /**
     * `06 12 34 56 78` → `+212612345678`; null when there is nothing dialable.
     *
     * A leading `+` or `00` is an international number and is respected as
     * typed: a Moroccan shop buys from Spain, and forcing `+212` onto a foreign
     * supplier's mobile would dial a stranger.
     */
    fun toE164(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length < MIN_DIGITS) return null

        return when {
            hasPlus -> "+$digits"
            digits.startsWith("00") -> "+${digits.drop(2)}".takeIf { it.length > MIN_DIGITS }
            // 0612345678 — the form every shop writes on a receipt.
            digits.length == 10 && digits.startsWith("0") -> "+$MOROCCO${digits.drop(1)}"
            // 612345678 — the same number with the trunk prefix dropped.
            digits.length == 9 && (digits.startsWith("6") || digits.startsWith("7")) ->
                "+$MOROCCO$digits"
            // 522112233 — a Casablanca landline, or anything else local.
            digits.length == 9 -> "+$MOROCCO$digits"
            else -> "+$digits"
        }
    }

    /** `tel:+212612345678`, or null when the field holds no usable number. */
    fun dialUri(raw: String): String? = toE164(raw)?.let { "tel:$it" }

    /** `https://wa.me/212612345678`, or null. WhatsApp takes no leading plus. */
    fun whatsAppUri(raw: String): String? =
        toE164(raw)?.removePrefix("+")?.let { "https://wa.me/$it" }

    /** Whether the contact actions are worth drawing at all. */
    fun hasNumber(raw: String): Boolean = toE164(raw) != null
}
