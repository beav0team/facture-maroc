// Shared state for the settings screens.
//
// The settings used to be one form that PATCHed the whole `shops` row. Split
// into groups, each screen must send **only its own fields**: a group that
// saved a slice built from its own local state would blank everything it does
// not show. So the full `Saisie` lives here, once, and `enregistrerGroupe()`
// projects the group's keys out of it.

import { ORDRE_TYPES, TYPES_DOCUMENT, designDepuisId, type DesignDocument } from './document'
import { analyserMontant } from './money'
import { prefixeValide } from './numbering'
import { auth, enregistrerBoutique, type Boutique } from './session.svelte'

export type Groupe = 'boutique' | 'facturation' | 'numerotation' | 'banque'

export type Saisie = {
  nom: string
  adresse: string
  telephone: string
  email: string
  ice: string
  if_fiscal: string
  rc: string
  patente: string
  cnss: string
  banque: string
  rib: string
  rib_titulaire: string
  clause_retard: string
  tva_defaut: string
  tva_non_applicable: boolean
  prix_ttc: boolean
  conditions_paiement: string
  numerotation_annuelle: boolean
  design_defaut: DesignDocument
  montant_lettres: boolean
  prefixes: Record<string, string>
}

export const CONDITIONS = [
  { valeur: 'cash', libelle: 'Comptant' },
  { valeur: '30j', libelle: '30 jours' },
  { valeur: '60j', libelle: '60 jours' },
  { valeur: 'fin_de_mois', libelle: 'Fin de mois' },
  { valeur: 'custom', libelle: 'Texte libre' },
]

function saisieVide(): Saisie {
  return {
    nom: '',
    adresse: '',
    telephone: '',
    email: '',
    ice: '',
    if_fiscal: '',
    rc: '',
    patente: '',
    cnss: '',
    banque: '',
    rib: '',
    rib_titulaire: '',
    clause_retard: '',
    tva_defaut: '20',
    tva_non_applicable: false,
    prix_ttc: false,
    conditions_paiement: '30j',
    numerotation_annuelle: false,
    design_defaut: designDepuisId('classique'),
    montant_lettres: true,
    prefixes: {},
  }
}

type EtatReglages = {
  saisie: Saisie
  /** The values as last loaded or saved, to tell "dirty" from "untouched". */
  reference: string
  erreur: string | null
  message: string | null
  enregistrement: boolean
}

export const reglages = $state<EtatReglages>({
  saisie: saisieVide(),
  reference: '',
  erreur: null,
  message: null,
  enregistrement: false,
})

/** Fills the form from the shop in session. Safe to call on every mount. */
export function chargerReglages(): void {
  const boutique = auth.boutique
  if (!boutique) return
  reglages.saisie = {
    nom: boutique.nom,
    adresse: boutique.adresse ?? '',
    telephone: boutique.telephone ?? '',
    email: boutique.email ?? '',
    ice: boutique.ice ?? '',
    if_fiscal: boutique.if_fiscal ?? '',
    rc: boutique.rc ?? '',
    patente: boutique.patente ?? '',
    cnss: boutique.cnss ?? '',
    banque: boutique.banque ?? '',
    rib: boutique.rib ?? '',
    rib_titulaire: boutique.rib_titulaire ?? '',
    clause_retard: boutique.clause_retard ?? '',
    tva_defaut: String(boutique.tva_defaut).replace('.', ','),
    tva_non_applicable: boutique.tva_non_applicable,
    prix_ttc: boutique.prix_ttc,
    conditions_paiement: boutique.conditions_paiement,
    numerotation_annuelle: Boolean(boutique.numerotation_annuelle),
    design_defaut: designDepuisId(boutique.design_defaut),
    montant_lettres: boutique.montant_lettres ?? true,
    prefixes: { ...(boutique.prefixes ?? {}) },
  }
  reglages.reference = JSON.stringify(reglages.saisie)
}

/** True when this group holds changes that were not saved yet. */
export function groupeModifie(groupe: Groupe): boolean {
  const actuel = JSON.parse(JSON.stringify(reglages.saisie)) as Saisie
  const origine = JSON.parse(reglages.reference || '{}') as Partial<Saisie>
  return CHAMPS_GROUPE[groupe].some(
    (champ) => JSON.stringify(actuel[champ]) !== JSON.stringify(origine[champ]),
  )
}

