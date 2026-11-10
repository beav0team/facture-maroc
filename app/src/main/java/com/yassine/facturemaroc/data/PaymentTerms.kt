package com.yassine.facturemaroc.data

/**
 * How a document is to be paid, as a structure rather than only as prose.
 *
 * The note printed under the totals used to be free text, which meant the app
 * could not tell a cash sale from a 60-day one — and the legal late-payment
 * clause must not land on an invoice that was paid at the counter. A preset
 * fills [note] so the shop still reads a sentence on the paper, but the stored
 * value is the code: an invoice written before this existed comes back as
 * [CUSTOM] with its text untouched.
 */
enum class PaymentTerms(val id: String, val label: String) {
    CASH("cash", "Comptant"),
    DAYS30("30j", "30 jours"),
    DAYS60("60j", "60 jours"),
    END_OF_MONTH("fin_de_mois", "Fin de mois"),
    CUSTOM("custom", "Texte libre"),
    ;

    /**
     * The sentence a preset writes into the note.
     *
     * Empty for [CUSTOM]: the shop's own words are never replaced by a preset.
     */
    val note: String
        get() = when (this) {
            CASH -> "Paiement comptant à la livraison."
            DAYS30 -> "Paiement à 30 jours date de facture."
            DAYS60 -> "Paiement à 60 jours date de facture."
            END_OF_MONTH -> "Paiement en fin de mois."
            CUSTOM -> ""
        }

    /** True for the presets that describe a deferred payment. */
    val isDeferred: Boolean get() = this == DAYS30 || this == DAYS60 || this == END_OF_MONTH

    companion object {
        fun fromId(id: String?): PaymentTerms =
            entries.firstOrNull { it.id == id } ?: CUSTOM

        /**
         * The preset whose sentence is exactly [note], or [CUSTOM].
         *
         * Used to preselect a chip when a document is reopened. An edited
         * sentence no longer matches, and that is the point: the shop's words
         * are not a preset any more.
         */
        fun fromNote(note: String): PaymentTerms {
            val text = note.trim()
            if (text.isEmpty()) return CUSTOM
            return entries.firstOrNull { it.note.isNotEmpty() && it.note == text } ?: CUSTOM
        }
    }
}
