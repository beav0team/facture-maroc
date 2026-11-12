// Port of `AmountWordsTest.kt`. The assertions are the Kotlin ones, translated
// one for one: the algorithm is the reference, so a divergence here means the
// web and the Android app would print different words for the same amount.

import { describe, expect, it } from 'vitest'
import { arreteEnLettres, montantEnLettres } from '../src/lib/lettres'

describe('montant en lettres', () => {
  it('a whole amount ends without a centime part', () => {
    expect(arreteEnLettres(936)).toBe(
      'ARRÊTÉE LA PRÉSENTE FACTURE À LA SOMME DE : NEUF CENT TRENTE-SIX DIRHAMS.',
    )
  })

  it('one dirham is singular', () => {
    expect(arreteEnLettres(1).endsWith('UN DIRHAM.')).toBe(true)
  })

  it('zero is written out, not left blank', () => {
    expect(arreteEnLettres(0)).toContain('ZÉRO DIRHAMS')
  })

  it('centimes are spelled after the dirhams', () => {
    expect(arreteEnLettres(12.5)).toContain('DOUZE DIRHAMS ET CINQUANTE CENTIMES')
  })

  it('cent stays singular when another number follows', () => {
    expect(arreteEnLettres(201)).toContain('DEUX CENT UN DIRHAMS')
  })

  it('cent is plural only when it ends the number', () => {
    expect(arreteEnLettres(200)).toContain('DEUX CENTS DIRHAMS')
  })

  it('eighty is plural alone and singular before a unit', () => {
    expect(arreteEnLettres(80)).toContain('QUATRE-VINGTS DIRHAMS')
    expect(arreteEnLettres(81)).toContain('QUATRE-VINGT-UN DIRHAMS')
  })

  it('seventy-one keeps its et', () => {
    expect(arreteEnLettres(71)).toContain('SOIXANTE ET ONZE DIRHAMS')
  })

  it('thousands keep the mille after their multiplier', () => {
    expect(arreteEnLettres(2000)).toContain('DEUX MILLE DIRHAMS')
    expect(arreteEnLettres(52_152)).toContain(
      'CINQUANTE-DEUX MILLE CENT CINQUANTE-DEUX DIRHAMS',
    )
    expect(arreteEnLettres(80_000)).toContain('QUATRE-VINGT MILLE DIRHAMS')
    expect(arreteEnLettres(200_000)).toContain('DEUX CENT MILLE DIRHAMS')
  })

  it('cent and vingt lose their s before mille', () => {
    expect(arreteEnLettres(200_000)).not.toContain('CENTS MILLE')
    expect(arreteEnLettres(80_000)).not.toContain('VINGTS MILLE')
    expect(arreteEnLettres(200)).toContain('DEUX CENTS DIRHAMS')
  })

  it('mille takes no s and no un', () => {
    expect(arreteEnLettres(1000)).toContain('MILLE DIRHAMS')
    expect(arreteEnLettres(1000)).not.toContain('UN MILLE')
    expect(arreteEnLettres(2000)).not.toContain('MILLES')
  })

  it('a million is spelled and the rest of the amount follows it', () => {
    expect(arreteEnLettres(1_200_000)).toContain('UN MILLION DEUX CENT MILLE DIRHAMS')
  })

  it('an amount that ends in a round hundred cents', () => {
    const phrase = arreteEnLettres(123)
    expect(phrase).toContain('CENT VINGT-TROIS DIRHAMS.')
    expect(phrase).not.toContain('CENTIME')
  })

  it('a quote is arrested as a quote, not as an invoice', () => {
    expect(arreteEnLettres(100, 'devis').startsWith('ARRÊTÉ LE PRÉSENT DEVIS')).toBe(true)
  })

  it('an order form is arrested as an order form', () => {
    expect(arreteEnLettres(100, 'bon_commande').startsWith('ARRÊTÉ LE PRÉSENT BON DE COMMANDE')).toBe(
      true,
    )
  })

  it('a credit note is arrested as a credit note', () => {
    expect(arreteEnLettres(100, 'avoir').startsWith('ARRÊTÉ LE PRÉSENT AVOIR')).toBe(true)
  })

  it('a proforma is arrested as a proforma', () => {
    expect(arreteEnLettres(100, 'proforma').startsWith('ARRÊTÉ LE PRÉSENT PROFORMA')).toBe(true)
  })

  it('a down payment is arrested as a down payment', () => {
    expect(arreteEnLettres(100, 'acompte').startsWith('ARRÊTÉ LE PRÉSENT ACOMPTE')).toBe(true)
  })

  it('an adjustment is arrested as an adjustment', () => {
    expect(arreteEnLettres(100, 'regul').startsWith('ARRÊTÉE LA PRÉSENTE RÉGULARISATION')).toBe(true)
  })

  it('a simplified invoice stays in the invoice family', () => {
    expect(arreteEnLettres(100, 'simplifiee').startsWith('ARRÊTÉE LA PRÉSENTE FACTURE')).toBe(true)
  })

  it('an export invoice stays in the invoice family', () => {
    expect(arreteEnLettres(100, 'export').startsWith('ARRÊTÉE LA PRÉSENTE FACTURE')).toBe(true)
  })

  it('eighty-two to eighty-nine are written out', () => {
    expect(arreteEnLettres(82)).toContain('QUATRE-VINGT-DEUX DIRHAMS')
    expect(arreteEnLettres(89)).toContain('QUATRE-VINGT-NEUF DIRHAMS')
    expect(arreteEnLettres(4083)).toContain('QUATRE MILLE QUATRE-VINGT-TROIS DIRHAMS')
    expect(arreteEnLettres(91)).toContain('QUATRE-VINGT-ONZE DIRHAMS')
  })

  it('every amount below ten thousand is written without failing', () => {
    for (let n = 0; n <= 9_999; n += 1) {
      expect(arreteEnLettres(n), `${n} produced nothing`).toContain('DIRHAM')
    }
  })
})

