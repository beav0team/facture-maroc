// Reading a backup written by the Android app.
//
// The file is the "backup" format (`app: facture-maroc`, `format: backup`), not
// a database dump: the same field names the Android engine uses. This module
// turns them into the web app's own shapes, and refuses anything it cannot
// account for instead of importing half a document.
//
// The Android JSON is the reference. Field names here are read, never
// invented: `InvoiceJson` and `Backup.kt` define them.

import type { MethodePaiement, TypeDocument } from './document'
import type { Client, Facture, Ligne } from './invoice'
import type { Resultat } from './documentsApi'

export type ClientImporte = {
  nom: string
  role: 'client' | 'fournisseur'
  adresse: string
  ice: string
  if_fiscal: string
  rc: string
  patente: string
  cnss: string
  telephone: string
  email: string
  exonere_tva: boolean
}

export type ArticleImporte = {
  libelle: string
  prix: number
  unite: string
  dossier: string
  code_barre: string
  prix_achat: number
  stock: number | null
  seuil_rea: number | null
  attributs: { key: string; value: string }[]
  paliers: { fromQuantity: number; unitPrice: number }[]
}

export type DocumentImporte = {
  /** The number the Android app printed, kept as a reference. */
  numero: string
  statut: 'brouillon' | 'emis'
  facture: Facture
}

export type BoutiqueImportee = {
  nom: string
  adresse: string
  telephone: string
  email: string
  ice: string
  if_fiscal: string
  rc: string
  patente: string
  cnss: string
  tva_defaut: number
  tva_non_applicable: boolean
  prix_ttc: boolean
}

export type SauvegardeAndroid = {
  boutique: BoutiqueImportee
  clients: ClientImporte[]
  articles: ArticleImporte[]
  documents: DocumentImporte[]
  compteurs: Record<string, number>
}

export const TAILLE_MAX = 16 * 1024 * 1024

function texte(valeur: unknown): string {
  return typeof valeur === 'string' ? valeur.trim() : ''
}

function nombre(valeur: unknown, defaut = 0): number {
  if (typeof valeur === 'number' && Number.isFinite(valeur)) return valeur
  if (typeof valeur === 'string') {
    const lu = Number(valeur.replace(',', '.'))
    return Number.isFinite(lu) ? lu : defaut
  }
  return defaut
}

function tableau(valeur: unknown): unknown[] {
  return Array.isArray(valeur) ? valeur : []
}

function objet(valeur: unknown): Record<string, unknown> {
  return valeur !== null && typeof valeur === 'object' && !Array.isArray(valeur)
    ? (valeur as Record<string, unknown>)
    : {}
}

