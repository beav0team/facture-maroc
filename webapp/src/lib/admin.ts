// Platform console, kept free of Svelte and Supabase so the formatting and the
// rules can be tested. One place for the numbers the admin reads.

export type BoutiqueAdmin = {
  id: string
  nom: string
  cree_le: string
  proprietaire: string | null
  proprietaire_id: string | null
  plan: 'gratuit' | 'pro'
  jusqu_au: string | null
  note: string
  documents: number
  clients: number
  articles: number
  pro: boolean
}

export type UtilisateurAdmin = {
  id: string
  email: string | null
  cree_le: string
  derniere_connexion: string | null
  boutique: string | null
  boutique_id: string | null
  est_admin: boolean
}

export type StatistiquesAdmin = {
  boutiques: number
  utilisateurs: number
  administrateurs: number
  pro: number
  documents: number
  documents_emis: number
  archives: number
  octets_archives: number
}

/** `2027-11-11` or a full timestamp → `11/11/2027`; empty input → `—`. */
export function dateCourte(valeur: string | null | undefined): string {
  if (!valeur) return '—'
  const jour = valeur.slice(0, 10)
  const morceaux = jour.split('-')
  if (morceaux.length !== 3) return jour
  return `${morceaux[2]}/${morceaux[1]}/${morceaux[0]}`
}

/** Local date, never UTC: a shop activating at 00:30 must not get yesterday. */
export function isoLocal(date: Date = new Date()): string {
  const deux = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${deux(date.getMonth() + 1)}-${deux(date.getDate())}`
}

/** The end date offered by « Pro 1 an », one year from today. */
export function finDansUnAn(depuis: Date = new Date()): string {
  return isoLocal(new Date(depuis.getFullYear() + 1, depuis.getMonth(), depuis.getDate()))
}

/**
 * A Pro plan needs a real end date: `boutique_est_pro()` reads a null
 * `paid_until` as "Pro forever", so an empty field would give the plan away
 * permanently. Same rule as `admin_abonnement()` in 0005, checked here first so
 * the admin does not have to wait for the database to say it.
 */
export function validerAbonnement(
  plan: 'gratuit' | 'pro',
  jusquAu: string | null,
  aujourdHui: string = isoLocal(),
): string | null {
  if (plan === 'gratuit') return null
  if (!jusquAu) return 'Choisissez une date de fin pour le plan Pro.'
  if (jusquAu < aujourdHui) return 'Cette date de fin est déjà passée.'
  return null
}

/** `1234` → `1,2 ko`. French decimals, a space before the unit. */
export function octets(valeur: number | null | undefined): string {
  const n = Math.max(0, Math.round(valeur ?? 0))
  if (n < 1000) return `${n} o`
  const unites = ['ko', 'Mo', 'Go', 'To']
  let reste = n / 1000
  let i = 0
  while (reste >= 1000 && i < unites.length - 1) {
    reste /= 1000
    i += 1
  }
  const texte = reste >= 100 ? String(Math.round(reste)) : reste.toFixed(1).replace('.', ',')
  return `${texte} ${unites[i]}`
}

/** One line for the shop card: the plan and, for Pro, until when. */
export function planLisible(boutique: BoutiqueAdmin): string {
  if (!boutique.pro) return 'Plan gratuit'
  if (!boutique.jusqu_au) return 'Pro sans date de fin'
  return `Pro jusqu’au ${dateCourte(boutique.jusqu_au)}`
}

/** An account that never signed in shows an em dash, not 1970. */
export function derniereConnexion(valeur: string | null): string {
  return valeur ? dateCourte(valeur) : 'jamais'
}
