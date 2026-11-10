package com.yassine.facturemaroc.ui

import com.yassine.facturemaroc.data.Money

fun Double.trimZeros(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

/**
 * A dirham amount, in the one format the whole app uses: `1 812,60 DH`.
 *
 * Kept as the same extension signature the 42 call sites already use, so the
 * callers change nothing and the bug disappears at the source. The formatting
 * itself lives in [Money], shared with the PDF and the statements.
 */
fun Double.asDirhams(): String = Money.dirhams(this)

/** `1 812,60` without the currency, for a column that prints `DH` once. */
fun Double.asAmount(): String = Money.plain(this)
