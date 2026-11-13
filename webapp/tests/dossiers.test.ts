import { describe, expect, it } from 'vitest'
import type { Article } from '../src/lib/catalogue'
import {
  afficher,
  aDuStock,
  articlesDans,
  articlesDirects,
  avecAncetres,
  compterArticles,
  descendantsDe,
  enfantsDe,
  erreurChemin,
  erreurNom,
  estDans,
  joindre,
  nomDe,
  normaliserChemin,
  parentDe,
  quantiteDans,
  rebase,
  suggestionCopie,
} from '../src/lib/dossiers'

// The rules the tree is made of, ported from the Android suite (FolderPathTest)
// so the two applications cannot disagree about what "inside Mobilier" means.

function article(partiel: Partial<Article>): Article {
  return {
    id: partiel.id ?? crypto.randomUUID(),
    shop_id: 'boutique',
    libelle: partiel.libelle ?? 'Article',
    prix: partiel.prix ?? 10,
    unite: partiel.unite ?? '',
    dossier: partiel.dossier ?? '',
    prix_achat: partiel.prix_achat ?? 0,
    code_barre: partiel.code_barre ?? '',
    stock: partiel.stock ?? null,
    seuil_rea: partiel.seuil_rea ?? null,
    attributs: [],
    paliers: [],
  }
}

const chaise = article({ libelle: 'Chaise en rotin', prix: 850, dossier: 'Mobilier/Chaises' })
const table = article({ libelle: 'Table basse', prix: 1200, dossier: 'Mobilier' })
const nettoyage = article({ libelle: 'Nettoyage', prix: 300, dossier: 'Entretien' })
const montage = article({ libelle: 'Montage', prix: 50 })
const tous = [chaise, table, nettoyage, montage]

describe('un chemin de dossier', () => {
  it('se coupe en parent et en nom', () => {
    expect(parentDe('Mobilier/Chaises')).toBe('Mobilier')
    expect(nomDe('Mobilier/Chaises')).toBe('Chaises')
  })

  it('n’a pas de parent à la racine', () => {
    expect(parentDe('Mobilier')).toBe('')
    expect(nomDe('Mobilier')).toBe('Mobilier')
  })

  it('se joint sans double séparateur', () => {
    expect(joindre('', 'Mobilier')).toBe('Mobilier')
    expect(joindre('Mobilier', 'Chaises')).toBe('Mobilier/Chaises')
  })

  it('se dessine avec des flèches, pas des barres obliques', () => {
    expect(afficher('Mobilier/Chaises')).toBe('Mobilier › Chaises')
    expect(afficher('Mobilier')).toBe('Mobilier')
    expect(afficher('')).toBe('Sans dossier')
  })

  it('se normalise avant d’être écrit', () => {
    expect(normaliserChemin('  Mobilier /  Chaises / ')).toBe('Mobilier/Chaises')
    expect(normaliserChemin('//')).toBe('')
  })
})

describe('estDans', () => {
  it('un dossier est dans lui-même et dans tout ce qui le contient', () => {
    expect(estDans('Mobilier/Chaises', 'Mobilier')).toBe(true)
    expect(estDans('Mobilier/Chaises/Rotin', 'Mobilier')).toBe(true)
    expect(estDans('Mobilier', 'Mobilier')).toBe(true)
    expect(estDans('mobilier/chaises', 'Mobilier')).toBe(true)
  })

  it('un dossier qui commence par les mêmes lettres n’est pas dedans', () => {
    // The trap a plain startsWith falls into: deleting "Mobilier" would take
    // "Mobilier d'appoint" with it, and the shop would lose a shelf it never
    // named.
    expect(estDans("Mobilier d'appoint", 'Mobilier')).toBe(false)
    expect(estDans('Entretien', 'Mobilier')).toBe(false)
  })

  it('rien n’est dans Sans dossier', () => {
    expect(estDans('Mobilier', '')).toBe(false)
    expect(estDans('', '')).toBe(true)
  })
})

describe('rebase', () => {
  it('renommer réécrit le chemin et garde tout ce qui est dessous', () => {
    expect(rebase('Mobilier', 'Mobilier', 'Mobilier/Tables')).toBe('Mobilier/Tables')
    expect(rebase('Mobilier/Chaises', 'Mobilier', 'Mobilier/Tables')).toBe(
      'Mobilier/Tables/Chaises',
    )
  })

  it('laisse le reste de l’arbre tranquille', () => {
    expect(rebase('Entretien', 'Mobilier', 'Mobilier/Tables')).toBe('Entretien')
  })
})

