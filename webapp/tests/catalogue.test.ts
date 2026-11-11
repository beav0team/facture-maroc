import { describe, expect, it } from 'vitest'
import {
  dossiersDe,
  entierDepuisTexte,
  erreurClient,
  etatStock,
  filtrerArticles,
  filtrerClients,
  libelleStock,
  valeursArticle,
  valeursClient,
  type Article,
  type ClientEnregistre,
  type SaisieArticle,
  type SaisieClient,
} from '../src/lib/catalogue'

function client(partiel: Partial<ClientEnregistre>): ClientEnregistre {
  return {
    id: partiel.id ?? crypto.randomUUID(),
    shop_id: 'boutique',
    nom: 'Sans nom',
    adresse: null,
    ice: null,
    if_fiscal: null,
    rc: null,
    patente: null,
    cnss: null,
    telephone: null,
    email: null,
    role: 'client',
    exonere_tva: false,
    note: null,
    ...partiel,
  }
}

function article(partiel: Partial<Article>): Article {
  return {
    id: partiel.id ?? crypto.randomUUID(),
    shop_id: 'boutique',
    libelle: 'Article',
    prix: 10,
    unite: '',
    dossier: '',
    prix_achat: 0,
    code_barre: '',
    stock: null,
    seuil_rea: null,
    attributs: [],
    paliers: [],
    ...partiel,
  }
}

function saisieClient(partiel: Partial<SaisieClient> = {}): SaisieClient {
  return {
    nom: '',
    role: 'client',
    adresse: '',
    ice: '',
    if_fiscal: '',
    rc: '',
    patente: '',
    cnss: '',
    telephone: '',
    email: '',
    exonere_tva: false,
    note: '',
    ...partiel,
  }
}

function saisieArticle(partiel: Partial<SaisieArticle> = {}): SaisieArticle {
  return {
    libelle: '',
    prix: '',
    unite: '',
    dossier: '',
    prix_achat: '',
    code_barre: '',
    service: false,
    stock: '',
    seuil_rea: '',
    ...partiel,
  }
}

describe('filtrerClients', () => {
  const clients = [
    client({ nom: 'Garage Atlas', telephone: '0612345678' }),
    client({ nom: 'Épicerie Al Amal', email: 'contact@amal.ma' }),
    client({ nom: 'Fournisseur Nord', role: 'fournisseur', ice: '001234567000012' }),
  ]

  it('trie par nom, sans tenir compte des accents', () => {
    expect(filtrerClients(clients, '').map((c) => c.nom)).toEqual([
      'Épicerie Al Amal',
      'Fournisseur Nord',
      'Garage Atlas',
    ])
  })

  it('trouve un nom tapé sans accent', () => {
    expect(filtrerClients(clients, 'epicerie')).toHaveLength(1)
  })

  it('cherche aussi dans le téléphone, l’e-mail et l’ICE', () => {
    expect(filtrerClients(clients, '0612')).toHaveLength(1)
    expect(filtrerClients(clients, 'amal.ma')).toHaveLength(1)
    expect(filtrerClients(clients, '001234')).toHaveLength(1)
  })

  it('filtre par rôle', () => {
    expect(filtrerClients(clients, '', 'fournisseur').map((c) => c.nom)).toEqual([
      'Fournisseur Nord',
    ])
    expect(filtrerClients(clients, '', 'client')).toHaveLength(2)
  })
})

describe('clients : validation et valeurs', () => {
  it('exige un nom', () => {
    expect(erreurClient(saisieClient())).toBe('Le nom est obligatoire.')
    expect(erreurClient(saisieClient({ nom: '   ' }))).toBe('Le nom est obligatoire.')
  })

  it('refuse une adresse e-mail mal formée', () => {
    expect(erreurClient(saisieClient({ nom: 'A', email: 'pas-un-email' }))).not.toBeNull()
    expect(erreurClient(saisieClient({ nom: 'A', email: 'a@b.ma' }))).toBeNull()
  })

  it('taille les champs et transforme le vide en null', () => {
    const valeurs = valeursClient(
      saisieClient({ nom: '  Atlas  ', telephone: '  ', email: ' a@b.ma ' }),
    )
    expect(valeurs.nom).toBe('Atlas')
    expect(valeurs.telephone).toBeNull()
    expect(valeurs.email).toBe('a@b.ma')
  })
})

