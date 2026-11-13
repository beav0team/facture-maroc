// The price list is the single source of truth, so it earns its own tests: a
// tier that disappears, a price that drifts or two cards marked « Recommandé »
// is a bug the marketing page would happily render.

import { describe, expect, it } from 'vitest'
import { CONTACT_ESSENTIEL, CONTACT_PRO, OFFRES, nomOffre } from '../src/lib/offres'

describe('offres', () => {
  it('propose exactement trois paliers, dans l’ordre du moins cher au plus cher', () => {
    expect(OFFRES.map((offre) => offre.cle)).toEqual(['gratuit', 'essentiel', 'pro'])
    expect(OFFRES.map((offre) => offre.nom)).toEqual(['Gratuit', 'Essentiel', 'Pro'])
  })

  it('garde les prix annoncés : 0, 240 et 480 DH par an', () => {
    expect(OFFRES.map((offre) => offre.prix)).toEqual(['0 DH', '240 DH', '480 DH'])
    expect(OFFRES[0].periode).toBe('pour toujours')
    expect(OFFRES.slice(1).map((offre) => offre.periode)).toEqual(['par an', 'par an'])
  })

  it('reste sous les 990 DH de l’ancien tarif Pro', () => {
    const montants = OFFRES.map((offre) => Number.parseInt(offre.prix, 10))
    expect(Math.max(...montants)).toBeLessThan(990)
  })

  it('ne recommande qu’un seul palier, et c’est le payant d’entrée', () => {
    expect(OFFRES.filter((offre) => offre.principal).map((offre) => offre.cle)).toEqual(['essentiel'])
  })

  it('garde le gratuit à cinq documents et Essentiel à cinquante', () => {
    expect(OFFRES[0].points[0]).toContain('5 documents')
    expect(OFFRES[1].points[0]).toContain('50 documents')
    expect(OFFRES[2].points[0]).toContain('illimités')
  })

  it('envoie les demandes payantes à la bonne adresse, avec le bon sujet', () => {
    expect(OFFRES[0].lien).toBe('/app/')
    expect(OFFRES[1].lien).toBe(CONTACT_ESSENTIEL)
    expect(OFFRES[2].lien).toBe(CONTACT_PRO)
    expect(CONTACT_ESSENTIEL).toContain('contact@factureapp.ma')
    expect(CONTACT_ESSENTIEL).toContain('Essentiel')
    expect(CONTACT_PRO).toContain('Pro')
  })

  it('ne cite aucun concurrent et dit que rien ne se renouvelle tout seul', () => {
    const texte = JSON.stringify(OFFRES)
    expect(texte).not.toMatch(/Factura MA|Fatora|ClickFacture|Facturaty|Hisab|Sundaya|Sage/)
    for (const offre of OFFRES.slice(1)) {
      expect(offre.points.join(' ')).toContain('sans reconduction automatique')
    }
  })

  it('nomme un palier, et retombe sur Gratuit pour une clé inconnue', () => {
    expect(nomOffre('gratuit')).toBe('Gratuit')
    expect(nomOffre('essentiel')).toBe('Essentiel')
    expect(nomOffre('pro')).toBe('Pro')
    expect(nomOffre('platine' as never)).toBe('Gratuit')
  })
})
