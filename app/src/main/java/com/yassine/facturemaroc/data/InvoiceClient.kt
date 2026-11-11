package com.yassine.facturemaroc.data

/**
 * Whether a saved party sells to the shop or buys from it.
 *
 * The same record shape is used for both because they are the same thing to a
 * printer — a name, an address and a set of legal numbers — and because a shop
 * with two unrelated address books would type the same garage in twice. The
 * role is what tells the pickers apart: a client list offered when invoicing,
 * a supplier list offered on a bon de commande.
 */
enum class PartyRole(val id: String, val label: String, val plural: String) {
    CLIENT("client", "Client", "Clients"),
    SUPPLIER("supplier", "Fournisseur", "Fournisseurs"),
    ;

    companion object {
        fun fromId(id: String?): PartyRole = entries.firstOrNull { it.id == id } ?: CLIENT
    }
}

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
    /**
     * Client or supplier. Written on the record rather than inferred from where
     * it is used, so a supplier picked for a bon de commande is still a
     * supplier in the directory afterwards.
     */
    val role: PartyRole = PartyRole.CLIENT,

    /**
     * Stable identity, so a rename keeps the history.
     *
     * Empty on a party typed before ids existed, and on a document that names a
     * client nobody saved; the name is then still the key, which is how every
     * document issued before 0.12 is matched. The store assigns one on save.
     */
    val id: String = "",
) {
    /** Legal ID fields that are non-empty, joined for printing. */
    val legalIdentifierList: List<String>
        get() = listOf(ice, ifNumber, rc, patente, cnss).filter { it.isNotBlank() }

    /**
     * The same identifiers with their names in front: `ICE : 001234567000025`.
     *
     * Printed as labels because a bare `001234567000025` is a number nobody can
     * identify, and a customer querying an invoice cannot tell whether it is an
     * ICE or a phone number. Same order everywhere, so two documents from the
     * same shop agree.
     */
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

/**
 * Whether this document can be corrected by an avoir.
 *
 * Same rule as the history: only a document that sold something can be
 * credited. Crediting an avoir cancels the cancellation, crediting a devis
 * refunds a price nobody paid, and a bon de commande is money going out.
 */
val DocumentType.canBeCredited: Boolean
    get() = this == DocumentType.FACTURE ||
        this == DocumentType.TICKET ||
        this == DocumentType.SIMPLIFIEE ||
        this == DocumentType.EXPORT

/**
 * Whether money can be received against this document.
 *
 * Only a document that actually asks to be paid: a devis is a price offered,
 * a bon de commande is something the shop is buying, a proforma has no
 * accounting value, and an avoir is money going back to the customer. Recording
 * a payment against any of them turns a quote into a sale that never happened.
 *
 * Acompte and régularisation are excluded on purpose too: an acompte is paid
 * when it is issued (the deposit is carried by the final invoice), and a
 * régularisation corrects another document that already carries the payment.
 */
val DocumentType.canBePaid: Boolean
    get() = this == DocumentType.FACTURE ||
        this == DocumentType.TICKET ||
        this == DocumentType.SIMPLIFIEE ||
        this == DocumentType.EXPORT

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
