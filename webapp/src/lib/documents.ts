// Documents as the database stores them, and as the engine reads them.
//
// Rows are snake_case and numbers may arrive as strings from PostgREST, so the
// conversion lives here, in one place, instead of inside each screen. The rule
// is the same as everywhere else in this app: `calculer` owns the amounts, this
// module only moves them between shapes.

import { estEnRetard, joursRestants } from './dates'
import type {
  ConditionsPaiement,
  DesignDocument,
  MethodePaiement,
  TypeDocument,
} from './document'
import { calculer, totalLigne, type Client, type Facture, type Ligne, type Paiement, type Totaux } from './invoice'
import { analyserMontant, arrondi, montant } from './money'

export type StatutDocument = 'brouillon' | 'emis'

/** The fields of a shop that print, frozen at emission. */
export type SnapshotBoutique = {
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
  tva_defaut: number
  tva_non_applicable: boolean
  prix_ttc: boolean
  logo_path: string | null
  /** Frozen at emission so a later setting change cannot alter a live preview. */
  montant_lettres?: boolean
}

/** What the customer was when the document was written. */
export type SnapshotClient = {
  nom: string
  adresse?: string
  telephone?: string
  email?: string
  ice?: string
  if_fiscal?: string
  rc?: string
  patente?: string
  cnss?: string
  exonere_tva?: boolean
}

export type DocumentRow = {
  id: string
  shop_id: string
  type: TypeDocument
  statut: StatutDocument
  numero: string
  date_emission: string
  date_validite: string | null
  echeance: string | null
  client_id: string | null
  client_snapshot: SnapshotClient
  reference: string
  incoterm: string
  tva_non_applicable: boolean
  taux_tva: number
  arrondi_dirham: boolean
  conditions_paiement: string
  clause_retard: string
  note_client: string
  note_remerciement: string
  surcharge_libelle: string
  surcharge_montant: number
  surcharge_pourcent: number
  remise_pourcent: number
  remise_montant: number
  acompte_montant: number
  acompte_reference: string
  design: DesignDocument
  total_ht: number
  remise: number
  surcharge: number
  tva: number
  total_ttc: number
  net_a_payer: number
  shop_snapshot: SnapshotBoutique | null
  emis_le: string | null
  created_at: string
  updated_at: string
}

export type LigneRow = {
  id: string
  document_id: string
  ordre: number
  article_id: string | null
  libelle: string
  quantite: number
  unite: string
  prix_unitaire: number
  remise_pourcent: number
  remise_montant: number
  exoneree: boolean
  tva_taux: number | null
  total_ht: number
  attributs: { cle?: string; key?: string; valeur?: string; value?: string }[]
}

export type PaiementRow = {
  id: string
  document_id: string
  montant: number
  date_paiement: string
  moyen: MethodePaiement
  reference: string
  created_at: string
}

export type ArchiveRow = {
  document_id: string
  storage_path: string
  sha256: string
  taille: number
  created_at: string
}

/** What `quota_boutique()` returns. `limite` is null on Pro. */
export type QuotaBoutique = {
  plan: 'gratuit' | 'pro'
  paid_until: string | null
  utilise: number
  limite: number | null
}

export const LIMITE_GRATUITE = 5

/** The printing fields of a shop row, in snapshot shape. */
export function snapshotBoutique(boutique: Partial<SnapshotBoutique>): SnapshotBoutique {
  return {
    nom: texte(boutique.nom),
    adresse: boutique.adresse ?? null,
    telephone: boutique.telephone ?? null,
    email: boutique.email ?? null,
    ice: boutique.ice ?? null,
    if_fiscal: boutique.if_fiscal ?? null,
    rc: boutique.rc ?? null,
    patente: boutique.patente ?? null,
    cnss: boutique.cnss ?? null,
    banque: boutique.banque ?? null,
    rib: boutique.rib ?? null,
    rib_titulaire: boutique.rib_titulaire ?? null,
    tva_defaut: typeof boutique.tva_defaut === 'number' ? boutique.tva_defaut : 20,
    tva_non_applicable: Boolean(boutique.tva_non_applicable),
    prix_ttc: Boolean(boutique.prix_ttc),
    logo_path: boutique.logo_path ?? null,
    montant_lettres: boutique.montant_lettres ?? true,
  }
}

