// @vitest-environment jsdom
//
// The console reads RPC columns whose names must match the screen (`cree_le`,
// `jusqu_au`, `est_admin`, `archive_path`…). A rename in SQL would type-check
// in TypeScript and render `undefined` in the browser; these tests mount the
// real components against a fake Supabase and read what an admin would see.
//
// Two invariants ride along: the screen only calls functions (never a table),
// and the dossier sends only the fields that really changed.

import { flushSync, mount, unmount } from 'svelte'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { appels, BOUTIQUES, COMPTES, ALERTES, FICHE, MEMBRES, DOCUMENTS, JOURNAL, stockage } =
  vi.hoisted(() => {
    const BOUTIQUES = [
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
      suspendue: false,
      membres: 2,
      derniere_activite: '2026-11-12T09:00:00+00:00',
      ice: '001234567000062',
      if_fiscal: '12345678',
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
      suspendue: true,
      membres: 1,
      derniere_activite: null,
      ice: null,
      if_fiscal: null,
    },
    {
      id: 'b4',
      nom: 'Medina',
      cree_le: '2026-10-01T10:00:00+00:00',
      proprietaire: 'medina@example.ma',
      proprietaire_id: 'u4',
      plan: 'essentiel',
      jusqu_au: '2027-03-01',
      note: '',
      documents: 12,
      clients: 3,
      articles: 5,
      pro: false,
      suspendue: false,
      membres: 1,
      derniere_activite: '2026-11-10T09:00:00+00:00',
      ice: '002345678000063',
      if_fiscal: '22334455',
    },
  ]
  const COMPTES = [
    {
      id: 'u1',
      email: 'opipablo45@gmail.com',
      cree_le: '2026-11-11T19:48:02+00:00',
      derniere_connexion: '2026-11-11T20:30:00+00:00',
      boutique: 'Atlas',
      boutique_id: 'b1',
      est_admin: false,
      confirme: true,
    },
    {
      id: 'u9',
      email: 'agnaou1996@gmail.com',
      cree_le: '2026-11-11T20:09:27+00:00',
      derniere_connexion: null,
      boutique: 'PNEUMATIQUE AGHARAS',
      boutique_id: 'b3',
      est_admin: true,
      confirme: false,
    },
  ]
  const ALERTES = [
    {
      genre: 'quota_atteint',
      shop_id: 'b2',
      nom: 'Benzen',
      detail: 'Quota gratuit utilisé : 5 / 5',
      echeance: null,
    },
    {
      genre: 'expire_bientot',
      shop_id: 'b1',
      nom: 'Atlas',
      detail: 'Pro jusqu’au 11/11/2027',
      echeance: '2027-11-11',
    },
  ]
  const FICHE = {
    boutique: {
      id: 'b1',
      nom: 'Atlas',
      created_at: '2026-11-11T19:48:19+00:00',
      ice: '001234567000062',
      if_fiscal: '12345678',
      rc: null,
      patente: null,
      cnss: null,
      adresse: null,
      telephone: null,
      email: null,
      banque: null,
      rib: null,
      rib_titulaire: null,
      clause_retard: null,
      conditions_paiement: '30j',
      tva_defaut: 20,
      arrondi_dirham: false,
      prix_ttc: false,
      tva_non_applicable: false,
      montant_lettres: true,
      numerotation_annuelle: false,
      design_defaut: 'classique',
      prefixes: {},
    },
    proprietaire: 'opipablo45@gmail.com',
    proprietaire_id: 'u1',
    membres: 1,
    pro: false,
    abonnement: { plan: 'gratuit', jusqu_au: null, note: '', modifie_le: null },
    quota: { utilise: 3, limite: 5, bonus: 0 },
    suspension: null,
    documents: 4,
    clients: 2,
    articles: 7,
    archives: { nombre: 3, octets: 1_500_000 },
    notes: [{ id: 'n1', note: 'Rappelé le 13/11.', par: 'agnaou1996@gmail.com', quand: '2026-11-13T08:00:00+00:00' }],
  }
  const MEMBRES = [
    {
      user_id: 'u1',
      email: 'opipablo45@gmail.com',
      role: 'owner',
      depuis: '2026-11-11T19:48:19+00:00',
      derniere_connexion: '2026-11-12T09:00:00+00:00',
    },
  ]

  const DOCUMENTS = [
    {
      id: 'd1',
      numero: 'F-20261112-0001',
      type: 'facture',
      statut: 'emis',
      total_ttc: 1200,
      date_emission: '12/11/2026',
      quand: '2026-11-12T10:00:00+00:00',
      client: 'Garage Atlas',
      archive_path: 'b1/d1.pdf',
      archive_taille: 1200,
    },
  ]

  const JOURNAL = [
    {
      id: 'j1',
      quand: '2026-11-13T08:00:00+00:00',
      acteur: 'agnaou1996@gmail.com',
      action: 'boutique_suspendue',
      cible_type: 'boutique',
      cible_id: 'b1',
      cible_nom: 'Atlas',
      details: { raison: 'Impayé' },
    },
  ]
  const stockage = { download: vi.fn(), remove: vi.fn(async () => ({ error: null })) }
  return {
    appels: [] as { nom: string; args: unknown }[],
    BOUTIQUES,
    COMPTES,
    ALERTES,
    FICHE,
    MEMBRES,
    DOCUMENTS,
    JOURNAL,
    stockage,
  }
})

