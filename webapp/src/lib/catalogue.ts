// The shop's catalogues: clients, suppliers and articles.
//
// Pure functions only — the screens own the network, this module owns the
// rules. Parsing and validation live here so they can be tested without a
// browser, and so a figure typed on one screen is read the same way on the
// next.

import { analyserMontant, montant } from './money'

export type RolePartie = 'client' | 'fournisseur'
export type FiltreRole = 'tous' | RolePartie

export type ClientEnregistre = {
  id: string
  shop_id: string
  nom: string
  adresse: string | null
  ice: string | null
  if_fiscal: string | null
  rc: string | null
  patente: string | null
  cnss: string | null
  telephone: string | null
  email: string | null
  role: RolePartie
  exonere_tva: boolean
  note: string | null
}

export type SaisieClient = {
  nom: string
  role: RolePartie
  adresse: string
  ice: string
  if_fiscal: string
  rc: string
  patente: string
  cnss: string
  telephone: string
  email: string
  exonere_tva: boolean
  note: string
}

export type Article = {
  id: string
  shop_id: string
  libelle: string
  prix: number
  unite: string
  dossier: string
  prix_achat: number
  code_barre: string
  /** null is a service: nothing to count, nothing to take off a shelf. */
  stock: number | null
  seuil_rea: number | null
  attributs: { key: string; value: string }[]
  paliers: { fromQuantity: number; unitPrice: number }[]
}

export type SaisieArticle = {
  libelle: string
  prix: string
  unite: string
  dossier: string
  prix_achat: string
  code_barre: string
  service: boolean
  stock: string
  seuil_rea: string
}

/** Trims, and turns an empty field into null rather than an empty string. */
export function texteOuNull(valeur: string): string | null {
  const propre = valeur.trim()
  return propre === '' ? null : propre
}

/** Lowercase without accents, so `Câble` is found by typing `cable`. */
export function normaliser(valeur: string): string {
  return valeur
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
    .trim()
}

// ------------------------------------------------------------------ clients

export function valeursClient(saisie: SaisieClient): Omit<ClientEnregistre, 'id' | 'shop_id'> {
  return {
    nom: saisie.nom.trim(),
    role: saisie.role,
    adresse: texteOuNull(saisie.adresse),
    ice: texteOuNull(saisie.ice),
    if_fiscal: texteOuNull(saisie.if_fiscal),
    rc: texteOuNull(saisie.rc),
    patente: texteOuNull(saisie.patente),
    cnss: texteOuNull(saisie.cnss),
    telephone: texteOuNull(saisie.telephone),
    email: texteOuNull(saisie.email),
    exonere_tva: saisie.exonere_tva,
    note: texteOuNull(saisie.note),
  }
}

/** The message to show, or null when the record can be saved. */
export function erreurClient(saisie: SaisieClient): string | null {
  const nom = saisie.nom.trim()
  if (nom === '') return 'Le nom est obligatoire.'
  if (nom.length > 160) return 'Le nom ne peut pas dépasser 160 caractères.'
  if (saisie.email.trim() !== '' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(saisie.email.trim())) {
    return 'Cette adresse e-mail n’a pas l’air valide.'
  }
  return null
}

/** Search across the fields a shop would type, then filter by role. */
export function filtrerClients(
  clients: ClientEnregistre[],
  recherche: string,
  role: FiltreRole = 'tous',
): ClientEnregistre[] {
  const terme = normaliser(recherche)
  return clients
    .filter((client) => role === 'tous' || client.role === role)
    .filter((client) => {
      if (terme === '') return true
      return [client.nom, client.telephone, client.email, client.ice, client.adresse]
        .filter((valeur): valeur is string => Boolean(valeur))
        .some((valeur) => normaliser(valeur).includes(terme))
    })
    .sort((a, b) => a.nom.localeCompare(b.nom, 'fr'))
}

export function roleClient(role: RolePartie): string {
  return role === 'fournisseur' ? 'Fournisseur' : 'Client'
}

