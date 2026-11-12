// "Exporter mes données" — the shop's own copy, as one JSON file.
//
// Deliberately not a ZIP: the archived PDFs stay downloadable from each
// document, and adding a zip library to the bundle to repackage files the
// browser already has would be the wrong trade. The JSON says so in its
// `note` field, so nobody thinks their invoices are missing.

import type { Boutique } from './session.svelte'

export type ExportDonnees = {
  application: string
  format: string
  version: number
  exporte_le: string
  boutique: Boutique
  clients: unknown[]
  articles: unknown[]
  documents: unknown[]
  lignes: unknown[]
  paiements: unknown[]
  compteurs: Record<string, unknown>
  note: string
}

export type SourcesExport = {
  boutique: Boutique
  clients: unknown[]
  articles: unknown[]
  documents: unknown[]
  lignes: unknown[]
  paiements: unknown[]
  compteurs: Record<string, unknown>
}

export const NOTE_EXPORT =
  'Les PDF émis ne sont pas inclus dans ce fichier : ils restent téléchargeables ' +
  'depuis chaque document de l’application, et un document émis ne se modifie plus.'

/** Builds the export object. Pure on purpose: the screen only does the IO. */
export function assemblerExport(sources: SourcesExport, maintenant: Date): ExportDonnees {
  return {
    application: 'factureapp',
    format: 'export',
    version: 1,
    exporte_le: maintenant.toISOString(),
    boutique: sources.boutique,
    clients: sources.clients,
    articles: sources.articles,
    documents: sources.documents,
    lignes: sources.lignes,
    paiements: sources.paiements,
    compteurs: sources.compteurs,
    note: NOTE_EXPORT,
  }
}

/** `factureapp-atelier-atlas-2026-11-12.json`, safe on every filesystem. */
export function nomFichierExport(nomBoutique: string, date: Date): string {
  const jour = date.toISOString().slice(0, 10)
  const nom = nomBoutique
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
  return `factureapp-${nom || 'boutique'}-${jour}.json`
}

/** Fails loudly instead of writing a file with a silent hole in it. */
export function erreurExport(sources: SourcesExport): string | null {
  if (sources.documents.length > 0 && sources.lignes.length === 0) {
    return 'Les documents sont là mais leurs lignes manquent : export annulé.'
  }
  return null
}

/**
 * Lines and payments hang off the document, not the shop: no `shop_id` column
 * exists on either table. They are fetched with `document_id in (…)`, which has
 * to be cut into batches — a shop with a few thousand documents would otherwise
 * build a URL long enough for PostgREST to reject it.
 */
export function lots<T>(elements: T[], taille = 150): T[][] {
  const sortie: T[][] = []
  for (let debut = 0; debut < elements.length; debut += taille) {
    sortie.push(elements.slice(debut, debut + taille))
  }
  return sortie
}
