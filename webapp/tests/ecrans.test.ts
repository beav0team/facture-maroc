// @vitest-environment jsdom
//
// The screens are the one part `svelte-check` cannot prove: a rune misused in a
// template type-checks and still throws in the browser. This mounts the two
// list screens against a fake Supabase and reads back what a shop would see.

import { flushSync, mount, unmount } from 'svelte'
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('../src/lib/supabase', () => {
  /** A thenable chain: every builder method returns itself, `await` yields data. */
  const chain = (donnees: unknown) => {
    const objet: Record<string, unknown> & { then?: unknown } = {}
    for (const methode of ['select', 'eq', 'order', 'limit', 'maybeSingle', 'insert', 'update', 'delete']) {
      objet[methode] = () => objet
    }
    objet.then = (resoudre: (valeur: unknown) => void) =>
      resoudre({ data: donnees, error: null })
    return objet
  }

  const clients = [
    {
      id: 'c1',
      shop_id: 'b1',
      nom: 'Garage Atlas',
      adresse: '12 rue des Fleurs, Casablanca',
      ice: '001234567000012',
      if_fiscal: '12345678',
      rc: null,
      patente: null,
      cnss: null,
      telephone: '0612345678',
      email: 'contact@atlas.ma',
      role: 'client',
      exonere_tva: false,
      note: null,
    },
  ]

  const articles = [
    {
      id: 'a1',
      shop_id: 'b1',
      libelle: 'Huile 5W30',
      prix: 89.9,
      unite: 'bidon',
      dossier: 'Moteur',
      prix_achat: 60,
      code_barre: '6111234567890',
      stock: 12,
      seuil_rea: 3,
      attributs: [],
      paliers: [],
    },
  ]

  return {
    supabase: { from: (table: string) => chain(table === 'clients' ? clients : articles) },
  }
})

vi.mock('../src/lib/session.svelte', () => ({
  auth: {
    boutique: {
      id: 'b1',
      nom: 'Atelier Atlas',
      adresse: null,
      telephone: null,
      email: null,
      ice: null,
      if_fiscal: null,
      rc: null,
      patente: null,
      cnss: null,
      banque: null,
      rib: null,
      rib_titulaire: null,
      clause_retard: null,
      tva_defaut: 20,
      tva_non_applicable: false,
      prix_ttc: false,
      conditions_paiement: '30j',
      logo_path: null,
      created_at: '2026-11-10T00:00:00Z',
    },
    user: { email: 'atelier@atlas.ma' },
    deconnexion: vi.fn(),
  },
  chargerBoutique: vi.fn(),
  enregistrerBoutique: vi.fn(),
}))

const montages: Array<{ instance: ReturnType<typeof mount>; cible: HTMLElement }> = []

async function rendre(composant: Parameters<typeof mount>[0]): Promise<HTMLElement> {
  const cible = document.createElement('div')
  document.body.append(cible)
  const instance = mount(composant, { target: cible })
  montages.push({ instance, cible })
  flushSync()
  // onMount starts the query; let its promise chain and the re-render settle.
  await new Promise((resoudre) => setTimeout(resoudre, 0))
  await new Promise((resoudre) => setTimeout(resoudre, 0))
  flushSync()
  return cible
}

afterEach(() => {
  for (const { instance, cible } of montages.splice(0)) {
    unmount(instance)
    cible.remove()
  }
})

// Dynamic imports keep the mocks in place before the component modules load.
const { default: Clients } = await import('../src/routes/Clients.svelte')
const { default: Articles } = await import('../src/routes/Articles.svelte')
const { default: Parametres } = await import('../src/routes/Parametres.svelte')

describe('écran Clients', () => {
  it('affiche la fiche chargée et ses coordonnées', async () => {
    const cible = await rendre(Clients)
    expect(cible.textContent).toContain('Garage Atlas')
    expect(cible.textContent).toContain('0612345678')
    expect(cible.textContent).toContain('ICE 001234567000012')
    expect(cible.textContent).toContain('1 client')
  })

  it('ouvre le formulaire de création', async () => {
    const cible = await rendre(Clients)
    const bouton = [...cible.querySelectorAll('button')].find((b) =>
      b.textContent?.includes('Nouvelle fiche'),
    )
    expect(bouton).toBeTruthy()
    bouton?.click()
    flushSync()
    expect(cible.textContent).toContain('Nom')
    expect(cible.querySelector('#f-nom')).toBeTruthy()
  })
})

describe('écran Paramètres', () => {
  it('remplit le formulaire avec la boutique chargée', async () => {
    const cible = await rendre(Parametres)
    expect(cible.textContent).toContain('Paramètres de la boutique')
    const nom = cible.querySelector<HTMLInputElement>('#b-nom')
    expect(nom?.value).toBe('Atelier Atlas')
    expect(cible.querySelector('#b-tva')).toBeTruthy()
  })
})

describe('écran Articles', () => {
  it('affiche l’article, son prix et son stock', async () => {
    const cible = await rendre(Articles)
    expect(cible.textContent).toContain('Huile 5W30')
    expect(cible.textContent).toContain('89,90 DH / bidon')
    expect(cible.textContent).toContain('12 en stock')
  })

  it('un service se déclare sans stock', async () => {
    const cible = await rendre(Articles)
    const bouton = [...cible.querySelectorAll('button')].find((b) =>
      b.textContent?.includes('Nouvel article'),
    )
    bouton?.click()
    flushSync()
    const service = cible.querySelector<HTMLInputElement>('input[type="checkbox"]')
    expect(service).toBeTruthy()
    expect(cible.querySelector('#a-stock')).toBeTruthy()
    service?.click()
    flushSync()
    expect(cible.querySelector('#a-stock')).toBeNull()
  })
})
