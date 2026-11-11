// Uploading and re-downloading the archived PDF.
//
// The bucket is private: a shop reads only under its own document ids (see the
// policies in 0003). The archive row stores the SHA-256 so a reprint can prove
// it is the same file that was issued, not a fresh rendering.

import type { DesignDocument } from './document'
import type { SnapshotBoutique } from './documents'
import type { Resultat } from './documentsApi'
import { enregistrerArchive, chargerArchive, messageErreur } from './documentsApi'
import type { Facture } from './invoice'
import { supabase } from './supabase'
import { urlLogo } from './storage'

export const BUCKET_ARCHIVES = 'archives'

export type ArchiveCreee = {
  chemin: string
  sha256: string
  taille: number
  bytes: Uint8Array
}

/** `F-20261111-0001.pdf`; anything a file name cannot hold becomes a dash. */
export function nomFichier(numero: string): string {
  const propre = numero.trim().replace(/[^A-Za-z0-9._-]+/g, '-')
  return `${propre === '' ? 'document' : propre}.pdf`
}

export function cheminArchive(documentId: string, numero: string): string {
  return `${documentId}/${nomFichier(numero)}`
}

async function logoDeBoutique(boutique: SnapshotBoutique) {
  const url = urlLogo(boutique.logo_path)
  if (!url) return null
  try {
    const reponse = await fetch(url)
    if (!reponse.ok) return null
    const type = /\.jpe?g$/i.test(boutique.logo_path ?? '') ? 'jpg' : 'png'
    return { bytes: new Uint8Array(await reponse.arrayBuffer()), type } as const
  } catch {
    return null
  }
}

/**
 * Renders the document, uploads the bytes and records the hash.
 *
 * Called once, at emission. A failure here is reported but never rolls back
 * the issue: the number is already taken and the document is already evidence.
 * The screen offers to regenerate the archive instead.
 */
export async function archiverDocument(params: {
  documentId: string
  facture: Facture
  boutique: SnapshotBoutique
  design: DesignDocument
  filigrane: string | null
}): Promise<Resultat<ArchiveCreee>> {
  if (!supabase) return { donnees: null, erreur: 'Supabase n’est pas configuré.' }

  // pdf-lib is a large dependency and most sessions never press "Émettre":
  // load it the first time a PDF is actually drawn.
  const { creerPdf, sha256Hex } = await import('./pdf')

  const logo = await logoDeBoutique(params.boutique)
  const bytes = await creerPdf({
    facture: params.facture,
    boutique: params.boutique,
    design: params.design,
    filigrane: params.filigrane,
    logo,
  })
  const sha256 = await sha256Hex(bytes)
  const chemin = cheminArchive(params.documentId, params.facture.numero)

  const envoi = await supabase.storage.from(BUCKET_ARCHIVES).upload(chemin, bytes, {
    contentType: 'application/pdf',
    upsert: true,
  })
  if (envoi.error) return { donnees: null, erreur: messageErreur(envoi.error) }

  const ligne = await enregistrerArchive(params.documentId, chemin, sha256, bytes.byteLength)
  if (ligne.erreur) return { donnees: null, erreur: ligne.erreur }

  return { donnees: { chemin, sha256, taille: bytes.byteLength, bytes }, erreur: null }
}

/** The archived bytes, for a reprint. */
export async function telechargerArchive(documentId: string, numero: string): Promise<Resultat<Blob>> {
  if (!supabase) return { donnees: null, erreur: 'Supabase n’est pas configuré.' }

  const archive = await chargerArchive(documentId)
  if (archive.erreur) return { donnees: null, erreur: archive.erreur }

  const chemin = archive.donnees?.storage_path ?? cheminArchive(documentId, numero)
  const { data, error } = await supabase.storage.from(BUCKET_ARCHIVES).download(chemin)
  if (error || !data) return { donnees: null, erreur: messageErreur(error) }
  return { donnees: data, erreur: null }
}

/** Hands the browser a download without leaving the page. */
export function telechargerBlob(blob: Blob, nom: string): void {
  const url = URL.createObjectURL(blob)
  const ancre = document.createElement('a')
  ancre.href = url
  ancre.download = nom
  document.body.append(ancre)
  ancre.click()
  ancre.remove()
  // Give the browser a tick to start the download before revoking.
  setTimeout(() => URL.revokeObjectURL(url), 2000)
}
