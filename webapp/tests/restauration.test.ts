// Relire un export : le fichier doit rentrer dans la boutique.
//
// Ce qui compte ici : refuser un fichier étranger en entier (pas à moitié),
// rendre les documents en brouillons, et ne jamais réattribuer un numéro déjà
// imprimé.

import { describe, expect, it } from 'vitest'
import {
  lireExport,
  preparerRestauration,
  resumeRestauration,
  TAILLE_MAX_EXPORT,
} from '../src/lib/restauration'

const DOC_ID = '11111111-1111-4111-8111-111111111111'

function exportValide(partiel: Record<string, unknown> = {}) {
  return {
    application: 'factureapp',
    format: 'export',
    version: 1,
    exporte_le: '2026-11-12T10:00:00.000Z',
    boutique: { nom: 'Atelier Atlas', ice: '001234567000012', if_fiscal: '12345678' },
    clients: [{ nom: 'Garage Atlas', role: 'client' }],
    articles: [{ libelle: 'Huile 5W30', prix: 99 }],
    documents: [
      {
        id: DOC_ID,
        type: 'facture',
        statut: 'emis',
        numero: 'F-20261111-0001',
        date_emission: '11/11/2026',
        client_snapshot: { nom: 'Garage Atlas', ice: '001234567000012' },
        reference: '',
        taux_tva: 20,
        design: 'classique',
      },
    ],
    lignes: [
      {
        document_id: DOC_ID,
        ordre: 0,
        libelle: 'Huile 5W30',
        quantite: 2,
        prix_unitaire: 100,
        tva_taux: 20,
        total_ht: 200,
      },
    ],
    paiements: [{ document_id: DOC_ID, montant: 100, date_paiement: '12/11/2026', moyen: 'cash' }],
    compteurs: { facture: 3 },
    note: '',
    ...partiel,
  }
}

describe('lireExport', () => {
  it('accepte un export FactureApp', () => {
    const lu = lireExport(JSON.stringify(exportValide()))
    expect(lu.erreur).toBeNull()
    expect(lu.donnees?.boutique.nom).toBe('Atelier Atlas')
  })

  it('refuse un fichier qui n’est pas du JSON', () => {
    expect(lireExport('pas du json').erreur).toContain('JSON')
  })

  it('refuse un JSON qui n’est pas un export FactureApp', () => {
    const lu = lireExport(JSON.stringify({ application: 'autre', format: 'export' }))
    expect(lu.erreur).toContain('pas un export FactureApp')
  })

  it('refuse un export d’une version future', () => {
    const lu = lireExport(JSON.stringify(exportValide({ version: 9 })))
    expect(lu.erreur).toContain('plus récente')
  })

  it('refuse un export incomplet au lieu d’en importer la moitié', () => {
    const lu = lireExport(JSON.stringify(exportValide({ lignes: undefined })))
    expect(lu.erreur).toContain('lignes')
  })
})

describe('preparerRestauration', () => {
  it('rend le document en brouillon, sans son numéro, avec la référence', () => {
    const lu = lireExport(JSON.stringify(exportValide()))
    const plan = preparerRestauration(lu.donnees!).donnees!
    expect(plan.documents).toHaveLength(1)
    const document = plan.documents[0]!
    expect(document.facture.numero).toBe('')
    expect(document.facture.reference).toBe('Restauration F-20261111-0001')
    expect(document.facture.lignes[0]?.description).toBe('Huile 5W30')
    expect(plan.compteurs).toEqual({ facture: 3 })
  })

  it('compte les paiements qu’elle ne restaure pas', () => {
    const lu = lireExport(JSON.stringify(exportValide()))
    const plan = preparerRestauration(lu.donnees!).donnees!
    expect(plan.paiementsNonRestaures).toBe(1)
    expect(resumeRestauration(plan)).toContain('1 paiement non restauré')
  })

  it('garde une référence existante et y ajoute la sienne', () => {
    const base = exportValide()
    ;(base.documents[0] as Record<string, unknown>).reference = 'Commande 42'
    const lu = lireExport(JSON.stringify(base))
    const document = preparerRestauration(lu.donnees!).donnees!.documents[0]!
    expect(document.facture.reference).toBe('Commande 42 · Restauration F-20261111-0001')
  })

  it('ignore un document sans identifiant et attache les lignes au bon document', () => {
    const base = exportValide()
    base.documents.push({ numero: 'F-sans-id' } as never)
    const lu = lireExport(JSON.stringify(base))
    const plan = preparerRestauration(lu.donnees!).donnees!
    expect(plan.documents).toHaveLength(1)
    expect(plan.documents[0]!.facture.lignes).toHaveLength(1)
  })
})

describe('taille maximale', () => {
  it('plafonne à 32 Mo, comme l’import Android', () => {
    expect(TAILLE_MAX_EXPORT).toBe(32 * 1024 * 1024)
  })
})
