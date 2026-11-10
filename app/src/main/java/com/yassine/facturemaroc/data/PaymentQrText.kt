package com.yassine.facturemaroc.data

import java.util.Locale

/**
 * The text a payment QR carries, built here and nowhere else.
 *
 * Deliberately plain text — bank, RIB, amount, document number — and never a
 * URL or a link to a payment page. The shop owns what the QR says, and a code
 * that pointed at some address would be a payment instruction the app invented
 * rather than one the shop chose.
 */
object PaymentQrText {

    /** Money the way an invoice writes it, without pulling in the PDF code. */
    private fun money(value: Double): String =
        String.format(Locale.FRANCE, "%,.2f", value) + " DH"

    /**
     * The payload, or null when there is no RIB to scan.
     *
     * A QR without an account to pay into is a code that tells the customer
     * nothing, so the absence of a RIB removes it rather than printing a
     * half-filled one.
     */
    fun of(data: InvoiceData, shop: ShopSettings): String? {
        val rib = shop.bankRibDigits
        if (rib.isEmpty()) return null
        return buildList {
            add("Règlement ${data.documentType.label.lowercase()} n° ${data.number}")
            if (shop.bankName.isNotBlank()) add("Banque : ${shop.bankName.trim()}")
            add("RIB : $rib")
            if (shop.bankHolder.isNotBlank()) add("Titulaire : ${shop.bankHolder.trim()}")
            add("Montant dû : ${money(data.balanceDue)}")
        }.joinToString("\n")
    }
}
