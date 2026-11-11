// Reading the Android backup format.
//
// The file is written by `Backup.kt` / `InvoiceJson.kt`; the field names in the
// sample below are copied from those writers. A parser that invents a key would
// import zeroes and look successful, which is why every mapping is asserted.

import { describe, expect, it } from 'vitest'
import { lireSauvegarde } from '../src/lib/importAndroid'

function sauvegarde(): string {
  return JSON.stringify({
    app: 'facture-maroc',
    format: 'backup',
    version: 1,
    exportedAt: 1762000000000,
    shop: {
      name: 'Atelier Atlas',
      address: '12 rue des Fleurs',
      phone: '0612345678',
      email: 'contact@atlas.ma',
      ice: '001234567000012',
      ifNumber: '12345678',
      rc: '98765',
      patente: '112233',
      cnss: '445566',
      vatPercent: 20,
      vatNotApplicable: false,
      pricesIncludeVat: true,
    },
    clients: [
      {
        id: 'c1',
        name: 'Garage Atlas',
        address: 'Zone industrielle',
        ice: '001234567000012',
        ifNumber: '111',
        rc: '',
        patente: '',
        cnss: '',
        phone: '0600000000',
        email: '',
        vatExempt: false,
        role: 'client',
      },
      {
        id: 'f1',
        name: 'Pièces du Nord',
        role: 'supplier',
        vatExempt: true,
      },
    ],
    items: [
      {
        id: 'a1',
        label: 'Huile 5W30',
        price: 99.99,
        unit: 'bidon',
        folder: 'Moteur',
        barcode: '6111234567890',
        purchasePrice: 70,
        stock: 12,
        reorderThreshold: 3,
        priceTiers: [{ fromQuantity: 5, unitPrice: 89.9 }],
        attributes: [{ key: 'Marque', value: 'Total' }],
      },
      { id: 'a2', label: 'Vidange', price: 150, unit: '', folder: '', barcode: '' },
    ],
    invoices: [
      {
        id: 'd1',
        status: 'issued',
        createdAt: 1762000000000,
        updatedAt: 1762000000000,
        data: {
          number: 'F-20261111-0042',
          date: '11/11/2026',
          client: {
            name: 'Garage Atlas',
            address: 'Zone industrielle',
            ice: '001234567000012',
            ifNumber: '111',
            vatExempt: false,
            role: 'client',
          },
          lines: [
            {
              description: 'Huile 5W30',
              quantity: 2,
              unitPrice: 83.325,
              unit: 'bidon',
              lineDiscountPercent: 10,
              taxExempt: false,
              vatPercent: 20,
              ttcPrice: 99.99,
            },
            {
              description: 'Main-d’œuvre',
              quantity: 1,
              unitPrice: 150,
              taxExempt: true,
            },
          ],
          discountPercent: 0,
          vatPercent: 20,
          paymentMethod: 'cash',
          documentType: 'facture',
          dueDate: '11/12/2026',
          referenceNumber: 'BON-12',
          vatNotApplicable: false,
          customerNote: 'Merci',
          paymentTerms: '30j',
          surchargeAmount: 0,
          payments: [{ paidAmount: 50, paidAt: '12/11/2026', note: 'Caisse', method: 'cash' }],
        },
      },
      {
        id: 'd2',
        status: 'draft',
        data: { number: '', date: '11/11/2026', client: {}, lines: [], vatPercent: 20 },
      },
    ],
    counters: { facture: 42, devis: 7 },
  })
}

describe('import Android', () => {
  it('lit la boutique, les clients, les articles et les documents', () => {
    const resultat = lireSauvegarde(sauvegarde())
    expect(resultat.erreur).toBeNull()
    const sauvegardeLue = resultat.donnees
    expect(sauvegardeLue?.boutique).toMatchObject({
      nom: 'Atelier Atlas',
      if_fiscal: '12345678',
      tva_defaut: 20,
      prix_ttc: true,
    })
    expect(sauvegardeLue?.clients).toHaveLength(2)
    expect(sauvegardeLue?.clients[1]?.role).toBe('fournisseur')
    expect(sauvegardeLue?.clients[1]?.exonere_tva).toBe(true)
    expect(sauvegardeLue?.articles[0]).toMatchObject({
      libelle: 'Huile 5W30',
      prix: 99.99,
      stock: 12,
      seuil_rea: 3,
      paliers: [{ fromQuantity: 5, unitPrice: 89.9 }],
    })
    expect(sauvegardeLue?.articles[1]?.stock).toBeNull()
    expect(sauvegardeLue?.compteurs).toEqual({ facture: 42, devis: 7 })
  })

  it('relit une facture avec ses lignes, sa remise et ses paiements', () => {
    const sauvegardeLue = lireSauvegarde(sauvegarde()).donnees
    const document = sauvegardeLue?.documents[0]
    expect(document?.numero).toBe('F-20261111-0042')
    expect(document?.statut).toBe('emis')
    expect(document?.facture.type).toBe('facture')
    expect(document?.facture.client.ice).toBe('001234567000012')
    expect(document?.facture.lignes[0]).toMatchObject({
      description: 'Huile 5W30',
      quantite: 2,
      prixUnitaire: 83.325,
      remisePourcent: 10,
      tauxTva: 20,
      prixTtc: 99.99,
    })
    expect(document?.facture.lignes[1]?.exoneree).toBe(true)
    expect(document?.facture.echeance).toBe('11/12/2026')
    expect(document?.facture.reference).toBe('BON-12')
    expect(document?.facture.paiements?.[0]).toMatchObject({ montant: 50, methode: 'cash' })
    expect(sauvegardeLue?.documents[1]?.statut).toBe('brouillon')
  })

  it('refuse un fichier qui n’est pas une sauvegarde', () => {
    expect(lireSauvegarde('{"app":"autre","format":"backup"}').erreur).toContain('pas une sauvegarde')
    expect(lireSauvegarde('pas du json').erreur).toContain('JSON valide')
    expect(lireSauvegarde(JSON.stringify({ app: 'facture-maroc', format: 'backup', version: 9 })).erreur).toContain(
      'non prise en charge',
    )
  })

  it('ignore une ligne sans désignation et un document sans données', () => {
    const brut = JSON.parse(sauvegarde())
    brut.invoices[0].data.lines.push({ description: '' })
    brut.invoices.push({ id: 'x', status: 'issued' })
    const lu = lireSauvegarde(JSON.stringify(brut)).donnees
    expect(lu?.documents[0]?.facture.lignes).toHaveLength(2)
    expect(lu?.documents).toHaveLength(2)
  })
})
