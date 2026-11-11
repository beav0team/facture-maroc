// The archived PDF.
//
// pdf-lib is a real PDF writer, so this suite asserts what the customer gets:
// a valid file, the same bytes twice for the same document (the archive hash
// must not change between a reprint and the original), French accents that do
// not throw, and a watermark only on the free plan.

import { describe, expect, it } from 'vitest'
import { assainir, creerPdf, creerRecuPdf, sha256Hex } from '../src/lib/pdf'
import { cheminArchive, nomFichier } from '../src/lib/archive'
import type { Facture } from '../src/lib/invoice'
import type { SnapshotBoutique } from '../src/lib/documents'

const boutique: SnapshotBoutique = {
  nom: 'Atelier Atlas',
  adresse: '12 rue des Fleurs, Casablanca',
  telephone: '0612345678',
  email: 'contact@atlas.ma',
  ice: '001234567000012',
  if_fiscal: '12345678',
  rc: '98765',
  patente: '112233',
  cnss: '445566',
  banque: 'Banque Populaire',
  rib: '123 456 7890 1234',
  rib_titulaire: 'Atelier Atlas',
  tva_defaut: 20,
  tva_non_applicable: false,
  prix_ttc: false,
  logo_path: null,
}

const facture: Facture = {
  numero: 'F-20261111-0001',
  date: '11/11/2026',
  echeance: '11/12/2026',
  client: {
    nom: 'Garage Atlas',
    adresse: 'Zone industrielle, Rabat',
    ice: '001234567000012',
    telephone: '0600000000',
  },
  lignes: [
    { description: 'Huile moteur 5W30', quantite: 2, prixUnitaire: 99.99, unite: 'bidon' },
    { description: 'Main-d’œuvre — vidange', quantite: 1, prixUnitaire: 150 },
  ],
  tauxTva: 20,
  conditionsPaiement: '30j',
  noteRemerciement: 'Merci de votre confiance.',
  clauseRetard: 'Tout retard entraîne des pénalités de 1 % par mois.',
}

function octets(bytes: Uint8Array): string {
  return new TextDecoder('latin1').decode(bytes)
}

describe('PDF', () => {
  it('produit un fichier PDF valide', async () => {
    const bytes = await creerPdf({ facture, boutique })
    expect(octets(bytes).startsWith('%PDF-')).toBe(true)
    expect(octets(bytes)).toContain('%%EOF')
  })

  it('un reçu tient sur une page et reste valide', async () => {
    const bytes = await creerRecuPdf({
      facture,
      boutique,
      paiement: { montant: 99.5, date: '12/11/2026', moyen: 'cash', reference: 'Caisse' },
    })
    expect(octets(bytes).startsWith('%PDF-')).toBe(true)
    expect(octets(bytes)).toContain('/Count 1')
  })

  it('produit exactement les mêmes octets pour le même document', async () => {
    const premier = await creerPdf({ facture, boutique })
    const second = await creerPdf({ facture, boutique })
    expect(await sha256Hex(premier)).toBe(await sha256Hex(second))
  })

  it('accents et espaces fines ne font pas planter le dessin', async () => {
    const avecAccents: Facture = {
      ...facture,
      lignes: [
        { description: 'Régulation — électrique \u202Ftest', quantite: 1, prixUnitaire: 1234.5 },
        { description: 'Café', quantite: 3, prixUnitaire: 12 },
      ],
    }
    const bytes = await creerPdf({ facture: avecAccents, boutique })
    expect(bytes.byteLength).toBeGreaterThan(1000)
  })

  it('le filigrane ne change pas la validité du fichier', async () => {
    const libre = await creerPdf({ facture, boutique })
    const gratuit = await creerPdf({ facture, boutique, filigrane: 'FACTUREAPP · GRATUIT' })
    expect(gratuit.byteLength).toBeGreaterThan(1000)
    expect(await sha256Hex(gratuit)).not.toBe(await sha256Hex(libre))
  })

  it('le modèle facture_maroc rend aussi un PDF', async () => {
    const bytes = await creerPdf({ facture, boutique, design: 'facture_maroc' })
    expect(octets(bytes).startsWith('%PDF-')).toBe(true)
  })

  it('pagine un document long', async () => {
    const long: Facture = {
      ...facture,
      lignes: Array.from({ length: 70 }, (_, index) => ({
        description: `Ligne ${index + 1} — pièce détachée`,
        quantite: 1,
        prixUnitaire: 10 + index,
      })),
    }
    const bytes = await creerPdf({ facture: long, boutique })
    expect(octets(bytes)).toContain('/Count 3')
  })
})

describe('assainir', () => {
  it('remplace ce que WinAnsi ne sait pas écrire', () => {
    expect(assainir('1\u202F234,50')).toBe('1 234,50')
    expect(assainir('−12,00')).toBe('-12,00')
    expect(assainir('l’atelier')).toBe("l'atelier")
    expect(assainir('électrique')).toBe('électrique')
    expect(assainir('日本')).toBe('')
  })
})

describe('chemins d’archive', () => {
  it('range le PDF sous la boutique puis le document', () => {
    expect(nomFichier('F-20261111-0001')).toBe('F-20261111-0001.pdf')
    expect(nomFichier('')).toBe('document.pdf')
    // Premier segment = boutique : c'est ce que vérifie la politique Storage.
    expect(cheminArchive('boutique', 'document')).toBe('boutique/document.pdf')
  })
})