vi.mock('../src/lib/supabase', () => {
  const reponses: Record<string, unknown> = {
    est_admin_plateforme: true,
    admin_statistiques: {
      boutiques: 3,
      utilisateurs: 2,
      administrateurs: 1,
      pro: 1,
      essentiel: 1,
      documents: 4,
      documents_emis: 3,
      archives: 3,
      octets_archives: 1_500_000,
    },
    admin_boutiques: BOUTIQUES,
    admin_utilisateurs: COMPTES,
    admin_alertes: ALERTES,
    admin_boutique: FICHE,
    admin_membres: MEMBRES,
    admin_documents: DOCUMENTS,
    admin_journal: JOURNAL,
    admin_nommer: null,
    admin_retirer: null,
    admin_abonnement: null,
    admin_modifier_boutique: null,
    reglages_publics: { whatsapp: '' },
    admin_reglage_whatsapp: '212612345678',
  }
  return {
    estConfigure: true,
    supabase: {
      auth: { getSession: async () => ({ data: { session: null } }) },
      rpc: async (nom: string, args: unknown) => {
        appels.push({ nom, args })
        // `admin_tarifs` rend le barème enregistré : la console le réaffiche et
        // annonce les prix que la base vient d'accepter.
        if (nom === 'admin_tarifs') {
          const envoyes = (args as { p_tarifs?: unknown } | null)?.p_tarifs
          return { data: envoyes ?? null, error: null }
        }
        return { data: reponses[nom] ?? null, error: null }
      },
      storage: { from: () => stockage },
      from: () => {
        throw new Error('la console ne doit pas interroger une table')
      },
    },
    client: () => null,
  }
})

import { auth } from '../src/lib/session.svelte'
import { route } from '../src/lib/router.svelte'
import Admin from '../src/routes/Admin.svelte'
import Fiche from '../src/routes/admin/Fiche.svelte'

let monte: ReturnType<typeof mount> | null = null

beforeEach(() => {
  appels.length = 0
  stockage.download.mockReset()
  auth.user = { id: 'u9', email: 'agnaou1996@gmail.com' } as never
  auth.boutique = { id: 'b3', nom: 'PNEUMATIQUE AGHARAS' } as never
  route.chemin = '/admin'
  document.body.innerHTML = ''
})

afterEach(() => {
  if (monte) unmount(monte)
  monte = null
})

async function rendre(composant = Admin, props: Record<string, unknown> = {}) {
  monte = mount(composant, { target: document.body, props })
  flushSync()
  for (let i = 0; i < 10; i += 1) {
    await Promise.resolve()
    flushSync()
  }
  return document.body
}

const noms = () => appels.map((appel) => appel.nom)

