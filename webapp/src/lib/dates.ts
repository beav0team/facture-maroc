// Dates as the app writes them: `dd/MM/yyyy`, parsed strictly.
//
// A lenient parser turns `31/02/2026` into 3 March and a shop chases a
// customer on a date nobody wrote, so every component is checked back against
// what was constructed.

const MOTIF = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/

/** `dd/MM/yyyy` to a local date, or null when the text is not a real day. */
export function analyserDate(texte: string | null | undefined): Date | null {
  const valeur = texte?.trim() ?? ''
  if (valeur === '') return null
  const trouve = MOTIF.exec(valeur)
  if (!trouve) return null

  const jour = Number(trouve[1] ?? NaN)
  const mois = Number(trouve[2] ?? NaN)
  const annee = Number(trouve[3] ?? NaN)
  if (!Number.isInteger(jour) || !Number.isInteger(mois) || !Number.isInteger(annee)) return null

  const date = new Date(annee, mois - 1, jour)
  if (
    date.getFullYear() !== annee ||
    date.getMonth() !== mois - 1 ||
    date.getDate() !== jour
  ) {
    return null
  }
  return date
}

/** Whole days since the epoch, from the local calendar day (DST-proof). */
export function jourDepuisEpoque(date: Date): number {
  return Math.round(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / 86_400_000)
}

/**
 * Late the day *after* the deadline, never on it, and never on an avoir.
 * The time of day is ignored.
 */
export function estEnRetard(
  echeance: string | null | undefined,
  etat: 'unpaid' | 'partial' | 'paid',
  aujourdHui: Date = new Date(),
): boolean {
  if (etat === 'paid') return false
  const due = analyserDate(echeance)
  if (!due) return false
  return jourDepuisEpoque(aujourdHui) > jourDepuisEpoque(due)
}

/** Whole days until the deadline; negative once it has passed. */
export function joursRestants(
  echeance: string | null | undefined,
  aujourdHui: Date = new Date(),
): number | null {
  const due = analyserDate(echeance)
  if (!due) return null
  return jourDepuisEpoque(due) - jourDepuisEpoque(aujourdHui)
}

/** `2026-11-10` to `dd/MM/yyyy`, for fields the browser gives as ISO. */
export function versDateFr(iso: string): string {
  const trouve = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso.trim())
  if (!trouve) return ''
  return `${trouve[3]}/${trouve[2]}/${trouve[1]}`
}
