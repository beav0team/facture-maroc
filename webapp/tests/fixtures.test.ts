// Replays the shared fixtures through the TypeScript engine.
//
// `fixtures/factures.json` is written by the Kotlin engine (FixtureTest): the
// expected blocks are what InvoiceData actually computes, never hand figures.
// If this file fails, the web app prints a different number than the Android
// document for the same invoice — that is the divergence this test exists to
// catch.

import { describe, expect, it } from 'vitest'
import factures from '../../fixtures/factures.json'
import numerotation from '../../fixtures/numerotation.json'
import { calculer, type Facture, type Totaux } from '../src/lib/invoice'
import { numeroFormate, prochaineValeur } from '../src/lib/numbering'

type CasFacture = {
  nom: string
  facture: Record<string, unknown>
  attendu: Record<string, unknown>
}

function nombre(valeur: unknown, defaut: number): number {
  return typeof valeur === 'number' ? valeur : defaut
}

function lireFacture(brut: Record<string, unknown>): Facture {
  const lignes = Array.isArray(brut.lignes) ? (brut.lignes as Record<string, unknown>[]) : []
  const paiements = Array.isArray(brut.paiements) ? (brut.paiements as number[]) : []

  return {
    numero: 'F-20261110-0001',
    date: '10/11/2026',
    client: { nom: 'Client', exonereTva: Boolean(brut.clientExonere) },
    lignes: lignes.map((ligne) => ({
      description: String(ligne.description ?? ''),
      quantite: nombre(ligne.quantite, 0),
      prixUnitaire: nombre(ligne.prixUnitaire, 0),
      remisePourcent: nombre(ligne.remisePourcent, 0),
      remiseMontant: nombre(ligne.remiseMontant, 0),
      exoneree: Boolean(ligne.exoneree),
      tauxTva: typeof ligne.tauxTva === 'number' ? ligne.tauxTva : null,
    })),
    remisePourcent: nombre(brut.remisePourcent, 0),
    remiseMontant: nombre(brut.remiseMontant, 0),
    tauxTva: nombre(brut.tauxTva, 20),
    type: (brut.type as Facture['type']) ?? 'facture',
    echeance: typeof brut.echeance === 'string' ? brut.echeance : null,
    tvaNonApplicable: Boolean(brut.tvaNonApplicable),
    surchargeMontant: nombre(brut.surchargeMontant, 0),
    surchargePourcent: nombre(brut.surchargePourcent, 0),
    acompte: nombre(brut.acompte, 0),
    paiements: paiements.map((montant) => ({ montant })),
  }
}

/** The same shape FixtureTest writes, so the comparison is field by field. */
function attenduDe(t: Totaux): Record<string, unknown> {
  return {
    sousTotal: t.sousTotal,
    remise: t.remise,
    remiseEnMontant: t.remiseEnMontant,
    afficheRemise: t.afficheRemise,
    totalHt: t.totalHt,
    surcharge: t.surcharge,
    tva: t.tva,
    total: t.total,
    baseTva: t.baseTva,
    tauxTvaEffectif: t.tauxTvaEffectif,
    tauxSurchargeTva: t.tauxSurchargeTva,
    lignesTva: t.lignesTva,
    lignesTvaImprimees: t.lignesTvaImprimees,
    acompteApplique: t.acompte,
    netAPayer: t.netAPayer,
    paye: t.paye,
    reste: t.reste,
    etatPaiement: t.etatPaiement,
    estSoldee: t.estSoldee,
    estAvoir: t.estAvoir,
    echeanceImprimee: t.echeanceImprimee,
  }
}

describe('fixtures du moteur Kotlin', () => {
  for (const cas of factures.cas as CasFacture[]) {
    it(cas.nom, () => {
      const f = lireFacture(cas.facture)
      expect(attenduDe(calculer(f))).toEqual(cas.attendu)
    })
  }
})

describe('numérotation', () => {
  const dateDe = (iso: string): Date => {
    const [annee = 1970, mois = 1, jour = 1] = iso.split('-').map(Number)
    return new Date(annee, mois - 1, jour)
  }

  for (const cas of numerotation.format) {
    it(`formate ${JSON.stringify(cas.prefixe)} le ${cas.date} → ${cas.attendu}`, () => {
      expect(numeroFormate(cas.prefixe, dateDe(cas.date), cas.valeur)).toBe(cas.attendu)
    })
  }

  for (const cas of numerotation.prochain) {
    it(`prochain ${cas.stocke}/${cas.anneeStockee} en ${cas.anneeCourante} remise=${cas.remiseAZero} → ${cas.attendu}`, () => {
      expect(
        prochaineValeur(cas.stocke, cas.anneeStockee, cas.anneeCourante, cas.remiseAZero),
      ).toBe(cas.attendu)
    })
  }
})
