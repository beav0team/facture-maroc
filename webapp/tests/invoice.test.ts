// The web-only dirham rounding.
//
// Kotlin has no equivalent: the Android documents are printed to the centime.
// The web app offers "arrondi au dirham" for shops that hand over whole
// dirhams, so the rule is pinned here and nowhere else: the centimes move into
// HT, VAT is untouched, and HT + TVA still equals TTC.

import { describe, expect, it } from 'vitest'
import { calculer, libelleTotal, type Facture } from '../src/lib/invoice'

function facture(surcharge: Partial<Facture> = {}): Facture {
  return {
    numero: 'F-20261110-0001',
    date: '10/11/2026',
    client: { nom: 'Garage Atlas' },
    lignes: [{ description: 'Huile', quantite: 1, prixUnitaire: 99.5 }],
    tauxTva: 20,
    ...surcharge,
  }
}

describe('arrondi au dirham', () => {
  it('laisse un total déjà rond intact', () => {
    const t = calculer(
      facture({
        lignes: [{ description: 'Service', quantite: 1, prixUnitaire: 120 }],
        tauxTva: 0,
        arrondiDirham: true,
      }),
    )
    expect(t.total).toBe(120)
    expect(t.totalHt).toBe(120)
    expect(t.tva).toBe(0)
  })

  it('descend les centimes dans le HT, pas dans la TVA', () => {
    const t = calculer(facture({ arrondiDirham: true }))
    // 99,50 + 19,90 = 119,40 → 119,00 ; la TVA reste 19,90.
    expect(t.total).toBe(119)
    expect(t.totalHt).toBe(99.1)
    expect(t.tva).toBe(19.9)
    expect(t.totalHt + t.tva).toBe(t.total)
    expect(t.lignesTva).toEqual([{ taux: 20, base: 99.1, montant: 19.9 }])
  })

  it('monte le HT quand le total arrondi monte', () => {
    const t = calculer(
      facture({
        lignes: [{ description: 'Service', quantite: 1, prixUnitaire: 99.6 }],
        tauxTva: 20,
        arrondiDirham: true,
      }),
    )
    // 99,60 + 19,92 = 119,52 → 120,00 ; le HT monte de 0,48, la TVA ne bouge pas.
    expect(t.total).toBe(120)
    expect(t.totalHt).toBe(100.08)
    expect(t.tva).toBe(19.92)
  })

  it('ne s’applique pas sans le drapeau', () => {
    const t = calculer(facture())
    expect(t.total).toBe(119.4)
  })
})

describe('libellé du total', () => {
  it('dit TTC quand une TVA est imprimée', () => {
    expect(libelleTotal(calculer(facture()))).toBe('Total TTC')
  })

  it('dit Total quand la TVA ne s’applique pas', () => {
    const t = calculer(facture({ tvaNonApplicable: true }))
    expect(t.lignesTvaImprimees).toEqual([])
    expect(libelleTotal(t)).toBe('Total')
  })

  it('garde Total avoir pour un avoir', () => {
    expect(libelleTotal(calculer(facture({ type: 'avoir' })))).toBe('Total avoir')
  })
})
