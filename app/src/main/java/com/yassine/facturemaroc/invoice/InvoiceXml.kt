package com.yassine.facturemaroc.invoice

import com.yassine.facturemaroc.data.DocumentType
import com.yassine.facturemaroc.data.InvoiceClient
import com.yassine.facturemaroc.data.InvoiceData
import com.yassine.facturemaroc.data.ShopSettings
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The invoice as a machine-readable document.
 *
 * Morocco's DGI e-invoicing format is not publicly frozen yet, so this is a
 * plain, documented schema rather than a guess at theirs: a stable namespace,
 * one element per field that already prints on the paper, and totals repeated
 * exactly as they appear. When the official schema is published, the mapping
 * is one file to change — nothing else in the app reads this format.
 *
 * Decimals are written with a dot, dates as ISO-8601, and every text node is
 * escaped, so the file opens in any accounting tool without a manual fix.
 */
object InvoiceXml {

    const val NAMESPACE = "urn:facture-maroc:invoice:1.0"
    const val VERSION = "1.0"

    fun build(data: InvoiceData, shop: ShopSettings): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<Facture xmlns=\"")
        append(NAMESPACE)
        append("\" version=\"")
        append(VERSION)
        append("\" type=\"")
        append(escape(data.documentType.id))
        append("\" devise=\"MAD\" lang=\"fr\">\n")

        append("  <Entete>\n")
        element("Numero", data.number, indent = 4)
        element("TypeDocument", data.documentType.label, indent = 4)
        element("Date", isoDate(data.date), indent = 4)
        data.validityDate?.takeIf { it.isNotBlank() }?.let {
            element("ValableJusquau", isoDate(it), indent = 4)
        }
        append("  </Entete>\n")

        append("  <Prestataire>\n")
        element("Nom", shop.name, indent = 4)
        element("Adresse", shop.address, indent = 4)
        element("ICE", shop.ice, indent = 4)
        element("IdentifiantFiscal", shop.ifNumber, indent = 4)
        element("RegistreCommerce", shop.rc, indent = 4)
        element("Patente", shop.patente, indent = 4)
        element("CNSS", shop.cnss, indent = 4)
        append("  </Prestataire>\n")

        appendClient("Client", data.client)
        data.supplier?.let { appendClient("Fournisseur", it) }

        append("  <Lignes>\n")
        data.lines.forEachIndexed { index, line ->
            append("    <Ligne numero=\"")
            append(index + 1)
            append("\">\n")
            element("Designation", line.description, indent = 6)
            element("Unite", line.unit, indent = 6)
            element("Quantite", line.quantity.toString(), indent = 6)
            element("PrixUnitaireHT", money(line.unitPrice), indent = 6)
            element("RemisePourcent", line.lineDiscountPercent.toString(), indent = 6)
            element("MontantHT", money(line.total), indent = 6)
            element("ExonereeTVA", line.taxExempt.toString(), indent = 6)
            append("    </Ligne>\n")
        }
        append("  </Lignes>\n")

        append("  <Totaux>\n")
        element("SousTotalHT", money(data.subtotal), indent = 4)
        element("RemiseGlobalePourcent", data.discountPercent.toString(), indent = 4)
        element("RemiseGlobaleMontant", money(data.discountAmount), indent = 4)
        element("BaseTVA", money(data.vatBase), indent = 4)
        element("TauxTVA", money(data.effectiveVatPercent), indent = 4)
        element("MontantTVA", money(data.vatAmount), indent = 4)
        element("TotalHT", money(data.totalExcludingVat), indent = 4)
        element("TotalTTC", money(data.total), indent = 4)
        append("  </Totaux>\n")

        data.referenceNumber.takeIf { it.isNotBlank() }?.let {
            append("  <ReferenceFacture>Annule et remplace la facture N° $it</ReferenceFacture>\n")
        }
        data.incoterm.takeIf { it.isNotBlank() }?.let {
            append("  <Incoterms>$it</Incoterms>\n")
        }

        if (data.documentType == DocumentType.FACTURE) {
            append("  <Paiement>\n")
            element("Mode", data.paymentMethod.french, indent = 4)
            append("  </Paiement>\n")
        }

        element("MontantEnLettres", data.amountInWords, indent = 2)
        append("</Facture>\n")
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

    private fun StringBuilder.appendClient(tag: String, client: InvoiceClient) {
        append("  <$tag>\n")
        element("Nom", client.name, indent = 4)
        element("Adresse", client.address, indent = 4)
        element("ICE", client.ice, indent = 4)
        element("IdentifiantFiscal", client.ifNumber, indent = 4)
        element("RegistreCommerce", client.rc, indent = 4)
        element("Patente", client.patente, indent = 4)
        element("CNSS", client.cnss, indent = 4)
        element("Telephone", client.phone, indent = 4)
        element("Email", client.email, indent = 4)
        element("ExonereTVA", client.vatExempt.toString(), indent = 4)
        append("  </$tag>\n")
    }

    private fun StringBuilder.element(name: String, value: String, indent: Int) {
        if (value.isBlank()) return
        append(" ".repeat(indent))
        append("<").append(name).append(">")
        append(escape(value))
        append("</").append(name).append(">\n")
    }

    private fun money(value: Double): String =
        String.format(Locale.US, "%.2f", if (value.isNaN() || value.isInfinite()) 0.0 else value)

    private fun isoDate(frenchDate: String): String = runCatching {
        val input = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).apply { isLenient = false }
        val output = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        output.format(input.parse(frenchDate)!!)
    }.getOrDefault(frenchDate)

    /** Escapes text so an ampersand in a client's name cannot break the file. */
    fun escape(value: String): String = buildString(value.length) {
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
}
