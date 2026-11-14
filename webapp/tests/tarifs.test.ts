// Le barème réglé depuis la console : ce que la page des tarifs imprime doit
// être exactement ce que la base applique, et un réglage douteux ne doit jamais
// se transformer en « NaN DH ».

import { describe, expect, it } from 'vitest'
import { OFFRES } from '../src/lib/offres'
import {
  TARIFS_DEFAUT,
  offresDepuis,
  pointsDepuisTexte,
  tarifsDepuisReglages,
  texteDepuisPoints,
  validerTarifs,
} from '../src/lib/tarifs'

describe('tarifsDepuisReglages', () => {
  it('retombe sur le barème publié quand rien n’est réglé', () => {
    expect(tarifsDepuisReglages({})).toEqual(TARIFS_DEFAUT)
    expect(tarifsDepuisReglages(null)).toEqual(TARIFS_DEFAUT)
    expect(tarifsDepuisReglages(undefined)).toEqual(TARIFS_DEFAUT)
    expect(TARIFS_DEFAUT.essentiel.prix).toBe(240)
    expect(TARIFS_DEFAUT.pro.prix).toBe(480)
    expect(TARIFS_DEFAUT.essentiel.quota).toBe(50)
    expect(TARIFS_DEFAUT.pro.quota).toBeNull()
  })

  it('lit un barème complet venu de la base', () => {
    const tarifs = tarifsDepuisReglages({
      essentiel: { prix: 300, quota: 60, points: ['60 documents par mois'] },
      pro: { prix: 600, quota: null, points: ['Illimité'] },
    })
    expect(tarifs.essentiel).toEqual({ prix: 300, quota: 60, points: ['60 documents par mois'] })
    expect(tarifs.pro.prix).toBe(600)
    expect(tarifs.pro.quota).toBeNull()
  })

  it('ignore un prix absurde, un quota sous le gratuit et une liste vide', () => {
    const tarifs = tarifsDepuisReglages({
      essentiel: { prix: 0, quota: 2, points: [] },
      pro: { prix: '480', quota: 100, points: ['a'] },
    })
    expect(tarifs.essentiel.prix).toBe(240)
    expect(tarifs.essentiel.quota).toBe(50)
    expect(tarifs.essentiel.points.length).toBeGreaterThan(0)
    // `pro` accepte un prix en texte (jsonb peut rendre une chaîne)
    expect(tarifs.pro.prix).toBe(480)
    // mais Pro ne devient jamais limité
    expect(tarifs.pro.quota).toBeNull()
  })

  it('coupe une liste trop longue et jette les lignes vides', () => {
    const points = Array.from({ length: 12 }, (_, i) => `Prestation ${i + 1}`)
    const tarifs = tarifsDepuisReglages({ essentiel: { points } })
    expect(tarifs.essentiel.points).toHaveLength(8)
    expect(tarifsDepuisReglages({ essentiel: { points: ['  ', 'ok'] } }).essentiel.points).toEqual(
      TARIFS_DEFAUT.essentiel.points,
    )
  })
})

describe('offresDepuis', () => {
  it('remplace le prix et les puces des deux paliers payants', () => {
    const offres = offresDepuis({
      essentiel: { prix: 300, quota: 60, points: ['60 documents par mois', 'Support par e-mail'] },
      pro: { prix: 600, quota: null, points: ['Tout, sans limite'] },
    })
    expect(offres.map((o) => o.cle)).toEqual(['gratuit', 'essentiel', 'pro'])
    expect(offres[1].prix).toBe('300 DH')
    expect(offres[1].points).toEqual(['60 documents par mois', 'Support par e-mail'])
    expect(offres[2].prix).toBe('600 DH')
    expect(offres[2].points).toEqual(['Tout, sans limite'])
  })

  it('laisse le gratuit intact', () => {
    const gratuit = OFFRES[0]
    expect(offresDepuis(TARIFS_DEFAUT)[0]).toEqual(gratuit)
  })

  it('garde le nom, la période et l’action de chaque carte', () => {
    const offres = offresDepuis(TARIFS_DEFAUT)
    expect(offres.map((o) => o.nom)).toEqual(['Gratuit', 'Essentiel', 'Pro'])
    expect(offres.map((o) => o.periode)).toEqual(['pour toujours', 'par an', 'par an'])
    expect(offres[2].action).toBe('Passer au Pro')
  })
})

describe('points et texte', () => {
  it('fait l’aller-retour entre la zone de saisie et la liste', () => {
    const texte = 'Une puce\n\n  Une autre  \n'
    expect(pointsDepuisTexte(texte)).toEqual(['Une puce', 'Une autre'])
    expect(texteDepuisPoints(['a', 'b'])).toBe('a\nb')
  })
})

describe('validerTarifs', () => {
  it('accepte le barème par défaut', () => {
    expect(validerTarifs(TARIFS_DEFAUT)).toBeNull()
  })

  it('refuse les cas que le SQL refuse aussi', () => {
    const base = TARIFS_DEFAUT
    expect(validerTarifs({ ...base, essentiel: { ...base.essentiel, prix: 0 } })).toContain('50 et 50 000')
    expect(validerTarifs({ ...base, essentiel: { ...base.essentiel, prix: 249.5 } })).toContain('entier')
    expect(validerTarifs({ ...base, essentiel: { ...base.essentiel, quota: 5 } })).toContain('6 et 100 000')
    expect(validerTarifs({ ...base, essentiel: { ...base.essentiel, quota: null } })).toContain('6 et 100 000')
    expect(validerTarifs({ ...base, pro: { ...base.pro, quota: 200 } })).toContain('illimité')
    expect(validerTarifs({ ...base, pro: { ...base.pro, points: [] } })).toContain('1 et 8')
    expect(
      validerTarifs({ ...base, pro: { ...base.pro, points: ['ok', 'x'.repeat(141)] } }),
    ).toContain('3 et 140')
  })
})
