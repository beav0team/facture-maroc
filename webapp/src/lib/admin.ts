// Platform console, kept free of Svelte and Supabase so the formatting and the
// rules can be tested. One place for the numbers the admin reads.
//
// Since migration 0014 the console drives three tiers: gratuit, essentiel, pro.

import type { ClePlan } from './offres'

export type Plan = ClePlan

export type BoutiqueAdmin = {
  id: string
  nom: string
  cree_le: string
  proprietaire: string | null
  proprietaire_id: string | null
  plan: Plan
  jusqu_au: string | null
  note: string
  documents: number
  clients: number
  articles: number
  pro: boolean
  suspendue: boolean
  membres: number
  derniere_activite: string | null
  ice: string | null
  if_fiscal: string | null
}

export type UtilisateurAdmin = {
  id: string
  email: string | null
  cree_le: string
  derniere_connexion: string | null
  boutique: string | null
  boutique_id: string | null
  est_admin: boolean
  confirme: boolean
}

export type StatistiquesAdmin = {
  boutiques: number
  utilisateurs: number
  administrateurs: number
  pro: number
  essentiel: number
  documents: number
  documents_emis: number
  archives: number
  octets_archives: number
}

export type MembreAdmin = {
  user_id: string
  email: string | null
  role: 'owner' | 'membre' | 'admin'
  depuis: string
  derniere_connexion: string | null
}

export type DocumentAdmin = {
  id: string
  numero: string
  type: string
  statut: 'brouillon' | 'emis'
  total_ttc: number
  date_emission: string
  quand: string
  client: string
  archive_path: string | null
  archive_taille: number | null
}

export type NoteAdmin = {
  id: string
  note: string
  par: string
  quand: string
}

export type FicheBoutique = {
  boutique: Record<string, unknown> & {
    id: string
    nom: string
    ice: string | null
    if_fiscal: string | null
    rc: string | null
    patente: string | null
    cnss: string | null
    adresse: string | null
    telephone: string | null
    email: string | null
    banque: string | null
    rib: string | null
    rib_titulaire: string | null
    clause_retard: string | null
    conditions_paiement: string
    tva_defaut: number
    arrondi_dirham: boolean
    prix_ttc: boolean
    tva_non_applicable: boolean
    montant_lettres: boolean
    numerotation_annuelle: boolean
    design_defaut: 'classique' | 'facture_maroc'
    prefixes: Record<string, string>
  }
  proprietaire: string | null
  proprietaire_id: string | null
  membres: number
  pro: boolean
  abonnement: {
    plan: Plan
    jusqu_au: string | null
    note: string
    modifie_le: string | null
  }
  quota: { utilise: number; limite: number | null; bonus: number }
  suspension: { raison: string; depuis: string } | null
  documents: number
  clients: number
  articles: number
  archives: { nombre: number; octets: number }
  notes: NoteAdmin[]
}

export type AlerteAdmin = {
  genre:
    | 'expire_bientot'
    | 'expire'
    | 'sans_proprietaire'
    | 'quota_atteint'
    | 'suspendue'
    | 'identite_incomplete'
  shop_id: string
  nom: string
  detail: string
  echeance: string | null
}

export type LigneJournal = {
  id: string
  quand: string
  acteur: string
  action: string
  cible_type: 'boutique' | 'compte' | 'plateforme'
  cible_id: string | null
  cible_nom: string
  details: Record<string, unknown>
}

/** The shop fields the console may write, and nothing else. */
export type ChampsBoutique = Partial<{
  nom: string
  ice: string
  if_fiscal: string
  rc: string
  patente: string
  cnss: string
  adresse: string
  telephone: string
  email: string
  banque: string
  rib: string
  rib_titulaire: string
  clause_retard: string
  conditions_paiement: string
  tva_defaut: number
  arrondi_dirham: boolean
  prix_ttc: boolean
  tva_non_applicable: boolean
  montant_lettres: boolean
  numerotation_annuelle: boolean
  design_defaut: 'classique' | 'facture_maroc'
  prefixes: Record<string, string>
}>

export type FiltreBoutique =
  | 'toutes'
  | 'pro'
  | 'essentiel'
  | 'gratuit'
  | 'expire'
  | 'suspendue'
  | 'sans_proprietaire'