describe('console d’administration — la coquille', () => {
  it('ne demande que le droit d’entrée et les chiffres, jamais une table', async () => {
    await rendre()
    expect(noms()).toContain('est_admin_plateforme')
    expect(noms()).toContain('admin_statistiques')
    // L'onglet « Vue d'ensemble » charge ses alertes, mais rien d'autre.
    expect(noms()).toContain('admin_alertes')
    expect(noms()).not.toContain('admin_boutiques')
  })

  it('refuse l’accès à un compte qui n’est pas administrateur', async () => {
    const { supabase } = await import('../src/lib/supabase')
    const espion = vi.spyOn(supabase!, 'rpc').mockResolvedValueOnce({ data: false, error: null } as never)
    const body = await rendre()
    expect(body.textContent).toContain('réservé aux administrateurs')
    espion.mockRestore()
  })

  it('affiche les quatre onglets et les chiffres de la plateforme', async () => {
    const body = await rendre()
    const texte = body.textContent ?? ''
    expect(texte).toContain('Vue d’ensemble')
    expect(texte).toContain('Boutiques')
    expect(texte).toContain('Comptes')
    expect(texte).toContain('Journal')
    expect(texte).toContain('3 boutiques')
    expect(texte).toContain('1 en Essentiel')
    expect(texte).toContain('1 en Pro')
    expect(texte).toContain('1,5 Mo')
  })
})

describe('vue d’ensemble', () => {
  it('affiche les alertes, les plus graves d’abord, avec un lien vers la fiche', async () => {
    const body = await rendre()
    const texte = body.textContent ?? ''
    expect(texte).toContain('Quota atteint')
    expect(texte).toContain('Quota gratuit utilisé : 5 / 5')
    expect(texte).toContain('Expire bientôt')
    const liens = [...body.querySelectorAll('a')].map((a) => a.getAttribute('href'))
    expect(liens).toContain('/app/admin/boutiques/b2')
    expect(liens).toContain('/app/admin/boutiques/b1')
  })

  it('garde l’ordre de gravité, quota avant expiration', async () => {
    const body = await rendre()
    const texte = body.textContent ?? ''
    expect(texte.indexOf('Quota atteint')).toBeLessThan(texte.indexOf('Expire bientôt'))
  })
})

describe('liste des boutiques', () => {
  it('montre le plan, le propriétaire et l’activité', async () => {
    route.chemin = '/admin/boutiques'
    const body = await rendre()
    const texte = body.textContent ?? ''
    expect(texte).toContain('Atlas')
    expect(texte).toContain('opipablo45@gmail.com')
    expect(texte).toContain('Pro jusqu’au 11/11/2027')
    expect(texte).toContain('Benzen')
    expect(texte).toContain('Plan gratuit')
    expect(texte).toContain('Suspendue')
    expect(texte).toContain('ICE / IF manquant')
  })

  it('filtre à la frappe et par étiquette', async () => {
    route.chemin = '/admin/boutiques'
    const body = await rendre()

    const recherche = body.querySelector('#filtre-boutiques') as HTMLInputElement
    recherche.value = 'benzen'
    recherche.dispatchEvent(new Event('input', { bubbles: true }))
    flushSync()
    expect(body.textContent).toContain('Benzen')
    expect(body.textContent).not.toContain('Pro jusqu’au')

    recherche.value = ''
    recherche.dispatchEvent(new Event('input', { bubbles: true }))
    flushSync()

    const suspendues = [...body.querySelectorAll('.filtres button')].find(
      (b) => b.textContent?.trim() === 'Suspendues',
    ) as HTMLButtonElement
    suspendues.click()
    flushSync()
    expect(body.textContent).toContain('Benzen')
    expect(body.textContent).not.toContain('Atlas')
  })
})

describe('comptes', () => {
  it('marque les administrateurs et les e-mails non confirmés', async () => {
    route.chemin = '/admin/comptes'
    const body = await rendre()
    const texte = body.textContent ?? ''
    expect(texte).toContain('agnaou1996@gmail.com')
    expect(texte).toContain('administrateur')
    expect(texte).toContain('e-mail non confirmé')
    expect(texte).toContain('jamais')
  })
})

describe('journal', () => {
  it('traduit l’action et cite son auteur', async () => {
    route.chemin = '/admin/journal'
    const body = await rendre()
    const texte = body.textContent ?? ''
    expect(texte).toContain('Boutique suspendue')
    expect(texte).toContain('agnaou1996@gmail.com')
    expect(texte).toContain('Impayé')
  })
})

