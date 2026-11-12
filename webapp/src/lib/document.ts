// Document types, with the identifiers the Android app already stores. Keep
// these strings identical to DocumentType.id in Kotlin: a document written on
// one side must be readable on the other.

export type TypeDocument =
  | 'facture'
  | 'devis'
  | 'bon_commande'
  | 'ticket'
  | 'avoir'
  | 'proforma'
  | 'acompte'
  | 'regul'
  | 'simplifiee'
  | 'export'

export type MetaDocument = {
  libelle: string
  /** Default number prefix, overridable per shop. */
  prefixe: string
  /** Only a document that sold something can be credited. */
  creditable: boolean
  /** Only a document that asks to be paid can receive a payment. */
  payable: boolean
  /** Avoir, régularisation, acompte: carries the number it relates to. */
  reference: boolean
}

export const TYPES_DOCUMENT: Record<TypeDocument, MetaDocument> = {
  facture: { libelle: 'Facture', prefixe: 'F', creditable: true, payable: true, reference: false },
  devis: { libelle: 'Devis', prefixe: 'D', creditable: false, payable: false, reference: false },
  bon_commande: {
    libelle: 'Bon de commande',
    prefixe: 'BC',
    creditable: false,
    payable: false,
    reference: false,
  },
  ticket: { libelle: 'Ticket (80mm)', prefixe: 'T', creditable: true, payable: true, reference: false },
  avoir: { libelle: 'Avoir', prefixe: 'A', creditable: false, payable: false, reference: true },
  proforma: { libelle: 'Proforma', prefixe: 'PF', creditable: false, payable: false, reference: false },
  acompte: { libelle: 'Acompte', prefixe: 'AC', creditable: false, payable: false, reference: true },
  regul: {
    libelle: 'Régularisation',
    prefixe: 'REG',
    creditable: false,
    payable: false,
    reference: true,
  },
  simplifiee: {
    libelle: 'Simplifiée',
    prefixe: 'FS',
    creditable: true,
    payable: true,
    reference: false,
  },
  export: { libelle: 'Export', prefixe: 'E', creditable: true, payable: true, reference: false },
}

export const ORDRE_TYPES: TypeDocument[] = [
  'facture',
  'devis',
  'bon_commande',
  'ticket',
  'avoir',
  'proforma',
  'acompte',
  'regul',
  'simplifiee',
  'export',
]

export function typeDepuisId(id: string | null | undefined): TypeDocument {
  return ORDRE_TYPES.find((type) => type === id) ?? 'facture'
}

/**
 * The paper layouts, chosen per document and defaulted by the shop.
 *
 * The ids are what the database stores and what an already-issued document
 * keeps: renaming one would orphan the documents archived under it. `ticket`
 * is the only one that does not print on A4 — it comes out on an 80 mm roll.
 */
export type DesignDocument =
  | 'classique'
  | 'moderne'
  | 'facture_maroc'
  | 'epure'
  | 'bandeau'
  | 'ticket'

/** Picker order. The label is what the shop reads; the id never changes. */
export const DESIGNS: Record<DesignDocument, string> = {
  classique: 'Classique',
  moderne: 'Moderne',
  facture_maroc: 'Facture Maroc',
  epure: 'Épuré',
  bandeau: 'Bandeau',
  ticket: 'Ticket (80 mm)',
}

export function designDepuisId(id: string | null | undefined): DesignDocument {
  return id && id in DESIGNS ? (id as DesignDocument) : 'classique'
}

export type MethodePaiement = 'cash' | 'check' | 'transfer' | 'card'

export const METHODES_PAIEMENT: Record<MethodePaiement, string> = {
  cash: 'Espèces',
  check: 'Chèque',
  transfer: 'Virement bancaire',
  card: 'Carte bancaire',
}

export type ConditionsPaiement = 'cash' | '30j' | '60j' | 'fin_de_mois' | 'custom'

export const CONDITIONS_PAIEMENT: Record<ConditionsPaiement, { libelle: string; note: string }> = {
  cash: { libelle: 'Comptant', note: 'Paiement comptant à la livraison.' },
  '30j': { libelle: '30 jours', note: 'Paiement à 30 jours date de facture.' },
  '60j': { libelle: '60 jours', note: 'Paiement à 60 jours date de facture.' },
  fin_de_mois: { libelle: 'Fin de mois', note: 'Paiement en fin de mois.' },
  custom: { libelle: 'Texte libre', note: '' },
}

/** VAT rates offered on new documents. 7 % and 14 % were removed in 2026. */
export const TAUX_TVA = [20, 10, 0] as const

/** Legacy rates, still readable on documents written before 2026. */
export const TAUX_TVA_LEGACY = [7, 14] as const

export function estTauxConnu(taux: number): boolean {
  return [...TAUX_TVA, ...TAUX_TVA_LEGACY].some((connu) => connu === taux)
}