/** PostgREST may hand a numeric column over as a string; never print `NaN`. */
function nombre(valeur: unknown, defaut = 0): number {
  if (typeof valeur === 'number') return Number.isFinite(valeur) ? valeur : defaut
  if (typeof valeur === 'string') return analyserMontant(valeur) ?? defaut
  return defaut
}

function texte(valeur: unknown): string {
  return typeof valeur === 'string' ? valeur : ''
}

/** A catalogue client, a snapshot, or a hand-typed name, all read the same. */
export function clientDeSnapshot(brut: unknown): Client {
  const source = (brut ?? {}) as Record<string, unknown>
  const exonere = source.exonere_tva ?? source.exonereTva
  return {
    nom: texte(source.nom),
    adresse: texte(source.adresse) || undefined,
    telephone: texte(source.telephone) || undefined,
    email: texte(source.email) || undefined,
    ice: texte(source.ice) || undefined,
    ifFiscal: texte(source.if_fiscal ?? source.ifFiscal) || undefined,
    rc: texte(source.rc) || undefined,
    patente: texte(source.patente) || undefined,
    cnss: texte(source.cnss) || undefined,
    exonereTva: Boolean(exonere),
    id: texte(source.id) || undefined,
  }
}

/** The frozen copy saved with a document, in the shape `calculer` reads. */
export function snapshotClient(client: Client | null | undefined): SnapshotClient {
  if (!client) return { nom: '' }
  return {
    nom: client.nom.trim(),
    adresse: client.adresse ?? '',
    telephone: client.telephone ?? '',
    email: client.email ?? '',
    ice: client.ice ?? '',
    if_fiscal: client.ifFiscal ?? '',
    rc: client.rc ?? '',
    patente: client.patente ?? '',
    cnss: client.cnss ?? '',
    exonere_tva: Boolean(client.exonereTva),
  }
}

function ligneDeRow(ligne: LigneRow): Ligne {
  const attributs = Array.isArray(ligne.attributs)
    ? ligne.attributs
        .map((attribut) => ({
          cle: texte(attribut.cle ?? attribut.key),
          valeur: texte(attribut.valeur ?? attribut.value),
        }))
        .filter((attribut) => attribut.cle !== '')
    : []
  return {
    description: ligne.libelle,
    quantite: nombre(ligne.quantite, 1),
    prixUnitaire: nombre(ligne.prix_unitaire),
    unite: ligne.unite || undefined,
    remisePourcent: nombre(ligne.remise_pourcent),
    remiseMontant: nombre(ligne.remise_montant),
    exoneree: Boolean(ligne.exoneree),
    tauxTva: ligne.tva_taux === null ? null : nombre(ligne.tva_taux),
    articleId: ligne.article_id,
    attributs,
  }
}

/** A stored document, read back into the engine's own shape. */
export function factureDeDocument(
  doc: DocumentRow,
  lignes: LigneRow[],
  paiements: PaiementRow[] = [],
): Facture {
  const echeance = doc.echeance?.trim() || null
  return {
    numero: doc.numero,
    date: doc.date_emission,
    client: clientDeSnapshot(doc.client_snapshot),
    lignes: [...lignes].sort((a, b) => a.ordre - b.ordre).map(ligneDeRow),
    remisePourcent: nombre(doc.remise_pourcent),
    remiseMontant: nombre(doc.remise_montant),
    tauxTva: nombre(doc.taux_tva, 20),
    arrondiDirham: Boolean(doc.arrondi_dirham),
    type: doc.type,
    dateValidite: doc.date_validite?.trim() || null,
    echeance,
    reference: doc.reference,
    incoterm: doc.incoterm,
    tvaNonApplicable: Boolean(doc.tva_non_applicable),
    noteClient: doc.note_client,
    conditionsPaiement: doc.conditions_paiement as ConditionsPaiement,
    clauseRetard: doc.clause_retard,
    noteRemerciement: doc.note_remerciement,
    surchargeLibelle: doc.surcharge_libelle,
    surchargeMontant: nombre(doc.surcharge_montant),
    surchargePourcent: nombre(doc.surcharge_pourcent),
    acompte: nombre(doc.acompte_montant),
    acompteReference: doc.acompte_reference,
    paiements: paiements.map(
      (paiement): Paiement => ({
        montant: nombre(paiement.montant),
        date: paiement.date_paiement,
        methode: paiement.moyen,
        note: paiement.reference,
      }),
    ),
  }
}