describe('réglages de la plateforme', () => {
  it('lit le numéro WhatsApp et ne touche à aucune boutique', async () => {
    route.chemin = '/admin/reglages'
    const body = await rendre()
    const texte = body.textContent ?? ''
    expect(texte).toContain('Achat par WhatsApp')
    expect(texte).toContain('Vide')
    expect(noms()).toContain('reglages_publics')
    expect(noms()).not.toContain('admin_boutiques')
  })

  it('enregistre un numéro marocain et l’affiche normalisé', async () => {
    route.chemin = '/admin/reglages'
    const body = await rendre()
    appels.length = 0

    const champ = body.querySelector('#r-whatsapp') as HTMLInputElement
    champ.value = '06 12 34 56 78'
    champ.dispatchEvent(new Event('input', { bubbles: true }))
    flushSync()

    ;(body.querySelector('form.formulaire') as HTMLFormElement).dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    )
    for (let i = 0; i < 6; i += 1) {
      await Promise.resolve()
      flushSync()
    }

    const appel = appels.find((a) => a.nom === 'admin_reglage_whatsapp')
    expect(appel).toBeDefined()
    expect(appel!.args).toEqual({ p_numero: '06 12 34 56 78' })

    const texte = body.textContent ?? ''
    expect(texte).toContain('Numéro enregistré : 06 12 34 56 78')
    expect(texte).toContain('Réglé')
  })

  it('refuse un texte sans appeler la base', async () => {
    route.chemin = '/admin/reglages'
    const body = await rendre()
    appels.length = 0

    const champ = body.querySelector('#r-whatsapp') as HTMLInputElement
    champ.value = 'bonjour'
    champ.dispatchEvent(new Event('input', { bubbles: true }))
    flushSync()

    ;(champ.closest('form') as HTMLFormElement).dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    )
    flushSync()

    expect(noms()).not.toContain('admin_reglage_whatsapp')
    expect(body.textContent).toContain('ne ressemble pas à un numéro marocain')
  })

  it('enregistre le barème des deux paliers et annonce les nouveaux prix', async () => {
    route.chemin = '/admin/reglages'
    const body = await rendre()
    appels.length = 0

    const saisir = (selecteur: string, valeur: string) => {
      const champ = body.querySelector(selecteur) as HTMLInputElement
      champ.value = valeur
      champ.dispatchEvent(new Event('input', { bubbles: true }))
    }
    saisir('#t-essentiel-prix', '300')
    saisir('#t-essentiel-quota', '60')
    saisir('#t-pro-prix', '600')
    flushSync()

    const formulaire = (body.querySelector('#t-essentiel-prix') as HTMLElement).closest(
      'form',
    ) as HTMLFormElement
    formulaire.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    for (let i = 0; i < 6; i += 1) {
      await Promise.resolve()
      flushSync()
    }

    const appel = appels.find((a) => a.nom === 'admin_tarifs')
    expect(appel).toBeDefined()
    const envoi = appel!.args as {
      p_tarifs: {
        essentiel: { prix: number; quota: number | null; points: string[] }
        pro: { prix: number; quota: number | null; points: string[] }
      }
    }
    expect(envoi.p_tarifs.essentiel.prix).toBe(300)
    expect(envoi.p_tarifs.essentiel.quota).toBe(60)
    expect(envoi.p_tarifs.essentiel.points.length).toBeGreaterThan(0)
    expect(envoi.p_tarifs.pro.prix).toBe(600)
    expect(envoi.p_tarifs.pro.quota).toBeNull()

    expect(body.textContent).toContain('Barème enregistré : Essentiel 300 DH, Pro 600 DH')
  })

  it('refuse un prix hors bornes sans appeler la base', async () => {
    route.chemin = '/admin/reglages'
    const body = await rendre()
    appels.length = 0

    const prix = body.querySelector('#t-essentiel-prix') as HTMLInputElement
    prix.value = '0'
    prix.dispatchEvent(new Event('input', { bubbles: true }))
    flushSync()

    ;(prix.closest('form') as HTMLFormElement).dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    )
    flushSync()

    expect(noms()).not.toContain('admin_tarifs')
    expect(body.textContent).toContain('entre 50 et 50 000 DH')
  })
})