export function barreDesModifications(): string | null {
  if (!reglages.reference) return null
  return groupeModifie('boutique') ||
    groupeModifie('facturation') ||
    groupeModifie('numerotation') ||
    groupeModifie('banque')
    ? 'Modifications non enregistrées'
    : null
}

const CHAMPS_GROUPE: Record<Groupe, (keyof Saisie)[]> = {
  boutique: ['nom', 'adresse', 'telephone', 'email', 'ice', 'if_fiscal', 'rc', 'patente', 'cnss'],
  facturation: [
    'tva_defaut',
    'tva_non_applicable',
    'prix_ttc',
    'conditions_paiement',
    'clause_retard',
    'design_defaut',
    'montant_lettres',
  ],
  numerotation: ['numerotation_annuelle', 'prefixes'],
  banque: ['banque', 'rib', 'rib_titulaire'],
}

function texte(valeur: string): string | null {
  const propre = valeur.trim()
  return propre === '' ? null : propre
}

function prefixesPropres(): Record<string, string> {
  const sortie: Record<string, string> = {}
  for (const type of ORDRE_TYPES) {
    const prefixe = (reglages.saisie.prefixes[type] ?? '').trim()
    if (prefixe !== '') sortie[type] = prefixe
  }
  return sortie
}

/** Validates the group and returns the row fields to send, or an error. */
type ChampsBoutique = Partial<Omit<Boutique, 'id' | 'created_at'>>

function champsDuGroupe(groupe: Groupe): { champs: ChampsBoutique } | { erreur: string } {
  const saisie = reglages.saisie

  if (groupe === 'boutique') {
    const nom = saisie.nom.trim()
    if (nom === '') return { erreur: 'Le nom de la boutique est obligatoire.' }
    return {
      champs: {
        nom,
        adresse: texte(saisie.adresse),
        telephone: texte(saisie.telephone),
        email: texte(saisie.email),
        ice: texte(saisie.ice),
        if_fiscal: texte(saisie.if_fiscal),
        rc: texte(saisie.rc),
        patente: texte(saisie.patente),
        cnss: texte(saisie.cnss),
      },
    }
  }

  if (groupe === 'facturation') {
    const tva = analyserMontant(saisie.tva_defaut)
    if (tva === null || tva < 0 || tva > 100) {
      return { erreur: 'Le taux de TVA doit être un nombre entre 0 et 100.' }
    }
    return {
      champs: {
        tva_defaut: Math.round(tva * 100) / 100,
        tva_non_applicable: saisie.tva_non_applicable,
        prix_ttc: saisie.prix_ttc,
        conditions_paiement: saisie.conditions_paiement,
        clause_retard: texte(saisie.clause_retard),
        design_defaut: saisie.design_defaut,
        montant_lettres: saisie.montant_lettres,
      },
    }
  }

  if (groupe === 'numerotation') {
    for (const type of ORDRE_TYPES) {
      const prefixe = (saisie.prefixes[type] ?? '').trim()
      if (prefixe !== '' && !prefixeValide(prefixe.replace(/-+$/, ''))) {
        return {
          erreur: `Le préfixe « ${prefixe} » du document « ${TYPES_DOCUMENT[type].libelle} » contient un caractère interdit.`,
        }
      }
    }
    return {
      champs: {
        numerotation_annuelle: saisie.numerotation_annuelle,
        prefixes: prefixesPropres(),
      },
    }
  }

  return {
    champs: {
      banque: texte(saisie.banque),
      rib: texte(saisie.rib),
      rib_titulaire: texte(saisie.rib_titulaire),
    },
  }
}

/** Saves one group. Returns true when the row was written. */
export async function enregistrerGroupe(groupe: Groupe): Promise<boolean> {
  reglages.erreur = null
  reglages.message = null

  const valide = champsDuGroupe(groupe)
  if ('erreur' in valide) {
    reglages.erreur = valide.erreur
    return false
  }

  reglages.enregistrement = true
  const probleme = await enregistrerBoutique(valide.champs)
  reglages.enregistrement = false

  if (probleme) {
    reglages.erreur = probleme
    return false
  }

  // Reload from the row we just wrote: keeps a normalised prefix or a rounded
  // VAT rate from looking dirty forever.
  chargerReglages()
  reglages.message = 'Réglages enregistrés.'
  return true
}

export function effacerMessages(): void {
  reglages.erreur = null
  reglages.message = null
}
