package com.yassine.facturemaroc.data

/** Who the invoice is for. A saved client, or one typed in for this sale only. */
data class InvoiceClient(
    val name: String,
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
    val phone: String = "",
    val email: String = "",
    /** True for clients exempt from TVA (export, certain gov bodies). */
    val vatExempt: Boolean = false,
) {
    /** Legal ID fields that are non-empty, joined for printing. */
    val legalIdentifierList: List<String>
        get() = listOf(ice, ifNumber, rc, patente, cnss).filter { it.isNotBlank() }
}

/** How the invoice was paid. Printed on the document, never tracked. */
enum class PaymentMethod(val id: String, val french: String) {
    CASH(id = "cash", french = "Espèces"),
    CHECK(id = "check", french = "Chèque"),
    TRANSFER(id = "transfer", french = "Virement bancaire"),
    CARD(id = "card", french = "Carte bancaire"),
    ;

    companion object {
        fun fromId(id: String?): PaymentMethod = entries.firstOrNull { it.id == id } ?: CASH
    }
}

/** What kind of document this is. Each has its own numbering and layout rules. */
enum class DocumentType(val id: String, val label: String, val prefix: String) {
    FACTURE("facture", "Facture", "F"),
    DEVIS("devis", "Devis", "D"),
    BON_DE_COMMANDE("bon_commande", "Bon de commande", "BC"),
    TICKET("ticket", "Ticket (80mm)", "T"),
    /** Avoir: cancels and replaces an earlier invoice. */
    AVOIR("avoir", "Avoir", "A"),
    /** Proforma: estimate with no accounting value. */
    PROFORMA("proforma", "Proforma", "PF"),
    /** Acompte: down payment on a sale to come. */
    ACOMPTE("acompte", "Acompte", "AC"),
    /** Régularisation: adjusts an earlier invoice. */
    REGULARISATION("regul", "Régularisation", "REG"),
    /** Facture simplifiée: simplified mentions for small retail sales. */
    SIMPLIFIEE("simplifiee", "Simplifiée", "FS"),
    /** Facture à l'export: export sale, Incoterms optional. */
    EXPORT("export", "Export", "E"),
    ;

    /** Documents that carry a reference to the invoice they relate to (avoir,
     *  régularisation, acompte). */
    val isReferenceDocument: Boolean
        get() = this == AVOIR || this == REGULARISATION || this == ACOMPTE

    /** Documents whose amount is billed (excludes quotes, orders and drafts). */
    companion object {
        fun fromId(id: String?): DocumentType = entries.firstOrNull { it.id == id } ?: FACTURE
    }
}
