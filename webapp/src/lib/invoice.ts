// The invoice arithmetic, ported from the Android app's InvoiceData.kt.
//
// Same rules, same order of operations, same rounding: every figure the site
// prints must land on the same centime as the Android document. `calculer` is
// pure and total — it never throws, and a malformed document yields zeroes
// rather than NaN on the page.
//
// Kotlin field names are noted beside each French one so the two engines can be
// read side by side.

import { arrondi } from './money'
import type { ConditionsPaiement, MethodePaiement, TypeDocument } from './document'

export type Client = {
  nom: string // name
  adresse?: string // address
  ice?: string
  ifFiscal?: string // ifNumber
  rc?: string
  patente?: string
  cnss?: string
  telephone?: string // phone
  email?: string
  exonereTva?: boolean // vatExempt
  role?: 'client' | 'fournisseur'
  id?: string
}

export type AttributLigne = { cle: string; valeur: string }

export type Ligne = {
  description: string
  quantite: number // quantity
  prixUnitaire: number // unitPrice
  unite?: string // unit
  remisePourcent?: number // lineDiscountPercent
  remiseMontant?: number // lineDiscountFixed
  exoneree?: boolean // taxExempt
  tauxTva?: number | null // vatPercent, null = document rate
  prixTtc?: number | null // ttcPrice
  articleId?: string | null // itemId
  coutUnitaireHt?: number | null // unitCostHt
  palierDepuis?: number | null // tierFromQuantity
  prixPalier?: number | null // appliedTierPrice
  attributs?: AttributLigne[] // lineAttributes
}

export type Paiement = {
  montant: number // paidAmount
  date?: string // paidAt, dd/MM/yyyy
  note?: string
  methode?: MethodePaiement | null
}

export type Facture = {
  numero: string // number
  date: string
  client: Client
  lignes: Ligne[] // lines
  remisePourcent?: number // discountPercent
  remiseMontant?: number // discountFixed
  tauxTva?: number // vatPercent
  /** Web only: round the TTC to the dirham, the difference landing in HT. */
  arrondiDirham?: boolean
  methodePaiement?: MethodePaiement
  type?: TypeDocument // documentType
  dateValidite?: string | null // validityDate
  echeance?: string | null // dueDate
  fournisseur?: Client | null // supplier
  reference?: string // referenceNumber
  incoterm?: string
  tvaNonApplicable?: boolean // vatNotApplicable
  noteClient?: string // customerNote
  conditionsPaiement?: ConditionsPaiement // paymentTerms
  clauseRetard?: string // latePenaltyClause
  noteRemerciement?: string // thankYouNote
  surchargeLibelle?: string // surchargeLabel
  surchargeMontant?: number // surchargeAmount
  surchargePourcent?: number // surchargePercent
  acompte?: number // depositAmount
  acompteReference?: string // depositReference
  paiements?: Paiement[] // payments
}

export type RecapitulatifTva = {
  taux: number
  base: number
  montant: number
}

export type EtatPaiement = 'unpaid' | 'partial' | 'paid'

export type Totaux = {
  sousTotal: number
  remise: number
  remiseEnMontant: boolean
  afficheRemise: boolean
  totalHt: number
  surcharge: number
  tva: number
  total: number
  baseTva: number
  tauxTvaEffectif: number
  tauxSurchargeTva: number
  lignesTva: RecapitulatifTva[]
  lignesTvaImprimees: RecapitulatifTva[]
  acompte: number
  netAPayer: number
  paye: number
  reste: number
  etatPaiement: EtatPaiement
  estSoldee: boolean
  estAvoir: boolean
  echeanceImprimee: string | null
}

const borne = (valeur: number, min: number, max: number): number =>
  Math.min(Math.max(valeur, min), max)

/**
 * One line before the document discount: quantity × price, then the line's own
 * discount (an amount wins over a percentage), never below zero.
 */
export function totalLigne(ligne: Ligne): number {
  const brut = ligne.quantite * ligne.prixUnitaire
  const remiseMontant = ligne.remiseMontant ?? 0
  if (remiseMontant > 0) return Math.max(0, brut - remiseMontant)
  return brut * (1 - borne(ligne.remisePourcent ?? 0, 0, 100) / 100)
}

function tvaNonApplicable(f: Facture): boolean {
  return Boolean(f.tvaNonApplicable) || Boolean(f.client.exonereTva)
}

/** The rate one line pays: exempt lines never pay, whatever the document says. */
export function tauxDeLigne(f: Facture, ligne: Ligne): number {
  if (ligne.exoneree || tvaNonApplicable(f)) return 0
  return Math.max(ligne.tauxTva ?? f.tauxTva ?? 20, 0)
}

/**
 * The rate the surcharge pays: the document rate, unless every taxed line
 * shares another single rate — a 10 % invoice with 20 % delivery would be a
 * document nobody can reconcile.
 */
function tauxSurcharge(f: Facture): number {
  if (tvaNonApplicable(f)) return 0
  const tauxTaxes = [...new Set(f.lignes.map((ligne) => tauxDeLigne(f, ligne)).filter((t) => t > 0))]
  if (tauxTaxes.length === 1) return tauxTaxes[0] ?? 0
  return Math.max(f.tauxTva ?? 20, 0)
}

function indiceDuMax(valeurs: number[]): number {
  let index = 0
  for (let i = 1; i < valeurs.length; i += 1) {
    if ((valeurs[i] ?? 0) > (valeurs[index] ?? 0)) index = i
  }
  return index
}