describe('enfantsDe', () => {
  const dossiers = ['Mobilier', 'Mobilier/Chaises', 'Mobilier/Chaises/Rotin', 'Entretien']

  it('ne liste que les enfants directs', () => {
    expect(enfantsDe('', dossiers)).toEqual(['Entretien', 'Mobilier'])
    expect(enfantsDe('Mobilier', dossiers)).toEqual(['Mobilier/Chaises'])
  })

  it('trie sur le nom, pas sur le chemin stocké', () => {
    expect(enfantsDe('Mobilier', ['Mobilier/Zeta', 'Mobilier/alpha', 'Mobilier/Beta'])).toEqual([
      'Mobilier/alpha',
      'Mobilier/Beta',
      'Mobilier/Zeta',
    ])
  })

  it('ne descend pas d’un niveau de trop quand un parent manque', () => {
    // A stored path without its parent still yields the first level only, so the
    // grid cannot draw a shelf nobody can reach.
    expect(enfantsDe('', ['Mobilier/Chaises/Rotin'])).toEqual(['Mobilier'])
  })

  it('liste tout ce qui est dessous, sauf lui-même', () => {
    expect(descendantsDe('Mobilier', dossiers)).toEqual(['Mobilier/Chaises', 'Mobilier/Chaises/Rotin'])
  })
})

describe('avecAncetres', () => {
  it('un sous-dossier vient toujours avec le dossier qui le porte', () => {
    expect(avecAncetres(['Mobilier/Chaises'])).toEqual(['Mobilier', 'Mobilier/Chaises'])
  })

  it('un dossier écrit de deux façons n’est dessiné qu’une fois', () => {
    expect(avecAncetres(['Mobilier', 'mobilier/Chaises'])).toEqual(['Mobilier', 'Mobilier/Chaises'])
  })

  it('une barre oblique en trop ne crée pas un niveau vide', () => {
    expect(avecAncetres(['/Mobilier//Chaises/'])).toEqual(['Mobilier', 'Mobilier/Chaises'])
  })
})

describe('les articles d’un dossier', () => {
  it('un dossier montre ce qu’il contient, pas ce qui est dessous', () => {
    expect(articlesDirects('Mobilier', tous)).toEqual([table])
  })

  it('un dossier interrogé en profondeur montre aussi ses sous-dossiers', () => {
    expect(articlesDans('Mobilier', tous)).toEqual([chaise, table])
  })

  it('Sans dossier ne contient que les articles sans dossier', () => {
    expect(articlesDans('', tous)).toEqual([montage])
  })

  it('le compteur d’une tuile compte tout ce qu’elle ouvre', () => {
    // The bug this guards: a tile counting deeply while the folder opens
    // shallowly, so "Mobilier (2)" opened onto one row.
    expect(compterArticles('Mobilier', tous)).toBe(2)
    expect(articlesDans('Mobilier', tous)).toHaveLength(2)
  })

  it('une tuile additionne les quantités de tout ce qu’elle ouvre', () => {
    // A shelf is not a list: one reference with twelve in stock is twelve.
    const deux = article({ dossier: 'Mobilier/Chaises', stock: 4 })
    const trois = article({ dossier: 'Mobilier', stock: 6 })
    expect(quantiteDans('Mobilier', [chaise, deux, trois])).toBe(10)
    expect(quantiteDans('', [chaise, montage])).toBe(0)
  })

  it('un service ne compte pas dans la quantité', () => {
    expect(quantiteDans('', [montage])).toBe(0)
    // ... and a folder of services only has no quantity to show at all.
    expect(aDuStock('', [montage])).toBe(false)
    expect(aDuStock('', [montage, article({ stock: 2 })])).toBe(true)
  })

  it('suit la casse de ce que l’article porte', () => {
    const minuscule = article({ dossier: 'mobilier/chaises' })
    expect(articlesDans('Mobilier', [minuscule])).toEqual([minuscule])
  })
})

describe('validation', () => {
  it('refuse un nom vide, un nom à barre oblique, un nom trop long', () => {
    expect(erreurNom('  ')).toContain('obligatoire')
    expect(erreurNom('Chaises/Rotin')).toContain('/')
    expect(erreurNom('x'.repeat(81))).toContain('80')
    expect(erreurNom('Chaises')).toBeNull()
  })

  it('accepte un chemin imbriqué et refuse les niveaux vides ou trop longs', () => {
    expect(erreurChemin('Mobilier/Chaises')).toBeNull()
    expect(erreurChemin('')).toBeNull()
    expect(erreurChemin(`Mobilier/${'x'.repeat(81)}`)).toContain('80')
    expect(erreurChemin('x'.repeat(201))).toContain('200')
  })
})

describe('suggestionCopie', () => {
  it('garde le nom quand il est libre, puis ajoute (copie)', () => {
    expect(suggestionCopie('Mobilier/Chaises', 'Entretien', ['Entretien'])).toBe(
      'Entretien/Chaises',
    )
    expect(suggestionCopie('Mobilier/Chaises', 'Mobilier', ['Mobilier', 'Mobilier/Chaises'])).toBe(
      'Mobilier/Chaises (copie)',
    )
    expect(
      suggestionCopie('Mobilier/Chaises', 'Mobilier', [
        'Mobilier',
        'Mobilier/Chaises',
        'Mobilier/Chaises (copie)',
      ]),
    ).toBe('Mobilier/Chaises (copie 2)')
  })
})