describe('fiche boutique', () => {
  it('lit le dossier, les membres, les documents et l’historique', async () => {
    const body = await rendre(Fiche, { id: 'b1' })
    const texte = body.textContent ?? ''
    expect(texte).toContain('Atlas')
    expect(texte).toContain('3 / 5 ce mois')
    expect(texte).toContain('Garage Atlas')
    expect(texte).toContain('F-20261112-0001')
    expect(texte).toContain('Rappelé le 13/11.')
    expect(texte).toContain('Boutique suspendue')
    expect(noms()).toEqual(
      expect.arrayContaining(['admin_boutique', 'admin_membres', 'admin_documents', 'admin_journal']),
    )
  })

  it('n’envoie que les champs modifiés', async () => {
    const body = await rendre(Fiche, { id: 'b1' })
    appels.length = 0

    const tva = body.querySelector('#b-tva') as HTMLInputElement
    tva.value = '14'
    tva.dispatchEvent(new Event('input', { bubbles: true }))
    flushSync()

    ;(body.querySelector('form.formulaire') as HTMLFormElement).dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    )
    for (let i = 0; i < 6; i += 1) {
      await Promise.resolve()
      flushSync()
    }

    const appel = appels.find((a) => a.nom === 'admin_modifier_boutique')
    expect(appel).toBeDefined()
    expect(appel!.args).toEqual({ p_shop: 'b1', p_champs: { tva_defaut: 14 } })
  })

  it('n’active pas la suppression tant que le nom tapé ne correspond pas', async () => {
    const body = await rendre(Fiche, { id: 'b1' })
    const champ = body.querySelector('#confirmation-nom') as HTMLInputElement
    const bouton = () =>
      [...body.querySelectorAll('button')].find(
        (b) => b.textContent?.trim() === 'Supprimer définitivement',
      ) as HTMLButtonElement

    expect(bouton().disabled).toBe(true)

    champ.value = 'Atla'
    champ.dispatchEvent(new Event('input', { bubbles: true }))
    flushSync()
    expect(bouton().disabled).toBe(true)

    champ.value = 'Atlas'
    champ.dispatchEvent(new Event('input', { bubbles: true }))
    flushSync()
    expect(bouton().disabled).toBe(false)
  })

  it('télécharge le PDF archivé par l’API Storage, pas par une table', async () => {
    const body = await rendre(Fiche, { id: 'b1' })
    const url = globalThis.URL as unknown as { createObjectURL: unknown; revokeObjectURL: unknown }
    const creation = vi.fn(() => 'blob:pdf')
    url.createObjectURL = creation
    url.revokeObjectURL = vi.fn()
    const clic = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    stockage.download.mockResolvedValue({ data: new Blob(['%PDF']), error: null })

    const pdf = [...body.querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === 'PDF',
    ) as HTMLButtonElement
    expect(pdf).toBeDefined()
    pdf.click()
    for (let i = 0; i < 6; i += 1) {
      await Promise.resolve()
      flushSync()
    }

    expect(stockage.download).toHaveBeenCalledWith('b1/d1.pdf')
    expect(creation).toHaveBeenCalled()
    expect(clic).toHaveBeenCalled()
    clic.mockRestore()
  })
})

describe('abonnement depuis la fiche', () => {
  it('propose explicitement Essentiel et Pro pour une boutique gratuite', async () => {
    const body = await rendre(Fiche, { id: 'b1' })
    const libelles = [...body.querySelectorAll('button')].map((b) => b.textContent?.trim())
    expect(libelles).toContain('Essentiel 1 an')
    expect(libelles).toContain('Pro 1 an')
    expect(libelles).toContain('Autre durée…')
  })

  it('active Essentiel pour un an depuis le bouton direct', async () => {
    const body = await rendre(Fiche, { id: 'b1' })
    const bouton = [...body.querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === 'Essentiel 1 an',
    ) as HTMLButtonElement
    bouton.click()
    for (let i = 0; i < 6; i += 1) {
      await Promise.resolve()
      flushSync()
    }
    const appel = appels.find((a) => a.nom === 'admin_abonnement')
    expect(appel).toBeDefined()
    expect((appel?.args as Record<string, unknown>).p_plan).toBe('essentiel')
    expect((appel?.args as Record<string, unknown>).p_jusqu_au).toBeTruthy()
  })

  it('ouvre le choix des trois paliers et désactive la date pour le gratuit', async () => {
    const body = await rendre(Fiche, { id: 'b1' })
    const autre = [...body.querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === 'Autre durée…',
    ) as HTMLButtonElement
    autre.click()
    flushSync()

    const palier = body.querySelector('#palier-abonnement') as HTMLSelectElement
    expect([...palier.options].map((o) => o.value)).toEqual(['gratuit', 'essentiel', 'pro'])

    palier.value = 'gratuit'
    palier.dispatchEvent(new Event('change', { bubbles: true }))
    flushSync()
    const fin = body.querySelector('#fin-abonnement') as HTMLInputElement
    expect(fin.disabled).toBe(true)
  })
})
