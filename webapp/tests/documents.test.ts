// Reading a database row back into a document.
//
// The traps this file pins: PostgREST may return a numeric column as a string,
// a line's `tva_taux` may be null (meaning "the document's rate"), payment rows
// use `date_paiement`/`moyen`, and the history line must never read "Payée"
// while money is owed.

import { describe, expect, it } from 'vitest'
import {
  brouillonVide,
  clientDeSnapshot,
  enteteDeFacture,
  factureDeDocument,
  lignesDeFacture,
  quotaAtteint,
  statutDocument,
  typeCompteDansQuota,
  texteExpiration,
  texteQuota,
  type DocumentRow,
  type LigneRow,
  type PaiementRow,
} from '../src/lib/documents'
import { calculer } from '../src/lib/invoice'

function row(partiel: Partial<DocumentRow> = {}): DocumentRow {
  return {
    id: '00000000-0000-4000-8000-000000000001',
    shop_id: '00000000-0000-4000-8000-000000000002',
    type: 'facture',
    statut: 'emis',
    numero: 'F-20261110-0001',
    date_emission: '10/11/2026',
    date_validite: null,
    echeance: '10/12/2026',
    client_id: null,
    client_snapshot: { nom: 'Garage Atlas', ice: '001234567000012' },
    reference: '',
    incoterm: '',
    tva_non_applicable: false,
    taux_tva: 20,
    arrondi_dirham: false,
    conditions_paiement: '30j',
    clause_retard: '',
    note_client: '',
    note_remerciement: '',
    surcharge_libelle: '',
    surcharge_montant: 0,
    surcharge_pourcent: 0,
    remise_pourcent: 0,
    remise_montant: 0,
    acompte_montant: 0,
    acompte_reference: '',
    design: 'classique',
    total_ht: 100,
    remise: 0,
    surcharge: 0,
    tva: 20,
    total_ttc: 120,
    net_a_payer: 120,
    shop_snapshot: null,
    emis_le: '2026-11-10T10:00:00Z',
    created_at: '2026-11-10T10:00:00Z',
    updated_at: '2026-11-10T10:00:00Z',
    ...partiel,
  }
}

function ligne(partiel: Partial<LigneRow> = {}): LigneRow {
  return {
    id: '00000000-0000-4000-8000-000000000003',
    document_id: '00000000-0000-4000-8000-000000000001',
    ordre: 0,
    article_id: null,
    libelle: 'Huile 5W30',
    quantite: 2,
    unite: 'bidon',
    prix_unitaire: 50,
    remise_pourcent: 0,
    remise_montant: 0,
    exoneree: false,
    tva_taux: 20,
    total_ht: 100,
    attributs: [],
    ...partiel,
  }
}

describe('factureDeDocument', () => {
  it('relit les montants texte de PostgREST', () => {
    const f = factureDeDocument(
      row({
        remise_pourcent: '10' as unknown as number,
        remise_montant: '0' as unknown as number,
        acompte_montant: '20.5' as unknown as number,
      }),
      [ligne({ prix_unitaire: '55.75' as unknown as number })],
    )
    expect(f.remisePourcent).toBe(10)
    expect(f.acompte).toBe(20.5)
    expect(f.lignes[0]?.prixUnitaire).toBe(55.75)
  })

  it('trie les lignes et garde tva_taux null comme taux du document', () => {
    const f = factureDeDocument(row({ taux_tva: 10 }), [
      ligne({ ordre: 1, libelle: 'Deuxième' }),
      ligne({ ordre: 0, libelle: 'Première', tva_taux: null }),
    ])
    expect(f.lignes.map((l) => l.description)).toEqual(['Première', 'Deuxième'])
    expect(f.lignes[0]?.tauxTva).toBeNull()
    expect(calculer(f).tauxTvaEffectif).toBe(10)
  })

  it('relit l’avoir et les paiements', () => {
    const paiements: PaiementRow[] = [
      {
        id: 'p1',
        document_id: 'd1',
        montant: '50' as unknown as number,
        date_paiement: '12/11/2026',
        moyen: 'cash',
        reference: 'Caisse',
        created_at: '2026-11-12T09:00:00Z',
      },
    ]
    const f = factureDeDocument(row({ type: 'avoir', echeance: null }), [ligne()], paiements)
    expect(f.type).toBe('avoir')
    expect(f.paiements?.[0]).toEqual({ montant: 50, date: '12/11/2026', methode: 'cash', note: 'Caisse' })
    expect(calculer(f).estAvoir).toBe(true)
    expect(calculer(f).echeanceImprimee).toBeNull()
  })

  it('lit le snapshot client, y compris l’exonération', () => {
    const client = clientDeSnapshot({ nom: 'Clinique', exonere_tva: true, if_fiscal: '123' })
    expect(client.nom).toBe('Clinique')
    expect(client.exonereTva).toBe(true)
    expect(client.ifFiscal).toBe('123')
  })
})

