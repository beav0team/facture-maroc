package com.yassine.facturemaroc.data

import java.util.Locale

/**
 * The one way this app writes a dirham amount.
 *
 * Before this existed, three formatters disagreed: the list printed
 * `3432 DH` (an integer shortcut), the builder printed `1812.60 DH` (English
 * separators) and the PDF printed `1 812,60 DH`. The same invoice showed a
 * different number on screen than on the paper the client signed.
 *
 * Rules, applied everywhere:
 *  - comma decimal separator, always two decimals;
 *  - narrow no-break space for thousands, so a line never breaks inside an
 *    amount;
 *  - the separator is normalised by hand rather than read from the platform,
 *    because Android's ICU data and the JVM disagree about whether the French
 *    grouping mark is the narrow or the regular no-break space.
 */
object Money {
    private const val NARROW_NO_BREAK_SPACE = '\u202F'
    private const val NO_BREAK_SPACE = '\u00A0'

    /** `1 812,60` — group-separated, no currency. */
    fun plain(value: Double): String =
        String.format(Locale.FRANCE, "%,.2f", finite(value))
            .replace(NO_BREAK_SPACE, NARROW_NO_BREAK_SPACE)
            .replace(' ', NARROW_NO_BREAK_SPACE)

    /** `1 812,60 DH` — what the screen and the paper both print. */
    fun dirhams(value: Double): String = plain(value) + " DH"

    /** `1 812,60` with a sign kept only when negative: `−812,60`. */
    fun signedPlain(value: Double): String =
        if (value < 0) "\u2212" + plain(-value) else plain(value)

    private fun finite(value: Double): Double =
        if (value.isNaN() || value.isInfinite()) 0.0 else value
}
