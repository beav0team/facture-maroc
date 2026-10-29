package com.yassine.facturemaroc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `thousands keep the mille after their multiplier`() {
        // The multiplier used to be returned on its own, so 2 000 read
        // "DEUX DIRHAMS" — an invoice ten times too small in words.
        assertTrue(AmountWords.sentence(2_000.0).contains("DEUX MILLE DIRHAMS"))
        assertTrue(
            AmountWords.sentence(52_152.0)
                .contains("CINQUANTE-DEUX MILLE CENT CINQUANTE-DEUX DIRHAMS"),
        )
        assertTrue(AmountWords.sentence(80_000.0).contains("QUATRE-VINGT MILLE DIRHAMS"))
        assertTrue(AmountWords.sentence(200_000.0).contains("DEUX CENT MILLE DIRHAMS"))
    }

    @Test
    fun `cent and vingt lose their s before mille`() {
        // "mille" is a numeral adjective, so the hundreds that end the
        // multiplier stay singular: deux cent mille, quatre-vingt mille.
        assertFalse(AmountWords.sentence(200_000.0).contains("CENTS MILLE"))
        assertFalse(AmountWords.sentence(80_000.0).contains("VINGTS MILLE"))
        // ...but a hundred that ends the whole amount keeps it.
        assertTrue(AmountWords.sentence(200.0).contains("DEUX CENTS DIRHAMS"))
    }

    @Test
    fun `mille takes no s and no un`() {
        assertTrue(AmountWords.sentence(1_000.0).contains("MILLE DIRHAMS"))
        assertFalse(AmountWords.sentence(1_000.0).contains("UN MILLE"))
        assertFalse(AmountWords.sentence(2_000.0).contains("MILLES"))
    }

    @Test
    fun `a million is spelled and the rest of the amount follows it`() {
        assertTrue(
            AmountWords.sentence(1_200_000.0).contains("UN MILLION DEUX CENT MILLE DIRHAMS"),
        )
    }

    @Test
    fun `an amount that ends in a round hundred cents`() {
        // 123.00 must not print "et zéro centime", which reads as a mistake.
        val sentence = AmountWords.sentence(123.0)

        assertTrue(sentence.contains("CENT VINGT-TROIS DIRHAMS."))
        assertFalse(sentence.contains("CENTIME"))
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
