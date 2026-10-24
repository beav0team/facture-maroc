package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountWordsTest {

    @Test
    fun `a whole amount ends without a centime part`() {
        assertEquals(
            "ARRÊTÉE LA PRÉSENTE FACTURE À LA SOMME DE : NEUF CENT TRENTE-SIX DIRHAMS.",
            AmountWords.sentence(936.0),
        )
    }

    @Test
    fun `one dirham is singular`() {
        assertTrue(AmountWords.sentence(1.0).endsWith("UN DIRHAM."))
    }

    @Test
    fun `zero is written out, not left blank`() {
        assertTrue(AmountWords.sentence(0.0).contains("ZÉRO DIRHAMS"))
    }

    @Test
    fun `centimes are spelled after the dirhams`() {
        assertTrue(AmountWords.sentence(12.5).contains("DOUZE DIRHAMS ET CINQUANTE CENTIMES"))
    }

    @Test
    fun `cent stays singular when another number follows`() {
        assertTrue(AmountWords.sentence(201.0).contains("DEUX CENT UN DIRHAMS"))
    }

    @Test
    fun `cent is plural only when it ends the number`() {
        assertTrue(AmountWords.sentence(200.0).contains("DEUX CENTS DIRHAMS"))
    }

    @Test
    fun `eighty is plural alone and singular before a unit`() {
        assertTrue(AmountWords.sentence(80.0).contains("QUATRE-VINGTS DIRHAMS"))
        assertTrue(AmountWords.sentence(81.0).contains("QUATRE-VINGT-UN DIRHAMS"))
    }

    @Test
    fun `seventy-one keeps its et`() {
        assertTrue(AmountWords.sentence(71.0).contains("SOIXANTE ET ONZE DIRHAMS"))
    }

    @Test
    fun `a quote is arrested as a quote, not as an invoice`() {
        val sentence = AmountWords.sentence(100.0, DocumentType.DEVIS)

        assertTrue(sentence.startsWith("ARRÊTÉ LE PRÉSENT DEVIS"))
    }

    @Test
    fun `an order form is arrested as an order form`() {
        val sentence = AmountWords.sentence(100.0, DocumentType.BON_DE_COMMANDE)

        assertTrue(sentence.startsWith("ARRÊTÉ LE PRÉSENT BON DE COMMANDE"))
    }

    @Test
    fun `a credit note is arrested as a credit note`() {
        val sentence = AmountWords.sentence(100.0, DocumentType.AVOIR)

        assertTrue(sentence.startsWith("ARRÊTÉ LE PRÉSENT AVOIR"))
    }

    @Test
    fun `a proforma is arrested as a proforma`() {
        val sentence = AmountWords.sentence(100.0, DocumentType.PROFORMA)

        assertTrue(sentence.startsWith("ARRÊTÉ LE PRÉSENT PROFORMA"))
    }

    @Test
    fun `a down payment is arrested as a down payment`() {
        val sentence = AmountWords.sentence(100.0, DocumentType.ACOMPTE)

        assertTrue(sentence.startsWith("ARRÊTÉ LE PRÉSENT ACOMPTE"))
    }

    @Test
    fun `an adjustment is arrested as an adjustment`() {
        val sentence = AmountWords.sentence(100.0, DocumentType.REGULARISATION)

        assertTrue(sentence.startsWith("ARRÊTÉE LA PRÉSENTE RÉGULARISATION"))
    }

    @Test
    fun `a simplified invoice stays in the invoice family`() {
        val sentence = AmountWords.sentence(100.0, DocumentType.SIMPLIFIEE)

        assertTrue(sentence.startsWith("ARRÊTÉE LA PRÉSENTE FACTURE"))
    }

    @Test
    fun `an export invoice stays in the invoice family`() {
        val sentence = AmountWords.sentence(100.0, DocumentType.EXPORT)

        assertTrue(sentence.startsWith("ARRÊTÉE LA PRÉSENTE FACTURE"))
    }
}
