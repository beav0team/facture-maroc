import { describe, expect, it } from 'vitest'
import {
  analyserMontant,
  arrondi,
  dirhams,
  montant,
  quantiteTexte,
  quantiteValeur,
  signeMontant,
  tauxTexte,
} from '../src/lib/money'

const FINE = '\u202F'

describe('montant', () => {
  it('garde deux décimales à un montant rond', () => {
    expect(dirhams(3432)).toBe(`3${FINE}432,00 DH`)
    expect(dirhams(0)).toBe('0,00 DH')
  })

  it('groupe les milliers avec une espace fine insécable', () => {
    expect(dirhams(1812.6)).toBe(`1${FINE}812,60 DH`)
    expect(dirhams(12_345.67)).toBe(`12${FINE}345,67 DH`)
    expect(dirhams(1_234_567.89)).toBe(`1${FINE}234${FINE}567,89 DH`)
  })

  it('arrondit les centimes, ne les tronque jamais', () => {
    expect(dirhams(83.325)).toBe('83,33 DH')
    expect(dirhams(999.999)).toBe(`1${FINE}000,00 DH`)
  })

  it('écrit un montant négatif avec le signe de la locale', () => {
    expect(montant(-812.6)).toBe('-812,60')
  })

  it('n’écrit un vrai signe moins que pour un montant négatif', () => {
    expect(signeMontant(812.6)).toBe('812,60')
    expect(signeMontant(-812.6)).toBe('\u2212812,60')
  })

  it('affiche zéro plutôt que de casser un document', () => {
    expect(dirhams(Number.NaN)).toBe('0,00 DH')
    expect(dirhams(Number.POSITIVE_INFINITY)).toBe('0,00 DH')
  })
})

describe('arrondi', () => {
  it('arrondit au centime comme le moteur Kotlin', () => {
    // Le littéral 149,985 est un cheveu AU-DESSUS de 149,985 et monte ;
    // le produit 1,5 × 99,99 est un cheveu EN DESSOUS et descend. Les deux
    // moteurs doivent suivre le double, pas le nombre décimal rêvé.
    expect(arrondi(149.985)).toBe(149.99)
    expect(arrondi(1.5 * 99.99)).toBe(149.98)
    // 2,675 × 100 vaut exactement 267,5 dans les deux langages : le demi
    // monte, donc 2,68 — c’est ce que Kotlin calcule aussi.
    expect(arrondi(2.675)).toBe(2.68)
    expect(arrondi(0.005)).toBe(0.01)
  })
})

describe('quantités', () => {
  it('s’imprime sans zéros inutiles', () => {
    expect(quantiteTexte(2)).toBe('2')
    expect(quantiteTexte(1.5)).toBe('1,5')
    expect(quantiteTexte(0.25)).toBe('0,25')
    expect(quantiteTexte(0.125)).toBe('0,125')
  })

  it('garde le point pour la machine', () => {
    expect(quantiteValeur(2)).toBe('2')
    expect(quantiteValeur(1.5)).toBe('1.5')
    expect(quantiteValeur(0.25)).toBe('0.25')
  })
})

describe('tauxTexte', () => {
  it('affiche un taux sans zéro final', () => {
    expect(tauxTexte(20)).toBe('20')
    expect(tauxTexte(7.5)).toBe('7.5')
  })
})

describe('analyserMontant', () => {
  it('lit un montant écrit à la française', () => {
    expect(analyserMontant('1 234,50')).toBe(1234.5)
    expect(analyserMontant(`1${FINE}234,50`)).toBe(1234.5)
    expect(analyserMontant('1.234,50')).toBe(1234.5)
  })

  it('lit aussi le point décimal', () => {
    expect(analyserMontant('1234.5')).toBe(1234.5)
  })

  it('renvoie null plutôt que NaN', () => {
    expect(analyserMontant('')).toBeNull()
    expect(analyserMontant('abc')).toBeNull()
  })
})
