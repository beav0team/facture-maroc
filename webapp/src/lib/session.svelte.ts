import type { User } from '@supabase/supabase-js'
import type { DesignDocument } from './document'
import { supabase } from './supabase'
import { messageRecuperation, typeRecuperation } from './recuperation'

export type Boutique = {
  id: string
  nom: string
  adresse: string | null
  telephone: string | null
  email: string | null
  ice: string | null
  if_fiscal: string | null
  rc: string | null
  patente: string | null
  cnss: string | null
  banque: string | null
  rib: string | null
  rib_titulaire: string | null
  clause_retard: string | null
  tva_defaut: number
  tva_non_applicable: boolean
  prix_ttc: boolean
  conditions_paiement: string
  logo_path: string | null
  prefixes: Record<string, string> | null
  numerotation_annuelle: boolean
  design_defaut: DesignDocument
  /** Print the amount in words under the totals (migration 0007). */
  montant_lettres: boolean
  created_at: string
}

type Etat = {
  pret: boolean
  user: User | null
  boutique: Boutique | null
  erreur: string | null
  chargement: boolean
  /** The shop arrived from a reset e-mail and must choose a new password. */
  recuperation: boolean
}

export const auth = $state<Etat>({
  pret: false,
  user: null,
  boutique: null,
  erreur: null,
  chargement: false,
  recuperation: false,
})

export async function demarrer(): Promise<void> {
  if (!supabase) {
    auth.pret = true
    return
  }

  // Read the recovery return before `getSession()` consumes the URL hash. The
  // `PASSWORD_RECOVERY` event alone is not enough: it can fire during the
  // client's initialisation, before this listener exists.
  if (typeof location !== 'undefined') {
    auth.recuperation = typeRecuperation(location.hash, location.search)
  }

  const { data } = await supabase.auth.getSession()
  auth.user = data.session?.user ?? null

  supabase.auth.onAuthStateChange((evenement, session) => {
    auth.user = session?.user ?? null
    if (evenement === 'PASSWORD_RECOVERY') auth.recuperation = true
    // On an OAuth return (Google) the session arrives after mount: the shop
    // must be reloaded, not only cleared on sign-out.
    if (session) void chargerBoutique()
    else auth.boutique = null
  })

  await chargerBoutique()
  auth.pret = true
}

export async function chargerBoutique(): Promise<void> {
  if (!supabase || !auth.user) {
    auth.boutique = null
    return
  }

  /*
   * Read the membership, never `shops` directly. A platform admin may select
   * every shop (policy « admin lit boutiques »), so the old
   * `shops … order by created_at limit 1` handed the admin the oldest shop on
   * the platform — the account opened as « Atlas » instead of its own shop.
   * `memberships` is scoped to `user_id = auth.uid()`, so this returns the shop
   * the user actually owns, admin or not.
   */
  const { data, error } = await supabase
    .from('memberships')
    .select('shop_id, shops(*)')
    .eq('user_id', auth.user.id)
    .order('created_at', { ascending: true })
    .limit(1)
    .maybeSingle()

  if (error) {
    auth.erreur = error.message
    return
  }
  auth.boutique = ((data as { shops?: Boutique | null } | null)?.shops ?? null) as Boutique | null
}

export async function connexion(email: string, motDePasse: string): Promise<string | null> {
  if (!supabase) return 'Supabase n’est pas configuré.'
  auth.chargement = true
  auth.erreur = null
  const { error } = await supabase.auth.signInWithPassword({ email, password: motDePasse })
  auth.chargement = false
  if (error) {
    auth.erreur = messageAuth(error.message)
    return auth.erreur
  }
  await chargerBoutique()
  return null
}

export async function inscription(email: string, motDePasse: string): Promise<string | null> {
  if (!supabase) return 'Supabase n’est pas configuré.'
  auth.chargement = true
  auth.erreur = null
  const { data, error } = await supabase.auth.signUp({ email, password: motDePasse })
  auth.chargement = false
  if (error) {
    auth.erreur = messageAuth(error.message)
    return auth.erreur
  }
  // With e-mail confirmation enabled there is no session yet.
  if (!data.session) {
    return 'Compte créé. Vérifie ta boîte mail pour confirmer l’adresse, puis connecte-toi.'
  }
  await chargerBoutique()
  return null
}

