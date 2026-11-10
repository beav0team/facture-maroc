package com.yassine.facturemaroc.invoice

import com.yassine.facturemaroc.data.InvoiceData
import com.yassine.facturemaroc.data.PaymentDue

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
    INVALID_SURCHARGE,
    MISSING_DOCUMENT_NUMBER,
    TOTAL_MISMATCH,
    INVALID_LINE_VAT_RATE,
    INVALID_ICE,
    MISSING_ICE,
    INVALID_DUE_DATE,
    DUE_DATE_BEFORE_ISSUE,
    INVALID_DEPOSIT,
    DEPOSIT_ABOVE_TOTAL,
}

/** Whether an issue stops the document leaving the phone. */
enum class Severity {
    /** The document would be wrong, or a shop would be breaking the law. */
    BLOCKING,

    /** Legally required information that is missing, but not a miscalculation. */
    WARNING,
}

data class ValidationIssue(
    val code: ValidationCode,
    val message: String,
    val severity: Severity = Severity.BLOCKING,
)

object InvoiceValidation {

    fun validate(data: InvoiceData): List<ValidationIssue> = buildList {
        if (data.lines.isEmpty()) {
            add(
                ValidationIssue(
                    ValidationCode.NO_LINES,
                    "Ajoutez au moins un article.",
                    severity = Severity.WARNING,
                ),
            )
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
            if (line.lineDiscountFixed < 0.0 || line.lineDiscountFixed.isNaN() ||
                line.lineDiscountFixed.isInfinite()
            ) {
                add(
                    ValidationIssue(
                        ValidationCode.DISCOUNT_OUT_OF_RANGE,
                        "Ligne $position : la remise en dirhams n'est pas un montant valide.",
                    ),
                )
            }
            val rate = line.vatPercent
            if (rate != null && (rate < 0.0 || rate > 100.0 || rate.isNaN())) {
                add(
                    ValidationIssue(
                        ValidationCode.INVALID_LINE_VAT_RATE,
                        "Ligne $position : le taux de TVA doit être compris entre 0 et 100 %.",
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

        data.dueDatePrinted?.let { due ->
            val parsed = PaymentDue.parse(due)
            if (parsed == null) {
                add(
                    ValidationIssue(
                        ValidationCode.INVALID_DUE_DATE,
                        "L'échéance n'est pas une date valide (jj/mm/aaaa).",
                    ),
                )
            } else {
                // A deadline before the document exists is not a deadline, it
                // is a typo — and one that would show up as "late" on the day
                // the customer receives the paper.
                val issued = PaymentDue.parse(data.date)
                if (issued != null && parsed.before(issued)) {
                    add(
                        ValidationIssue(
                            ValidationCode.DUE_DATE_BEFORE_ISSUE,
                            "L'échéance est antérieure à la date du document.",
                            severity = Severity.WARNING,
                        ),
                    )
                }
            }
        }

        if (data.depositAmount < 0.0 || data.depositAmount.isNaN() ||
            data.depositAmount.isInfinite()
        ) {
            add(
                ValidationIssue(
                    ValidationCode.INVALID_DEPOSIT,
                    "L'acompte déduit n'est pas un montant valide.",
                ),
            )
        } else if (data.depositAmount > data.total) {
            // Not blocking: the shop may be typing while the lines are still
            // being added, and the deposit is clamped on the page either way.
            add(
                ValidationIssue(
                    ValidationCode.DEPOSIT_ABOVE_TOTAL,
                    "L'acompte dépasse le total : il sera plafonné au total.",
                    severity = Severity.WARNING,
                ),
            )
        }

        if (data.discountFixed < 0.0 || data.discountFixed.isNaN() || data.discountFixed.isInfinite()) {
            add(
                ValidationIssue(
                    ValidationCode.DISCOUNT_OUT_OF_RANGE,
                    "La remise générale en dirhams n'est pas un montant valide.",
                ),
            )
        }

        if (data.surchargePercent < 0.0 || data.surchargePercent > 100.0 ||
            data.surchargePercent.isNaN()
        ) {
            add(
                ValidationIssue(
                    ValidationCode.INVALID_SURCHARGE,
                    "Le pourcentage de surcharge doit être compris entre 0 et 100 %.",
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

        if (data.surchargeAmount < 0.0 || data.surchargeAmount.isNaN() ||
            data.surchargeAmount.isInfinite()
        ) {
            add(
                ValidationIssue(
                    ValidationCode.INVALID_SURCHARGE,
                    "La surcharge doit être un montant positif.",
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

        // --- the client's ICE, which a business invoice cannot do without
        //
        // Both are warnings rather than blockers: a shop at the counter must not
        // be stopped from printing a sale because the buyer has not sent its ICE
        // yet. The number is still worth checking, because a 13-digit ICE is a
        // typo that the tax office will find later.
        val party = data.party
        val ice = party.ice.filter { it.isDigit() }
        val iceLooksWrong = party.ice.isNotBlank() &&
            (ice.length != 15 || party.ice.any { !it.isDigit() && !it.isWhitespace() })
        if (iceLooksWrong) {
            add(
                ValidationIssue(
                    ValidationCode.INVALID_ICE,
                    "L'ICE doit comporter 15 chiffres.",
                    severity = Severity.WARNING,
                ),
            )
        }
        val partyIsBusiness = party.ifNumber.isNotBlank() || party.rc.isNotBlank() ||
            party.patente.isNotBlank() || party.cnss.isNotBlank()
        if (party.ice.isBlank() && partyIsBusiness) {
            add(
                ValidationIssue(
                    ValidationCode.MISSING_ICE,
                    "Le client a un IF, un RC ou une patente mais pas d'ICE : " +
                        "l'ICE est obligatoire sur une facture entre entreprises.",
                    severity = Severity.WARNING,
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
        // that was edited, or a formula that drifted. Per line, because a
        // document can now carry several rates.
        val exactSubtotal = data.lines.sumOf { it.total }
        val exactDiscount = if (data.discountFixed > 0.0) {
            data.discountFixed.coerceAtMost(exactSubtotal).coerceAtLeast(0.0)
        } else {
            exactSubtotal * data.discountPercent.coerceIn(0, 100) / 100.0
        }
        val keep = if (exactSubtotal <= 0.0) 1.0 else (exactSubtotal - exactDiscount) / exactSubtotal
        val nets = mutableMapOf<Double, Double>()
        data.lines.forEach { line ->
            val rate = data.rateOf(line)
            nets[rate] = (nets[rate] ?: 0.0) + line.total * keep
        }
        val exactSurcharge = if (data.surchargePercent > 0.0) {
            (exactSubtotal - exactDiscount).coerceAtLeast(0.0) *
                data.surchargePercent.coerceIn(0.0, 100.0) / 100.0
        } else {
            data.surchargeAmount.coerceAtLeast(0.0)
        }
        val exactVat = nets.entries.sumOf { (rate, net) -> net * rate / 100.0 } +
            exactSurcharge * data.surchargeVatPercent / 100.0
        val recomputedTotal = round(exactSubtotal - exactDiscount + exactSurcharge + exactVat)

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
