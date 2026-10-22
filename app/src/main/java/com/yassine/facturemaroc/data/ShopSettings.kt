package com.yassine.facturemaroc.data

/** The shop's own details, printed on every invoice. */
data class ShopSettings(
    val name: String = "",
    val address: String = "",
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
    /** Prochain numéro de facture utilisé par la numérotation automatique. 1 par défaut. */
    val invoiceCounter: Int = 1,
) {
    /** Legal ID fields that are non-empty, joined for printing. */
    val legalIdentifierList: List<String>
        get() = listOf(ice, ifNumber, rc, patente, cnss).filter { it.isNotBlank() }
}
