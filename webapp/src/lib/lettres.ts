// Montant en toutes lettres — port of the Android `data/AmountWords.kt`.
//
// The Kotlin file is the reference, not the spec: the rules below are the ones
// it encodes, including the ones that only exist because a bug shipped once
// ("cent" plural only when it ends the number, "quatre-vingt-un" vs
// "quatre-vingts", 82-89 that used to throw and leave the PDF unwritten).
//
// Everything is uppercase, Latin-1 only: the PDF is drawn with Helvetica and
// `pdf.ts`'s `assainir()` would otherwise strip the accents.

import type { TypeDocument } from './document'

const UNITES = ['', 'UN', 'DEUX', 'TROIS', 'QUATRE', 'CINQ', 'SIX', 'SEPT', 'HUIT', 'NEUF']
const ADOLESCENTS = [
  'DIX',
  'ONZE',
  'DOUZE',
  'TREIZE',
  'QUATORZE',
  'QUINZE',
  'SEIZE',
  'DIX-SEPT',
  'DIX-HUIT',
  'DIX-NEUF',
]
const DIZAINES = [
  '',
  '',
  'VINGT',
  'TRENTE',
  'QUARANTE',
  'CINQUANTE',
  'SOIXANTE',
  'SOIXANTE',
  'QUATRE-VINGT',
  'QUATRE-VINGT',
]

const OUVERTURES: Record<TypeDocument, string> = {
  facture: 'ARRÊTÉE LA PRÉSENTE FACTURE À LA SOMME DE :',
  devis: 'ARRÊTÉ LE PRÉSENT DEVIS À LA SOMME DE :',
  bon_commande: 'ARRÊTÉ LE PRÉSENT BON DE COMMANDE À LA SOMME DE :',
  avoir: 'ARRÊTÉ LE PRÉSENT AVOIR À LA SOMME DE :',
  proforma: 'ARRÊTÉ LE PRÉSENT PROFORMA À LA SOMME DE :',
  acompte: 'ARRÊTÉ LE PRÉSENT ACOMPTE À LA SOMME DE :',
  regul: 'ARRÊTÉE LA PRÉSENTE RÉGULARISATION À LA SOMME DE :',
  ticket: 'ARRÊTÉE LA PRÉSENTE FACTURE À LA SOMME DE :',
  simplifiee: 'ARRÊTÉE LA PRÉSENTE FACTURE À LA SOMME DE :',
  export: 'ARRÊTÉE LA PRÉSENTE FACTURE À LA SOMME DE :',
}

function nombreEnLettres(n: number): string {
  if (n === 0) return ''
  if (n < 10) return UNITES[n]!
  if (n < 20) return ADOLESCENTS[n - 10]!

  if (n < 70) {
    const dizaine = Math.floor(n / 10)
    const unite = n % 10
    const t = DIZAINES[dizaine]!
    if (unite === 1 && dizaine === 8) {
      // Unreachable by construction (kept from the Kotlin source): 81 is
      // handled by the 80-99 branch below, and DIZAINES[8] has no SOIXANTE.
      return t.replace('SOIXANTE', 'SOIXANTE ET ONZE')
    }
    if (unite === 1) return `${t} ET UN`
    if (unite === 0) return t
    return `${t}-${UNITES[unite]!}`
  }

  if (n < 80) {
    // 60-79: soixante-dix, soixante et onze, soixante-dix-sept…
    const reste = n - 60
    if (reste === 10) return 'SOIXANTE-DIX'
    if (reste === 11) return 'SOIXANTE ET ONZE'
    if (reste < 20) return `SOIXANTE-${ADOLESCENTS[reste - 10]!}`
    const dizaine = DIZAINES[Math.floor(reste / 10)]!
    const unite = reste % 10
    return `SOIXANTE-${dizaine}${unite === 0 ? '' : `-${UNITES[unite]!}`}`
  }

  if (n < 100) {
    // 80-99: quatre-vingts, quatre-vingt-un, quatre-vingt-deux…
    const reste = n - 80
    if (reste === 0) return 'QUATRE-VINGTS'
    if (reste === 1) return 'QUATRE-VINGT-UN'
    if (reste < 10) return `QUATRE-VINGT-${UNITES[reste]!}`
    return `QUATRE-VINGT-${ADOLESCENTS[reste - 10]!}`
  }

  if (n < 1000) {
    const centaine = Math.floor(n / 100)
    const reste = n % 100
    const h = centaine === 1 ? 'CENT' : `${UNITES[centaine]!} CENT`
    if (reste === 0) {
      // "cent" takes its s only when it ends the number: deux cents, but
      // deux cent un.
      return centaine > 1 ? `${h}S` : h
    }
    return `${h} ${nombreEnLettres(reste)}`
  }

  if (n < 1_000_000) {
    const milliers = Math.floor(n / 1000)
    const reste = n % 1000
    // "mille" is invariable, and "cent"/"vingt" lose their s before it:
    // 200 000 is "deux cent mille", 80 000 is "quatre-vingt mille".
    const multiplicateur =
      milliers === 1
        ? ''
        : nombreEnLettres(milliers).replace(/CENTS$/, 'CENT').replace(/VINGTS$/, 'VINGT')
    const t = milliers === 1 ? 'MILLE' : `${multiplicateur} MILLE`
    if (reste === 0) return t
    return `${t} ${nombreEnLettres(reste)}`
  }

  const millions = Math.floor(n / 1_000_000)
  const reste = n % 1_000_000
  return (
    `${nombreEnLettres(millions)} MILLION${millions > 1 ? 'S' : ''}` +
    (reste === 0 ? '' : ` ${nombreEnLettres(reste)}`)
  )
}

/**
 * The amount alone: "CINQ CENTS DIRHAMS ET QUINZE CENTIMES".
 *
 * Used where the legal opening would be wrong — a payment receipt states a sum
 * received, it does not "arrêter la présente facture".
 */
export function montantEnLettres(montant: number): string {
  let dirhams = Math.floor(montant)
  let centimes = Math.round((montant - dirhams) * 100)
  // Web-only hardening: a float sitting just under a whole dirham (935,999999)
  // would otherwise read "ET CENT CENTIMES". The Kotlin side renders amounts
  // rounded to the centime first, so this never fires there.
  if (centimes === 100) {
    dirhams += 1
    centimes = 0
  }
  const texteDirhams = dirhams === 0 ? 'ZÉRO' : nombreEnLettres(dirhams)
  const texteCentimes = centimes === 0 ? '' : nombreEnLettres(centimes)
  return (
    `${texteDirhams} ${dirhams === 1 ? 'DIRHAM' : 'DIRHAMS'}` +
    (texteCentimes === ''
      ? ''
      : ` ET ${texteCentimes} ${centimes === 1 ? 'CENTIME' : 'CENTIMES'}`)
  )
}

/** The whole legal sentence, with the opening owned by the document type. */
export function arreteEnLettres(montant: number, type: TypeDocument = 'facture'): string {
  return `${OUVERTURES[type] ?? OUVERTURES.facture} ${montantEnLettres(montant)}.`
}
