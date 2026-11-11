import type { User } from '@supabase/supabase-js'
import { supabase } from './supabase'

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
  design_defaut: 'classique' | 'facture_maroc'
  created_at: string
}

type Etat = {
  pret: boolean
  user: User | null
  boutique: Boutique | null
  erreur: string | null
  chargement: boolean
}

export const auth = $state<Etat>({
  pret: false,
  user: null,
  boutique: null,
  erreur: null,
  chargement: false,
})

export async function demarrer(): Promise<void> {
  if (!supabase) {
    auth.pret = true
    return
  }

  const { data } = await supabase.auth.getSession()
  auth.user = data.session?.user ?? null

  supabase.auth.onAuthStateChange((_evenement, session) => {
    auth.user = session?.user ?? null
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

  // RLS: this query can only return shops the user is a member of.
  const { data, error } = await supabase
    .from('shops')
    .select('*')
    .order('created_at', { ascending: true })
    .limit(1)
    .maybeSingle()

  if (error) {
    auth.erreur = error.message
    return
  }
  auth.boutique = (data as Boutique | null) ?? null
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
  const { error } = await supabase.auth.resetPasswordForEmail(email, {
    redirectTo: `${location.origin}/app/`,
  })
  if (error) return messageAuth(error.message)
  return null
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
