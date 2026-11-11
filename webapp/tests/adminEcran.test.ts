// @vitest-environment jsdom
//
// The console reads three functions whose column names must match the screen
// (`cree_le`, `jusqu_au`, `est_admin`…). A rename in SQL would type-check in
// TypeScript and render `undefined` in the browser; this mounts the screen
// against a fake Supabase and reads what the admin would see.

import { flushSync, mount, unmount } from 'svelte'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { appels, BOUTIQUES, COMPTES } = vi.hoisted(() => ({
  appels: [] as { nom: string; args: unknown }[],
  BOUTIQUES: [
  {
    id: 'b1',
    nom: 'Atlas',
    cree_le: '2026-11-11T19:48:19+00:00',
    proprietaire: 'opipablo45@gmail.com',
    proprietaire_id: 'u1',
    plan: 'pro',
    jusqu_au: '2027-11-11',
    note: 'Virement reçu',
    documents: 4,
    clients: 2,
    articles: 7,
    pro: true,
  },
  {
    id: 'b2',
    nom: 'Benzen',
    cree_le: '2026-11-11T20:02:44+00:00',
    proprietaire: 'sbensaria660@gmail.com',
    proprietaire_id: 'u2',
    plan: 'gratuit',
    jusqu_au: null,
    note: '',
    documents: 0,
    clients: 0,
    articles: 0,
    pro: false,
  },
],
  COMPTES: [
  {
    id: 'u1',
    email: 'opipablo45@gmail.com',
    cree_le: '2026-11-11T19:48:02+00:00',
    derniere_connexion: '2026-11-11T20:30:00+00:00',
    boutique: 'Atlas',
    boutique_id: 'b1',
    est_admin: false,
  },
  {
    id: 'u9',
    email: 'agnaou1996@gmail.com',
    cree_le: '2026-11-11T20:09:27+00:00',
    derniere_connexion: null,
    boutique: 'PNEUMATIQUE AGHARAS',
    boutique_id: 'b3',
    est_admin: true,
  },
],
  }))

vi.mock('../src/lib/supabase', () => {
  const reponses: Record<string, unknown> = {
    est_admin_plateforme: true,
    admin_statistiques: {
      boutiques: 2,
      utilisateurs: 2,
      administrateurs: 1,
      pro: 1,
      documents: 4,
      documents_emis: 3,
      archives: 3,
      octets_archives: 1_500_000,
    },
    admin_boutiques: BOUTIQUES,
    admin_utilisateurs: COMPTES,
    admin_nommer: 'u1',
    admin_retirer: null,
    admin_abonnement: null,
  }
  return {
    estConfigure: true,
    supabase: {
      auth: { getSession: async () => ({ data: { session: null } }) },
      rpc: async (nom: string, args: unknown) => {
        appels.push({ nom, args })
        return { data: reponses[nom] ?? null, error: null }
      },
      from: () => {
        throw new Error('la console ne doit pas interroger une table')
      },
    },
    client: () => null,
  }
})

import { auth } from '../src/lib/session.svelte'
import Admin from '../src/routes/Admin.svelte'

let monte: ReturnType<typeof mount> | null = null

beforeEach(() => {
  appels.length = 0
  auth.user = { id: 'u9', email: 'agnaou1996@gmail.com' } as never
  auth.boutique = { id: 'b3', nom: 'PNEUMATIQUE AGHARAS' } as never
  document.body.innerHTML = ''
})

afterEach(() => {
  if (monte) unmount(monte)
  monte = null
})

async function rendre() {
  monte = mount(Admin, { target: document.body })
  flushSync()
  for (let i = 0; i < 8; i += 1) {
    await Promise.resolve()
    flushSync()
  }
  return document.body
}

describe('console d’administration', () => {
  it('lit les trois fonctions et n’interroge aucune table', async () => {
    await rendre()
    const lues = appels.map((appel) => appel.nom)
    expect(lues).toContain('admin_statistiques')
    expect(lues).toContain('admin_boutiques')
    expect(lues).toContain('admin_utilisateurs')
  })

  it('affiche les boutiques, leur propriétaire et leur plan', async () => {
    const body = await rendre()
    const texte = body.textContent ?? ''
    expect(texte).toContain('Atlas')
    expect(texte).toContain('opipablo45@gmail.com')
    expect(texte).toContain('Pro jusqu’au 11/11/2027')
    expect(texte).toContain('Benzen')
    expect(texte).toContain('Plan gratuit')
    expect(texte).toContain('1,5 Mo')
  })

  it('propose « Pro 1 an » pour une boutique gratuite et « Prolonger » pour une Pro', async () => {
    const body = await rendre()
    const boutons = [...body.querySelectorAll('button')].map((b) => b.textContent?.trim())
    expect(boutons).toContain('Pro 1 an')
    expect(boutons).toContain('Prolonger…')
    expect(boutons).toContain('Repasser en gratuit')
  })

  it('marque l’administrateur et le compte courant', async () => {
    const body = await rendre()
    const texte = body.textContent ?? ''
    expect(texte).toContain('agnaou1996@gmail.com')
    expect(texte).toContain('administrateur')
    expect(texte).toContain('vous')
    expect(texte).toContain('jamais')
  })
})
