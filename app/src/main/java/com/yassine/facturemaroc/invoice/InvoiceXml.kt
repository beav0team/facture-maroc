package com.yassine.facturemaroc.invoice

import com.yassine.facturemaroc.data.DocumentType
import com.yassine.facturemaroc.data.InvoiceClient
import com.yassine.facturemaroc.data.InvoiceData
import com.yassine.facturemaroc.data.PaymentMethod
import com.yassine.facturemaroc.data.ShopSettings
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The invoice as UBL 2.1, the format the DGI has said it will accept.
 *
 * In April 2026 the DGI's director general confirmed publicly that the retained
 * format is UBL ("Le format de la facture, ce sera de l'UBL"), with a clearance
 * model in which the DGI validates a document before it reaches the customer.
 * The profiling rules — which optional elements Morocco requires, whether a
 * qualified signature is mandatory per document — are not published yet. This
 * file therefore emits the standard document as faithfully as possible and puts
 * every Moroccan-specific mention where the standard has a slot for it, so that
 * adopting the DGI profile later is a change here and nowhere else.
 *
 * Deliberate choices, each one reversible:
 *
 * - **Avoir is a `CreditNote`**, not an `Invoice` with a credit-note code. A
 *   credit note is a different legal instrument; labelling one as an invoice is
 *   worse than emitting a document an older parser refuses.
 * - **Devis and bon de commande use type code 386 (proforma)**, because they
 *   are not invoices and must never be read as an amount owed. When a true
 *   `Quote` document is needed, this is the branch to extend.
 * - **Patente and CNSS go in `CompanyLegalForm`.** UBL has no dedicated element
 *   for either, and dropping them would make the file claim less than the paper
 *   does. They are printed as free text, which is what the element is for.
 * - **No signature.** UBL has no signature slot; XAdES wraps the document from
 *   outside, and a qualified Moroccan certificate lives on a USB token that a
 *   phone cannot use. Signing is a boundary the caller owns, not this writer.
 *
 * Element order is the schema sequence, not a logical order: UBL validates as a
 * sequence, so `Note` must precede `DocumentCurrencyCode` and `AllowanceCharge`
 * must precede `TaxTotal`. Reordering for readability breaks validation.
 */
object InvoiceXml {

    const val UBL_VERSION = "2.1"

    /** Marks the profile this app writes, so a receiver can tell it apart. */
    const val CUSTOMIZATION_ID = "urn:facture-maroc:ubl:1.0"

    const val INVOICE_NAMESPACE =
        "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
    const val CREDIT_NOTE_NAMESPACE =
        "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2"
    const val CURRENCY = "MAD"
    const val COUNTRY_CODE = "MA"

    private const val CAC =
        "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
    private const val CBC =
        "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"

