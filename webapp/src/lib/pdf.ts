// The archived invoice, drawn with pdf-lib.
//
// This is the file the customer receives and the shop keeps. Once a document
// is issued, the archive is the truth: a reprint downloads these bytes, never
// a fresh render. That is why the generator is deterministic — same document,
// same input, same bytes — and why every call sets its own metadata instead of
// letting the library stamp the clock.
//
// Standard Helvetica is used on purpose: it is WinAnsi-encoded, so French
// accents survive, and it is built into every PDF reader. The only trap is the
// narrow no-break space the money formatter writes for thousands; it is not in
// WinAnsi and is replaced before drawing.

import { PDFDocument, StandardFonts, rgb, type PDFFont, type PDFPage } from 'pdf-lib'
import type { DesignDocument, TypeDocument } from './document'
import type { SnapshotBoutique } from './documents'
import { analyserDate } from './dates'
import { calculer, libelleTotal, totalLigne, type Facture, type Totaux } from './invoice'
import { arreteEnLettres, montantEnLettres } from './lettres'
import { montant } from './money'

const PAGE_L = 595.28
const PAGE_H = 841.89
const MARGE = 42
const LARGEUR_UTILE = PAGE_L - MARGE * 2

const ENCRE = rgb(0.043, 0.106, 0.302)
const ARDOISE = rgb(0.302, 0.357, 0.486)
const TRAIT = rgb(0.796, 0.835, 0.925)
const BLEU = rgb(0.118, 0.247, 0.824)
const SAFRAN = rgb(0.969, 0.788, 0.282)
const BLANC = rgb(1, 1, 1)

/** The one accent the templates derive their rules and tints from. */
const ACCENT = BLEU
const TEINTE = rgb(0.925, 0.945, 1)

type Couleur = ReturnType<typeof rgb>

export type LogoPdf = { bytes: Uint8Array; type: 'png' | 'jpg' }

export type OptionsPdf = {
  facture: Facture
  boutique: SnapshotBoutique
  design?: DesignDocument
  totaux?: Totaux
  logo?: LogoPdf | null
  /** `true` prints the deposit/payment block even when nothing was paid. */
  montrerReglement?: boolean
  /** Prints "ARRÊTÉE LA PRÉSENTE FACTURE À LA SOMME DE : …" under the totals. */
  montantLettres?: boolean
}

/**
 * WinAnsi cannot encode the typographic characters the app likes. The narrow
 * space would throw while drawing, so it becomes a plain space; the minus sign
 * becomes a hyphen; anything outside Latin-1 is dropped rather than drawn as a
 * black box.
 */
export function assainir(texte: string): string {
  return texte
    .replace(/[\u202F\u00A0\u2009]/g, ' ')
    .replace(/\u2212/g, '-')
    .replace(/[\u2018\u2019]/g, "'")
    .replace(/[\u201C\u201D]/g, '"')
    .replace(/[\u2013\u2014]/g, '-')
    .replace(/[^\x00-\xFF]/g, '')
}

/** `1 812,60` — the same figure the screen shows. */
function dh(valeur: number): string {
  return assainir(montant(valeur))
}

function texteOuVide(valeur: string | null | undefined): string {
  return valeur?.trim() ?? ''
}

/** Split a paragraph into lines that fit `largeur`, breaking on spaces. */
export function couper(texte: string, police: PDFFont, taille: number, largeur: number): string[] {
  const lignes: string[] = []
  for (const paragraphe of texte.split(/\r?\n/)) {
    const mots = assainir(paragraphe).split(/\s+/).filter(Boolean)
    if (mots.length === 0) {
      lignes.push('')
      continue
    }
    let courante = ''
    for (const mot of mots) {
      const essai = courante === '' ? mot : `${courante} ${mot}`
      if (police.widthOfTextAtSize(essai, taille) <= largeur || courante === '') {
        courante = essai
      } else {
        lignes.push(courante)
        courante = mot
      }
    }
    if (courante !== '') lignes.push(courante)
  }
  return lignes
}