describe('parsing the words back', () => {
  // Same idea as the Kotlin suite: read the French back into a number and
  // compare with the source amount. It catches a dropped "cent" or a lost
  // "mille", which a substring assertion on one example would not.

  const MOTS: Record<string, number> = {
    ZÉRO: 0,
    ZERO: 0,
    UN: 1,
    DEUX: 2,
    TROIS: 3,
    QUATRE: 4,
    CINQ: 5,
    SIX: 6,
    SEPT: 7,
    HUIT: 8,
    NEUF: 9,
    DIX: 10,
    ONZE: 11,
    DOUZE: 12,
    TREIZE: 13,
    QUATORZE: 14,
    QUINZE: 15,
    SEIZE: 16,
    'DIX-SEPT': 17,
    'DIX-HUIT': 18,
    'DIX-NEUF': 19,
    VINGT: 20,
    TRENTE: 30,
    QUARANTE: 40,
    CINQUANTE: 50,
    SOIXANTE: 60,
  }

  /** Inverse of the engine, for the test only. Handles cent/mille/million. */
  function enNombre(texte: string): number {
    const jetons = texte
      .replace(/CENTS/g, 'CENT')
      .replace(/VINGTS/g, 'VINGT')
      .replace(/-/g, ' ')
      .split(/\s+/)
      .filter((j) => j !== '' && j !== 'ET')

    let total = 0
    let courant = 0
    for (let i = 0; i < jetons.length; i += 1) {
      const jeton = jetons[i]
      if (jeton === 'CENT') {
        courant = (courant === 0 ? 1 : courant) * 100
      } else if (jeton === 'MILLE') {
        total += (courant === 0 ? 1 : courant) * 1000
        courant = 0
      } else if (jeton === 'MILLION' || jeton === 'MILLIONS') {
        total += (courant === 0 ? 1 : courant) * 1_000_000
        courant = 0
      } else if (jeton === 'QUATRE' && jetons[i + 1] === 'VINGT') {
        // "quatre-vingt" is a multiplier, not 4 + 20.
        courant += 80
        i += 1
      } else {
        const valeur = MOTS[jeton]
        if (valeur === undefined) throw new Error(`mot inconnu : ${jeton} dans « ${texte} »`)
        courant += valeur
      }
    }
    return total + courant
  }

  it('round-trips every dirham amount below 2000', () => {
    for (let n = 0; n < 2000; n += 1) {
      const mots = montantEnLettres(n).replace(/ DIRHAMS?$/, '')
      expect(enNombre(mots), `${n} → ${mots}`).toBe(n)
    }
  })

  it('round-trips every dirham amount below a million, sampled', () => {
    for (const n of [2_000, 4_083, 52_152, 80_000, 200_000, 201_000, 999_999]) {
      const mots = montantEnLettres(n).replace(/ DIRHAMS?$/, '')
      expect(enNombre(mots), `${n} → ${mots}`).toBe(n)
    }
  })

  it('round-trips every centime from 1 to 99', () => {
    for (let centimes = 1; centimes <= 99; centimes += 1) {
      const phrase = montantEnLettres(7 + centimes / 100)
      const apres = phrase
        .split(' ET ')
        .slice(1)
        .join(' ET ')
        .replace(/ CENTIMES?$/, '')
      expect(enNombre(apres), `${centimes} → ${apres}`).toBe(centimes)
    }
  })
})
