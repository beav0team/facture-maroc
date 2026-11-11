// Password recovery, kept free of Supabase so it can be tested.
//
// The reset e-mail redirects to `/app/`; Supabase then appends the recovery
// session to the URL as `#access_token=…&type=recovery` (the client runs the
// implicit flow, the default). The app has to notice that before `getSession()`
// consumes it, otherwise the shop lands on the dashboard with no way to choose
// a new password. A `?type=recovery` query is accepted too, for a link built by
// hand or by a future PKCE switch.

const MINIMUM = 6

/** True when the URL carries a recovery return, in the hash or the query. */
export function typeRecuperation(hash: string, recherche: string): boolean {
  const duHash = new URLSearchParams(hash.replace(/^#/, ''))
  const deLaRequete = new URLSearchParams(recherche.replace(/^\?/, ''))
  return duHash.get('type') === 'recovery' || deLaRequete.get('type') === 'recovery'
}

export type Validation = { motDePasse: string } | { erreur: string }

/**
 * A new password typed twice. Supabase enforces the real rule (minimum length,
 * not the previous password); this only catches what the form can see, so the
 * shop gets its answer before a round trip.
 */
export function validerNouveau(motDePasse: string, confirmation: string): Validation {
  if (motDePasse.length < MINIMUM) {
    return { erreur: `Le mot de passe doit faire au moins ${MINIMUM} caractères.` }
  }
  if (motDePasse !== confirmation) {
    return { erreur: 'Les deux mots de passe ne sont pas identiques.' }
  }
  return { motDePasse }
}

/** Last-resort French text when Supabase returns an English message. */
export function messageRecuperation(brut: string): string {
  const m = brut.toLowerCase()
  if (m.includes('should be different')) {
    return 'Le nouveau mot de passe doit être différent de l’ancien.'
  }
  if (m.includes('password should be at least') || m.includes('password is too short')) {
    return `Le mot de passe doit faire au moins ${MINIMUM} caractères.`
  }
  if (m.includes('session') || m.includes('jwt')) {
    return 'Ce lien a expiré. Demande un nouveau lien de réinitialisation.'
  }
  if (m.includes('rate limit')) {
    return 'Trop de tentatives. Réessaie dans quelques minutes.'
  }
  return brut
}
