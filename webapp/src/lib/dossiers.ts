// The article folders: one path string, the same rule as the Android catalogue.
//
// A folder is stored as "Mobilier/Chaises" rather than as a node with an id.
// The path is readable in the database, sorts next to its descendants and needs
// no join to resolve, and every catalogue written before folders nested still
// loads. The price is that the whole tree lives in string rules: what is one
// level up, what is below, and what merely starts with the same letters. Those
// rules decide which articles a folder shows and what a rename rewrites, so they
// are pure, tested, and the screens only draw them.
//
// The trap that shapes `estDans`: a plain `startsWith` puts "Mobilier d'appoint"
// inside "Mobilier", and deleting one would take the other with it.

import type { Article } from './catalogue'

export const SEPARATEUR = '/'
export const SEPARATEUR_AFFICHAGE = ' › '
export const RACINE = ''
export const SANS_DOSSIER = 'Sans dossier'

/** The database check is 1..200 on the whole path; names are narrower. */
export const LONGUEUR_MAX_CHEMIN = 200
export const LONGUEUR_MAX_NOM = 80

function meme(a: string, b: string): boolean {
  return a.toLowerCase() === b.toLowerCase()
}

/** `Mobilier/Chaises` → `Mobilier`; a top-level folder has no parent. */
export function parentDe(chemin: string): string {
  const coupe = chemin.lastIndexOf(SEPARATEUR)
  return coupe < 0 ? RACINE : chemin.slice(0, coupe)
}

/** `Mobilier/Chaises` → `Chaises`. */
export function nomDe(chemin: string): string {
  const coupe = chemin.lastIndexOf(SEPARATEUR)
  return coupe < 0 ? chemin : chemin.slice(coupe + 1)
}

/** A name placed inside a parent; into the root it is the name itself. */
export function joindre(parent: string, nom: string): string {
  return parent.trim() === RACINE ? nom : `${parent}${SEPARATEUR}${nom}`
}

/** The folder's segments, without the empties a sloppy path carries. */
export function segmentsDe(chemin: string): string[] {
  return chemin
    .split(SEPARATEUR)
    .map((segment) => segment.trim())
    .filter((segment) => segment !== '')
}

/** How a path is drawn: `Mobilier › Chaises`, and `Sans dossier` for the root. */
export function afficher(chemin: string): string {
  const segments = segmentsDe(chemin)
  return segments.length === 0 ? SANS_DOSSIER : segments.join(SEPARATEUR_AFFICHAGE)
}

/** The path a typed value really means: trimmed, no empty segment, no slash ends. */
export function normaliserChemin(chemin: string): string {
  return segmentsDe(chemin).join(SEPARATEUR)
}

/** Whether `dossier` is `chemin` itself or lives somewhere below it. */
export function estDans(dossier: string, chemin: string): boolean {
  const cible = normaliserChemin(chemin).toLowerCase()
  const valeur = normaliserChemin(dossier).toLowerCase()
  // Nothing is inside "Sans dossier": an empty target matches only the empty
  // folder, so the unfiled shelf cannot swallow the tree.
  if (cible === '') return valeur === ''
  return valeur === cible || valeur.startsWith(`${cible}${SEPARATEUR}`)
}

/** `dossier` with the part named by `de` replaced by `vers`. */
export function rebase(dossier: string, de: string, vers: string): string {
  if (!estDans(dossier, de)) return dossier
  return vers + dossier.slice(normaliserChemin(de).length)
}

/** The message to show for a folder name, or null when it is usable. */
export function erreurNom(nom: string): string | null {
  const propre = nom.trim()
  if (propre === '') return 'Le nom du dossier est obligatoire.'
  if (propre.includes(SEPARATEUR)) return 'Un nom de dossier ne contient pas de « / ».'
  if (propre.length > LONGUEUR_MAX_NOM) {
    return `Un nom de dossier ne peut pas dépasser ${LONGUEUR_MAX_NOM} caractères.`
  }
  return null
}

/** The message to show for a whole path typed into the article form, or null. */
export function erreurChemin(chemin: string): string | null {
  const propre = normaliserChemin(chemin)
  if (propre === '') return null
  if (propre.length > LONGUEUR_MAX_CHEMIN) {
    return `Un dossier ne peut pas dépasser ${LONGUEUR_MAX_CHEMIN} caractères.`
  }
  const trop = segmentsDe(chemin).find((segment) => segment.length > LONGUEUR_MAX_NOM)
  if (trop !== undefined) {
    return `Chaque nom de dossier est limité à ${LONGUEUR_MAX_NOM} caractères.`
  }
  return null
}

/**
 * Every folder plus the parents it implies, deduplicated case-insensitively.
 *
 * `["mobilier/Chaises"]` yields `["mobilier", "mobilier/Chaises"]`, so a shelf
 * cannot exist without its parent — otherwise the tile for "mobilier" is missing
 * and the tree has a hole where a folder should be. The first spelling wins, so
 * one shelf never becomes two tiles.
 */
