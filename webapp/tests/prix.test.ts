// Price bases, ported from PriceBasis.kt.
//
// The invariant this file protects: a shop that quotes "99,99 DH TTC" must get
// 99,99 back once the tax is removed and added again, however the buyer is
// taxed. Rounding the unit price at the door breaks that by a centime.

import { describe, expect, it } from 'vitest'
import { avecExoneration, avecTva, avecTaux, horsTva, reevaluerLignesTtc } from '../src/lib/prix'
import type { Ligne } from '../src/lib/invoice'

function ligne(partiel: Partial<Ligne> = {}): Ligne {
  return { description: 'Service', quantite: 1, prixUnitaire: 100, ...partiel }
}

describe('bases de prix', () => {
  it('retire la TVA sans arrondir le prix unitaire', () => {
    expect(horsTva(99.99, 20)).toBeCloseTo(83.325, 6)
    expect(avecTva(horsTva(99.99, 20), 20)).toBe(99.99)
  })

  it('un taux nul ne divise pas', () => {
    expect(horsTva(150, 0)).toBe(150)
    expect(avecTva(150, 0)).toBe(150)
  })

  it('relit une ligne TTC à 10 %', () => {
    const [relue] = reevaluerLignesTtc([ligne({ prixTtc: 110, prixUnitaire: horsTva(110, 20) })], 10)
    expect(relue?.prixUnitaire).toBeCloseTo(100, 6)
  })

  it('une ligne HT ne bouge pas quand le client change', () => {
    const [relue] = reevaluerLignesTtc([ligne({ prixUnitaire: 125, prixTtc: null })], 20)
    expect(relue?.prixUnitaire).toBe(125)
  })

  it('exonérer garde le prix TTC tapé', () => {
    const relue = avecExoneration(ligne({ prixUnitaire: 125, prixTtc: 150 }), true, 20)
    expect(relue.exoneree).toBe(true)
    expect(relue.prixUnitaire).toBe(150)
  })

  it('changer le taux d’une ligne TTC garde le prix payé', () => {
    const relue = avecTaux(ligne({ prixUnitaire: 100, prixTtc: 120 }), 10)
    expect(relue.tauxTva).toBe(10)
    expect(relue.prixUnitaire).toBeCloseTo(109.0909, 4)
  })
})
