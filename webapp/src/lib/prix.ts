// Reading a price the way the shop typed it, ported from PriceBasis.kt.
//
// A document only prints HT + TVA. A shop, though, quotes whichever number it
// quotes: "150 DH, TVA comprise" or "125 DH HT". This converts the first into
// the second and leaves the second alone.
//
// The conversion keeps every decimal instead of rounding the unit price: the
// shop quoted a TTC price and that price must come back. `99,99 TTC` at 20 %
// is `83,325 HT`; rounding it to `83,33` makes the invoice total `99,996 →
// 100,00`, a centime more than the customer was told. Rounding happens once,
// on the totals, where `calculer` already does it.

import { arrondi } from './money'
import type { Ligne } from './invoice'

/** The HT price behind `prix`, read as TTC when `taux` is positive. */
export function horsTva(prix: number, taux: number): number {
  return taux <= 0 ? prix : prix / (1 + taux / 100)
}

/** The TTC price behind `prix`, for showing a line back to the shop. */
export function avecTva(prix: number, taux: number): number {
  return taux <= 0 ? prix : arrondi(prix * (1 + taux / 100))
}

/**
 * The same lines, with every TTC-quoted price re-read at `taux`.
 *
 * Whether the number the shop typed has TVA inside it depends on who is
 * buying, so a client change re-reads the price instead of freezing it. A line
 * typed HT has no `prixTtc` and comes back untouched.
 */
export function reevaluerLignesTtc(lignes: Ligne[], taux: number): Ligne[] {
  return lignes.map((ligne) => avecExoneration(ligne, Boolean(ligne.exoneree), taux))
}

/** `ligne` with `exoneree` set, and its price read again if quoted TTC. */
export function avecExoneration(ligne: Ligne, exoneree: boolean, taux: number): Ligne {
  const tape = ligne.prixTtc
  if (tape === null || tape === undefined) return { ...ligne, exoneree }
  const tauxLigne = exoneree ? 0 : (ligne.tauxTva ?? taux)
  return { ...ligne, exoneree, prixUnitaire: horsTva(tape, tauxLigne) }
}

/** `ligne` with its own rate set, price re-read if quoted TTC. */
export function avecTaux(ligne: Ligne, taux: number | null): Ligne {
  const miseAJour: Ligne = {
    ...ligne,
    tauxTva: taux,
    exoneree: taux === 0 ? Boolean(ligne.exoneree) : false,
  }
  const tape = miseAJour.prixTtc
  if (tape === null || tape === undefined) return miseAJour
  const effectif = miseAJour.exoneree ? 0 : (taux ?? 0)
  return { ...miseAJour, prixUnitaire: horsTva(tape, effectif) }
}

/** One line from a catalogue article, priced in the shop's own basis. */
export function ligneDepuisArticle(
  article: { id: string; libelle: string; prix: number; unite: string },
  prixTtc: boolean,
  taux: number,
): Ligne {
  if (prixTtc && taux > 0) {
    return {
      description: article.libelle,
      quantite: 1,
      prixUnitaire: horsTva(article.prix, taux),
      prixTtc: article.prix,
      unite: article.unite || undefined,
      articleId: article.id,
      remisePourcent: 0,
      remiseMontant: 0,
      exoneree: false,
      tauxTva: taux,
    }
  }
  return {
    description: article.libelle,
    quantite: 1,
    prixUnitaire: article.prix,
    unite: article.unite || undefined,
    articleId: article.id,
    remisePourcent: 0,
    remiseMontant: 0,
    exoneree: false,
    tauxTva: taux,
  }
}
