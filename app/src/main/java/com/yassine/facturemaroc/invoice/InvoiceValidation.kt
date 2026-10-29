package com.yassine.facturemaroc.invoice

import com.yassine.facturemaroc.data.InvoiceData

/**
 * What would make this document wrong, stated before it is sent.
 *
 * Every rule here is one a shop can actually trip: a line with no name, a
 * price typed with a stray minus, a total that no longer matches its lines.
 * The arithmetic lives on [InvoiceData], so the check re-adds the lines from
 * scratch instead of trusting the property it is testing.
 */
enum class ValidationCode {
    NO_LINES,
    LINE_WITHOUT_DESCRIPTION,
    NEGATIVE_QUANTITY,
    INVALID_PRICE,
    DISCOUNT_OUT_OF_RANGE,
    VAT_RATE_OUT_OF_RANGE,
    MISSING_DOCUMENT_NUMBER,
    TOTAL_MISMATCH,
}

data class ValidationIssue(
    val code: ValidationCode,
    val message: String,
)

object InvoiceValidation {

    fun validate(data: InvoiceData): List<ValidationIssue> = buildList {
        if (data.lines.isEmpty()) {
            add(ValidationIssue(ValidationCode.NO_LINES, "Ajoutez au moins un article."))
        }

        data.lines.forEachIndexed { index, line ->
            val position = index + 1
            if (line.description.isBlank()) {
                add(
                    ValidationIssue(
                        ValidationCode.LINE_WITHOUT_DESCRIPTION,
                        "Ligne $position : la désignation est vide.",
                    ),
                )
            }
            if (line.quantity <= 0) {
                add(
                    ValidationIssue(
                        ValidationCode.NEGATIVE_QUANTITY,
                        "Ligne $position : la quantité doit être supérieure à zéro.",
                    ),
                )
            }
            if (line.unitPrice < 0.0 || line.unitPrice.isNaN() || line.unitPrice.isInfinite()) {
                add(
                    ValidationIssue(
                        ValidationCode.INVALID_PRICE,
                        "Ligne $position : le prix n'est pas un montant valide.",
                    ),
                )
            }
            if (line.lineDiscountPercent !in 0..100) {
                add(
                    ValidationIssue(
                        ValidationCode.DISCOUNT_OUT_OF_RANGE,
                        "Ligne $position : la remise doit être comprise entre 0 et 100 %.",
                    ),
                )
            }
        }

        if (data.discountPercent !in 0..100) {
            add(
                ValidationIssue(
                    ValidationCode.DISCOUNT_OUT_OF_RANGE,
                    "La remise générale doit être comprise entre 0 et 100 %.",
                ),
            )
        }

        if (data.vatPercent < 0.0 || data.vatPercent > 100.0 || data.vatPercent.isNaN()) {
            add(
                ValidationIssue(
                    ValidationCode.VAT_RATE_OUT_OF_RANGE,
                    "Le taux de TVA doit être compris entre 0 et 100 %.",
                ),
            )
        }

        if (data.number.isBlank()) {
            add(
                ValidationIssue(
                    ValidationCode.MISSING_DOCUMENT_NUMBER,
                    "Le document n'a pas de numéro.",
                ),
            )
        }

        // Re-add the lines the way the printed page does: each line rounded to
        // the centime, then summed. If this disagrees with the stored total, one
        // of them is lying and the document must not leave the phone.
        val recomputedSubtotal = round(data.lines.sumOf { round(it.total) })

        // The total, rebuilt from the lines rather than from the figures it is
        // made of: `totalExcludingVat + vatAmount` is true by construction and
        // could never fail. This repeats [InvoiceData]'s arithmetic on purpose —
        // a second reading of the same lines is the only way to catch a total
        // that was edited, or a formula that drifted.
        val exactSubtotal = data.lines.sumOf { it.total }
        val exactTaxable = data.lines.filterNot { it.taxExempt }.sumOf { it.total }
        val exactDiscount = exactSubtotal * data.discountPercent.coerceIn(0, 100) / 100.0
        val taxableNet = if (exactSubtotal == 0.0) {
            0.0
        } else {
            exactTaxable - exactDiscount * (exactTaxable / exactSubtotal)
        }
        val recomputedTotal = round(
            exactSubtotal - exactDiscount +
                taxableNet * data.effectiveVatPercent.coerceAtLeast(0.0) / 100.0,
        )

        if (!nearlyEqual(recomputedSubtotal, data.subtotal) || !nearlyEqual(recomputedTotal, data.total)) {
            add(
                ValidationIssue(
                    ValidationCode.TOTAL_MISMATCH,
                    "Le total ne correspond pas à la somme des lignes. Vérifiez les remises.",
                ),
            )
        }
    }

    private fun round(value: Double): Double = Math.round(value * 100.0) / 100.0

    private fun nearlyEqual(a: Double, b: Double): Boolean = Math.abs(a - b) < 0.005
}