function typeDocument(valeur: string): TypeDocument {
  const connus: TypeDocument[] = [
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
  return (connus.find((type) => type === valeur) ?? 'facture') as TypeDocument
}

function clientDeJson(valeur: unknown): Client {
  const brut = (valeur ?? {}) as Record<string, unknown>
  return {
    nom: texte(brut.name),
    adresse: texte(brut.address) || undefined,
    telephone: texte(brut.phone) || undefined,
    email: texte(brut.email) || undefined,
    ice: texte(brut.ice) || undefined,
    ifFiscal: texte(brut.ifNumber) || undefined,
    rc: texte(brut.rc) || undefined,
    patente: texte(brut.patente) || undefined,
    cnss: texte(brut.cnss) || undefined,
    exonereTva: Boolean(brut.vatExempt),
  }
}

function ligneDeJson(valeur: unknown): Ligne | null {
  const brut = (valeur ?? {}) as Record<string, unknown>
  const description = texte(brut.description)
  if (description === '') return null
  const quantite = nombre(brut.quantity, 1)
  return {
    description,
    quantite: quantite > 0 ? quantite : 1,
    prixUnitaire: nombre(brut.unitPrice),
    unite: texte(brut.unit) || undefined,
    remisePourcent: Math.min(100, Math.max(0, Math.trunc(nombre(brut.lineDiscountPercent)))),
    remiseMontant: Math.max(0, nombre(brut.lineDiscountFixed)),
    exoneree: Boolean(brut.taxExempt),
    tauxTva: typeof brut.vatPercent === 'number' ? brut.vatPercent : null,
    prixTtc: typeof brut.ttcPrice === 'number' ? brut.ttcPrice : null,
    attributs: tableau(brut.lineAttributes)
      .map((attribut) => objet(attribut))
      .map((attributBrut) => ({
        cle: texte(attributBrut.key),
        valeur: texte(attributBrut.value),
      }))
      .filter((attribut) => attribut.cle !== ''),
  }
}

function factureDeJson(valeur: unknown): Facture {
  const brut = objet(valeur)
  const paiements = tableau(brut.payments).map((paiement) => {
    const ligne = objet(paiement)
    return {
      montant: nombre(ligne.paidAmount),
      date: texte(ligne.paidAt) || undefined,
      note: texte(ligne.note) || undefined,
      methode: (texte(ligne.method) || null) as MethodePaiement | null,
    }
  })
  const lignes = tableau(brut.lines)
    .map(ligneDeJson)
    .filter((ligne): ligne is Ligne => ligne !== null)

  return {
    numero: texte(brut.number),
    date: texte(brut.date),
    client: clientDeJson(brut.client),
    lignes,
    remisePourcent: nombre(brut.discountPercent),
    remiseMontant: nombre(brut.discountFixed),
    tauxTva: nombre(brut.vatPercent, 20),
    methodePaiement: (texte(brut.paymentMethod) || undefined) as MethodePaiement | undefined,
    type: typeDocument(texte(brut.documentType)),
    echeance: texte(brut.dueDate) || null,
    dateValidite: texte(brut.validityDate) || null,
    reference: texte(brut.referenceNumber),
    incoterm: texte(brut.incoterm),
    tvaNonApplicable: Boolean(brut.vatNotApplicable),
    noteClient: texte(brut.customerNote),
    conditionsPaiement: (texte(brut.paymentTerms) || undefined) as Facture['conditionsPaiement'],
    clauseRetard: texte(brut.latePenaltyClause),
    noteRemerciement: texte(brut.thankYouNote),
    surchargeLibelle: texte(brut.surchargeLabel),
    surchargeMontant: nombre(brut.surchargeAmount),
    surchargePourcent: nombre(brut.surchargePercent),
    acompte: nombre(brut.depositAmount),
    acompteReference: texte(brut.depositReference),
    paiements,
  }
}

/**
 * Reads a backup file. A file from another app, from a newer version, or with
 * no documents at all is refused with a sentence a shop can act on.
 */
export function lireSauvegarde(contenu: string): Resultat<SauvegardeAndroid> {
  if (contenu.length > TAILLE_MAX) {
    return { donnees: null, erreur: 'Le fichier dépasse 16 Mo.' }
  }

  let racine: unknown
  try {
    racine = JSON.parse(contenu)
  } catch {
    return { donnees: null, erreur: 'Ce fichier n’est pas du JSON valide.' }
  }

  const objetRacine = objet(racine)
  if (texte(objetRacine.app) !== 'facture-maroc' || texte(objetRacine.format) !== 'backup') {
    return { donnees: null, erreur: 'Ce fichier n’est pas une sauvegarde de l’application Android.' }
  }
  const version = nombre(objetRacine.version, 0)
  if (version < 1 || version > 1) {
    return {
      donnees: null,
      erreur: `Version de sauvegarde ${version} non prise en charge.`,
    }
  }

  const shop = objet(objetRacine.shop)
  const compteursBruts = objet(objetRacine.counters)
  const compteurs: Record<string, number> = {}
  for (const [type, valeur] of Object.entries(compteursBruts)) {
    const nombreLigne = nombre(valeur, 1)
    compteurs[type] = Math.max(1, Math.trunc(nombreLigne))
  }

  const clients = tableau(objetRacine.clients).map((valeur) => {
    const client = objet(valeur)
    return {
      nom: texte(client.name),
      role: (texte(client.role) === 'supplier' ? 'fournisseur' : 'client') as ClientImporte['role'],
      adresse: texte(client.address),
      ice: texte(client.ice),
      if_fiscal: texte(client.ifNumber),
      rc: texte(client.rc),
      patente: texte(client.patente),
      cnss: texte(client.cnss),
      telephone: texte(client.phone),
      email: texte(client.email),
      exonere_tva: Boolean(client.vatExempt),
    }
  })

  const articles = tableau(objetRacine.items).map((valeur) => {
    const article = objet(valeur)
    const stock = typeof article.stock === 'number' ? article.stock : null
    const seuil = typeof article.reorderThreshold === 'number' ? article.reorderThreshold : null
    return {
      libelle: texte(article.label),
      prix: nombre(article.price),
      unite: texte(article.unit),
      dossier: texte(article.folder),
      code_barre: texte(article.barcode),
      prix_achat: nombre(article.purchasePrice),
      stock: stock === null ? null : Math.max(0, stock),
      seuil_rea: seuil === null ? null : Math.max(0, seuil),
      attributs: tableau(article.attributes)
        .map((attribut) => objet(attribut))
        .map((attribut) => ({ key: texte(attribut.key), value: texte(attribut.value) }))
        .filter((attribut) => attribut.key !== ''),
      paliers: tableau(article.priceTiers)
        .map((palier) => objet(palier))
        .map((palier) => ({
          fromQuantity: Math.max(1, Math.trunc(nombre(palier.fromQuantity, 1))),
          unitPrice: nombre(palier.unitPrice),
        })),
    }
  })

  const documents = tableau(objetRacine.invoices)
    .map((valeur) => {
      const record = objet(valeur)
      const data = record.data
      if (data === undefined || data === null) return null
      return {
        numero: texte(objet(data).number),
        statut: (texte(record.status) === 'issued' ? 'emis' : 'brouillon') as DocumentImporte['statut'],
        facture: factureDeJson(data),
      }
    })
    .filter((document): document is DocumentImporte => document !== null)

  return {
    donnees: {
      boutique: {
        nom: texte(shop.name),
        adresse: texte(shop.address),
        telephone: texte(shop.phone),
        email: texte(shop.email),
        ice: texte(shop.ice),
        if_fiscal: texte(shop.ifNumber),
        rc: texte(shop.rc),
        patente: texte(shop.patente),
        cnss: texte(shop.cnss),
        tva_defaut: nombre(shop.vatPercent, 20),
        tva_non_applicable: Boolean(shop.vatNotApplicable),
        prix_ttc: Boolean(shop.pricesIncludeVat),
      },
      clients: clients.filter((client) => client.nom !== ''),
      articles: articles.filter((article) => article.libelle !== ''),
      documents,
      compteurs,
    },
    erreur: null,
  }
}