/** The payload `enregistrer_brouillon()` expects, amounts already computed. */
export function enteteDeFacture(
  shopId: string,
  facture: Facture,
  design: DesignDocument,
  totaux: Totaux = calculer(facture),
): Record<string, unknown> {
  return {
    shop_id: shopId,
    type: facture.type ?? 'facture',
    date_emission: facture.date,
    date_validite: facture.dateValidite ?? '',
    echeance: facture.echeance ?? '',
    client_id: facture.client.id ?? '',
    client_snapshot: snapshotClient(facture.client),
    reference: facture.reference ?? '',
    incoterm: facture.incoterm ?? '',
    tva_non_applicable: Boolean(facture.tvaNonApplicable),
    taux_tva: facture.tauxTva ?? 20,
    arrondi_dirham: Boolean(facture.arrondiDirham),
    conditions_paiement: facture.conditionsPaiement ?? '30j',
    clause_retard: facture.clauseRetard ?? '',
    note_client: facture.noteClient ?? '',
    note_remerciement: facture.noteRemerciement ?? '',
    surcharge_libelle: facture.surchargeLibelle ?? '',
    surcharge_montant: facture.surchargeMontant ?? 0,
    surcharge_pourcent: facture.surchargePourcent ?? 0,
    remise_pourcent: facture.remisePourcent ?? 0,
    remise_montant: facture.remiseMontant ?? 0,
    acompte_montant: facture.acompte ?? 0,
    acompte_reference: facture.acompteReference ?? '',
    design,
    total_ht: totaux.totalHt,
    remise: totaux.remise,
    surcharge: totaux.surcharge,
    tva: totaux.tva,
    total_ttc: totaux.total,
    net_a_payer: totaux.netAPayer,
  }
}

/** The line payload, in the order the editor shows them. */
export function lignesDeFacture(lignes: Ligne[]): Record<string, unknown>[] {
  return lignes.map((ligne, ordre) => ({
    ordre,
    article_id: ligne.articleId ?? '',
    libelle: ligne.description,
    quantite: ligne.quantite,
    unite: ligne.unite ?? '',
    prix_unitaire: ligne.prixUnitaire,
    remise_pourcent: ligne.remisePourcent ?? 0,
    remise_montant: ligne.remiseMontant ?? 0,
    exoneree: Boolean(ligne.exoneree),
    tva_taux: ligne.tauxTva ?? '',
    total_ht: arrondi(totalLigne(ligne)),
    attributs: (ligne.attributs ?? []).map((attribut) => ({
      cle: attribut.cle,
      valeur: attribut.valeur,
    })),
  }))
}

export type TonStatut = 'neutral' | 'due' | 'overdue' | 'paid' | 'draft'

export type LigneStatut = { texte: string; ton: TonStatut }

function dateCourte(valeur: string): string {
  const morceaux = valeur.trim().split('/')
  return morceaux.length === 3 ? `${morceaux[0]}/${morceaux[1]}` : valeur.trim()
}

/**
 * The single line a history row shows. Same priority as the Android app:
 * late > balance > paid > draft, so a row can never read "paid" while money is
 * still owed.
 */
export type SituationReglement = {
  paye: number
  reste: number
  etatPaiement: 'unpaid' | 'partial' | 'paid'
}

/**
 * The payment position from the stored columns and the payment rows, without
 * re-running the whole engine: the list screen shows hundreds of rows and the
 * totals are already on each document.
 */