export async function creerPdf(options: OptionsPdf): Promise<Uint8Array> {
  const design = options.design ?? 'classique'
  // The roll is a different page, not a different drawing: it gets its own
  // renderer instead of pretending to be A4 with a narrow margin.
  if (design === 'ticket') return creerPdfTicket(options)

  const { facture, boutique } = options
  const totaux = options.totaux ?? calculer(facture)

  const pdf = await PDFDocument.create()
  const normal = await pdf.embedFont(StandardFonts.Helvetica)
  const gras = await pdf.embedFont(StandardFonts.HelveticaBold)
  const italique = await pdf.embedFont(StandardFonts.HelveticaOblique)

  const logo = options.logo ? await embarquerLogo(pdf, options.logo) : null

  const date = analyserDate(facture.date) ?? new Date(2000, 0, 1)
  pdf.setTitle(`${libelleDocument(facture)} ${facture.numero}`.trim())
  pdf.setAuthor(texteOuVide(boutique.nom))
  pdf.setSubject('Document commercial')
  pdf.setCreator('FactureApp')
  pdf.setProducer('FactureApp')
  pdf.setCreationDate(date)
  pdf.setModificationDate(date)

  let page = pdf.addPage([PAGE_L, PAGE_H])
  let y = PAGE_H - MARGE

  // ---------------------------------------------------------------- header
  const titre = assainir(libelleDocument(facture).toUpperCase())
  const numeroTexte = assainir(facture.numero || 'Brouillon')
  const legalBoutique = identifiantsVendeur(boutique)

  /** L'adresse, le contact et les identifiants, avec la taille de chacun. */
  function lignesBoutique(): Array<{ texte: string; taille: number }> {
    const lignes: Array<{ texte: string; taille: number }> = []
    const adresse = texteOuVide(boutique.adresse)
    if (adresse) lignes.push({ texte: adresse, taille: 8.5 })
    const contact = [boutique.telephone, boutique.email].filter(Boolean).join(' · ')
    if (contact) lignes.push({ texte: contact, taille: 8 })
    if (legalBoutique) lignes.push({ texte: legalBoutique, taille: 7.5 })
    return lignes
  }

  /** En-tête compact des pages 2 et suivantes, commun aux modèles A4. */
  function enteteSuite(pageCourante: PDFPage, couleur: Couleur): number {
    pageCourante.drawText(assainir(`${libelleDocument(facture)} · suite`), {
      x: MARGE,
      y: PAGE_H - 52,
      size: 9,
      font: italique,
      color: ARDOISE,
    })
    pageCourante.drawText(numeroTexte, {
      x: PAGE_L - MARGE - normal.widthOfTextAtSize(numeroTexte, 9),
      y: PAGE_H - 52,
      size: 9,
      font: normal,
      color: ARDOISE,
    })
    pageCourante.drawLine({
      start: { x: MARGE, y: PAGE_H - 62 },
      end: { x: PAGE_L - MARGE, y: PAGE_H - 62 },
      thickness: 1,
      color: couleur,
    })
    return PAGE_H - 84
  }

  /** Classique : la boutique à gauche, l'identité du document à droite. */
  function enteteClassique(pageCourante: PDFPage, premiere: boolean): number {
    if (!premiere) return enteteSuite(pageCourante, TRAIT)
    if (logo) pageCourante.drawImage(logo, { x: MARGE, y: PAGE_H - 88, width: 48, height: 48 })
    const decalage = logo ? 58 : 0
    pageCourante.drawText(assainir(boutique.nom), {
      x: MARGE + decalage,
      y: PAGE_H - 56,
      size: 16,
      font: gras,
      color: ENCRE,
    })
    let ligneY = PAGE_H - 70
    for (const { texte, taille } of lignesBoutique()) {
      for (const morceau of couper(texte, normal, taille, 330)) {
        pageCourante.drawText(morceau, {
          x: MARGE + decalage,
          y: ligneY,
          size: taille,
          font: normal,
          color: ARDOISE,
        })
        ligneY -= taille + 3
      }
    }
    pageCourante.drawText(titre, {
      x: PAGE_L - MARGE - gras.widthOfTextAtSize(titre, 20),
      y: PAGE_H - 54,
      size: 20,
      font: gras,
      color: BLEU,
    })
    pageCourante.drawText(numeroTexte, {
      x: PAGE_L - MARGE - normal.widthOfTextAtSize(numeroTexte, 10),
      y: PAGE_H - 70,
      size: 10,
      font: normal,
      color: ENCRE,
    })
    const dateDoc = `Date ${assainir(facture.date)}`
    pageCourante.drawText(dateDoc, {
      x: PAGE_L - MARGE - normal.widthOfTextAtSize(dateDoc, 9),
      y: PAGE_H - 84,
      size: 9,
      font: normal,
      color: ARDOISE,
    })
    pageCourante.drawLine({
      start: { x: MARGE, y: PAGE_H - 100 },
      end: { x: PAGE_L - MARGE, y: PAGE_H - 100 },
      thickness: 1,
      color: TRAIT,
    })
    return PAGE_H - 120
  }

  /** Moderne : titre largement à droite, filet d'accent sous l'en-tête. */
  function enteteModerne(pageCourante: PDFPage, premiere: boolean): number {
    if (!premiere) return enteteSuite(pageCourante, ACCENT)
    if (logo) pageCourante.drawImage(logo, { x: MARGE, y: PAGE_H - 82, width: 34, height: 34 })
    const x = MARGE + (logo ? 44 : 0)
    pageCourante.drawText(assainir(boutique.nom), {
      x,
      y: PAGE_H - 52,
      size: 17,
      font: gras,
      color: ENCRE,
    })
    let ligneY = PAGE_H - 66
    for (const { texte, taille } of lignesBoutique()) {
      pageCourante.drawText(assainir(texte), {
        x,
        y: ligneY,
        size: Math.min(taille, 8),
        font: normal,
        color: ARDOISE,
      })
      ligneY -= taille + 2
    }
    pageCourante.drawText(titre, {
      x: PAGE_L - MARGE - gras.widthOfTextAtSize(titre, 24),
      y: PAGE_H - 54,
      size: 24,
      font: gras,
      color: ACCENT,
    })
    pageCourante.drawText(numeroTexte, {
      x: PAGE_L - MARGE - normal.widthOfTextAtSize(numeroTexte, 10),
      y: PAGE_H - 72,
      size: 10,
      font: normal,
      color: ENCRE,
    })
    const dateDoc = `Date ${assainir(facture.date)}`
    pageCourante.drawText(dateDoc, {
      x: PAGE_L - MARGE - normal.widthOfTextAtSize(dateDoc, 9),
      y: PAGE_H - 86,
      size: 9,
      font: normal,
      color: ARDOISE,
    })
    pageCourante.drawLine({
      start: { x: MARGE, y: PAGE_H - 98 },
      end: { x: PAGE_L - MARGE, y: PAGE_H - 98 },
      thickness: 0.6,
      color: TRAIT,
    })
    pageCourante.drawLine({
      start: { x: MARGE, y: PAGE_H - 103 },
      end: { x: PAGE_L - MARGE, y: PAGE_H - 103 },
      thickness: 2.5,
      color: ACCENT,
    })
    return PAGE_H - 124
  }

  /** Facture Maroc : le bandeau sombre, la bande safran, le titre en safran. */
  function enteteMaroc(pageCourante: PDFPage, premiere: boolean): number {
    if (!premiere) return enteteSuite(pageCourante, ACCENT)
    pageCourante.drawRectangle({ x: 0, y: PAGE_H - 108, width: PAGE_L, height: 108, color: ENCRE })
    pageCourante.drawRectangle({ x: 0, y: PAGE_H - 112, width: PAGE_L, height: 4, color: SAFRAN })
    if (logo) {
      pageCourante.drawImage(logo, { x: MARGE, y: PAGE_H - 92, width: 46, height: 46, opacity: 0.95 })
      pageCourante.drawText(assainir(boutique.nom), {
        x: MARGE + 56,
        y: PAGE_H - 62,
        size: 15,
        font: gras,
        color: BLANC,
      })
    } else {
      pageCourante.drawText(assainir(boutique.nom), {
        x: MARGE,
        y: PAGE_H - 56,
        size: 17,
        font: gras,
        color: BLANC,
      })
    }
    const legal = identifiantsVendeur(boutique)
    if (legal) {
      pageCourante.drawText(assainir(legal), {
        x: MARGE + (logo ? 56 : 0),
        y: PAGE_H - 78,
        size: 8,
        font: normal,
        color: rgb(0.85, 0.9, 1),
      })
    }
    pageCourante.drawText(titre, {
      x: PAGE_L - MARGE - gras.widthOfTextAtSize(titre, 18),
      y: PAGE_H - 52,
      size: 18,
      font: gras,
      color: SAFRAN,
    })
    pageCourante.drawText(numeroTexte, {
      x: PAGE_L - MARGE - normal.widthOfTextAtSize(numeroTexte, 10),
      y: PAGE_H - 72,
      size: 10,
      font: normal,
      color: BLANC,
    })
    return PAGE_H - 132
  }

  /** Épuré : un trait d'accent, des capitales, presque pas de filets. */
  function enteteEpure(pageCourante: PDFPage, premiere: boolean): number {
    if (!premiere) return enteteSuite(pageCourante, ACCENT)
    pageCourante.drawRectangle({ x: MARGE, y: PAGE_H - 48, width: 26, height: 3, color: ACCENT })
    if (logo) pageCourante.drawImage(logo, { x: MARGE, y: PAGE_H - 96, width: 32, height: 32 })
    const x = MARGE + (logo ? 40 : 0)
    pageCourante.drawText(assainir(boutique.nom), {
      x,
      y: PAGE_H - 80,
      size: 15,
      font: gras,
      color: ENCRE,
    })
    let ligneY = PAGE_H - 92
    for (const { texte, taille } of lignesBoutique()) {
      pageCourante.drawText(assainir(texte), {
        x,
        y: ligneY,
        size: Math.min(taille, 7.5),
        font: normal,
        color: ARDOISE,
      })
      ligneY -= taille
    }
    pageCourante.drawText(titre, {
      x: PAGE_L - MARGE - gras.widthOfTextAtSize(titre, 10),
      y: PAGE_H - 44,
      size: 10,
      font: gras,
      color: ACCENT,
    })
    const dateDoc = assainir(facture.date)
    pageCourante.drawText(numeroTexte, {
      x: PAGE_L - MARGE - normal.widthOfTextAtSize(numeroTexte, 8.5),
      y: PAGE_H - 60,
      size: 8.5,
      font: normal,
      color: ENCRE,
    })
    pageCourante.drawText(dateDoc, {
      x: PAGE_L - MARGE - normal.widthOfTextAtSize(dateDoc, 8),
      y: PAGE_H - 72,
      size: 8,
      font: normal,
      color: ARDOISE,
    })
    pageCourante.drawLine({
      start: { x: MARGE, y: PAGE_H - 112 },
      end: { x: PAGE_L - MARGE, y: PAGE_H - 112 },
      thickness: 0.8,
      color: TRAIT,
    })
    return PAGE_H - 132
  }

  /** Bandeau : toute la largeur en haut, texte blanc, tableau teinté. */
  function enteteBandeau(pageCourante: PDFPage, premiere: boolean): number {
    if (!premiere) return enteteSuite(pageCourante, ACCENT)
    pageCourante.drawRectangle({ x: 0, y: PAGE_H - 86, width: PAGE_L, height: 86, color: ACCENT })
    if (logo) pageCourante.drawImage(logo, { x: MARGE, y: PAGE_H - 72, width: 38, height: 38 })
    const x = MARGE + (logo ? 48 : 0)
    pageCourante.drawText(assainir(boutique.nom), {
      x,
      y: PAGE_H - 46,
      size: 16,
      font: gras,
      color: BLANC,
    })
    let ligneY = PAGE_H - 58
    for (const { texte, taille } of lignesBoutique()) {
      pageCourante.drawText(assainir(texte), {
        x,
        y: ligneY,
        size: Math.min(taille, 7.5),
        font: normal,
        color: rgb(0.87, 0.9, 1),
      })
      ligneY -= taille + 1.5
    }
    pageCourante.drawText(titre, {
      x: PAGE_L - MARGE - gras.widthOfTextAtSize(titre, 18),
      y: PAGE_H - 46,
      size: 18,
      font: gras,
      color: BLANC,
    })
    pageCourante.drawText(numeroTexte, {
      x: PAGE_L - MARGE - normal.widthOfTextAtSize(numeroTexte, 9),
      y: PAGE_H - 62,
      size: 9,
      font: normal,
      color: rgb(0.87, 0.9, 1),
    })
    return PAGE_H - 110
  }

  function entete(pageCourante: PDFPage, premiere: boolean): number {
    switch (design) {
      case 'moderne':
        return enteteModerne(pageCourante, premiere)
      case 'facture_maroc':
        return enteteMaroc(pageCourante, premiere)
      case 'epure':
        return enteteEpure(pageCourante, premiere)
      case 'bandeau':
        return enteteBandeau(pageCourante, premiere)
      default:
        return enteteClassique(pageCourante, premiere)
    }
  }

  y = entete(page, true)

  // ---------------------------------------------------------------- parties
  const client = facture.client
  const boiteClient = [
    client.nom || 'Client',
    texteOuVide(client.adresse),
    [client.telephone, client.email].filter(Boolean).join(' · '),
    [
      client.ice ? `ICE ${client.ice}` : '',
      client.ifFiscal ? `IF ${client.ifFiscal}` : '',
      client.rc ? `RC ${client.rc}` : '',
    ]
      .filter(Boolean)
      .join('  ·  '),
  ].filter(Boolean)

  page.drawText('CLIENT', { x: MARGE, y, size: 7.5, font: gras, color: ARDOISE })
  y -= 13
  let lignesClient = 0
  for (const ligne of boiteClient) {
    page.drawText(assainir(ligne), { x: MARGE, y, size: 9.5, font: lignesClient === 0 ? gras : normal, color: ENCRE })
    y -= 12
    lignesClient += 1
  }

  // Dates on the right, aligned with the client block.
  let yDate = y + lignesClient * 12 - 1
  const dates: Array<[string, string]> = [['Date', facture.date]]
  if (facture.echeance) dates.push(['Échéance', facture.echeance])
  if (facture.dateValidite) dates.push(['Validité', facture.dateValidite])
  if (facture.conditionsPaiement) {
    dates.push(['Paiement', conditionLibelle(facture.conditionsPaiement)])
  }
  for (const [libelle, valeur] of dates) {
    page.drawText(assainir(`${libelle} : ${valeur}`), {
      x: PAGE_L - MARGE - 220,
      y: yDate,
      size: 9,
      font: normal,
      color: ARDOISE,
    })
    yDate -= 12
  }
  if (facture.reference?.trim()) {
    // La mention qu'un contrôle cherche sur un document de correction : le
    // document corrigé doit être nommé, pas seulement référencé.
    page.drawText(assainir(mentionReference(facture) ?? ''), {
      x: PAGE_L - MARGE - 220,
      y: yDate,
      size: 9,
      font: normal,
      color: ARDOISE,
    })
  }

  y = Math.min(y, yDate) - 20

  // ---------------------------------------------------------------- lines
  const colonnes = { designation: MARGE, quantite: 330, unite: 365, prix: 400, tva: 470, total: PAGE_L - MARGE }

  // Chaque modèle traite l'en-tête du tableau à sa façon : l'un le remplit,
  // l'autre le souligne, le troisième s'en passe. La géométrie, elle, ne bouge
  // pas — les colonnes doivent s'aligner avec les lignes dessinées plus bas.
  const fondTete =
    design === 'facture_maroc' ? rgb(0.94, 0.956, 1)
    : design === 'bandeau' ? TEINTE
    : design === 'classique' ? rgb(0.965, 0.973, 1)
    : null
  const couleurTete =
    design === 'moderne' || design === 'bandeau' ? ACCENT
    : design === 'facture_maroc' ? BLEU
    : design === 'epure' ? ARDOISE
    : ENCRE
  const tailleTete = design === 'epure' ? 7.5 : 8

  function enteteLignes(pageCourante: PDFPage, position: number): number {
    if (fondTete) {
      pageCourante.drawRectangle({
        x: MARGE,
        y: position - 4,
        width: LARGEUR_UTILE,
        height: 18,
        color: fondTete,
      })
    }
    pageCourante.drawText('Désignation', { x: colonnes.designation + 4, y: position, size: tailleTete, font: gras, color: couleurTete })
    pageCourante.drawText('Qté', { x: colonnes.quantite, y: position, size: tailleTete, font: gras, color: couleurTete })
    pageCourante.drawText('P.U. HT', { x: colonnes.prix, y: position, size: tailleTete, font: gras, color: couleurTete })
    pageCourante.drawText('TVA', { x: colonnes.tva, y: position, size: tailleTete, font: gras, color: couleurTete })
    const total = 'Total HT'
    pageCourante.drawText(total, {
      x: colonnes.total - gras.widthOfTextAtSize(total, tailleTete),
      y: position,
      size: tailleTete,
      font: gras,
      color: couleurTete,
    })
    if (design === 'moderne' || design === 'epure') {
      pageCourante.drawLine({
        start: { x: MARGE, y: position - 6 },
        end: { x: PAGE_L - MARGE, y: position - 6 },
        thickness: design === 'moderne' ? 1.2 : 0.8,
        color: design === 'moderne' ? ACCENT : TRAIT,
      })
    }
    return position - 20
  }

  y = enteteLignes(page, y)

  const lignes = facture.lignes
  for (let index = 0; index < lignes.length; index += 1) {
    const ligne = lignes[index]
    if (!ligne) continue
    if (y < 190) {
      page = pdf.addPage([PAGE_L, PAGE_H])
      y = entete(page, false)
      y = enteteLignes(page, y)
    }

    const description = couper(ligne.description || '—', normal, 9, 250)
    const totalLigneHt = totalLigne(ligne)
    const hauteur = Math.max(14, description.length * 11)
    page.drawText(assainir(description[0] ?? ''), {
      x: colonnes.designation + 4,
      y,
      size: 9,
      font: normal,
      color: ENCRE,
    })
    for (let d = 1; d < description.length; d += 1) {
      page.drawText(assainir(description[d] ?? ''), {
        x: colonnes.designation + 4,
        y: y - d * 11,
        size: 9,
        font: normal,
        color: ARDOISE,
      })
    }
    const quantite = `${assainir(montant(ligne.quantite).replace(',00', ''))}${ligne.unite ? ` ${ligne.unite}` : ''}`
    page.drawText(quantite, { x: colonnes.quantite, y, size: 9, font: normal, color: ENCRE })
    page.drawText(dh(ligne.prixUnitaire), { x: colonnes.prix, y, size: 9, font: normal, color: ENCRE })
    const taux = ligne.exoneree || facture.tvaNonApplicable ? '—' : `${ligne.tauxTva ?? facture.tauxTva ?? 20} %`
    page.drawText(taux, { x: colonnes.tva, y, size: 9, font: normal, color: ARDOISE })
    const montantTexte = dh(totalLigneHt)
    page.drawText(montantTexte, {
      x: colonnes.total - normal.widthOfTextAtSize(montantTexte, 9),
      y,
      size: 9,
      font: normal,
      color: ENCRE,
    })
    y -= hauteur
  }

  // ---------------------------------------------------------------- totals
  if (y < 260) {
    page = pdf.addPage([PAGE_L, PAGE_H])
    y = entete(page, false)
  }

  y -= 10
  const xEtiquette = 330
  const xValeur = PAGE_L - MARGE
  const ligneTotal = (libelle: string, valeur: string, grasTexte = false, couleur = ENCRE) => {
    page.drawText(assainir(libelle), {
      x: xEtiquette,
      y,
      size: grasTexte ? 10 : 9,
      font: grasTexte ? gras : normal,
      color: couleur,
    })
    page.drawText(valeur, {
      x: xValeur - (grasTexte ? gras : normal).widthOfTextAtSize(valeur, grasTexte ? 10 : 9),
      y,
      size: grasTexte ? 10 : 9,
      font: grasTexte ? gras : normal,
      color: couleur,
    })
    y -= grasTexte ? 16 : 13
  }

  if (totaux.afficheRemise) ligneTotal('Sous-total', dh(totaux.sousTotal))
  if (totaux.afficheRemise) ligneTotal('Remise', `-${dh(totaux.remise)}`)
  ligneTotal('Total HT', dh(totaux.totalHt))
  if (totaux.surcharge > 0) {
    ligneTotal(texteOuVide(facture.surchargeLibelle) || 'Frais', dh(totaux.surcharge))
  }
  for (const tva of totaux.lignesTvaImprimees) {
    // Le taux, sa base, son montant : la base est ce qui rend le document
    // vérifiable à la main.
    ligneTotal(libelleRecapTva(tva), dh(tva.montant))
  }
  const accentFilet = design === 'moderne' || design === 'epure' || design === 'bandeau'
  page.drawLine({
    start: { x: xEtiquette, y: y + 10 },
    end: { x: xValeur, y: y + 10 },
    thickness: accentFilet ? 1.2 : 0.8,
    color: accentFilet ? ACCENT : TRAIT,
  })
  ligneTotal(libelleTotal(totaux), dh(totaux.total), true)
  if (totaux.acompte > 0) {
    ligneTotal('Acompte', `-${dh(totaux.acompte)}`)
  }
  if (totaux.paye > 0) {
    ligneTotal('Déjà payé', `-${dh(totaux.paye)}`)
  }
  ligneTotal(
    totaux.estAvoir ? 'Net à créditer' : 'Net à payer',
    dh(totaux.netAPayer),
    true,
    design === 'classique' ? ENCRE : design === 'facture_maroc' ? BLEU : ACCENT,
  )
  // La mention que la loi attend d'une facture sans TVA (auto-entrepreneur,
  // exonération). La référence légale précise reste dans la note du document :
  // elle dépend du régime, le logiciel n'a pas à l'inventer.
  if (facture.tvaNonApplicable) {
    y -= 2
    page.drawText('TVA non applicable', {
      x: xEtiquette,
      y,
      size: 8,
      font: normal,
      color: ARDOISE,
    })
    y -= 12
  }

  // Le montant arrêté en toutes lettres : c'est le total du document que la
  // phrase déclare, pas ce qui reste à payer. Un acompte se déduit du net à
  // payer ; il ne change pas la somme à laquelle la facture est arrêtée.
  if (options.montantLettres) {
    y -= 4
    const phrase = montantArrete(totaux, facture.type ?? 'facture')
    for (const ligne of couper(phrase, normal, 8, PAGE_L - 2 * MARGE)) {
      page.drawText(ligne, { x: MARGE, y, size: 8, font: normal, color: ENCRE })
      y -= 10
    }
    y -= 4
  }

  // ---------------------------------------------------------------- footer
  let yPied = Math.min(y - 16, 150)
  const blocs: Array<[string, string]> = []
  if (texteOuVide(boutique.banque) || texteOuVide(boutique.rib)) {
    blocs.push([
      'Règlement',
      [
        texteOuVide(boutique.banque),
        boutique.rib ? `RIB ${boutique.rib}` : '',
        texteOuVide(boutique.rib_titulaire),
      ]
        .filter(Boolean)
        .join(' · '),
    ])
  }
  if (facture.clauseRetard?.trim()) blocs.push(['Retard de paiement', facture.clauseRetard])
  if (facture.noteClient?.trim()) blocs.push(['Note', facture.noteClient])
  if (facture.noteRemerciement?.trim()) blocs.push(['', facture.noteRemerciement])

  page.drawLine({
    start: { x: MARGE, y: yPied + 10 },
    end: { x: PAGE_L - MARGE, y: yPied + 10 },
    thickness: 0.8,
    color: TRAIT,
  })
  for (const [titre, contenu] of blocs) {
    if (titre) {
      page.drawText(assainir(titre.toUpperCase()), { x: MARGE, y: yPied, size: 7, font: gras, color: ARDOISE })
      yPied -= 11
    }
    for (const ligne of couper(contenu, normal, 8.5, 300)) {
      if (yPied < MARGE + 30) break
      page.drawText(ligne, { x: MARGE, y: yPied, size: 8.5, font: normal, color: ENCRE })
      yPied -= 11
    }
    yPied -= 4
  }

  const pied = `FactureApp · ${assainir(boutique.nom)}`
  const pages = pdf.getPages()
  pages.forEach((pageCourante, index) => {
    pageCourante.drawText(pied, { x: MARGE, y: MARGE - 12, size: 7.5, font: normal, color: ARDOISE })
    const numerotation = `Page ${index + 1} / ${pages.length}`
    pageCourante.drawText(numerotation, {
      x: PAGE_L - MARGE - normal.widthOfTextAtSize(numerotation, 7.5),
      y: MARGE - 12,
      size: 7.5,
      font: normal,
      color: ARDOISE,
    })
  })

  const bytes = await pdf.save({ useObjectStreams: false })
  return bytes
}

