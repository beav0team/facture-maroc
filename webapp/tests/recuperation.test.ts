// @vitest-environment jsdom
//
// The recovery path is the one screen nobody reaches on purpose: it is opened
// from a mail link, once. These tests pin the two ways it can go wrong — the
// link is not recognised, or the two passwords are not compared.

import { flushSync, mount, unmount } from 'svelte'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { messageRecuperation, typeRecuperation, validerNouveau } from '../src/lib/recuperation'

const { updateUser } = vi.hoisted(() => ({ updateUser: vi.fn(async () => ({ error: null })) }))

vi.mock('../src/lib/supabase', () => {
  const chain = (donnees: unknown) => {
    const objet: Record<string, unknown> & { then?: unknown } = {}
    for (const methode of ['select', 'eq', 'order', 'limit', 'maybeSingle']) {
      objet[methode] = () => objet
    }
    objet.then = (resoudre: (valeur: unknown) => void) => resoudre({ data: donnees, error: null })
    return objet
  }

  return {
    estConfigure: true,
    supabase: {
      auth: {
        getSession: async () => ({ data: { session: null } }),
        onAuthStateChange: () => ({ data: { subscription: { unsubscribe: () => {} } } }),
        signOut: async () => ({ error: null }),
        updateUser,
      },
      from: () => chain(null),
    },
    client: () => null,
  }
})

import { auth } from '../src/lib/session.svelte'
import MotDePasse from '../src/routes/MotDePasse.svelte'

let monte: ReturnType<typeof mount> | null = null

afterEach(() => {
  if (monte) unmount(monte)
  monte = null
  document.body.innerHTML = ''
  auth.user = null
  updateUser.mockClear()
})

function rendre() {
  monte = mount(MotDePasse, { target: document.body })
  flushSync()
  return document.body
}

function saisir(id: string, valeur: string) {
  const champ = document.getElementById(id) as HTMLInputElement
  champ.value = valeur
  champ.dispatchEvent(new Event('input', { bubbles: true }))
  flushSync()
}

describe('typeRecuperation', () => {
  it('reconnaît le retour implicit dans le fragment', () => {
    const hash = '#access_token=abc&expires_in=3600&refresh_token=def&type=recovery'
    expect(typeRecuperation(hash, '')).toBe(true)
  })

  it('reconnaît un retour en paramètre de requête', () => {
    expect(typeRecuperation('', '?type=recovery&code=xyz')).toBe(true)
  })

  it('ignore une connexion Google ordinaire', () => {
    expect(typeRecuperation('#access_token=abc&type=bearer', '?code=xyz')).toBe(false)
    expect(typeRecuperation('', '')).toBe(false)
  })
})

describe('validerNouveau', () => {
  it('refuse un mot de passe trop court', () => {
    expect(validerNouveau('12345', '12345')).toEqual({
      erreur: 'Le mot de passe doit faire au moins 6 caractères.',
    })
  })

  it('refuse deux saisies différentes', () => {
    expect(validerNouveau('motdepasse', 'motdepassé')).toEqual({
      erreur: 'Les deux mots de passe ne sont pas identiques.',
    })
  })

  it('accepte six caractères identiques', () => {
    expect(validerNouveau('abc123', 'abc123')).toEqual({ motDePasse: 'abc123' })
  })
})

describe('messageRecuperation', () => {
  it('traduit les refus que Supabase renvoie en anglais', () => {
    expect(messageRecuperation('New password should be different from the old password.')).toBe(
      'Le nouveau mot de passe doit être différent de l’ancien.',
    )
    expect(messageRecuperation('Auth session missing!')).toContain('expiré')
    expect(messageRecuperation('Rate limit exceeded')).toContain('Trop de tentatives')
  })
})

describe('écran Nouveau mot de passe', () => {
  it('explique qu’un lien sans session est expiré', () => {
    auth.user = null
    const body = rendre()
    expect(body.textContent).toContain('a expiré ou a déjà servi')
    expect(body.querySelector('form')).toBeNull()
  })

  it('ne change pas le mot de passe quand les deux saisies diffèrent', async () => {
    auth.user = { id: 'u1', email: 'boutique@exemple.ma' } as never
    const body = rendre()

    saisir('nouveau', 'motdepasse')
    saisir('confirmation', 'motdepasse2')
    body.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    flushSync()

    expect(body.textContent).toContain('ne sont pas identiques')
    expect(updateUser).not.toHaveBeenCalled()
  })
})