export function avecAncetres(chemins: string[]): string[] {
  const vus = new Map<string, string>()
  for (const chemin of chemins) {
    let courant = RACINE
    for (const segment of segmentsDe(chemin)) {
      const candidat = joindre(courant, segment)
      const connu = vus.get(candidat.toLowerCase())
      courant = connu ?? candidat
      if (connu === undefined) vus.set(candidat.toLowerCase(), candidat)
    }
  }
  return [...vus.values()].sort((a, b) => a.localeCompare(b, 'fr'))
}

/**
 * Whether `dossier` sits under the folder being browsed.
 *
 * Different from [estDans] for the root on purpose: nothing is filed "inside
 * Sans dossier" — that tile holds only unfiled articles — but every top-level
 * folder is a child of the catalogue itself, which is the folder the grid starts
 * in. Without this the root grid would be empty.
 */
function sousLe(parent: string, dossier: string): boolean {
  if (normaliserChemin(parent) === RACINE) return normaliserChemin(dossier) !== RACINE
  return estDans(dossier, parent)
}

/** The direct sub-folders of `parent`, as full paths, A→Z. */
export function enfantsDe(parent: string, dossiers: string[]): string[] {
  const profondeur = segmentsDe(parent).length
  const vus = new Map<string, string>()
  for (const dossier of dossiers) {
    if (!sousLe(parent, dossier) || meme(dossier, parent)) continue
    const segments = segmentsDe(dossier)
    const premier = segments[profondeur]
    if (premier === undefined) continue
    const enfant = joindre(parent, premier)
    const cle = enfant.toLowerCase()
    if (!vus.has(cle)) vus.set(cle, enfant)
  }
  return [...vus.values()].sort((a, b) => a.localeCompare(b, 'fr'))
}

/** Every folder below `chemin`, not counting itself. */
export function descendantsDe(chemin: string, dossiers: string[]): string[] {
  return dossiers.filter((dossier) => sousLe(chemin, dossier) && !meme(dossier, chemin))
}

/**
 * The articles in `chemin` **and everything below it**.
 *
 * Deep on purpose: a search inside "Mobilier" that ignored "Mobilier/Chaises"
 * would tell a shop its chairs do not exist. `""` means "Sans dossier".
 */
export function articlesDans(chemin: string, articles: Article[]): Article[] {
  return articles.filter((article) => estDans(article.dossier ?? RACINE, chemin))
}

/** Only the articles filed exactly in `chemin`, for browsing a folder. */
export function articlesDirects(chemin: string, articles: Article[]): Article[] {
  return articles.filter((article) => meme(article.dossier ?? RACINE, chemin))
}

/** How many articles a tile stands for: the folder and all of its descendants. */
export function compterArticles(chemin: string, articles: Article[]): number {
  return articlesDans(chemin, articles).length
}

/**
 * How many units sit on the shelf in `chemin`, sub-folders included.
 *
 * This is what a shop counts when it looks at a shelf, and it is not the number
 * of articles: a folder holding one tyre with twelve in stock is twelve, not
 * one. Services (`stock === null`) contribute nothing — they cannot be counted,
 * and counting them as zero would be right by accident but for the wrong
 * reason.
 */
export function quantiteDans(chemin: string, articles: Article[]): number {
  return articlesDans(chemin, articles).reduce(
    (total, article) => total + (article.stock ?? 0),
    0,
  )
}

/**
 * Whether `chemin` holds anything countable at all.
 *
 * A folder of services has no quantity to show: printing "0 en stock" beside
 * "3 articles" would read as a shelf that ran out when there is no shelf.
 */
export function aDuStock(chemin: string, articles: Article[]): boolean {
  return articlesDans(chemin, articles).some((article) => article.stock !== null)
}

/**
 * Where a copy of `chemin` lands under `parent`: the same name when it is free
 * there, then `(copie)`, then `(copie 2)`, exactly like the Android catalogue.
 */
export function suggestionCopie(chemin: string, parent: string, dossiers: string[]): string {
  const base = nomDe(normaliserChemin(chemin))
  const pris = new Set(dossiers.map((dossier) => dossier.toLowerCase()))
  for (let suffixe = 0; suffixe < 100; suffixe += 1) {
    const nom = suffixe === 0 ? base : suffixe === 1 ? `${base} (copie)` : `${base} (copie ${suffixe})`
    const candidat = joindre(normaliserChemin(parent), nom)
    if (!pris.has(candidat.toLowerCase())) return candidat
  }
  return joindre(normaliserChemin(parent), `${base} (copie 100)`)
}

/** The parent a folder's articles return to when the folder is deleted. */
export function dossierDeRepli(chemin: string): string {
  return parentDe(normaliserChemin(chemin))
}