/** Every figure the document prints. Pure: same input, same output. */
export function calculer(f: Facture): Totaux {
  const lignes = f.lignes ?? []

  const totalTtc = f.tauxTva ?? 20
  const tauxTvaEffectif = tvaNonApplicable(f) ? 0 : totalTtc

  // --- lines and the document discount ------------------------------------
  const sousTotalExact = lignes.reduce((somme, ligne) => somme + totalLigne(ligne), 0)
  const sousTotal = arrondi(
    lignes.reduce((somme, ligne) => somme + arrondi(totalLigne(ligne)), 0),
  )

  const remiseFixe = f.remiseMontant ?? 0
  const remise =
    remiseFixe > 0
      ? arrondi(borne(remiseFixe, 0, sousTotal))
      : arrondi((sousTotal * borne(f.remisePourcent ?? 0, 0, 100)) / 100)

  const remiseExacte =
    remiseFixe > 0
      ? borne(remiseFixe, 0, sousTotalExact)
      : (sousTotalExact * borne(f.remisePourcent ?? 0, 0, 100)) / 100

  const totalHt = arrondi(sousTotal - remise)

  // The discount is spread over the lines, not deducted from the tax alone.
  const facteurConservation =
    sousTotalExact <= 0 ? 1 : (sousTotalExact - remiseExacte) / sousTotalExact

  // --- surcharge -----------------------------------------------------------
  const surchargePourcent = f.surchargePourcent ?? 0
  const surchargeExacte =
    surchargePourcent > 0
      ? (Math.max(sousTotalExact - remiseExacte, 0) * borne(surchargePourcent, 0, 100)) / 100
      : Math.max(f.surchargeMontant ?? 0, 0)
  const surcharge = arrondi(surchargeExacte)

  // --- VAT -----------------------------------------------------------------
  const nets = new Map<number, number>()
  for (const ligne of lignes) {
    const taux = tauxDeLigne(f, ligne)
    nets.set(taux, (nets.get(taux) ?? 0) + totalLigne(ligne) * facteurConservation)
  }
  const tauxCharge = tauxSurcharge(f)
  if (surchargeExacte > 0) {
    nets.set(tauxCharge, (nets.get(tauxCharge) ?? 0) + surchargeExacte)
  }

  let tvaExacte = 0
  for (const [taux, net] of nets) tvaExacte += (net * taux) / 100

  let total = arrondi(sousTotalExact - remiseExacte + surchargeExacte + tvaExacte)
  let totalHtAffiche = totalHt
  if (f.arrondiDirham) {
    // The customer pays whole dirhams; the centimes move to HT, never to VAT.
    const totalArrondi = Math.round(total)
    totalHtAffiche = arrondi(totalHt + (totalArrondi - total))
    total = totalArrondi
  }
  // Derived from the total so HT + TVA = TTC to the centime on every document.
  const tva = arrondi(total - totalHtAffiche - surcharge)

  // --- VAT recap, highest rate first --------------------------------------
  const brut = [...nets.entries()].sort((a, b) => b[0] - a[0])
  const bases: Array<[number, number]> = brut.map(([taux, net]) => [taux, arrondi(net)])

  const sommeBases = bases.reduce((somme, [, base]) => somme + base, 0)
  const ecartBase = arrondi(arrondi(totalHtAffiche + surcharge) - sommeBases)
  if (ecartBase !== 0 && bases.length > 0) {
    const index = indiceDuMax(bases.map(([, base]) => base))
    const base = bases[index]
    if (base) bases[index] = [base[0], arrondi(base[1] + ecartBase)]
  }

  const montants = bases.map(([taux, base]) => arrondi((base * taux) / 100))
  const ecartMontant = arrondi(tva - montants.reduce((somme, m) => somme + m, 0))
  if (ecartMontant !== 0 && montants.length > 0) {
    const index = indiceDuMax(montants)
    const montant = montants[index]
    if (montant !== undefined) montants[index] = arrondi(montant + ecartMontant)
  }

  const lignesTva: RecapitulatifTva[] = bases.map(([taux, base], index) => ({
    taux,
    base,
    montant: montants[index] ?? 0,
  }))

  const baseTva = arrondi(
    lignesTva.filter((ligne) => ligne.taux > 0).reduce((somme, ligne) => somme + ligne.base, 0),
  )

  const tauxAffiches = lignesTva.filter((ligne) => ligne.taux > 0 && ligne.base !== 0)
  const lignesTvaImprimees =
    tauxAffiches.length > 0
      ? tauxAffiches
      : f.tvaNonApplicable
        ? []
        : [{ taux: tauxTvaEffectif, base: baseTva, montant: tva }]

  // --- payments and deposit ------------------------------------------------
  const acompte = arrondi(borne(f.acompte ?? 0, 0, total))
  const netAPayer = arrondi(total - acompte)
  const encaisse = (f.paiements ?? []).reduce((somme, p) => somme + p.montant, 0)
  const paye = arrondi(borne(encaisse, 0, netAPayer))
  const reste = arrondi(netAPayer - paye)

  const etatPaiement: EtatPaiement =
    total > 0 && reste <= 0 ? 'paid' : paye > 0 ? 'partial' : 'unpaid'

  const estAvoir = (f.type ?? 'facture') === 'avoir'
  const echeance = f.echeance?.trim()

  return {
    sousTotal,
    remise,
    remiseEnMontant: remiseFixe > 0,
    afficheRemise: remise > 0,
    totalHt: totalHtAffiche,
    surcharge,
    tva,
    total,
    baseTva,
    tauxTvaEffectif,
    tauxSurchargeTva: tauxCharge,
    lignesTva,
    lignesTvaImprimees,
    acompte,
    netAPayer,
    paye,
    reste,
    etatPaiement,
    estSoldee: total > 0 && etatPaiement === 'paid',
    estAvoir,
    echeanceImprimee: estAvoir || !echeance ? null : echeance,
  }
}
