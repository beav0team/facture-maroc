import { describe, expect, it } from 'vitest'
import {
  champsModifies,
  dateCourte,
  derniereConnexion,
  estExpiree,
  filtrerBoutiques,
  filtrerComptes,
  finDansUnAn,
  horodatage,
  isoLocal,
  libelleAction,
  libelleTypeDocument,
  octets,
  planLisible,
  quotaLisible,
  resumeDetails,
  validerAbonnement,
  validerChampsBoutique,
  type BoutiqueAdmin,
  type FicheBoutique,
  type UtilisateurAdmin,
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
  suspendue: false,
  membres: 1,
  derniere_activite: null,
  ice: '001234567000062',
  if_fiscal: '12345678',
  ...morceaux,
})

const compte = (morceaux: Partial<UtilisateurAdmin> = {}): UtilisateurAdmin => ({
  id: 'u1',
  email: 'opipablo45@gmail.com',
  cree_le: '2026-11-11T19:48:02+00:00',
  derniere_connexion: null,
  boutique: 'Atlas',
  boutique_id: 'b1',
  est_admin: false,
  confirme: true,
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

  it('refuse un Essentiel sans date, en nommant le palier', () => {
    expect(validerAbonnement('essentiel', null, '2026-11-11')).toContain('Essentiel')
  })

  it('accepte Essentiel avec une date future', () => {
    expect(validerAbonnement('essentiel', '2027-11-11', '2026-11-11')).toBeNull()
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

  it('dit Essentiel tant que la date court', () => {
    expect(
      planLisible(boutique({ plan: 'essentiel', jusqu_au: '2027-03-01' }), '2026-11-13'),
    ).toBe('Essentiel jusqu’au 01/03/2027')
  })

  it('dit Essentiel expiré quand la date est passée, sans effacer l’histoire', () => {
    expect(
      planLisible(boutique({ plan: 'essentiel', jusqu_au: '2026-03-01' }), '2026-11-13'),
    ).toBe('Essentiel expiré le 01/03/2026')
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

describe('horodatage', () => {
  it('écrit la date et l’heure locales', () => {
    const texte = horodatage(new Date(2026, 10, 13, 7, 5).toISOString())
    expect(texte).toBe('13/11/2026 · 07:05')
  })

  it('écrit un tiret sans valeur', () => {
    expect(horodatage(null)).toBe('—')
  })
})

describe('estExpiree', () => {
  it('ne signale rien pour un Pro vivant', () => {
    expect(estExpiree(boutique({ pro: true, plan: 'pro', jusqu_au: '2027-01-01' }), '2026-11-13')).toBe(
      false,
    )
  })

  it('signale un Pro dont la date est passée', () => {
    expect(estExpiree(boutique({ plan: 'pro', jusqu_au: '2026-11-12' }), '2026-11-13')).toBe(true)
  })

  it('ne signale pas une boutique jamais passée en Pro', () => {
    expect(estExpiree(boutique(), '2026-11-13')).toBe(false)
  })

  it('signale un Essentiel dont la date est passée', () => {
    expect(
      estExpiree(boutique({ plan: 'essentiel', jusqu_au: '2026-11-12' }), '2026-11-13'),
    ).toBe(true)
  })
})

describe('filtrerBoutiques', () => {
  const liste = [
    boutique({ id: 'b1', nom: 'Atlas', pro: true, plan: 'pro', jusqu_au: '2027-11-11' }),
    boutique({ id: 'b2', nom: 'Benzen', proprietaire: 'sbensaria660@gmail.com', ice: null }),
    boutique({ id: 'b3', nom: 'Agharas', suspendue: true, proprietaire: null }),
    boutique({ id: 'b4', nom: 'Vieux Pro', plan: 'pro', jusqu_au: '2026-01-01' }),
    boutique({ id: 'b5', nom: 'Medina', plan: 'essentiel', jusqu_au: '2027-03-01', ice: '002345678000063' }),
    boutique({ id: 'b6', nom: 'Ancien Essentiel', plan: 'essentiel', jusqu_au: '2026-02-01', ice: null }),
  ]

  it('cherche dans le nom, l’e-mail et l’ICE', () => {
    expect(filtrerBoutiques(liste, 'benzen').map((b) => b.id)).toEqual(['b2'])
    expect(filtrerBoutiques(liste, 'sbensaria').map((b) => b.id)).toEqual(['b2'])
    expect(filtrerBoutiques(liste, '001234567000062').map((b) => b.id)).toEqual(['b1', 'b3', 'b4'])
  })

  it('filtre par plan, suspension, expiration et absence de propriétaire', () => {
    expect(filtrerBoutiques(liste, '', 'pro', '2026-11-13').map((b) => b.id)).toEqual(['b1'])
    expect(filtrerBoutiques(liste, '', 'essentiel', '2026-11-13').map((b) => b.id)).toEqual(['b5'])
    expect(filtrerBoutiques(liste, '', 'gratuit', '2026-11-13').map((b) => b.id)).toEqual(['b2', 'b3'])
    expect(filtrerBoutiques(liste, '', 'suspendue', '2026-11-13').map((b) => b.id)).toEqual(['b3'])
    expect(filtrerBoutiques(liste, '', 'expire', '2026-11-13').map((b) => b.id)).toEqual(['b4', 'b6'])
    expect(filtrerBoutiques(liste, '', 'sans_proprietaire', '2026-11-13').map((b) => b.id)).toEqual([
      'b3',
    ])
  })

  it('ignore la casse et les espaces autour de la recherche', () => {
    expect(filtrerBoutiques(liste, '  ATLAS ', 'toutes').map((b) => b.id)).toEqual(['b1'])
  })
})

describe('filtrerComptes', () => {
  const comptes = [
    compte({ id: 'u1', email: 'a@exemple.ma', boutique: 'Atlas' }),
    compte({ id: 'u2', email: 'b@exemple.ma', boutique: 'Benzen', confirme: false }),
  ]

  it('cherche dans l’e-mail et la boutique', () => {
    expect(filtrerComptes(comptes, 'benzen').map((c) => c.id)).toEqual(['u2'])
    expect(filtrerComptes(comptes, 'a@exemple').map((c) => c.id)).toEqual(['u1'])
  })

  it('rend tout sans recherche', () => {
    expect(filtrerComptes(comptes, '   ')).toHaveLength(2)
  })
})

describe('libellés', () => {
  it('traduit les types de document', () => {
    expect(libelleTypeDocument('acompte')).toBe('Facture d’acompte')
    expect(libelleTypeDocument('inconnu')).toBe('inconnu')
  })

  it('traduit les actions du journal', () => {
    expect(libelleAction('boutique_suspendue')).toBe('Boutique suspendue')
    expect(libelleAction('abonnement')).toBe('Abonnement modifié')
    expect(libelleAction('autre')).toBe('autre')
  })

  it('résume les détails d’une action', () => {
    expect(resumeDetails('quota_bonus', { documents_sup: 3, raison: 'geste' })).toBe(
      '+3 document(s) · geste',
    )
    expect(resumeDetails('membre_role', { compte: 'a@b.ma', ancien: 'owner', nouveau: 'membre' })).toBe(
      'a@b.ma : owner → membre',
    )
    expect(resumeDetails('boutique_modifiee', { champs: { nom: 'X', ice: 'Y', tva_defaut: 20 } })).toBe(
      'nom, ICE, TVA',
    )
  })

  it('résume un changement d’abonnement avec son palier, sa date et sa note', () => {
    expect(
      resumeDetails('abonnement', {
        ancien: 'gratuit',
        nouveau: 'essentiel',
        jusqu_au: '2027-11-11',
        note: 'virement reçu',
      }),
    ).toBe('Gratuit → Essentiel jusqu’au 11/11/2027 · virement reçu')

    expect(
      resumeDetails('abonnement', {
        ancien: 'pro',
        nouveau: 'gratuit',
        jusqu_au: null,
        note: '',
      }),
    ).toBe('Pro → Gratuit')

    expect(
      resumeDetails('abonnement', {
        ancien: 'aucun',
        nouveau: 'pro',
        jusqu_au: '2027-01-05',
        note: '',
      }),
    ).toBe('aucun → Pro jusqu’au 05/01/2027')
  })
})

describe('champsModifies', () => {
  const fiche: FicheBoutique['boutique'] = {
    id: 'b1',
    nom: 'Atlas',
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
  }

  it('n’envoie que ce qui a changé, et coupe les espaces', () => {
    expect(
      champsModifies(fiche, { nom: '  Atlas  ', ice: '001234567000062', tva_defaut: 14 }),
    ).toEqual({ tva_defaut: 14 })
  })

  it('envoie un champ vidé, parce que vider est un changement', () => {
    expect(champsModifies({ ...fiche, rc: '12345' }, { rc: '   ' })).toEqual({ rc: '' })
  })

  it('compare les booléens et les objets', () => {
    expect(champsModifies(fiche, { arrondi_dirham: true })).toEqual({ arrondi_dirham: true })
    expect(champsModifies(fiche, { prefixes: {} })).toEqual({})
  })
})

describe('validerChampsBoutique', () => {
  it('refuse un nom vide ou trop long', () => {
    expect(validerChampsBoutique({ nom: '  ' })).toContain('nom')
    expect(validerChampsBoutique({ nom: 'x'.repeat(121) })).toContain('nom')
  })

  it('refuse une TVA hors bornes', () => {
    expect(validerChampsBoutique({ tva_defaut: 101 })).toContain('TVA')
    expect(validerChampsBoutique({ tva_defaut: -1 })).toContain('TVA')
  })

  it('refuse un modèle inconnu et un e-mail mal formé', () => {
    expect(validerChampsBoutique({ design_defaut: 'moderne' as never })).toContain('Modèle')
    expect(validerChampsBoutique({ email: 'pas-un-email' })).toContain('e-mail')
  })

  it('accepte un e-mail vide et les valeurs normales', () => {
    expect(validerChampsBoutique({ email: '' })).toBeNull()
    expect(validerChampsBoutique({ nom: 'Atlas', tva_defaut: 20 })).toBeNull()
  })
})

describe('quotaLisible', () => {
  const fiche = (quota: FicheBoutique['quota'], pro: boolean): FicheBoutique =>
    ({ pro, quota }) as FicheBoutique

  it('montre l’usage et la limite', () => {
    expect(quotaLisible(fiche({ utilise: 3, limite: 8, bonus: 3 }, false))).toBe('3 / 8 ce mois')
  })

  it('dit illimité en Pro', () => {
    expect(quotaLisible(fiche({ utilise: 40, limite: null, bonus: 0 }, true))).toBe(
      'Documents illimités',
    )
  })
})
