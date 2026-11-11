import { describe, expect, it } from 'vitest'
import { analyserDate, estEnRetard, joursRestants, versDateFr } from '../src/lib/dates'

describe('analyserDate', () => {
  it('lit une date française', () => {
    const date = analyserDate('10/11/2026')
    expect(date?.getFullYear()).toBe(2026)
    expect(date?.getMonth()).toBe(10)
    expect(date?.getDate()).toBe(10)
  })

  it('refuse un jour qui n’existe pas plutôt que de le reporter', () => {
    expect(analyserDate('31/02/2026')).toBeNull()
    expect(analyserDate('32/01/2026')).toBeNull()
    expect(analyserDate('10/13/2026')).toBeNull()
  })

  it('refuse le vide et le texte libre', () => {
    expect(analyserDate('')).toBeNull()
    expect(analyserDate(null)).toBeNull()
    expect(analyserDate('demain')).toBeNull()
  })
})

describe('estEnRetard', () => {
  const le = (jour: number, mois: number, annee: number) => new Date(annee, mois - 1, jour)

  it('n’est pas en retard le jour de l’échéance', () => {
    expect(estEnRetard('10/11/2026', 'unpaid', le(10, 11, 2026))).toBe(false)
  })

  it('est en retard le lendemain, quelle que soit l’heure', () => {
    expect(estEnRetard('10/11/2026', 'unpaid', le(11, 11, 2026))).toBe(true)
    const tard = new Date(2026, 10, 11, 23, 59, 59)
    expect(estEnRetard('10/11/2026', 'partial', tard)).toBe(true)
  })

  it('n’est jamais en retard une fois payée', () => {
    expect(estEnRetard('10/11/2026', 'paid', le(31, 12, 2026))).toBe(false)
  })

  it('ignore une échéance absente ou illisible', () => {
    expect(estEnRetard(null, 'unpaid', le(31, 12, 2026))).toBe(false)
    expect(estEnRetard('31/02/2026', 'unpaid', le(31, 12, 2026))).toBe(false)
  })

  it('compte les jours restants, négatifs une fois passés', () => {
    expect(joursRestants('10/11/2026', le(5, 11, 2026))).toBe(5)
    expect(joursRestants('10/11/2026', le(10, 11, 2026))).toBe(0)
    expect(joursRestants('10/11/2026', le(12, 11, 2026))).toBe(-2)
  })
})

describe('versDateFr', () => {
  it('convertit une date ISO', () => {
    expect(versDateFr('2026-11-10')).toBe('10/11/2026')
  })
})