/** 80 mm roll. One column, no table: the receipt a till actually prints. */
async function creerPdfTicket(options: OptionsPdf): Promise<Uint8Array> {
  const { facture, boutique } = options
  const totaux = options.totaux ?? calculer(facture)
  const L = 226.77
  const M = 14
  const utile = L - M * 2

  const pdf = await PDFDocument.create()
  const normal = await pdf.embedFont(StandardFonts.Helvetica)
  const gras = await pdf.embedFont(StandardFonts.HelveticaBold)
  const document = facture

  const date = analyserDate(document.date) ?? new Date(2000, 0, 1)
  pdf.setTitle(`${libelleDocument(document)} ${document.numero}`.trim())
  pdf.setAuthor(texteOuVide(boutique.nom))
  pdf.setSubject('Document commercial')
  pdf.setCreator('FactureApp')
  pdf.setProducer('FactureApp')
  pdf.setCreationDate(date)
  pdf.setModificationDate(date)

  const logo = options.logo ? await embarquerLogo(pdf, options.logo) : null

  const nomLignes = couper(texteOuVide(boutique.nom) || 'Boutique', gras, 11, utile)
  const coordonnees = [
    texteOuVide(boutique.adresse),
    boutique.telephone ?? '',
    identifiantsVendeur(boutique, ' · '),
  ].filter(Boolean)
  const coordsLignes = coordonnees.flatMap((ligne) => couper(ligne, normal, 6.5, utile))

  const client = document.client
  // Le ticket porte les mêmes identifiants que l'A4 : un contrôle au comptoir
  // ne doit pas dépendre du format choisi. Une ligne par famille, parce que le
  // rouleau fait 80 mm et que rien n'y est découpé automatiquement.
  const clientIdentifiants = identifiantsClient(client, ' · ')
  const clientLignes = [
    client.nom || 'Client',
    texteOuVide(client.adresse),
    texteOuVide(client.telephone),
    clientIdentifiants,
  ].filter(Boolean)

  const descW = utile - 54
  const lignes = document.lignes.map((ligne) => {
    const desc = couper(ligne.description || '—', normal, 7.5, descW)
    return { ligne, desc, hauteur: desc.length * 9 + 10 }
  })

  const rangees: Array<[string, string, boolean]> = []
  if (totaux.afficheRemise) {
    rangees.push(['Sous-total', dh(totaux.sousTotal), false])
    rangees.push(['Remise', `-${dh(totaux.remise)}`, false])
  }
  rangees.push(['Total HT', dh(totaux.totalHt), false])
  if (totaux.surcharge > 0) {
    rangees.push([texteOuVide(document.surchargeLibelle) || 'Frais', dh(totaux.surcharge), false])
  }
  for (const tva of totaux.lignesTvaImprimees) {
    rangees.push([libelleRecapTva(tva), dh(tva.montant), false])
  }
  rangees.push([libelleTotal(totaux), dh(totaux.total), true])
  if (totaux.acompte > 0) rangees.push(['Acompte', `-${dh(totaux.acompte)}`, false])
  if (totaux.paye > 0) rangees.push(['Déjà payé', `-${dh(totaux.paye)}`, false])
  rangees.push([totaux.estAvoir ? 'Net à créditer' : 'Net à payer', dh(totaux.netAPayer), true])
  if (document.tvaNonApplicable) rangees.push(['TVA non applicable', '', false])

  // The roll is 80 mm wide: the sentence has to be wrapped, and its height
  // counted in the page height below, or it would print over the footer.
  const lignesLettres = options.montantLettres
    ? couper(montantArrete(totaux, document.type ?? 'facture'), normal, 7, L - 2 * M)
    : []

  const hLogo = logo ? 40 : 0
  const hLignes = lignes.reduce((total, ligne) => total + ligne.hauteur, 0)
  const reference = mentionReference(document)
  const lignesReference = reference ? couper(reference, normal, 6.5, utile) : []
  const hauteur =
    M +
    hLogo +
    nomLignes.length * 13 + 2 +
    coordsLignes.length * 8 + 6 + 14 +
    10 + 14 + lignesReference.length * 8 +
    clientLignes.length * 9 + 2 + 12 +
    hLignes +
    2 + 12 +
    rangees.length * 10 +
    (lignesLettres.length > 0 ? lignesLettres.length * 9 + 8 : 0) +
    8 +
    M
  const PAGE_T = Math.max(260, hauteur)

  const page = pdf.addPage([L, PAGE_T])
  let y = PAGE_T - M

  if (logo) {
    const largeur = 34
    page.drawImage(logo, { x: (L - largeur) / 2, y: y - largeur, width: largeur, height: largeur })
    y -= hLogo
  }

  for (const ligne of nomLignes) {
    page.drawText(ligne, {
      x: (L - gras.widthOfTextAtSize(ligne, 11)) / 2,
      y,
      size: 11,
      font: gras,
      color: ENCRE,
    })
    y -= 13
  }
  y -= 2
  for (const ligne of coordsLignes) {
    page.drawText(ligne, {
      x: (L - normal.widthOfTextAtSize(ligne, 6.5)) / 2,
      y,
      size: 6.5,
      font: normal,
      color: ARDOISE,
    })
    y -= 8
  }

  y -= 6
  page.drawLine({
    start: { x: M, y },
    end: { x: L - M, y },
    thickness: 0.7,
    color: TRAIT,
    dashArray: [2, 2],
  })
  y -= 14

  page.drawText(assainir(libelleDocument(document).toUpperCase()), {
    x: M,
    y,
    size: 7.5,
    font: gras,
    color: ACCENT,
  })
  const numero = assainir(document.numero || 'Brouillon')
  page.drawText(numero, {
    x: L - M - normal.widthOfTextAtSize(numero, 7.5),
    y,
    size: 7.5,
    font: normal,
    color: ENCRE,
  })
  y -= 10
  page.drawText(assainir(document.date), { x: M, y, size: 7, font: normal, color: ARDOISE })
  y -= 10
  for (const ligne of lignesReference) {
    page.drawText(assainir(ligne), { x: M, y, size: 6.5, font: normal, color: ARDOISE })
    y -= 8
  }
  y -= 4

  for (let index = 0; index < clientLignes.length; index += 1) {
    page.drawText(assainir(clientLignes[index] ?? ''), {
      x: M,
      y,
      size: index === 0 ? 8.5 : 7,
      font: index === 0 ? gras : normal,
      color: index === 0 ? ENCRE : ARDOISE,
    })
    y -= 9
  }

  y -= 2
  page.drawLine({
    start: { x: M, y },
    end: { x: L - M, y },
    thickness: 0.7,
    color: TRAIT,
    dashArray: [2, 2],
  })
  y -= 12

  for (const { ligne, desc } of lignes) {
    const montantLigne = dh(totalLigne(ligne))
    page.drawText(desc[0] ?? '', { x: M, y, size: 7.5, font: normal, color: ENCRE })
    page.drawText(montantLigne, {
      x: L - M - normal.widthOfTextAtSize(montantLigne, 7.5),
      y,
      size: 7.5,
      font: normal,
      color: ENCRE,
    })
    y -= 9
    for (let d = 1; d < desc.length; d += 1) {
      page.drawText(desc[d] ?? '', { x: M, y, size: 7, font: normal, color: ARDOISE })
      y -= 9
    }
    const quantite = `${assainir(montant(ligne.quantite).replace(',00', ''))}${ligne.unite ? ` ${ligne.unite}` : ''}`
    page.drawText(assainir(`${quantite} × ${dh(ligne.prixUnitaire)}`), {
      x: M,
      y,
      size: 6.5,
      font: normal,
      color: ARDOISE,
    })
    y -= 10
  }

  y -= 2
  page.drawLine({
    start: { x: M, y },
    end: { x: L - M, y },
    thickness: 0.7,
    color: TRAIT,
    dashArray: [2, 2],
  })
  y -= 12

  for (const [libelle, valeur, fort] of rangees) {
    const police = fort ? gras : normal
    const taille = fort ? 8 : 7.5
    page.drawText(assainir(libelle), {
      x: M,
      y,
      size: taille,
      font: police,
      color: fort ? ENCRE : ARDOISE,
    })
    page.drawText(valeur, {
      x: L - M - police.widthOfTextAtSize(valeur, taille),
      y,
      size: taille,
      font: police,
      color: fort ? (libelle.startsWith('Net') ? ACCENT : ENCRE) : ENCRE,
    })
    y -= 10
  }

  if (lignesLettres.length > 0) {
    y -= 4
    page.drawLine({
      start: { x: M, y: y + 2 },
      end: { x: L - M, y: y + 2 },
      thickness: 0.7,
      color: TRAIT,
      dashArray: [2, 2],
    })
    y -= 8
    for (const ligne of lignesLettres) {
      page.drawText(ligne, { x: M, y, size: 7, font: normal, color: ENCRE })
      y -= 9
    }
  }

  const pied = `FactureApp · ${assainir(boutique.nom)}`
  const piedTaille = 5.5
  const piedLargeur = Math.min(normal.widthOfTextAtSize(pied, piedTaille), utile)
  page.drawText(pied, {
    x: (L - piedLargeur) / 2,
    y: M,
    size: piedTaille,
    font: normal,
    color: ARDOISE,
  })

  return pdf.save({ useObjectStreams: false })
}