    fun build(data: InvoiceData, shop: ShopSettings): String {
        val isCreditNote = data.documentType == DocumentType.AVOIR
        val root = if (isCreditNote) "CreditNote" else "Invoice"
        val namespace = if (isCreditNote) CREDIT_NOTE_NAMESPACE else INVOICE_NAMESPACE
        val x = Xml()

        x.raw("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        x.open(
            "$root xmlns=\"$namespace\" xmlns:cac=\"$CAC\" xmlns:cbc=\"$CBC\"",
        )

        // --- sequence 2..22: identifiers, dates, type, notes, currency
        x.element("cbc:UBLVersionID", UBL_VERSION)
        x.element("cbc:CustomizationID", CUSTOMIZATION_ID)
        x.element("cbc:ID", data.number)
        x.element("cbc:IssueDate", isoDate(data.date))
        // A credit note has no payment due date in the UBL schema, so the date
        // is only written on the document types that have the element.
        if (!isCreditNote) {
            data.validityDate?.takeIf { it.isNotBlank() }?.let {
                // A quote's validity is the closest thing a devis has to a due date.
                x.element("cbc:DueDate", isoDate(it))
            }
        }
        x.element(
            if (isCreditNote) "cbc:CreditNoteTypeCode" else "cbc:InvoiceTypeCode",
            typeCode(data.documentType),
        )

        if (data.documentType != DocumentType.FACTURE) {
            x.element("cbc:Note", data.documentType.label)
        }
        if (data.referenceNumber.isNotBlank() && data.documentType.isReferenceDocument) {
            x.element("cbc:Note", "Annule et remplace la facture N° ${data.referenceNumber}")
        }
        if (data.incoterm.isNotBlank() && data.documentType == DocumentType.EXPORT) {
            x.element("cbc:Note", "Incoterms : ${data.incoterm}")
        }
        // The amount spelled out is the line that makes a paper document hard
        // to alter, so it travels in the file too rather than only on the PDF.
        x.element("cbc:Note", data.amountInWords)

        x.element("cbc:DocumentCurrencyCode", CURRENCY)

        // --- sequence 25: the document this one relates to
        if (data.referenceNumber.isNotBlank() && data.documentType.isReferenceDocument) {
            x.open("cac:BillingReference")
            x.open("cac:InvoiceDocumentReference")
            x.element("cbc:ID", data.referenceNumber)
            x.close("cac:InvoiceDocumentReference")
            x.close("cac:BillingReference")
        }

        // --- sequence 32..34: parties
        //
        // The shop issues the document, so it is the supplier party whichever
        // type this is. The customer party is [InvoiceData.party] and not
        // [InvoiceData.client] for the same reason the paper prints it: on a bon
        // de commande the recipient is the supplier, and a file that names the
        // client there contradicts the document it claims to be.
        x.open("cac:AccountingSupplierParty")
        party(x, shop.asClient())
        x.close("cac:AccountingSupplierParty")

        x.open("cac:AccountingCustomerParty")
        party(x, data.party)
        x.close("cac:AccountingCustomerParty")

        // --- sequence 40: how it was paid (an invoice only, as on paper)
        if (data.documentType == DocumentType.FACTURE) {
            x.open("cac:PaymentMeans")
            x.element("cbc:PaymentMeansCode", paymentMeansCode(data.paymentMethod))
            x.close("cac:PaymentMeans")
        }

        // --- sequence 43: the invoice-level discount, before the tax block
        if (data.discountPercent > 0 && data.discountAmount > 0.0) {
            allowance(x, data.discountPercent, data.discountAmount, data.subtotal)
        }

        // --- sequence 48: tax, one subtotal per rate actually applied
        //
        // A zero rate is declared exempt rather than "standard rate at 0 %". An
        // auto-entrepreneur is outside the tax, not charging a zero-rated one,
        // and a receiver that reads `S · 0 %` codes it as the second.
        x.open("cac:TaxTotal")
        x.money("cbc:TaxAmount", data.vatAmount)
        val exemptBase = round(data.totalExcludingVat - data.vatBase)
        if (data.effectiveVatPercent > 0.0 && data.vatBase > 0.0) {
            taxSubtotal(x, data.vatBase, data.vatAmount, data.effectiveVatPercent, taxable = true)
            if (exemptBase > 0.0) {
                taxSubtotal(x, exemptBase, 0.0, 0.0, taxable = false)
            }
        } else {
            taxSubtotal(x, data.totalExcludingVat, 0.0, 0.0, taxable = false)
        }
        x.close("cac:TaxTotal")

        // --- sequence 50: totals
        x.open("cac:LegalMonetaryTotal")
        x.money("cbc:LineExtensionAmount", data.subtotal)
        x.money("cbc:TaxExclusiveAmount", data.totalExcludingVat)
        x.money("cbc:TaxInclusiveAmount", data.total)
        if (data.discountAmount > 0.0) {
            x.money("cbc:AllowanceTotalAmount", data.discountAmount)
        }
        x.money("cbc:PayableAmount", data.total)
        x.close("cac:LegalMonetaryTotal")

        // --- sequence 51: lines
        val lineTag = if (isCreditNote) "cac:CreditNoteLine" else "cac:InvoiceLine"
        val quantityTag =
            if (isCreditNote) "cbc:CreditedQuantity" else "cbc:InvoicedQuantity"
        data.lines.forEachIndexed { index, line ->
            x.open(lineTag)
            x.element("cbc:ID", (index + 1).toString())
            x.element(quantityTag, line.quantity.toString(), " unitCode=\"${unitCode(line.unit)}\"")
            x.money("cbc:LineExtensionAmount", line.total)
            if (line.lineDiscountPercent > 0) {
                val gross = line.quantity * line.unitPrice
                allowance(x, line.lineDiscountPercent, gross - line.total, gross)
            }
            x.open("cac:Item")
            x.element("cbc:Name", line.description)
            x.open("cac:ClassifiedTaxCategory")
            val exempt = line.taxExempt || data.effectiveVatPercent <= 0.0
            x.element("cbc:ID", if (exempt) "E" else "S")
            x.decimal("cbc:Percent", if (exempt) 0.0 else data.effectiveVatPercent)
            x.open("cac:TaxScheme")
            x.element("cbc:ID", "VAT")
            x.close("cac:TaxScheme")
            x.close("cac:ClassifiedTaxCategory")
            x.close("cac:Item")
            x.open("cac:Price")
            x.money("cbc:PriceAmount", line.unitPrice)
            x.close("cac:Price")
            x.close(lineTag)
        }

        x.close(root)
        return x.toString()
    }