describe('enteteDeFacture', () => {
  it('produit le payload attendu par enregistrer_brouillon', () => {
    const facture = factureDeDocument(row({ design: 'facture_maroc' }), [ligne()])
    const entete = enteteDeFacture('boutique', facture, 'facture_maroc')
    expect(entete.shop_id).toBe('boutique')
    expect(entete.type).toBe('facture')
    expect(entete.taux_tva).toBe(20)
    expect(entete.design).toBe('facture_maroc')
    expect(entete.total_ttc).toBe(120)
    expect(entete.client_snapshot).toMatchObject({ nom: 'Garage Atlas', exonere_tva: false })
  })

  it('garde le total HT d’une ligne avant la remise du document', () => {
    const lignes = lignesDeFacture([
      { description: 'Service', quantite: 2, prixUnitaire: 100, remisePourcent: 10 },
    ])
    expect(lignes[0]?.total_ht).toBe(180)
    expect(lignes[0]?.libelle).toBe('Service')
  })
})

describe('statutDocument', () => {
  const jour = new Date(2026, 10, 15)

  it('un brouillon est un brouillon', () => {
    expect(statutDocument(row({ statut: 'brouillon' }), calculer(factureDeDocument(row(), [ligne()])))).toEqual({
      texte: 'Brouillon',
      ton: 'draft',
    })
  })

  it('un avoir ne dit rien', () => {
    const f = factureDeDocument(row({ type: 'avoir' }), [ligne()])
    expect(statutDocument(row({ type: 'avoir' }), calculer(f), jour)).toEqual({ texte: '', ton: 'neutral' })
  })

  it('un devis dit jusqu’à quand il vaut', () => {
    const f = factureDeDocument(row({ type: 'devis', date_validite: '20/11/2026' }), [ligne()])
    expect(statutDocument(row({ type: 'devis', date_validite: '20/11/2026' }), calculer(f), jour)).toEqual({
      texte: 'Devis · valable 20/11',
      ton: 'neutral',
    })
  })

  it('compte les jours de retard, jamais le jour de l’échéance', () => {
    const doc = row({ echeance: '10/11/2026' })
    const f = factureDeDocument(doc, [ligne()])
    expect(statutDocument(doc, calculer(f), jour)).toEqual({ texte: '5 j de retard', ton: 'overdue' })
    expect(statutDocument(doc, calculer(f), new Date(2026, 10, 10)).ton).not.toBe('overdue')
  })

  it('une facture soldée dit quand elle l’a été', () => {
    const doc = row()
    const f = factureDeDocument(doc, [ligne()], [
      {
        id: 'p1',
        document_id: doc.id,
        montant: 120,
        date_paiement: '14/11/2026',
        moyen: 'cash',
        reference: '',
        created_at: '',
      },
    ])
    expect(statutDocument(doc, calculer(f), jour, '14/11/2026')).toEqual({
      texte: 'Payée le 14/11',
      ton: 'paid',
    })
  })

  it('un paiement partiel dit ce qui reste dû', () => {
    const doc = row()
    const f = factureDeDocument(doc, [ligne()], [
      {
        id: 'p1',
        document_id: doc.id,
        montant: 20,
        date_paiement: '14/11/2026',
        moyen: 'cash',
        reference: '',
        created_at: '',
      },
    ])
    expect(statutDocument(doc, calculer(f), jour)).toEqual({ texte: '100,00 dû', ton: 'due' })
  })

  it('une facture sans échéance est impayée, sans faux retard', () => {
    const doc = row({ echeance: null })
    const f = factureDeDocument(doc, [ligne()])
    expect(statutDocument(doc, calculer(f), jour)).toEqual({ texte: 'Impayée', ton: 'neutral' })
  })

  it('une échéance future annonce la date', () => {
    const doc = row({ echeance: '05/12/2026' })
    const f = factureDeDocument(doc, [ligne()])
    expect(statutDocument(doc, calculer(f), jour)).toEqual({ texte: 'Échéance 05/12', ton: 'neutral' })
  })
})

