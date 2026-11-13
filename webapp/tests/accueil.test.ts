// @vitest-environment jsdom
//
// The home screen used to print « Plan gratuit · 5 factures / mois » as a
// literal, so a shop that had paid still read the free plan right after login.
// These tests mount the real screen and read the badge.

import { flushSync, mount, unmount } from 'svelte'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const etat = vi.hoisted(() => ({ quota: null as unknown }))

vi.mock('../src/lib/supabase', () => {
  const chain = () => {
    const objet: Record<string, unknown> = {}
    for (const m of ['select', 'eq', 'order', 'limit', 'maybeSingle', 'insert', 'update', 'delete']) {
      objet[m] = () => objet
    }
    objet.then = (resoudre: (valeur: unknown) => void) => resoudre({ data: null, error: null })
    return objet
  }
  return {
    estConfigure: true,
    supabase: {
      auth: {
        getSession: async () => ({ data: { session: null } }),
        onAuthStateChange: () => ({ data: { subscription: { unsubscribe: () => {} } } }),
        signOut: async () => ({ error: null }),
      },
      from: () => chain(),
      rpc: async () => ({ data: etat.quota, error: null }),
    },
    client: () => null,
  }
})

import { auth } from '../src/lib/session.svelte'
import Accueil from '../src/routes/Accueil.svelte'

let monte: ReturnType<typeof mount> | null = null

beforeEach(() => {
  auth.user = { id: 'u1', email: 'boutique@exemple.ma' } as never
  auth.boutique = { id: 's1', nom: 'PNEUMATIQUE AGHARAS' } as never
  document.body.innerHTML = ''
})

afterEach(() => {
  if (monte) unmount(monte)
  monte = null
})

async function rendre() {
  monte = mount(Accueil, { target: document.body })
  flushSync()
  // The quota is fetched in `onMount`, so let the promise settle.
  for (let i = 0; i < 5; i += 1) {
    await Promise.resolve()
    flushSync()
  }
  return document.body
}

describe('Accueil — étiquette du plan', () => {
  it('affiche le palier Pro quand la boutique est payée', async () => {
    etat.quota = { plan: 'pro', paid_until: '2027-11-11', utilise: 0, limite: null }
    const body = await rendre()
    expect(body.textContent).toContain('Pro · Documents illimités')
    expect(body.textContent).not.toContain('Gratuit ·')
  })

  it('affiche le quota réel quand la boutique est gratuite', async () => {
    etat.quota = { plan: 'gratuit', paid_until: null, utilise: 2, limite: 5 }
    const body = await rendre()
    expect(body.textContent).toContain('Gratuit · 2 / 5 documents ce mois')
  })

  it('affiche le palier Essentiel et sa limite de 50', async () => {
    etat.quota = { plan: 'essentiel', paid_until: '2027-11-11', utilise: 7, limite: 50 }
    const body = await rendre()
    expect(body.textContent).toContain('Essentiel · 7 / 50 documents ce mois')
  })

  it('dit qu’un abonnement est terminé au lieu de faire croire à un plan gratuit', async () => {
    etat.quota = {
      plan: 'gratuit',
      paid_until: '2026-10-01',
      utilise: 3,
      limite: 5,
      expire: true,
    }
    const body = await rendre()
    expect(body.textContent).toContain('Gratuit · abonnement terminé')
  })
})