/**
 * La phrase qui nomme le document corrigé.
 *
 * Un avoir annule, une régularisation rectifie, un acompte s'impute : trois
 * relations différentes, et « Réf. : » ne dit ni laquelle ni sur quoi. Les
 * mêmes mots sur l'A4 et sur le ticket.
 */
export function mentionReference(facture: Facture): string | null {
  const reference = facture.reference?.trim()
  if (!reference) return null
  const type = facture.type ?? 'facture'
  const ouverture =
    type === 'avoir' ? 'Annule et remplace la facture N°'
    : type === 'regul' ? 'Rectifie la facture N°'
    : type === 'acompte' ? 'Acompte sur la facture N°'
    : 'Réf. facture N°'
  return `${ouverture} ${reference}`
}

/**
 * La ligne de récapitulatif d'un taux de TVA : le taux, sa base, le montant.
 *
 * Sans la base, un document à deux taux ne se vérifie pas à la main : le
 * contrôleur doit refaire le calcul, et le montant seul ne prouve rien. Un
 * taux à 0 % (exonération) n'a pas de base à imprimer.
 */
export function libelleRecapTva(tva: { taux: number; base: number }): string {
  const base = tva.taux > 0 ? ` sur ${dh(tva.base)}` : ''
  return `TVA ${tva.taux} %${base}`
}