describe('quota', () => {
  it('bloque la facture qui dépasse le palier', () => {
    expect(quotaAtteint({ plan: 'gratuit', paid_until: null, utilise: 4, limite: 5 })).toBe(false)
    expect(quotaAtteint({ plan: 'gratuit', paid_until: null, utilise: 5, limite: 5 })).toBe(true)
    expect(quotaAtteint({ plan: 'essentiel', paid_until: '2027-01-01', utilise: 49, limite: 50 })).toBe(false)
    expect(quotaAtteint({ plan: 'essentiel', paid_until: '2027-01-01', utilise: 50, limite: 50 })).toBe(true)
    expect(quotaAtteint({ plan: 'pro', paid_until: '2027-01-01', utilise: 99, limite: null })).toBe(false)
  })

  it('écrit le quota comme un humain le lit', () => {
    expect(texteQuota({ plan: 'gratuit', paid_until: null, utilise: 2, limite: 5 })).toBe(
      '2 / 5 documents ce mois',
    )
    expect(texteQuota({ plan: 'essentiel', paid_until: '2027-01-01', utilise: 12, limite: 50 })).toBe(
      '12 / 50 documents ce mois',
    )
    expect(texteQuota({ plan: 'pro', paid_until: null, utilise: 0, limite: null })).toBe(
      'Documents illimités',
    )
  })

  it('annonce la fin d’un abonnement, et rien quand il court encore', () => {
    expect(texteExpiration({ plan: 'gratuit', paid_until: null, utilise: 0, limite: 5 })).toBe('')
    expect(
      texteExpiration({
        plan: 'gratuit',
        paid_until: '2026-10-01',
        utilise: 0,
        limite: 5,
        expire: true,
      }),
    ).toBe('Votre abonnement s’est terminé le 01/10/2026. Vous êtes repassé au plan gratuit.')
  })
})

describe('typeCompteDansQuota', () => {
  it('compte tout ce qui facture, jamais un devis ni un avoir', () => {
    for (const type of ['facture', 'ticket', 'acompte', 'regul', 'simplifiee', 'export'] as const) {
      expect(typeCompteDansQuota(type), type).toBe(true)
    }
    for (const type of ['devis', 'bon_commande', 'proforma', 'avoir'] as const) {
      expect(typeCompteDansQuota(type), type).toBe(false)
    }
    expect(typeCompteDansQuota(undefined)).toBe(false)
  })
})

describe('brouillonVide', () => {
  it('date le brouillon du jour et reprend les réglages de la boutique', () => {
    const saisie = brouillonVide('devis', 10, 'facture_maroc', new Date(2026, 10, 11))
    expect(saisie.id).toBeNull()
    expect(saisie.statut).toBe('brouillon')
    expect(saisie.design).toBe('facture_maroc')
    expect(saisie.facture.date).toBe('11/11/2026')
    expect(saisie.facture.tauxTva).toBe(10)
    expect(saisie.facture.type).toBe('devis')
    expect(saisie.facture.lignes).toEqual([])
  })
})

// Le message d'erreur d'une émission refusée doit dire quoi corriger et où.
// `emettre_document` lève une exception avec un `hint` (« Renseignez-le dans
// Paramètres → Boutique ») : sans lui, l'utilisateur lit ce qui manque sans
// savoir où le mettre.
describe('messageErreur', () => {
  it('ajoute le hint, quel que soit le code SQL', async () => {
    const { messageErreur } = await import('../src/lib/documentsApi')
    expect(
      messageErreur({
        message: 'L’ICE de votre boutique manque : il est obligatoire sur une facture.',
        hint: 'Renseignez-le dans Paramètres → Boutique.',
        code: '22023',
      }),
    ).toBe(
      'L’ICE de votre boutique manque : il est obligatoire sur une facture. Renseignez-le dans Paramètres → Boutique.',
    )
  })

  it('ne répète pas le hint déjà contenu dans le message', async () => {
    const { messageErreur } = await import('../src/lib/documentsApi')
    expect(messageErreur({ message: 'Déjà là. Le hint.', hint: 'Le hint.' })).toBe('Déjà là. Le hint.')
  })
})
