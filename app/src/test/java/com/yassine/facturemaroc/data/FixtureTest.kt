package com.yassine.facturemaroc.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

/**
 * Replays the JSON files in `fixtures/` through the Kotlin engine.
 *
 * The expected blocks are never written by hand: on a missing block this test
 * writes what the engine computed and fails, asking to be run again. That keeps
 * the Kotlin engine the reference the TypeScript port is measured against —
 * `webapp/tests/fixtures.test.ts` reads the same files.
 */
class FixtureTest {

    private val racine: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) {
        it.parentFile
    }.firstOrNull { File(it, "fixtures/factures.json").isFile }
        ?: error("dossier fixtures introuvable depuis ${System.getProperty("user.dir")}")

    @Test
    fun `les factures des fixtures correspondent au moteur`() {
        val fichier = File(racine, "fixtures/factures.json")
        val racineJson = JSONObject(fichier.readText())
        val cas = racineJson.getJSONArray("cas")
        var genere = false

        for (i in 0 until cas.length()) {
            val c = cas.getJSONObject(i)
            val nom = c.getString("nom")
            val data = lireFacture(c.getJSONObject("facture"))
            if (!c.has("attendu")) {
                c.put("attendu", attenduDe(data))
                genere = true
            } else {
                verifier(nom, attenduDe(data), c.getJSONObject("attendu"), "")
            }
        }

        if (genere) {
            fichier.writeText(racineJson.toString(2) + "\n")
            fail("Valeurs attendues générées dans fixtures/factures.json — relancer le test.")
        }
    }

    @Test
    fun `la numerotation des fixtures correspond au moteur`() {
        val fichier = File(racine, "fixtures/numerotation.json")
        val racineJson = JSONObject(fichier.readText())
        var genere = false

        val formats = racineJson.getJSONArray("format")
        for (i in 0 until formats.length()) {
            val c = formats.getJSONObject(i)
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .apply { isLenient = false }
                .parse(c.getString("date"))!!
            val obtenu = InvoiceNumber.format(c.getString("prefixe"), date, c.getInt("valeur"))
            if (!c.has("attendu")) {
                c.put("attendu", obtenu)
                genere = true
            } else if (c.getString("attendu") != obtenu) {
                fail("format[$i] : attendu ${c.getString("attendu")}, obtenu $obtenu")
            }
        }

        val prochains = racineJson.getJSONArray("prochain")
        for (i in 0 until prochains.length()) {
            val c = prochains.getJSONObject(i)
            val obtenu = InvoiceNumber.effectiveNext(
                stored = c.getInt("stocke"),
                storedYear = c.getInt("anneeStockee"),
                currentYear = c.getInt("anneeCourante"),
                resetAnnually = c.getBoolean("remiseAZero"),
            )
            if (!c.has("attendu")) {
                c.put("attendu", obtenu)
                genere = true
            } else if (c.getInt("attendu") != obtenu) {
                fail("prochain[$i] : attendu ${c.getInt("attendu")}, obtenu $obtenu")
            }
        }

        if (genere) {
            fichier.writeText(racineJson.toString(2) + "\n")
            fail("Valeurs attendues générées dans fixtures/numerotation.json — relancer le test.")
        }
    }

    // ---------------------------------------------------------------- entrée

    private fun lireFacture(o: JSONObject): InvoiceData {
        val lignesJson = o.optJSONArray("lignes") ?: JSONArray()
        val lignes = (0 until lignesJson.length()).map { i ->
            val l = lignesJson.getJSONObject(i)
            InvoiceLine(
                description = l.getString("description"),
                quantity = l.getDouble("quantite"),
                unitPrice = l.getDouble("prixUnitaire"),
                lineDiscountPercent = l.optInt("remisePourcent", 0),
                lineDiscountFixed = l.optDouble("remiseMontant", 0.0),
                taxExempt = l.optBoolean("exoneree", false),
                vatPercent = if (l.has("tauxTva") && !l.isNull("tauxTva")) {
                    l.getDouble("tauxTva")
                } else {
                    null
                },
            )
        }

        val paiementsJson = o.optJSONArray("paiements") ?: JSONArray()
        val paiements = (0 until paiementsJson.length()).map { i ->
            PaymentInfo(paidAmount = paiementsJson.getDouble(i))
        }

        return InvoiceData(
            number = "F-20261110-0001",
            date = "10/11/2026",
            client = InvoiceClient(name = "Client", vatExempt = o.optBoolean("clientExonere", false)),
            lines = lignes,
            discountPercent = o.optInt("remisePourcent", 0),
            discountFixed = o.optDouble("remiseMontant", 0.0),
            vatPercent = o.optDouble("tauxTva", 20.0),
            documentType = DocumentType.fromId(o.optString("type", "facture")),
            dueDate = if (o.has("echeance")) o.getString("echeance") else null,
            vatNotApplicable = o.optBoolean("tvaNonApplicable", false),
            surchargeAmount = o.optDouble("surchargeMontant", 0.0),
            surchargePercent = o.optDouble("surchargePourcent", 0.0),
            depositAmount = o.optDouble("acompte", 0.0),
            payments = paiements,
        )
    }

    // -------------------------------------------------------------- sortie

    private fun attenduDe(d: InvoiceData): JSONObject = JSONObject().apply {
        put("sousTotal", d.subtotal)
        put("remise", d.discountAmount)
        put("remiseEnMontant", d.discountInAmount)
        put("afficheRemise", d.showsDiscount)
        put("totalHt", d.totalExcludingVat)
        put("surcharge", d.surcharge)
        put("tva", d.vatAmount)
        put("total", d.total)
        put("baseTva", d.vatBase)
        put("tauxTvaEffectif", d.effectiveVatPercent)
        put("tauxSurchargeTva", d.surchargeVatPercent)
        put("lignesTva", recap(d.vatBreakdown))
        put("lignesTvaImprimees", recap(d.vatPrintRows))
        put("acompteApplique", d.deposit)
        put("netAPayer", d.netToPay)
        put("paye", d.paidAmount)
        put("reste", d.balanceDue)
        put("etatPaiement", d.paymentState.id)
        put("estSoldee", d.isSettled)
        put("estAvoir", d.isCredit)
        put("echeanceImprimee", d.dueDatePrinted ?: JSONObject.NULL)
    }

    private fun recap(rows: List<VatRecap>): JSONArray = JSONArray().apply {
        rows.forEach { row ->
            put(JSONObject().put("taux", row.rate).put("base", row.base).put("montant", row.amount))
        }
    }

    // ----------------------------------------------------------- comparaison

    private fun verifier(nom: String, reel: JSONObject, attendu: JSONObject, prefixe: String) {
        val cles = attendu.keys()
        while (cles.hasNext()) {
            val cle = cles.next()
            comparer("$nom / $prefixe$cle", attendu.get(cle), reel.get(cle))
        }
    }

    private fun comparer(chemin: String, voulu: Any?, obtenu: Any?) {
        when {
            voulu is JSONArray && obtenu is JSONArray -> {
                if (voulu.length() != obtenu.length()) {
                    fail("$chemin : ${voulu.length()} attendus, ${obtenu.length()} obtenus")
                }
                for (i in 0 until voulu.length()) {
                    comparer("$chemin[$i]", voulu.get(i), obtenu.get(i))
                }
            }
            voulu is JSONObject && obtenu is JSONObject -> {
                val cles = voulu.keys()
                while (cles.hasNext()) {
                    val cle = cles.next()
                    comparer("$chemin.$cle", voulu.get(cle), obtenu.get(cle))
                }
            }
            else -> if (!sameValue(voulu, obtenu)) {
                fail("$chemin : attendu $voulu, obtenu $obtenu")
            }
        }
    }

    private fun sameValue(a: Any?, b: Any?): Boolean = when {
        a is Number && b is Number -> abs(a.toDouble() - b.toDouble()) < 1e-9
        else -> a == b
    }
}
