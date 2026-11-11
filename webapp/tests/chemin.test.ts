import { describe, expect, it } from 'vitest'
import { cheminDepuis, urlDe } from '../src/lib/chemin'

describe('cheminDepuis', () => {
  it('maps the app root to /', () => {
    expect(cheminDepuis('/app/')).toBe('/')
    expect(cheminDepuis('/app')).toBe('/')
  })

  it('strips the /app prefix', () => {
    expect(cheminDepuis('/app/factures')).toBe('/factures')
    expect(cheminDepuis('/app/factures/')).toBe('/factures')
    expect(cheminDepuis('/app/factures/abc')).toBe('/factures/abc')
  })

  it('leaves a path outside the app untouched', () => {
    expect(cheminDepuis('/a-propos.html')).toBe('/a-propos.html')
    expect(cheminDepuis('/')).toBe('/')
  })

  it('does not confuse /appartement with the /app base', () => {
    expect(cheminDepuis('/appartement')).toBe('/appartement')
  })
})

describe('urlDe', () => {
  it('always prefixes with /app', () => {
    expect(urlDe('/login')).toBe('/app/login')
    expect(urlDe('login')).toBe('/app/login')
    expect(urlDe('/')).toBe('/app/')
  })
})
