import { describe, expect, it } from 'vitest'
import {
  dateCourte,
  derniereConnexion,
  finDansUnAn,
  isoLocal,
  octets,
  planLisible,
  validerAbonnement,
  type BoutiqueAdmin,
} from '../src/lib/admin'

const boutique = (morceaux: Partial<BoutiqueAdmin> = {}): BoutiqueAdmin => ({
  id: 'b1',
  nom: 'Atlas',
  cree_le: '2026-11-11T19:48:19+00:00',
  proprietaire: 'opipablo45@gmail.com',
  proprietaire_id: 'u1',
  plan: 'gratuit',
  jusqu_au: null,
  note: '',
  documents: 0,
  clients: 0,
  articles: 0,
  pro: false,
  ...morceaux,
})

describe('dateCourte', () => {
  it('affiche une date SQL en jour/mois/année', () => {
    expect(dateCourte('2027-11-11')).toBe('11/11/2027')
  })

  it('accepte un horodatage complet', () => {
    expect(dateCourte('2026-11-11T19:48:19+00:00')).toBe('11/11/2026')
  })

  it('écrit un tiret quand la date manque', () => {
    expect(dateCourte(null)).toBe('—')
    expect(dateCourte('')).toBe('—')
  })
})

describe('finDansUnAn', () => {
  it('garde le jour et le mois', () => {
    expect(finDansUnAn(new Date(2026, 10, 11))).toBe('2027-11-11')
  })

  it('n’utilise pas UTC : 23 h locales restent le même jour', () => {
    expect(isoLocal(new Date(2026, 10, 11, 23, 30))).toBe('2026-11-11')
  })
})

describe('validerAbonnement', () => {
  it('accepte le retour en gratuit sans date', () => {
    expect(validerAbonnement('gratuit', null, '2026-11-11')).toBeNull()
  })

  it('refuse un Pro sans date de fin', () => {
    expect(validerAbonnement('pro', null, '2026-11-11')).toContain('date de fin')
  })

  it('refuse une date passée', () => {
    expect(validerAbonnement('pro', '2026-11-10', '2026-11-11')).toContain('déjà passée')
  })

  it('accepte aujourd’hui et demain', () => {
    expect(validerAbonnement('pro', '2026-11-11', '2026-11-11')).toBeNull()
    expect(validerAbonnement('pro', '2026-11-12', '2026-11-11')).toBeNull()
  })
})

describe('octets', () => {
  it('reste en octets sous le millier', () => {
    expect(octets(0)).toBe('0 o')
    expect(octets(999)).toBe('999 o')
  })

  it('passe en kilo et méga avec une virgule', () => {
    expect(octets(1234)).toBe('1,2 ko')
    expect(octets(1_500_000)).toBe('1,5 Mo')
  })

  it('arrondit les grands nombres sans décimale', () => {
    expect(octets(123_456_789)).toBe('123 Mo')
  })
})

describe('planLisible', () => {
  it('dit gratuit', () => {
    expect(planLisible(boutique())).toBe('Plan gratuit')
  })

  it('dit Pro avec sa date', () => {
    expect(planLisible(boutique({ pro: true, plan: 'pro', jusqu_au: '2027-11-11' }))).toBe(
      'Pro jusqu’au 11/11/2027',
    )
  })

  it('dit Pro sans date', () => {
    expect(planLisible(boutique({ pro: true, plan: 'pro', jusqu_au: null }))).toBe('Pro sans date de fin')
  })
})

describe('derniereConnexion', () => {
  it('écrit « jamais » pour un compte qui n’a pas ouvert l’app', () => {
    expect(derniereConnexion(null)).toBe('jamais')
  })

  it('affiche la date sinon', () => {
    expect(derniereConnexion('2026-11-11T20:09:27+00:00')).toBe('11/11/2026')
  })
})
