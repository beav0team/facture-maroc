package com.yassine.facturemaroc.ui

fun Double.trimZeros(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

fun Double.asDirhams(): String =
    if (this == Math.floor(this) && !this.isInfinite()) "${toLong()} DH"
    else String.format(java.util.Locale.US, "%.2f DH", this)