    /** A file name a shop can find again in its Downloads folder. */
    fun fileName(data: InvoiceData): String {
        val prefix = when (data.documentType) {
            DocumentType.FACTURE -> "facture"
            DocumentType.DEVIS -> "devis"
            DocumentType.BON_DE_COMMANDE -> "bon_commande"
            DocumentType.TICKET -> "ticket"
            DocumentType.AVOIR -> "avoir"
            DocumentType.PROFORMA -> "proforma"
            DocumentType.ACOMPTE -> "acompte"
            DocumentType.REGULARISATION -> "regul"
            DocumentType.SIMPLIFIEE -> "simplifiee"
            DocumentType.EXPORT -> "export"
        }
        val number = data.number.ifBlank { "document" }
        return "${prefix}_$number.xml"
    }

    /**
     * UN/CEFACT 1001 document type codes, the values a clearance platform expects.
     *
     * Devis and bon de commande are marked 386 (proforma) on purpose: they carry
     * prices but no debt, and a receiver that adds one to its ledger because it
     * looked like a 380 would be creating an invoice nobody issued.
     */
    internal fun typeCode(type: DocumentType): String = when (type) {
        DocumentType.FACTURE,
        DocumentType.SIMPLIFIEE,
        DocumentType.TICKET,
        DocumentType.EXPORT,
        DocumentType.ACOMPTE,
        -> "380"

        DocumentType.AVOIR -> "381"
        DocumentType.REGULARISATION -> "384"
        DocumentType.PROFORMA,
        DocumentType.DEVIS,
        DocumentType.BON_DE_COMMANDE,
        -> "386"
    }

    /** UN/ECE Recommendation 20 units; `C62` is "one", the safe default. */
    internal fun unitCode(unit: String): String = when (unit.trim().lowercase(Locale.FRANCE)) {
        "heure", "heures", "h" -> "HUR"
        "jour", "jours", "j" -> "DAY"
        "semaine", "semaines" -> "WEE"
        "mois" -> "MON"
        "kg", "kilo", "kilos" -> "KGM"
        "g", "gramme", "grammes" -> "GRM"
        "l", "litre", "litres" -> "LTR"
        "m", "metre", "mètre", "metres", "mètres" -> "MTR"
        "m2", "m²" -> "MTK"
        "m3", "m³" -> "MTQ"
        "km" -> "KMT"
        "forfait", "unité", "unite", "unités", "unites", "pièce", "piece", "" -> "C62"
        else -> "C62"
    }

    /** UN/CEFACT 4461 payment means codes. */
    internal fun paymentMeansCode(method: PaymentMethod): String = when (method) {
        PaymentMethod.CASH -> "10"
        PaymentMethod.CHECK -> "20"
        PaymentMethod.TRANSFER -> "30"
        PaymentMethod.CARD -> "48"
    }

    private fun party(x: Xml, who: InvoiceClient) {
        x.open("cac:Party")
        if (who.ice.isNotBlank()) {
            x.open("cac:PartyIdentification")
            x.element("cbc:ID", who.ice, " schemeID=\"ICE\"")
            x.close("cac:PartyIdentification")
        }
        if (who.name.isNotBlank()) {
            x.open("cac:PartyName")
            x.element("cbc:Name", who.name)
            x.close("cac:PartyName")
        }
        x.open("cac:PostalAddress")
        x.element("cbc:StreetName", who.address)
        x.open("cac:Country")
        x.element("cbc:IdentificationCode", COUNTRY_CODE)
        x.close("cac:Country")
        x.close("cac:PostalAddress")

        if (who.ifNumber.isNotBlank()) {
            // Morocco has no separate VAT number: the identifiant fiscal is the
            // tax identifier a VAT registration would use, so it goes in the
            // element the standard reserves for one.
            x.open("cac:PartyTaxScheme")
            x.element("cbc:CompanyID", who.ifNumber)
            x.open("cac:TaxScheme")
            x.element("cbc:ID", "VAT")
            x.close("cac:TaxScheme")
            x.close("cac:PartyTaxScheme")
        }

        // Opened only when there is something to put in it: an empty
        // PartyLegalEntity is schema-legal but says nothing, and a customer
        // typed in as a walk-in sale has no legal identity to state.
        if (who.name.isNotBlank() || who.rc.isNotBlank() ||
            who.patente.isNotBlank() || who.cnss.isNotBlank()
        ) {
            x.open("cac:PartyLegalEntity")
            x.element("cbc:RegistrationName", who.name)
            if (who.rc.isNotBlank()) {
                x.element("cbc:CompanyID", who.rc, " schemeID=\"RC\"")
            }
            val extra = listOfNotNull(
                who.patente.takeIf { it.isNotBlank() }?.let { "Patente $it" },
                who.cnss.takeIf { it.isNotBlank() }?.let { "CNSS $it" },
            )
            if (extra.isNotEmpty()) {
                x.element("cbc:CompanyLegalForm", extra.joinToString(" · "))
            }
            x.close("cac:PartyLegalEntity")
        }

        if (who.phone.isNotBlank() || who.email.isNotBlank()) {
            x.open("cac:Contact")
            x.element("cbc:Telephone", who.phone)
            x.element("cbc:ElectronicMail", who.email)
            x.close("cac:Contact")
        }
        x.close("cac:Party")
    }

