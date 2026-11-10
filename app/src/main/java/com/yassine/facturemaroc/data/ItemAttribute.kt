package com.yassine.facturemaroc.data

/**
 * One free key/value attribute on an article, carried onto each invoice line
 * that uses it.
 *
 * Keys are neutral suggestions — Référence, Marque, Modèle, Dimensions, Lot /
 * série… — and the shop may add any key/value pair it wants. Nothing about the
 * key is industry-specific: a garage and a stationer share the same model.
 */
data class ItemAttribute(
    val key: String,
    val value: String,
)

object ItemAttributeKeys {
    /** Manufacturer's reference, e.g. `E-100`. */
    const val REFERENCE = "Référence"
    /** Brand, e.g. `Legrand`. */
    const val BRAND = "Marque"
    /** Model, e.g. `Câble 1,5 mm²`. */
    const val MODEL = "Modèle"
    /** Dimensions, e.g. `2 m × 5 mm`. */
    const val DIMENSIONS = "Dimensions"
    /** Batch / serial number, e.g. `LOT-2026-04`. */
    const val BATCH = "Lot / série"
}
