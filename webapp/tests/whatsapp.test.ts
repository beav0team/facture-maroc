// Le numéro WhatsApp est un réglage, pas une constante : il se normalise, se met
// en lien avec le bon message, et son absence laisse la page utilisable. Ces
// tests fixent les trois, parce qu'un numéro faux n'affiche pas d'erreur — il
// envoie un client au mauvais endroit.

import { describe, expect, it } from 'vitest'
import { afficherNumero, lienWhatsApp, messageWhatsApp, normaliserNumero, numeroValide } from '../src/lib/whatsapp'

describe('normaliserNumero', () => {
  it('ramène un numéro marocain local au format wa.me', () => {
    expect(normaliserNumero('06 12 34 56 78')).toBe('212612345678')
    expect(normaliserNumero('0612345678')).toBe('212612345678')
    expect(normaliserNumero('06-12-34-56-78')).toBe('212612345678')
    expect(normaliserNumero('07 00 11 22 33')).toBe('212700112233')
  })

  it('accepte le format international avec ou sans +', () => {
    expect(normaliserNumero('+212612345678')).toBe('212612345678')
    expect(normaliserNumero('212 (6) 12.34.56.78')).toBe('212612345678')
    expect(normaliserNumero('+33 6 12 34 56 78')).toBe('33612345678')
  })

  it('refuse un texte, un numéro trop court ou un zéro de tête', () => {
    expect(normaliserNumero('bonjour')).toBe('')
    expect(normaliserNumero('0612')).toBe('')
    expect(normaliserNumero('')).toBe('')
    expect(normaliserNumero(null)).toBe('')
    expect(normaliserNumero(undefined)).toBe('')
  })

  it('dit la même chose que le SQL sur une saisie vide', () => {
    expect(numeroValide('   ')).toBe(false)
    expect(numeroValide('06 12 34 56 78')).toBe(true)
  })
})

describe('afficherNumero', () => {
  it('rend un numéro marocain lisible à la française', () => {
    expect(afficherNumero('212612345678')).toBe('06 12 34 56 78')
  })

  it('garde le + pour un numéro étranger, et rien pour un vide', () => {
    expect(afficherNumero('33612345678')).toBe('+33612345678')
    expect(afficherNumero('')).toBe('')
  })
})

describe('messageWhatsApp', () => {
  it('nomme le plan et son prix, lus dans offres.ts', () => {
    expect(messageWhatsApp('essentiel')).toContain('Essentiel')
    expect(messageWhatsApp('essentiel')).toContain('240 DH par an')
    expect(messageWhatsApp('pro')).toContain('Pro')
    expect(messageWhatsApp('pro')).toContain('480 DH par an')
  })

  it('demande le nom de la boutique, sans rien promettre d’autre', () => {
    const message = messageWhatsApp('pro')
    expect(message).toContain('Nom de ma boutique')
    expect(message).not.toMatch(/carte bancaire|CB|paiement en ligne/)
  })
})

describe('lienWhatsApp', () => {
  it('construit un lien wa.me encodé', () => {
    const lien = lienWhatsApp('06 12 34 56 78', 'essentiel')
    expect(lien.startsWith('https://wa.me/212612345678?text=')).toBe(true)
    // le texte décodé est bien le message, retours à la ligne compris
    const texte = decodeURIComponent(lien.split('?text=')[1])
    expect(texte).toBe(messageWhatsApp('essentiel'))
    expect(lien).not.toContain(' ')
  })

  it('rend une chaîne vide quand aucun numéro n’est réglé', () => {
    expect(lienWhatsApp('', 'pro')).toBe('')
    expect(lienWhatsApp('bonjour', 'pro')).toBe('')
  })
})
