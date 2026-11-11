// Document numbers, ported from InvoiceNumber.kt in the Android app.
//
// Shape: `<prefixe>-<yyyyMMdd>-<0000>`. The counter and its year live with the
// shop (later, a Supabase row); this module only holds the rules, so they are
// testable without a browser or a database.

import { TYPES_DOCUMENT, type TypeDocument } from './document'

/** The counter key used on the Android side: one series per document type. */
export function cleCompteur(type: TypeDocument): string {
  return `next_${type}`
}

export function prefixeParDefaut(type: TypeDocument): string {
  return TYPES_DOCUMENT[type].prefixe
}

/**
 * The printed number. A prefix already ending in `-` gets no second one; a
 * blank prefix prints the date and the sequence alone rather than a dangling
 * dash. Values below 1 print as 0001; above 9999 they keep their digits.
 */
export function numeroFormate(prefixe: string, date: Date, valeur: number): string {
  const mois = String(date.getMonth() + 1).padStart(2, '0')
  const jour = String(date.getDate()).padStart(2, '0')
  const horodatage = `${date.getFullYear()}${mois}${jour}`
  const rang = String(Math.max(1, Math.trunc(valeur))).padStart(4, '0')
  const propre = prefixe.trim().replace(/-+$/, '')
  return propre === '' ? `${horodatage}-${rang}` : `${propre}-${horodatage}-${rang}`
}

/**
 * The next value a series will get.
 *
 * A counter stamped with another year restarts at 1 when the annual reset is
 * on. A counter with no year at all is kept: an unknown year is not a year that
 * changed, and resetting it would reissue numbers on a shop that never asked.
 */
export function prochaineValeur(
  stocke: number,
  anneeStockee: number,
  anneeCourante: number,
  remiseAZero: boolean,
): number {
  if (remiseAZero && anneeStockee > 0 && anneeStockee !== anneeCourante) return 1
  return Math.max(1, Math.trunc(stocke))
}

/**
 * A prefix must stay usable as a file name: a slash would create a path.
 * Letters and digits are allowed in any script, plus `-` and `_`.
 */
export function prefixeValide(valeur: string): boolean {
  return /^[\p{L}\p{N}_-]+$/u.test(valeur)
}