describe('filtrerArticles', () => {
  const articles = [
    article({ libelle: 'Huile 5W30', dossier: 'Moteur', code_barre: '6111234567890' }),
    article({ libelle: 'Main-d’œuvre', dossier: '', unite: 'heure' }),
    article({ libelle: 'Filtre à air', dossier: 'Moteur' }),
  ]

  it('trouve par libellé, code-barres ou unité', () => {
    expect(filtrerArticles(articles, 'huile')).toHaveLength(1)
    expect(filtrerArticles(articles, '6111')).toHaveLength(1)
    expect(filtrerArticles(articles, 'heure')).toHaveLength(1)
  })

  it('distingue « tous les dossiers » de « sans dossier »', () => {
    expect(filtrerArticles(articles, '', null)).toHaveLength(3)
    expect(filtrerArticles(articles, '', '')).toHaveLength(1)
    expect(filtrerArticles(articles, '', 'Moteur')).toHaveLength(2)
  })

  it('liste les dossiers non vides, A→Z', () => {
    expect(dossiersDe(articles)).toEqual(['Moteur'])
  })
})

describe('articles : saisie', () => {
  it('lit un prix à la française et arrondit au centime', () => {
    const valeurs = valeursArticle(saisieArticle({ libelle: 'Câble', prix: '1 234,567' }))
    expect('erreur' in valeurs).toBe(false)
    if (!('erreur' in valeurs)) {
      expect(valeurs.prix).toBe(1234.57)
      expect(valeurs.prix_achat).toBe(0)
    }
  })

  it('un service n’a pas de stock, un produit si', () => {
    const service = valeursArticle(saisieArticle({ libelle: 'Main-d’œuvre', prix: '150', service: true }))
    expect('erreur' in service).toBe(false)
    if (!('erreur' in service)) {
      expect(service.stock).toBeNull()
      expect(service.seuil_rea).toBeNull()
    }

    const produit = valeursArticle(
      saisieArticle({ libelle: 'Huile', prix: '89,90', stock: '12', seuil_rea: '3' }),
    )
    expect('erreur' in produit).toBe(false)
    if (!('erreur' in produit)) {
      expect(produit.stock).toBe(12)
      expect(produit.seuil_rea).toBe(3)
    }
  })

  it('refuse un prix négatif ou un stock décimal', () => {
    expect(valeursArticle(saisieArticle({ libelle: 'X', prix: '-1' }))).toEqual({
      erreur: 'Le prix de vente ne peut pas être négatif.',
    })
    expect(
      valeursArticle(saisieArticle({ libelle: 'X', prix: '1', stock: '2,5' })),
    ).toEqual({ erreur: 'Le stock doit être un nombre entier.' })
  })

  it('garde un stock négatif : c’est une information, pas une erreur', () => {
    const valeurs = valeursArticle(saisieArticle({ libelle: 'X', prix: '1', stock: '-2' }))
    expect('erreur' in valeurs).toBe(false)
    if (!('erreur' in valeurs)) expect(valeurs.stock).toBe(-2)
  })
})

describe('entierDepuisTexte', () => {
  it('lit le vide comme « non renseigné »', () => {
    expect(entierDepuisTexte('')).toBeNull()
    expect(entierDepuisTexte('  ')).toBeNull()
  })

  it('accepte un entier négatif', () => {
    expect(entierDepuisTexte('-3')).toBe(-3)
  })

  it('signale un décimal ou du texte', () => {
    expect(entierDepuisTexte('1,5')).toBe('erreur')
    expect(entierDepuisTexte('abc')).toBe('erreur')
  })
})

describe('etatStock', () => {
  it('un service est neutre', () => {
    expect(etatStock(article({ stock: null }))).toBe('service')
    expect(libelleStock(article({ stock: null }))).toBe('Service')
  })

  it('un stock négatif est en rupture', () => {
    expect(etatStock(article({ stock: -1 }))).toBe('rupture')
  })

  it('le seuil se compare à stock <= seuil', () => {
    expect(etatStock(article({ stock: 2, seuil_rea: 2 }))).toBe('seuil')
    expect(etatStock(article({ stock: 3, seuil_rea: 2 }))).toBe('ok')
  })
})
