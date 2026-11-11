// The free generator's draft, carried across the sign-up.
//
// A visitor who fills the public generator and then creates an account should
// not retype the invoice. The draft is kept in `localStorage` — local to their
// browser, never sent — and consumed once by the editor. The payload is small
// and deliberately partial: names, rates and lines, no identifiers.

import { analyserMontant } from './money'

export const CLE_BROUILLON_PUBLIC = 'factureapp-brouillon-public'

export type LignePublique = { description: string; quantite: number; prixUnitaire: number }

export type BrouillonPublic = {
  boutique: string
  client: string
  taux: number
  lignes: LignePublique[]
}

type Stockage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

function stockageDisponible(): Stockage | null {
  try {
    if (typeof localStorage === 'undefined') return null
    return localStorage
  } catch {
    return null
  }
}

export function memoriserBrouillon(brouillon: BrouillonPublic, stockage: Stockage | null = stockageDisponible()): void {
  if (!stockage) return
  try {
    stockage.setItem(CLE_BROUILLON_PUBLIC, JSON.stringify(brouillon))
  } catch {
    // A full or blocked storage must not stop the visitor from signing up.
  }
}

export function oublierBrouillon(stockage: Stockage | null = stockageDisponible()): void {
  if (!stockage) return
  try {
    stockage.removeItem(CLE_BROUILLON_PUBLIC)
  } catch {
    // Nothing to do: the draft is a convenience, not data the shop owns.
  }
}

/** Reads and validates the handoff, then removes it so it is not applied twice. */
export function reprendreBrouillon(stockage: Stockage | null = stockageDisponible()): BrouillonPublic | null {
  if (!stockage) return null
  let brut: string | null = null
  try {
    brut = stockage.getItem(CLE_BROUILLON_PUBLIC)
  } catch {
    return null
  }
  if (!brut) return null

  try {
    const lu = JSON.parse(brut) as Record<string, unknown>
    const lignes = Array.isArray(lu.lignes) ? lu.lignes : []
    const propres: LignePublique[] = []
    for (const valeur of lignes) {
      if (valeur === null || typeof valeur !== 'object') continue
      const ligne = valeur as Record<string, unknown>
      const description = typeof ligne.description === 'string' ? ligne.description.trim() : ''
      const quantite = analyserMontant(String(ligne.quantite ?? ''))
      const prix = analyserMontant(String(ligne.prixUnitaire ?? ''))
      if (description === '' || quantite === null || quantite <= 0 || prix === null || prix < 0) continue
      propres.push({ description, quantite, prixUnitaire: prix })
    }
    if (propres.length === 0) {
      oublierBrouillon(stockage)
      return null
    }

    const taux = analyserMontant(String(lu.taux ?? '')) ?? 20
    const brouillon: BrouillonPublic = {
      boutique: typeof lu.boutique === 'string' ? lu.boutique.trim() : '',
      client: typeof lu.client === 'string' ? lu.client.trim() : '',
      taux: taux >= 0 && taux <= 100 ? taux : 20,
      lignes: propres,
    }
    oublierBrouillon(stockage)
    return brouillon
  } catch {
    oublierBrouillon(stockage)
    return null
  }
}
