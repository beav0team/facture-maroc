package com.yassine.facturemaroc.data

/** The shop's own details, printed on every invoice. */
data class ShopSettings(
    val name: String = "",
    val address: String = "",
    /** How a customer reaches the shop — printed under the address. */
    val phone: String = "",
    val email: String = "",
    /** Identifiant Commun de l'Entreprise — required on a B2B invoice. */
    val ice: String = "",
    /** Identifiant Fiscal — Moroccan tax ID. */
    val ifNumber: String = "",
    /** Registre de Commerce — commercial registry. */
    val rc: String = "",
    /** Patente — business license. */
    val patente: String = "",
    /** CNSS — social security. */
    val cnss: String = "",
    /**
     * The bank the customer pays into, printed with the payment details.
     *
     * Empty fields print nothing: a shop that takes cash has no RIB to show,
     * and a blank "Banque :" line would look like a form that was not filled.
     */
    val bankName: String = "",
    /** The 24-digit Moroccan RIB. Validated with a warning, never blocked. */
    val bankRib: String = "",
    /** The account holder, when it is not simply the shop's name. */
    val bankHolder: String = "",
    /**
     * The legal late-payment clause, printed under the totals on a document
     * that is not paid at the counter.
     *
     * The default is a short model clause, not legal advice: Settings asks the
     * shop to have it checked by its accountant. The shop can clear it, and a
     * cleared clause prints nothing at all rather than an empty heading.
     */
    val latePenaltyClause: String = DEFAULT_LATE_PENALTY_CLAUSE,
    /**
     * How the exported PDF is named. Placeholders: `{number}`, `{client}`,
     * `{date}` and `{type}`. The extension is always `.pdf`.
     */
    val pdfFileNamePattern: String = DEFAULT_PDF_PATTERN,
    /** Shop logo bytes, stored in filesDir and mapped in prefs. */
    val logoBytes: ByteArray? = null,
    /** Default TVA rate applied after discount. Morocco's standard is 20. */
    val vatPercent: Double = 20.0,
    /** Auto-entrepreneur: TVA non applicable, le taux saisi est ignoré. */
    val vatNotApplicable: Boolean = false,
    /**
     * True when the shop quotes its prices TTC — TVA already inside them.
     *
     * Moroccan shops quote both ways: a garage usually says "150 DH, TVA
     * comprise", a wholesaler says "125 DH HT". The document is always written
     * HT + TVA, because that is what an invoice has to show; this flag only
     * says how the number typed on a line should be read.
     */
    val pricesIncludeVat: Boolean = false,
    /*
     * Which optional fields the shop's articles show.
     *
     * A shop that sells goods fills a barcode and a purchase price on every article; a
     * plumber selling three services fills neither, and asking both of them for
     * the same seven fields is what made the article form feel like data entry.
     * These switch the *form* and the *card*, never the data: hiding a field
     * keeps whatever is already stored on the article, and turning it back on
     * shows it again.
     *
     * All true by default, so an installation that predates these settings sees
     * exactly the app it had.
     */
    val showItemBarcode: Boolean = true,
    val showItemPurchasePrice: Boolean = true,
    val showItemUnit: Boolean = true,
    val showItemStock: Boolean = true,
    val showItemFolder: Boolean = true,
    /**
     * How stock is valued and how a sale's cost is derived.
     *
     * A setting, not a per-document choice: the shop values its shelf one way,
     * and a document that used a different method from the one on the next line
     * would make the two margins incomparable. Changing it affects future
     * movements only — an issued line carries the cost frozen on it.
     */
    val costMethod: CostMethod = CostMethod.DEFAULT,
) {
    /** Legal ID fields that are non-empty, joined for printing. */
    val legalIdentifierList: List<String>
        get() = listOf(ice, ifNumber, rc, patente, cnss).filter { it.isNotBlank() }

    /** Phone and email, whichever are filled, for the line under the address. */
    val contactList: List<String>
        get() = listOf(phone, email).filter { it.isNotBlank() }

    /** The identifiers with their names in front, the way an invoice prints them. */
    val legalIdentifierLabels: List<String>
        get() = listOf(
            "ICE" to ice,
            "IF" to ifNumber,
            "RC" to rc,
            "Patente" to patente,
            "CNSS" to cnss,
        ).filter { it.second.isNotBlank() }
            .map { (label, value) -> "$label : ${value.trim()}" }

    /** Whether there is a bank account to print at all. */
    val hasBankDetails: Boolean
        get() = bankRib.isNotBlank() || bankName.isNotBlank() || bankHolder.isNotBlank()

    /**
     * The RIB digits only, for the 24-digit check.
     *
     * Shops write the RIB in groups of four with spaces, and a validation that
     * counted the spaces would reject every real one.
     */
    val bankRibDigits: String
        get() = bankRib.filter { it.isDigit() }

    /** Whether the RIB is missing or does not look like a Moroccan RIB. */
    val bankRibLooksWrong: Boolean
        get() = bankRibDigits.isNotEmpty() && bankRibDigits.length != 24

    companion object {
        /**
         * A short model clause in French. Deliberately makes no claim about a
         * rate or an amount: those depend on the contract and on the law in
         * force, and inventing a figure here would be inventing legal text.
         */
        const val DEFAULT_LATE_PENALTY_CLAUSE: String =
            "Passé le délai de paiement, des intérêts de retard au taux légal " +
                "ainsi qu'une indemnité forfaitaire pour frais de recouvrement " +
                "seront dus, conformément à la réglementation en vigueur."

        const val DEFAULT_PDF_PATTERN: String = "facture_{number}"
    }
}
