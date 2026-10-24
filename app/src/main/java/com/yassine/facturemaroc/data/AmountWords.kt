package com.yassine.facturemaroc.data

/**
 * French number-to-words for invoice amounts.
 *
 * Ported from Tyre Inventory. The reverse-parser trick: tests parse French back
 * into a number and compare with the source amount, plus an exhaustive sweep of
 * all 100 centime values.
 */
object AmountWords {

    private val units = arrayOf(
        "", "UN", "DEUX", "TROIS", "QUATRE", "CINQ", "SIX", "SEPT", "HUIT", "NEUF"
    )
    private val teens = arrayOf(
        "DIX", "ONZE", "DOUZE", "TREIZE", "QUATORZE", "QUINZE", "SEIZE", "DIX-SEPT",
        "DIX-HUIT", "DIX-NEUF"
    )
    private val tens = arrayOf(
        "", "", "VINGT", "TRENTE", "QUARANTE", "CINQUANTE", "SOIXANTE", "SOIXANTE",
        "QUATRE-VINGT", "QUATRE-VINGT"
    )

    fun sentence(amount: Double, documentType: DocumentType = DocumentType.FACTURE): String {
        val dirhams = Math.floor(amount).toInt()
        val centimes = Math.round((amount - dirhams) * 100).toInt()
        val dirhamsText = if (dirhams == 0) "ZÉRO" else numberToWords(dirhams)
        val centimesText = if (centimes == 0) "" else numberToWords(centimes)
        val opening = when (documentType) {
            DocumentType.DEVIS -> "ARRÊTÉ LE PRÉSENT DEVIS À LA SOMME DE :"
            DocumentType.BON_DE_COMMANDE -> "ARRÊTÉ LE PRÉSENT BON DE COMMANDE À LA SOMME DE :"
            DocumentType.AVOIR -> "ARRÊTÉ LE PRÉSENT AVOIR À LA SOMME DE :"
            DocumentType.PROFORMA -> "ARRÊTÉ LE PRÉSENT PROFORMA À LA SOMME DE :"
            DocumentType.ACOMPTE -> "ARRÊTÉ LE PRÉSENT ACOMPTE À LA SOMME DE :"
            DocumentType.REGULARISATION ->
                "ARRÊTÉE LA PRÉSENTE RÉGULARISATION À LA SOMME DE :"
            DocumentType.FACTURE, DocumentType.TICKET, DocumentType.SIMPLIFIEE,
            DocumentType.EXPORT ->
                "ARRÊTÉE LA PRÉSENTE FACTURE À LA SOMME DE :"
        }
        return "$opening $dirhamsText ${if (dirhams == 1) "DIRHAM" else "DIRHAMS"}${if (centimesText.isNotEmpty()) " ET $centimesText ${if (centimes == 1) "CENTIME" else "CENTIMES"}" else ""}."
    }

    private fun numberToWords(n: Int): String {
        if (n == 0) return ""
        if (n < 10) return units[n]
        if (n < 20) return teens[n - 10]
        if (n < 70) {
            val ten = n / 10
            val unit = n % 10
            val t = tens[ten]
            return if (unit == 1 && ten == 8) {
                // 71 is "soixante et onze"
                t.replace("SOIXANTE", "SOIXANTE ET ONZE")
            } else if (unit == 1) {
                "$t ET UN"
            } else if (unit == 0) {
                t
            } else {
                "$t-${units[unit]}"
            }
        }
        if (n < 80) {
            // 60-79: soixante-dix, soixante et onze, etc.
            val base = 60
            val remainder = n - 60
            return if (remainder == 10) {
                "SOIXANTE-DIX"
            } else if (remainder == 11) {
                "SOIXANTE ET ONZE"
            } else if (remainder < 20) {
                "SOIXANTE-${teens[remainder - 10]}"
            } else {
                "SOIXANTE-${tens[remainder / 10]}${if (remainder % 10 == 0) "" else "-${units[remainder % 10]}"}"
            }
        }
        if (n < 100) {
            // 80-99: quatre-vingts, quatre-vingt-un, etc.
            val base = 80
            val remainder = n - 80
            return if (remainder == 0) {
                "QUATRE-VINGTS"
            } else if (remainder == 1) {
                "QUATRE-VINGT-UN"
            } else if (remainder < 20) {
                "QUATRE-VINGT-${teens[remainder - 10]}"
            } else {
                val ten = remainder / 10
                val unit = remainder % 10
                val t = tens[8]
                return if (unit == 0) {
                    t
                } else if (unit == 1) {
                    "$t-UN"
                } else {
                    "$t-${units[unit]}"
                }
            }
        }
        if (n < 1000) {
            val hundred = n / 100
            val remainder = n % 100
            val h = if (hundred == 1) "CENT" else "${units[hundred]} CENT"
            return if (remainder == 0) {
                // "cent" takes an s only when it ends the number: deux cents,
                // but deux cent un. A stray space before that s turns the word
                // into three ("cent s trente"), which is what this used to do.
                if (hundred > 1) h + "S" else h
            } else {
                "$h ${numberToWords(remainder)}"
            }
        }
        if (n < 1_000_000) {
            val thousand = n / 1000
            val remainder = n % 1000
            val t = if (thousand == 1) "MILLE" else numberToWords(thousand)
            return if (remainder == 0) {
                t
            } else {
                "$t ${numberToWords(remainder)}"
            }
        }
        return numberToWords(n / 1_000_000) + " MILLION" + (if (n / 1_000_000 > 1) "S" else "") +
            (if (n % 1_000_000 == 0) "" else " ${numberToWords(n % 1_000_000)}")
    }

    private fun trim(s: String): String = s.trim().replace("\\s+".toRegex(), " ")
}
