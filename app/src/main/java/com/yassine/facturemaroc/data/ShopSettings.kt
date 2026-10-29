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
    /** Prochain numéro de facture utilisé par la numérotation automatique. 1 par défaut. */
    val invoiceCounter: Int = 1,
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
