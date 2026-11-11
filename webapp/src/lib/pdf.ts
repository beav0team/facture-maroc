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

import { PDFDocument, StandardFonts, degrees, rgb, type PDFFont, type PDFPage } from 'pdf-lib'
import type { DesignDocument } from './document'
import type { SnapshotBoutique } from './documents'
import { analyserDate } from './dates'
import { calculer, totalLigne, type Facture, type Totaux } from './invoice'
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
const ROUGE = rgb(0.702, 0.149, 0.118)

export type LogoPdf = { bytes: Uint8Array; type: 'png' | 'jpg' }

export type OptionsPdf = {
  facture: Facture
  boutique: SnapshotBoutique
  design?: DesignDocument
  totaux?: Totaux
  /** "FACTUREAPP · GRATUIT" across the page while the shop is on the free plan. */
  filigrane?: string | null
  logo?: LogoPdf | null
  /** `true` prints the deposit/payment block even when nothing was paid. */
  montrerReglement?: boolean
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
  const { facture, boutique } = options
  const design = options.design ?? 'classique'
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
  function entete(pageCourante: PDFPage, premiere: boolean): number {
    if (design === 'facture_maroc') {
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
      const legal = [
        boutique.ice ? `ICE ${boutique.ice}` : '',
        boutique.if_fiscal ? `IF ${boutique.if_fiscal}` : '',
        boutique.rc ? `RC ${boutique.rc}` : '',
        boutique.patente ? `Patente ${boutique.patente}` : '',
      ]
        .filter(Boolean)
        .join('  ·  ')
      if (legal) {
        pageCourante.drawText(assainir(legal), {
          x: MARGE + (logo ? 56 : 0),
          y: PAGE_H - 78,
          size: 8,
          font: normal,
          color: rgb(0.85, 0.9, 1),
        })
      }
      pageCourante.drawText(assainir(libelleDocument(facture).toUpperCase()), {
        x: PAGE_L - MARGE - gras.widthOfTextAtSize(assainir(libelleDocument(facture).toUpperCase()), 18),
        y: PAGE_H - 52,
        size: 18,
        font: gras,
        color: SAFRAN,
      })
      const numero = assainir(facture.numero || 'Brouillon')
      pageCourante.drawText(numero, {
        x: PAGE_L - MARGE - normal.widthOfTextAtSize(numero, 10),
        y: PAGE_H - 72,
        size: 10,
        font: normal,
        color: BLANC,
      })
      return PAGE_H - 132
    }

    // Classique: white page, rule under the header.
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
    const coordonnees = [texteOuVide(boutique.adresse), [boutique.telephone, boutique.email].filter(Boolean).join(' · ')]
    for (const ligne of coordonnees) {
      if (!ligne) continue
      pageCourante.drawText(assainir(ligne), {
        x: MARGE + decalage,
        y: ligneY,
        size: 8.5,
        font: normal,
        color: ARDOISE,
      })
      ligneY -= 11
    }
    const legal = [
      boutique.ice ? `ICE ${boutique.ice}` : '',
      boutique.if_fiscal ? `IF ${boutique.if_fiscal}` : '',
      boutique.rc ? `RC ${boutique.rc}` : '',
      boutique.patente ? `Patente ${boutique.patente}` : '',
      boutique.cnss ? `CNSS ${boutique.cnss}` : '',
    ]
      .filter(Boolean)
      .join('  ·  ')
    if (legal) {
      pageCourante.drawText(assainir(legal), {
        x: MARGE + decalage,
        y: ligneY,
        size: 8,
        font: normal,
        color: ARDOISE,
      })
    }

    const titre = assainir(libelleDocument(facture).toUpperCase())
    pageCourante.drawText(titre, {
      x: PAGE_L - MARGE - gras.widthOfTextAtSize(titre, 20),
      y: PAGE_H - 54,
      size: 20,
      font: gras,
      color: BLEU,
    })
    const numero = assainir(facture.numero || 'Brouillon')
    pageCourante.drawText(numero, {
      x: PAGE_L - MARGE - normal.widthOfTextAtSize(numero, 10),
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
    if (!premiere) {
      pageCourante.drawText(assainir(`${libelleDocument(facture)} · suite`), {
        x: MARGE,
        y: PAGE_H - 114,
        size: 8,
        font: italique,
        color: ARDOISE,
      })
    }
    return PAGE_H - 120
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
    page.drawText(assainir(`Réf. : ${facture.reference}`), {
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
  function enteteLignes(pageCourante: PDFPage, position: number): number {
    pageCourante.drawRectangle({
      x: MARGE,
      y: position - 4,
      width: LARGEUR_UTILE,
      height: 18,
      color: design === 'facture_maroc' ? rgb(0.94, 0.956, 1) : rgb(0.965, 0.973, 1),
    })
    pageCourante.drawText('Désignation', { x: colonnes.designation + 4, y: position, size: 8, font: gras, color: ENCRE })
    pageCourante.drawText('Qté', { x: colonnes.quantite, y: position, size: 8, font: gras, color: ENCRE })
    pageCourante.drawText('P.U. HT', { x: colonnes.prix, y: position, size: 8, font: gras, color: ENCRE })
    pageCourante.drawText('TVA', { x: colonnes.tva, y: position, size: 8, font: gras, color: ENCRE })
    const total = 'Total HT'
    pageCourante.drawText(total, {
      x: colonnes.total - gras.widthOfTextAtSize(total, 8),
      y: position,
      size: 8,
      font: gras,
      color: ENCRE,
    })
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
    ligneTotal(`TVA ${tva.taux} %`, dh(tva.montant))
  }
  page.drawLine({
    start: { x: xEtiquette, y: y + 4 },
    end: { x: xValeur, y: y + 4 },
    thickness: 0.8,
    color: TRAIT,
  })
  ligneTotal(totaux.estAvoir ? 'Total avoir' : 'Total TTC', dh(totaux.total), true)
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
    design === 'facture_maroc' ? BLEU : ENCRE,
  )

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

  // ---------------------------------------------------------------- watermark
  if (options.filigrane) {
    const texte = assainir(options.filigrane)
    for (const pageCourante of pages) {
      const taille = 34
      const largeur = gras.widthOfTextAtSize(texte, taille)
      pageCourante.drawText(texte, {
        x: (PAGE_L - largeur * 0.7) / 2,
        y: PAGE_H / 2 - 40,
        size: taille,
        font: gras,
        color: ROUGE,
        opacity: 0.08,
        rotate: degrees(35),
      })
    }
  }

  const bytes = await pdf.save({ useObjectStreams: false })
  return bytes
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
  y -= 30

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
