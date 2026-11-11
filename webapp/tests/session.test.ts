// @vitest-environment jsdom
//
// The shop shown after login must come from the membership, never from the
// `shops` table: a platform admin can SELECT every shop, and the first version
// loaded `shops … order by created_at limit 1`, which opened the admin's
// session as the oldest shop on the platform.

import { beforeEach, describe, expect, it, vi } from 'vitest'

const { appels, reponses } = vi.hoisted(() => ({
  appels: [] as string[],
  reponses: {} as Record<string, unknown>,
}))

vi.mock('../src/lib/supabase', () => {
  const tableau = (table: string) => {
    const objet: Record<string, unknown> = {}
    for (const methode of ['select', 'eq', 'order', 'limit']) {
      objet[methode] = () => objet
    }
    objet.maybeSingle = async () => ({ data: reponses[table] ?? null, error: null })
    objet.then = (resoudre: (valeur: unknown) => void) =>
      resoudre({ data: reponses[table] ?? null, error: null })
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
      from: (table: string) => {
        appels.push(table)
        return tableau(table)
      },
      rpc: async () => ({ data: null, error: null }),
    },
    client: () => null,
  }
})

import { auth, chargerBoutique } from '../src/lib/session.svelte'

beforeEach(() => {
  appels.length = 0
  for (const cle of Object.keys(reponses)) delete reponses[cle]
  auth.user = { id: 'u1', email: 'agnaou1996@gmail.com' } as never
  auth.boutique = null
  auth.erreur = null
})

describe('chargerBoutique', () => {
  it('prend la boutique de l’appartenance, pas la plus ancienne du site', async () => {
    reponses.memberships = {
      shop_id: 's2',
      shops: { id: 's2', nom: 'PNEUMATIQUE AGHARAS' },
    }
    // Ce que la table `shops` renverrait pour un administrateur plateforme.
    reponses.shops = { id: 's1', nom: 'Atlas' }

    await chargerBoutique()

    expect(auth.boutique?.nom).toBe('PNEUMATIQUE AGHARAS')
    expect(appels).toContain('memberships')
    expect(appels).not.toContain('shops')
  })

  it('ne montre aucune boutique sans appartenance', async () => {
    reponses.memberships = null

    await chargerBoutique()

    expect(auth.boutique).toBeNull()
  })

  it('efface la boutique à la déconnexion', async () => {
    auth.boutique = { id: 's2', nom: 'PNEUMATIQUE AGHARAS' } as never
    auth.user = null

    await chargerBoutique()

    expect(auth.boutique).toBeNull()
  })
})