// ----------------------------------------------------------------- articles

/** `2`, `2,5`, `-1` — an integer a shelf can hold, or a clear error. */
export function entierDepuisTexte(texte: string): number | null | 'erreur' {
  const propre = texte.trim()
  if (propre === '') return null
  const valeur = Number(propre.replace(',', '.'))
  if (!Number.isFinite(valeur) || !Number.isInteger(valeur)) return 'erreur'
  return valeur
}

export function valeursArticle(
  saisie: SaisieArticle,
): Omit<Article, 'id' | 'shop_id' | 'attributs' | 'paliers'> | { erreur: string } {
  const libelle = saisie.libelle.trim()
  if (libelle === '') return { erreur: 'Le libellé est obligatoire.' }
  if (libelle.length > 200) return { erreur: 'Le libellé ne peut pas dépasser 200 caractères.' }

  const prix = analyserMontant(saisie.prix)
  if (prix === null) return { erreur: 'Le prix de vente n’est pas un nombre.' }
  if (prix < 0) return { erreur: 'Le prix de vente ne peut pas être négatif.' }

  const prixAchat = saisie.prix_achat.trim() === '' ? 0 : analyserMontant(saisie.prix_achat)
  if (prixAchat === null) return { erreur: 'Le prix d’achat n’est pas un nombre.' }
  if (prixAchat < 0) return { erreur: 'Le prix d’achat ne peut pas être négatif.' }

  const stock = saisie.service ? null : entierDepuisTexte(saisie.stock)
  if (stock === 'erreur') return { erreur: 'Le stock doit être un nombre entier.' }

  const seuil = saisie.service ? null : entierDepuisTexte(saisie.seuil_rea)
  if (seuil === 'erreur') return { erreur: 'Le seuil de réapprovisionnement doit être un entier.' }
  if (seuil !== null && seuil < 0) return { erreur: 'Le seuil ne peut pas être négatif.' }

  return {
    libelle,
    prix: Math.round(prix * 100) / 100,
    unite: saisie.unite.trim(),
    dossier: saisie.dossier.trim(),
    prix_achat: Math.round(prixAchat * 100) / 100,
    code_barre: saisie.code_barre.trim(),
    stock,
    seuil_rea: seuil,
  }
}

/**
 * `dossier = null` shows everything; `''` shows only the unfiled articles,
 * which is a real answer — "Sans dossier", not "no filter".
 */
export function filtrerArticles(
  articles: Article[],
  recherche: string,
  dossier: string | null = null,
): Article[] {
  const terme = normaliser(recherche)
  return articles
    .filter((article) => dossier === null || article.dossier === dossier)
    .filter((article) => {
      if (terme === '') return true
      return [article.libelle, article.code_barre, article.dossier, article.unite]
        .filter((valeur): valeur is string => Boolean(valeur))
        .some((valeur) => normaliser(valeur).includes(terme))
    })
    .sort((a, b) => a.libelle.localeCompare(b.libelle, 'fr'))
}

/** The folders that actually contain something, A→Z. */
export function dossiersDe(articles: Article[]): string[] {
  const dossiers = new Set(articles.map((article) => article.dossier).filter(Boolean))
  return [...dossiers].sort((a, b) => a.localeCompare(b, 'fr'))
}

export type EtatStock = 'service' | 'rupture' | 'seuil' | 'ok'

/** One colour, one meaning: red out, amber low, muted service. */
export function etatStock(article: Article): EtatStock {
  if (article.stock === null) return 'service'
  if (article.stock < 0) return 'rupture'
  if (article.seuil_rea !== null && article.stock <= article.seuil_rea) return 'seuil'
  return 'ok'
}

export function libelleStock(article: Article): string {
  if (article.stock === null) return 'Service'
  if (article.stock < 0) return 'Stock négatif'
  return `${article.stock} en stock`
}

export function libellePrix(article: Article): string {
  const prix = `${montant(article.prix)} DH`
  return article.unite === '' ? prix : `${prix} / ${article.unite}`
}