export async function deconnexion(): Promise<void> {
  if (!supabase) return
  await supabase.auth.signOut()
  auth.user = null
  auth.boutique = null
}

/**
 * Opens the Google page. On return Supabase puts the session in the URL and
 * `onAuthStateChange` takes over. The provider must be enabled in
 * Supabase → Authentication → Providers, with /app/ in the Redirect URLs.
 */
export async function connexionGoogle(): Promise<string | null> {
  if (!supabase) return 'Supabase n’est pas configuré.'
  auth.erreur = null
  const { error } = await supabase.auth.signInWithOAuth({
    provider: 'google',
    options: {
      redirectTo: `${location.origin}/app/`,
      queryParams: { prompt: 'select_account' },
    },
  })
  if (error) return messageAuth(error.message)
  return null
}

export async function motDePasseOublie(email: string): Promise<string | null> {
  if (!supabase) return 'Supabase n’est pas configuré.'
  // The link returns to the app root: Supabase appends the recovery session to
  // the URL and `demarrer()` turns it into the "new password" screen. Pointing
  // it at a dedicated path would also need that path allowlisted in Supabase
  // and rewritten in docs/_redirects for nothing.
  const { error } = await supabase.auth.resetPasswordForEmail(email, {
    redirectTo: `${location.origin}/app/`,
  })
  if (error) return messageAuth(error.message)
  return null
}

/**
 * Saves the password chosen on the recovery screen. The recovery link already
 * opened a session; `updateUser` is the only call allowed to use it. The hash
 * is cleared so a reload does not replay the recovery token.
 */
export async function changerMotDePasse(motDePasse: string): Promise<string | null> {
  if (!supabase) return 'Supabase n’est pas configuré.'
  auth.chargement = true
  const { error } = await supabase.auth.updateUser({ password: motDePasse })
  auth.chargement = false
  if (error) return messageRecuperation(error.message)
  if (typeof location !== 'undefined' && location.hash) {
    history.replaceState({}, '', location.pathname + location.search)
  }
  await chargerBoutique()
  return null
}

/** Leaves the recovery screen once the new password is saved. */
export function terminerRecuperation(): void {
  auth.recuperation = false
  if (typeof location !== 'undefined' && location.hash) {
    history.replaceState({}, '', location.pathname + location.search)
  }
}

export async function creerBoutique(nom: string): Promise<string | null> {
  if (!supabase) return 'Supabase n’est pas configuré.'
  auth.chargement = true
  const { error } = await supabase.rpc('create_shop_for_me', { p_nom: nom })
  auth.chargement = false
  if (error) return error.message
  await chargerBoutique()
  return null
}

/** Saves the shop's own fields. RLS allows a member; the form sends all of them. */
export async function enregistrerBoutique(
  champs: Partial<Omit<Boutique, 'id' | 'created_at'>>,
): Promise<string | null> {
  if (!supabase) return 'Supabase n’est pas configuré.'
  if (!auth.boutique) return 'Aucune boutique à enregistrer.'
  const { error } = await supabase.from('shops').update(champs).eq('id', auth.boutique.id)
  if (error) return error.message
  await chargerBoutique()
  return null
}

function messageAuth(brut: string): string {
  const m = brut.toLowerCase()
  if (m.includes('invalid login credentials')) return 'E-mail ou mot de passe incorrect.'
  if (m.includes('email not confirmed')) return 'Confirme d’abord ton adresse e-mail.'
  if (m.includes('user already registered')) return 'Un compte existe déjà avec cette adresse.'
  if (m.includes('password should be at least')) return 'Le mot de passe doit faire au moins 6 caractères.'
  if (m.includes('rate limit')) return 'Trop de tentatives. Réessaie dans quelques minutes.'
  return brut
}