/**
 * Le montant arrêté en toutes lettres.
 *
 * C'est le total du document que la phrase déclare, pas ce qui reste à payer :
 * un acompte se déduit du net à payer, il ne change pas la somme à laquelle la
 * facture est arrêtée.
 */
export function montantArrete(totaux: Totaux, type: TypeDocument): string {
  return arreteEnLettres(totaux.total, type)
}

/** Les identifiants légaux du client, dans l'ordre où un contrôle les lit. */
export function identifiantsClient(
  client: { ice?: string | null; ifFiscal?: string | null; rc?: string | null },
  separateur = '  ·  ',
): string {
  return [
    client.ice ? `ICE ${client.ice}` : '',
    client.ifFiscal ? `IF ${client.ifFiscal}` : '',
    client.rc ? `RC ${client.rc}` : '',
  ]
    .filter(Boolean)
    .join(separateur)
}

/** Les identifiants légaux du vendeur, tels qu'ils s'impriment. */
export function identifiantsVendeur(
  boutique: SnapshotBoutique,
  separateur = '  ·  ',
): string {
  return [
    boutique.ice ? `ICE ${boutique.ice}` : '',
    boutique.if_fiscal ? `IF ${boutique.if_fiscal}` : '',
    boutique.rc ? `RC ${boutique.rc}` : '',
    boutique.patente ? `Patente ${boutique.patente}` : '',
    boutique.cnss ? `CNSS ${boutique.cnss}` : '',
  ]
    .filter(Boolean)
    .join(separateur)
}

