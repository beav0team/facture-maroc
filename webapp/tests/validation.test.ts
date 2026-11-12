import { describe, expect, it } from 'vitest'
import {
  decomposerIce,
  lienWeliproIce,
  validerIceCleControle,
} from '../src/lib/validation'

describe('decomposerIce', () => {
  it('décompose un ICE valide en trois parties', () => {
    expect(decomposerIce('001534931000016')).toEqual({
      entreprise: '001534931',
      etablissement: '0000',
      cleControle: '16',
    })
  })

  it('accepte les espaces et tirets', () => {
    expect(decomposerIce('001534931-0000-16')).toEqual({
      entreprise: '001534931',
      etablissement: '0000',
      cleControle: '16',
    })
    expect(decomposerIce('001534931 0000 16')).toEqual({
      entreprise: '001534931',
      etablissement: '0000',
      cleControle: '16',
    })
  })

  it('retourne null quand ce n’est pas 15 chiffres', () => {
    expect(decomposerIce('00153493100001')).toBeNull() // 14 chiffres
    expect(decomposerIce('0015349310000167')).toBeNull() // 16 chiffres
    expect(decomposerIce('00153493100001A')).toBeNull() // une lettre
    expect(decomposerIce('')).toBeNull()
  })
})

describe('validerIceCleControle', () => {
  it('valide l’ICE de la Banque Centrale Populaire', () => {
    expect(validerIceCleControle('001534931000016')).toBe(true)
  })

  it('valide l’ICE de Maroc Telecom', () => {
    expect(validerIceCleControle('001522585000066')).toBe(true)
  })

  it('valide l’ICE d’Orange Maroc', () => {
    expect(validerIceCleControle('001524628000001')).toBe(true)
  })

  it('rejette un ICE bien formé mais avec une clé erronée', () => {
    expect(validerIceCleControle('001534931000017')).toBe(false)
    expect(validerIceCleControle('001534931000000')).toBe(false)
  })

  it('retourne null quand ce n’est pas 15 chiffres', () => {
    expect(validerIceCleControle('00153493100001')).toBeNull()
    expect(validerIceCleControle('')).toBeNull()
  })
})

describe('lienWeliproIce', () => {
  it('construit un lien vers l’outil de vérification Welipro', () => {
    expect(lienWeliproIce('001534931000016')).toBe(
      'https://maroc.welipro.com/outils/verifier-ice?ice=001534931000016',
    )
  })

  it('nettoie les espaces et tirets dans le lien', () => {
    expect(lienWeliproIce('001534931-0000-16')).toBe(
      'https://maroc.welipro.com/outils/verifier-ice?ice=001534931000016',
    )
  })
})