export function situationDeReglement(
  doc: Pick<DocumentRow, 'net_a_payer'>,
  paiements: Pick<PaiementRow, 'montant'>[],
): SituationReglement {
  const net = nombre(doc.net_a_payer)
  const encaisse = paiements.reduce((somme, paiement) => somme + nombre(paiement.montant), 0)
  const paye = Math.min(Math.max(encaisse, 0), net)
  const reste = Math.max(net - paye, 0)
  return {
    paye,
    reste,
    etatPaiement: net > 0 && reste <= 0 ? 'paid' : paye > 0 ? 'partial' : 'unpaid',
  }
}

export function statutDocument(
  doc: Pick<DocumentRow, 'statut' | 'type' | 'date_validite' | 'echeance' | 'numero'>,
  totaux: SituationReglement,
  aujourdHui: Date = new Date(),
  dernierPaiement = '',
): LigneStatut {
  if (doc.statut === 'brouillon') return { texte: 'Brouillon', ton: 'draft' }
  if (doc.type === 'avoir') return { texte: '', ton: 'neutral' }

  if (doc.type === 'devis') {
    const validite = doc.date_validite?.trim() ?? ''
    return {
      texte: validite === '' ? 'Devis' : `Devis · valable ${dateCourte(validite)}`,
      ton: 'neutral',
    }
  }

  if (totaux.etatPaiement !== 'paid' && estEnRetard(doc.echeance, totaux.etatPaiement, aujourdHui)) {
    const reste = joursRestants(doc.echeance, aujourdHui) ?? 0
    return { texte: `${Math.abs(reste)} j de retard`, ton: 'overdue' }
  }

  if (totaux.etatPaiement === 'paid') {
    const jour = dernierPaiement.trim()
    return {
      texte: jour === '' ? 'Payée' : `Payée le ${dateCourte(jour)}`,
      ton: 'paid',
    }
  }

  if (totaux.etatPaiement === 'partial') {
    return { texte: `${montant(totaux.reste)} dû`, ton: 'due' }
  }

  const echeance = doc.echeance?.trim() ?? ''
  return echeance === ''
    ? { texte: 'Impayée', ton: 'neutral' }
    : { texte: `Échéance ${dateCourte(echeance)}`, ton: 'neutral' }
}

/** Free plan exhausted: the next invoice would be refused by the database. */
export function quotaAtteint(quota: QuotaBoutique | null | undefined): boolean {
  if (!quota) return false
  if (quota.plan === 'pro') return false
  return quota.utilise >= (quota.limite ?? LIMITE_GRATUITE)
}

/** The sentence the editor and the dashboard print above the quota bar. */
export function texteQuota(quota: QuotaBoutique | null | undefined): string {
  if (!quota) return ''
  if (quota.plan === 'pro') return 'Plan Pro'
  const limite = quota.limite ?? LIMITE_GRATUITE
  return `${quota.utilise} / ${limite} factures ce mois`
}

/** A draft as the editor holds it: the engine document plus its page setup. */
export type SaisieDocument = {
  id: string | null
  facture: Facture
  design: DesignDocument
  statut: StatutDocument
}

/** A stored document, read back into the editor's shape. */
export function saisieDeDocument(
  doc: DocumentRow,
  lignes: LigneRow[],
  paiements: PaiementRow[] = [],
): SaisieDocument {
  return {
    id: doc.id,
    facture: factureDeDocument(doc, lignes, paiements),
    design: doc.design,
    statut: doc.statut,
  }
}

/** A brand-new draft, dated today, with the shop's defaults already applied. */
export function brouillonVide(
  type: TypeDocument,
  tauxTva: number,
  design: DesignDocument,
  aujourdHui: Date = new Date(),
): SaisieDocument {
  const jour = String(aujourdHui.getDate()).padStart(2, '0')
  const mois = String(aujourdHui.getMonth() + 1).padStart(2, '0')
  const annee = aujourdHui.getFullYear()
  return {
    id: null,
    statut: 'brouillon',
    design,
    facture: {
      numero: '',
      date: `${jour}/${mois}/${annee}`,
      client: { nom: '' },
      lignes: [],
      tauxTva,
      type,
    },
  }
}
