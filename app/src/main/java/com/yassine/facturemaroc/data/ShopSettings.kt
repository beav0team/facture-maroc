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
     * A tyre shop fills a barcode and a purchase price on every article; a
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
}