export const FILTRES_BOUTIQUE: { valeur: FiltreBoutique; libelle: string }[] = [
  { valeur: 'toutes', libelle: 'Toutes' },
  { valeur: 'pro', libelle: 'Pro' },
  { valeur: 'essentiel', libelle: 'Essentiel' },
  { valeur: 'gratuit', libelle: 'Gratuit' },
  { valeur: 'expire', libelle: 'Abonnement expiré' },
  { valeur: 'suspendue', libelle: 'Suspendues' },
  { valeur: 'sans_proprietaire', libelle: 'Sans propriétaire' },
]

/** `2027-11-11` or a full timestamp → `11/11/2027`; empty input → `—`. */
export function dateCourte(valeur: string | null | undefined): string {
  if (!valeur) return '—'
  const jour = valeur.slice(0, 10)
  const morceaux = jour.split('-')
  if (morceaux.length !== 3) return jour
  return `${morceaux[2]}/${morceaux[1]}/${morceaux[0]}`
}

/** `2027-11-11T07:05:00Z` → `11/11/2027 · 07:05`, or `—`. */
export function horodatage(valeur: string | null | undefined): string {
  if (!valeur) return '—'
  const date = new Date(valeur)
  if (Number.isNaN(date.getTime())) return dateCourte(valeur)
  const deux = (n: number) => String(n).padStart(2, '0')
  return `${deux(date.getDate())}/${deux(date.getMonth() + 1)}/${date.getFullYear()} · ${deux(
    date.getHours(),
  )}:${deux(date.getMinutes())}`
}