    private fun taxSubtotal(
        x: Xml,
        base: Double,
        tax: Double,
        percent: Double,
        taxable: Boolean,
    ) {
        x.open("cac:TaxSubtotal")
        x.money("cbc:TaxableAmount", base)
        x.money("cbc:TaxAmount", tax)
        x.open("cac:TaxCategory")
        x.element("cbc:ID", if (taxable) "S" else "E")
        x.decimal("cbc:Percent", if (taxable) percent else 0.0)
        x.open("cac:TaxScheme")
        x.element("cbc:ID", "VAT")
        x.close("cac:TaxScheme")
        x.close("cac:TaxCategory")
        x.close("cac:TaxSubtotal")
    }

    private fun allowance(
        x: Xml,
        percent: Int,
        amount: Double,
        base: Double,
    ) {
        x.open("cac:AllowanceCharge")
        x.element("cbc:ChargeIndicator", "false")
        x.element("cbc:AllowanceChargeReason", "Remise")
        x.element("cbc:MultiplierFactorNumeric", formatMoney(percent / 100.0))
        x.money("cbc:Amount", amount)
        x.money("cbc:BaseAmount", base)
        x.close("cac:AllowanceCharge")
    }

    private fun formatMoney(value: Double): String = String.format(
        Locale.US,
        "%.2f",
        if (value.isNaN() || value.isInfinite()) 0.0 else value,
    )

    private fun round(value: Double): Double = Math.round(value * 100.0) / 100.0

    private fun isoDate(frenchDate: String): String = runCatching {
        val input = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).apply { isLenient = false }
        val output = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        output.format(input.parse(frenchDate)!!)
    }.getOrDefault(frenchDate)

    /** Escapes text so an ampersand in a client's name cannot break the file. */
    internal fun escape(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(char)
            }
        }
    }

    /** Indenting writer: UBL is read by machines but debugged by people. */
    private class Xml {
        private val builder = StringBuilder(2048)
        private var depth = 0

        fun raw(text: String) {
            builder.append(text).append('\n')
        }

        fun open(tag: String) {
            indent()
            builder.append('<').append(tag).append(">\n")
            depth++
        }

        fun close(tag: String) {
            depth--
            indent()
            builder.append("</").append(tag).append(">\n")
        }

        fun element(tag: String, value: String, attributes: String = "") {
            val text = value.trim()
            if (text.isEmpty()) return
            indent()
            builder.append('<').append(tag).append(attributes).append('>')
            builder.append(escape(text))
            builder.append("</").append(tag).append(">\n")
        }

        fun money(tag: String, value: Double) {
            indent()
            builder.append('<').append(tag).append(" currencyID=\"")
            builder.append(CURRENCY).append("\">")
            builder.append(formatMoney(value))
            builder.append("</").append(tag).append(">\n")
        }

        /**
         * A plain decimal, no `currencyID`.
         *
         * `cbc:Percent` and friends are `PercentType`, which has no attributes
         * at all: writing a currency on them is a schema violation, and a
         * validator rejects the whole document for it.
         */
        fun decimal(tag: String, value: Double) {
            indent()
            builder.append('<').append(tag).append('>')
            builder.append(formatMoney(value))
            builder.append("</").append(tag).append(">\n")
        }

        private fun indent() {
            repeat(depth) { builder.append("  ") }
        }

        override fun toString(): String = builder.toString()
    }
}

/** The shop, seen as a party to the document. */
private fun ShopSettings.asClient(): InvoiceClient = InvoiceClient(
    name = name,
    address = address,
    ice = ice,
    ifNumber = ifNumber,
    rc = rc,
    patente = patente,
    cnss = cnss,
    vatExempt = false,
)
