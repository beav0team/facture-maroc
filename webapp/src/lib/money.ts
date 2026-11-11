// The one way the web app writes a dirham amount. Ported from the Android
// app's Money.kt so a figure reads the same on the site, the Android screen and
// the printed document: comma decimal, narrow no-break space for thousands,
// always two decimals.

const ESPACE_FINE = '\u202F'

/** Half-up rounding to two decimals, like Kotlin's Math.round(v * 100) / 100. */
export function arrondi(valeur: number): number {
  if (!Number.isFinite(valeur)) return 0
  return Math.round(valeur * 100) / 100
}

/** `1 812,60` — grouped, no currency. */
export function montant(valeur: number): string {
  const sur = Number.isFinite(valeur) ? valeur : 0
  const negatif = sur < 0
  const centimes = Math.round(Math.abs(sur) * 100)
  const entier = Math.floor(centimes / 100)
    .toString()
    .replace(/\B(?=(\d{3})+(?!\d))/g, ESPACE_FINE)
  const decimales = (centimes % 100).toString().padStart(2, '0')
  return `${negatif && centimes !== 0 ? '-' : ''}${entier},${decimales}`
}

/** `1 812,60 DH` — what the screen and the paper both print. */
export function dirhams(valeur: number): string {
  return `${montant(valeur)} DH`
}

/** `812,60`, or `−812,60` with a real minus sign. */
export function signeMontant(valeur: number): string {
  return valeur < 0 ? `\u2212${montant(-valeur)}` : montant(valeur)
}

/** `2`, `1.5`, `0.25` — a quantity as a machine reads it. */
export function quantiteValeur(quantite: number): string {
  if (!Number.isFinite(quantite)) return '0'
  const arrondie = Math.round(quantite * 1000) / 1000
  if (arrondie === Math.trunc(arrondie)) return String(arrondie)
  return arrondie.toFixed(3).replace(/0+$/, '').replace(/\.$/, '')
}

/** `2`, `1,5`, `0,25` — a quantity as it is printed. */
export function quantiteTexte(quantite: number): string {
  return quantiteValeur(quantite).replace('.', ',')
}

/** `20`, `10`, `7.5` — a rate without trailing zeros. */
export function tauxTexte(taux: number): string {
  if (Number.isInteger(taux)) return String(taux)
  return String(taux).replace(/0+$/, '').replace(/\.$/, '')
}

/**
 * Reads an amount typed by a shop: `1 234,50`, `1.234,50`, `1234.5`.
 *
 * Returns null rather than NaN so a form can keep the old value instead of
 * writing a broken figure into a document.
 */
export function analyserMontant(texte: string): number | null {
  let propre = texte.trim().replace(/[\s\u00A0\u202F]/g, '').replace(/\u2212/g, '-')
  if (propre === '') return null
  if (propre.includes(',')) {
    // French input: dots group thousands, the comma is the decimal mark.
    propre = propre.replace(/\./g, '').replace(',', '.')
  }
  const valeur = Number(propre)
  return Number.isFinite(valeur) ? valeur : null
}