/** Local date, never UTC: a shop activating at 00:30 must not get yesterday. */
export function isoLocal(date: Date = new Date()): string {
  const deux = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${deux(date.getMonth() + 1)}-${deux(date.getDate())}`
}

/** The end date offered by « un an », one year from today. */
export function finDansUnAn(depuis: Date = new Date()): string {
  return isoLocal(new Date(depuis.getFullYear() + 1, depuis.getMonth(), depuis.getDate()))
}

/** The name of a tier, as the console writes it. */
export function nomPlan(plan: Plan): string {
  if (plan === 'pro') return 'Pro'
  if (plan === 'essentiel') return 'Essentiel'
  return 'Gratuit'
}

/**
 * A paid plan needs a real end date: `plan_effectif()` reads a null
 * `paid_until` as "forever" on Pro, so an empty field would give the plan away
 * permanently. Same rule as `admin_abonnement()` in 0014, checked here first so
 * the admin does not have to wait for the database to say it.
 */
export function validerAbonnement(
  plan: Plan,
  jusquAu: string | null,
  aujourdHui: string = isoLocal(),
): string | null {
  if (plan === 'gratuit') return null
  if (!jusquAu) return `Choisissez une date de fin pour le plan ${nomPlan(plan)}.`
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

/**
 * One line for the shop card: the plan, when it ends, and when it ended.
 * A paid tier whose date is behind us is not « Plan gratuit » — the shop paid
 * once and the console must say why the quota is back.
 */
export function planLisible(
  boutique: { pro: boolean; plan: Plan; jusqu_au: string | null },
  aujourdHui: string = isoLocal(),
): string {
  const fin = boutique.jusqu_au?.slice(0, 10) ?? null
  if (boutique.pro) {
    return fin ? `Pro jusqu’au ${dateCourte(fin)}` : 'Pro sans date de fin'
  }
  if (boutique.plan === 'essentiel' && fin && fin >= aujourdHui) {
    return `Essentiel jusqu’au ${dateCourte(fin)}`
  }
  if (boutique.plan !== 'gratuit' && fin) {
    return `${nomPlan(boutique.plan)} expiré le ${dateCourte(fin)}`
  }
  return 'Plan gratuit'
}

/** A paid tier that is still running: the plan reads Pro, or Essentiel in date. */
export function estActive(
  boutique: { pro: boolean; plan: Plan; jusqu_au: string | null },
  aujourdHui: string = isoLocal(),
): boolean {
  if (boutique.pro) return true
  if (boutique.plan !== 'essentiel') return false
  const fin = boutique.jusqu_au?.slice(0, 10)
  return Boolean(fin && fin >= aujourdHui)
}

/** An account that never signed in shows an em dash, not 1970. */
export function derniereConnexion(valeur: string | null): string {
  return valeur ? dateCourte(valeur) : 'jamais'
}

/** A paid subscription whose end date is behind us. */
export function estExpiree(boutique: BoutiqueAdmin, aujourdHui: string = isoLocal()): boolean {
  if (boutique.plan === 'gratuit' || boutique.pro) return false
  return Boolean(boutique.jusqu_au && boutique.jusqu_au.slice(0, 10) < aujourdHui)
}

/** The shop list, narrowed by a free-text search and a filter chip. */
export function filtrerBoutiques(
  boutiques: BoutiqueAdmin[],
  recherche: string,
  filtre: FiltreBoutique = 'toutes',
  aujourdHui: string = isoLocal(),
): BoutiqueAdmin[] {
  const texte = recherche.trim().toLowerCase()
  return boutiques.filter((boutique) => {
    if (filtre === 'pro' && !boutique.pro) return false
    if (
      filtre === 'essentiel' &&
      (boutique.plan !== 'essentiel' || !estActive(boutique, aujourdHui))
    ) {
      return false
    }
    if (filtre === 'gratuit' && boutique.plan !== 'gratuit') return false
    if (filtre === 'expire' && !estExpiree(boutique, aujourdHui)) return false
    if (filtre === 'suspendue' && !boutique.suspendue) return false
    if (filtre === 'sans_proprietaire' && boutique.proprietaire) return false
    if (!texte) return true
    const champs = [boutique.nom, boutique.proprietaire ?? '', boutique.ice ?? '', boutique.if_fiscal ?? '']
    return champs.some((champ) => champ.toLowerCase().includes(texte))
  })
}

/** Accounts narrowed by e-mail or shop name. */
export function filtrerComptes(comptes: UtilisateurAdmin[], recherche: string): UtilisateurAdmin[] {
  const texte = recherche.trim().toLowerCase()
  if (!texte) return comptes
  return comptes.filter((compte) =>
    [compte.email ?? '', compte.boutique ?? ''].some((champ) =>
      champ.toLowerCase().includes(texte),
    ),
  )
}

/** Reasons shown in the overview, in the order they deserve attention. */
export function alertesTriees(alertes: AlerteAdmin[]): AlerteAdmin[] {
  const rang: Record<AlerteAdmin['genre'], number> = {
    suspendue: 0,
    quota_atteint: 1,
    expire: 2,
    expire_bientot: 3,
    sans_proprietaire: 4,
    identite_incomplete: 5,
  }
  return [...alertes].sort((a, b) => rang[a.genre] - rang[b.genre] || a.nom.localeCompare(b.nom, 'fr'))
}

const TYPES_DOCUMENT: Record<string, string> = {
  facture: 'Facture',
  devis: 'Devis',
  bon_commande: 'Bon de commande',
  ticket: 'Ticket',
  avoir: 'Avoir',
  proforma: 'Proforma',
  acompte: 'Facture d’acompte',
  regul: 'Régularisation',
  simplifiee: 'Facture simplifiée',
  export: 'Export',
}

/** `acompte` → `Facture d’acompte`; an unknown type stays readable. */
export function libelleTypeDocument(type: string): string {
  return TYPES_DOCUMENT[type] ?? type
}

/** Field names the identity form writes, spelled the way the shop says them. */
const CHAMPS_BOUTIQUE: Record<string, string> = {
  nom: 'nom',
  ice: 'ICE',
  if_fiscal: 'IF',
  rc: 'RC',
  patente: 'patente',
  cnss: 'CNSS',
  adresse: 'adresse',
  telephone: 'téléphone',
  email: 'e-mail',
  banque: 'banque',
  rib: 'RIB',
  rib_titulaire: 'titulaire du RIB',
  clause_retard: 'clause de retard',
  conditions_paiement: 'conditions de paiement',
  tva_defaut: 'TVA',
  arrondi_dirham: 'arrondi au dirham',
  prix_ttc: 'prix TTC',
  tva_non_applicable: 'TVA non applicable',
  montant_lettres: 'montant en lettres',
  numerotation_annuelle: 'numérotation annuelle',
  design_defaut: 'modèle',
  prefixes: 'préfixes',
}

const ACTIONS: Record<string, string> = {
  boutique_modifiee: 'Fiche modifiée',
  note_ajoutee: 'Note ajoutée',
  quota_bonus: 'Quota bonifié',
  boutique_suspendue: 'Boutique suspendue',
  boutique_reprise: 'Suspension levée',
  boutique_supprimee: 'Boutique supprimée',
  archives_purgees: 'Archives purgées',
  membre_role: 'Rôle changé',
  propriete_transferee: 'Propriété transférée',
  membre_detache: 'Membre détaché',
  abonnement: 'Abonnement modifié',
}

export function libelleAction(action: string): string {
  return ACTIONS[action] ?? action
}

/** One short French line for the audit log, whatever the action carried. */
export function resumeDetails(action: string, details: Record<string, unknown>): string {
  const chaine = (cle: string): string => {
    const valeur = details[cle]
    return valeur === null || valeur === undefined ? '' : String(valeur)
  }
  switch (action) {
    case 'boutique_modifiee': {
      const champs = details.champs
      if (!champs || typeof champs !== 'object') return ''
      return Object.keys(champs as Record<string, unknown>)
        .map((cle) => CHAMPS_BOUTIQUE[cle] ?? cle)
        .join(', ')
    }
    case 'note_ajoutee':
      return chaine('note')
    case 'quota_bonus':
      return `+${chaine('documents_sup')} document(s) · ${chaine('raison')}`.trim()
    case 'boutique_suspendue':
      return chaine('raison')
    case 'boutique_supprimee':
      return `${chaine('documents')} document(s), ${chaine('archives')} archive(s)`
    case 'archives_purgees':
      return `${chaine('nombre')} archive(s) avant le ${dateCourte(chaine('avant'))}`
    case 'membre_role':
      return `${chaine('compte')} : ${chaine('ancien')} → ${chaine('nouveau')}`
    case 'membre_detache':
      return `${chaine('compte')} (${chaine('role')})`
    case 'propriete_transferee':
      return `${chaine('ancien')} → ${chaine('nouveau')}`
    case 'abonnement': {
      const nom = (cle: string): string => {
        const valeur = chaine(cle)
        return valeur === '' || valeur === 'aucun' ? 'aucun' : nomPlan(valeur as Plan)
      }
      const nouveau = chaine('nouveau') === 'gratuit'
        ? 'Gratuit'
        : `${nom('nouveau')} jusqu’au ${dateCourte(chaine('jusqu_au'))}`
      const note = chaine('note')
      return [`${nom('ancien')} → ${nouveau}`, note].filter(Boolean).join(' · ')
    }
    default:
      return ''
  }
}

/**
 * What the identity form sends. Only the keys that differ, so the audit log
 * shows what really changed and a stale field cannot overwrite a concurrent
 * edit.
 */
export function champsModifies(
  boutique: FicheBoutique['boutique'],
  saisie: ChampsBoutique,
): ChampsBoutique {
  const sortie: Record<string, unknown> = {}
  for (const [cle, valeur] of Object.entries(saisie)) {
    const avant = (boutique as Record<string, unknown>)[cle]
    const identique =
      typeof valeur === 'object' && valeur !== null
        ? JSON.stringify(valeur) === JSON.stringify(avant ?? {})
        : normaliser(valeur) === normaliser(avant)
    if (identique) continue
    sortie[cle] = typeof valeur === 'string' ? valeur.trim() : valeur
  }
  return sortie as ChampsBoutique
}

function normaliser(valeur: unknown): string {
  if (valeur === null || valeur === undefined) return ''
  if (typeof valeur === 'boolean') return valeur ? '1' : '0'
  return String(valeur).trim()
}

/** The same rules as `admin_modifier_boutique`, checked before the round trip. */
export function validerChampsBoutique(champs: ChampsBoutique): string | null {
  if ('nom' in champs) {
    const nom = (champs.nom ?? '').trim()
    if (nom.length < 1 || nom.length > 120) return 'Le nom doit faire entre 1 et 120 caractères.'
  }
  if ('tva_defaut' in champs) {
    const tva = Number(champs.tva_defaut)
    if (!Number.isFinite(tva) || tva < 0 || tva > 100) return 'La TVA doit être entre 0 et 100.'
  }
  if ('design_defaut' in champs) {
    if (!['classique', 'facture_maroc'].includes(String(champs.design_defaut))) {
      return 'Modèle de document inconnu.'
    }
  }
  if ('email' in champs && (champs.email ?? '').trim()) {
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test((champs.email ?? '').trim())) {
      return 'Cette adresse e-mail n’est pas valide.'
    }
  }
  return null
}

/** `5 / 8` or `Documents illimités` — the quota line of a shop card. */
export function quotaLisible(fiche: FicheBoutique): string {
  if (fiche.pro) return 'Documents illimités'
  const limite = fiche.quota.limite ?? 5
  return `${fiche.quota.utilise} / ${limite} ce mois`
}

/** Longest useful label before a row wraps. */
export function tronquer(texte: string, longueur = 80): string {
  return texte.length > longueur ? `${texte.slice(0, longueur - 1)}…` : texte
}