function libelleDocument(facture: Facture): string {
  const type = facture.type ?? 'facture'
  const libelles: Record<string, string> = {
    facture: 'Facture',
    devis: 'Devis',
    bon_commande: 'Bon de commande',
    ticket: 'Ticket',
    avoir: 'Avoir',
    proforma: 'Facture proforma',
    acompte: 'Facture d’acompte',
    regul: 'Facture de régularisation',
    simplifiee: 'Facture simplifiée',
    export: 'Facture export',
  }
  return libelles[type] ?? 'Facture'
}

function conditionLibelle(id: string): string {
  const libelles: Record<string, string> = {
    cash: 'Comptant',
    '30j': '30 jours',
    '60j': '60 jours',
    fin_de_mois: 'Fin de mois',
    custom: 'Selon accord',
  }
  return libelles[id] ?? id
}

async function embarquerLogo(pdf: PDFDocument, logo: LogoPdf) {
  return logo.type === 'png' ? pdf.embedPng(logo.bytes) : pdf.embedJpg(logo.bytes)
}

/** SHA-256 of the archived bytes, in lowercase hex. */
export async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const tampon = await crypto.subtle.digest('SHA-256', bytes as unknown as ArrayBuffer)
  return [...new Uint8Array(tampon)].map((octet) => octet.toString(16).padStart(2, '0')).join('')
}

