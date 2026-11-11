// The handoff from the free generator to a new account.
//
// It is a convenience kept in the visitor's own browser: it must never throw,
// must be consumed once, and must drop anything that is not a usable line.

import { describe, expect, it } from 'vitest'
import {
  CLE_BROUILLON_PUBLIC,
  memoriserBrouillon,
  oublierBrouillon,
  reprendreBrouillon,
} from '../src/lib/brouillonPublic'

function stockage(initial: Record<string, string> = {}) {
  const donnees = new Map(Object.entries(initial))
  return {
    donnees,
    getItem: (cle: string) => donnees.get(cle) ?? null,
    setItem: (cle: string, valeur: string) => void donnees.set(cle, valeur),
    removeItem: (cle: string) => void donnees.delete(cle),
  }
}

describe('brouillon public', () => {
  it('rend le brouillon une seule fois', () => {
    const s = stockage()
    memoriserBrouillon(
      {
        boutique: 'Atelier',
        client: 'Garage',
        taux: 10,
        lignes: [{ description: 'Vidange', quantite: 1, prixUnitaire: 150 }],
      },
      s,
    )
    const premier = reprendreBrouillon(s)
    expect(premier).toEqual({
      boutique: 'Atelier',
      client: 'Garage',
      taux: 10,
      lignes: [{ description: 'Vidange', quantite: 1, prixUnitaire: 150 }],
    })
    expect(reprendreBrouillon(s)).toBeNull()
    expect(s.donnees.has(CLE_BROUILLON_PUBLIC)).toBe(false)
  })

  it('écarte les lignes vides ou impossibles', () => {
    const s = stockage({
      [CLE_BROUILLON_PUBLIC]: JSON.stringify({
        client: 'Client',
        taux: 20,
        lignes: [
          { description: '', quantite: 1, prixUnitaire: 10 },
          { description: 'Service', quantite: 0, prixUnitaire: 10 },
          { description: 'Produit', quantite: '2', prixUnitaire: '12,50' },
        ],
      }),
    })
    const brouillon = reprendreBrouillon(s)
    expect(brouillon?.lignes).toEqual([{ description: 'Produit', quantite: 2, prixUnitaire: 12.5 }])
  })

  it('refuse un JSON abîmé sans lever', () => {
    const s = stockage({ [CLE_BROUILLON_PUBLIC]: '{pas du json' })
    expect(reprendreBrouillon(s)).toBeNull()
    expect(s.donnees.has(CLE_BROUILLON_PUBLIC)).toBe(false)
  })

  it('ne fait rien quand le stockage est absent', () => {
    expect(() => memoriserBrouillon({ boutique: '', client: '', taux: 20, lignes: [] }, null)).not.toThrow()
    expect(reprendreBrouillon(null)).toBeNull()
    expect(() => oublierBrouillon(null)).not.toThrow()
  })
})