export type RecuOptions = {
  facture: Facture
  boutique: SnapshotBoutique
  paiement: { montant: number; date: string; moyen: string; reference?: string }
  /** "Reçu la somme de …" in words, when the shop prints amounts in letters. */
  montantLettres?: boolean
}

/**
 * The receipt for one payment. Short on purpose: it proves a sum was received
 * against a numbered document, it is not a second invoice.
 */
export async function creerRecuPdf(options: RecuOptions): Promise<Uint8Array> {
  const { facture, boutique, paiement } = options
  const pdf = await PDFDocument.create()
  const normal = await pdf.embedFont(StandardFonts.Helvetica)
  const gras = await pdf.embedFont(StandardFonts.HelveticaBold)

  const date = analyserDate(paiement.date) ?? analyserDate(facture.date) ?? new Date(2000, 0, 1)
  pdf.setTitle(`Reçu ${facture.numero}`.trim())
  pdf.setAuthor(texteOuVide(boutique.nom))
  pdf.setCreator('FactureApp')
  pdf.setProducer('FactureApp')
  pdf.setCreationDate(date)
  pdf.setModificationDate(date)

  const page = pdf.addPage([PAGE_L, PAGE_H])
  let y = PAGE_H - MARGE

  page.drawText(assainir(boutique.nom), { x: MARGE, y, size: 16, font: gras, color: ENCRE })
  y -= 16
  const coordonnees = [texteOuVide(boutique.adresse), [boutique.telephone, boutique.email].filter(Boolean).join(' · ')]
  for (const ligne of coordonnees) {
    if (!ligne) continue
    page.drawText(assainir(ligne), { x: MARGE, y, size: 8.5, font: normal, color: ARDOISE })
    y -= 11
  }
  const legal = [
    boutique.ice ? `ICE ${boutique.ice}` : '',
    boutique.if_fiscal ? `IF ${boutique.if_fiscal}` : '',
    boutique.rc ? `RC ${boutique.rc}` : '',
  ]
    .filter(Boolean)
    .join('  ·  ')
  if (legal) {
    page.drawText(assainir(legal), { x: MARGE, y, size: 8, font: normal, color: ARDOISE })
  }

  page.drawText('REÇU', {
    x: PAGE_L - MARGE - gras.widthOfTextAtSize('REÇU', 22),
    y: PAGE_H - 56,
    size: 22,
    font: gras,
    color: BLEU,
  })
  const dateTexte = assainir(`Date : ${paiement.date}`)
  page.drawText(dateTexte, {
    x: PAGE_L - MARGE - normal.widthOfTextAtSize(dateTexte, 9),
    y: PAGE_H - 76,
    size: 9,
    font: normal,
    color: ARDOISE,
  })

  page.drawLine({
    start: { x: MARGE, y: PAGE_H - 116 },
    end: { x: PAGE_L - MARGE, y: PAGE_H - 116 },
    thickness: 1,
    color: TRAIT,
  })

  y = PAGE_H - 150
  page.drawText('Reçu de', { x: MARGE, y, size: 8, font: gras, color: ARDOISE })
  y -= 16
  page.drawText(assainir(facture.client.nom || 'Client'), { x: MARGE, y, size: 13, font: gras, color: ENCRE })
  y -= 30

  page.drawText('la somme de', { x: MARGE, y, size: 8, font: gras, color: ARDOISE })
  y -= 22
  page.drawText(assainir(`${dh(paiement.montant)} DH`), { x: MARGE, y, size: 24, font: gras, color: ENCRE })
  y -= 22

  // The amount alone, not the invoice's "ARRÊTÉE LA PRÉSENTE FACTURE" opening:
  // a receipt states a sum received, it does not close a document.
  if (options.montantLettres) {
    for (const ligne of couper(montantEnLettres(paiement.montant), normal, 9.5, LARGEUR_UTILE - 20)) {
      page.drawText(ligne, { x: MARGE, y, size: 9.5, font: normal, color: ARDOISE })
      y -= 13
    }
    y -= 8
  }
  y -= 10

  const moyens: Record<string, string> = {
    cash: 'Espèces',
    check: 'Chèque',
    transfer: 'Virement bancaire',
    card: 'Carte bancaire',
  }
  const phrases = [
    `au titre de la facture ${facture.numero || '—'} du ${facture.date}.`,
    `Moyen de paiement : ${moyens[paiement.moyen] ?? paiement.moyen}.`,
  ]
  if (paiement.reference?.trim()) phrases.push(`Référence : ${paiement.reference.trim()}.`)
  for (const phrase of phrases) {
    for (const ligne of couper(phrase, normal, 11, LARGEUR_UTILE - 20)) {
      page.drawText(ligne, { x: MARGE, y, size: 11, font: normal, color: ENCRE })
      y -= 16
    }
  }

  y -= 50
  page.drawLine({
    start: { x: PAGE_L - MARGE - 170, y: y + 20 },
    end: { x: PAGE_L - MARGE, y: y + 20 },
    thickness: 0.8,
    color: TRAIT,
  })
  page.drawText('Signature et cachet', {
    x: PAGE_L - MARGE - 170,
    y,
    size: 8,
    font: normal,
    color: ARDOISE,
  })

  page.drawText(`FactureApp · ${assainir(boutique.nom)}`, {
    x: MARGE,
    y: MARGE - 12,
    size: 7.5,
    font: normal,
    color: ARDOISE,
  })

  return pdf.save({ useObjectStreams: false })
}
