// Supabase calls for the documents screens.
//
// Every write goes through an RPC: the number, the quota and the frozen shop
// snapshot are decided by the database, not by a tab. The screens only read
// rows and render what the engine computes.

import { supabase } from './supabase'
import type { TypeDocument } from './document'
import type {
  DocumentRow,
  LigneRow,
  PaiementRow,
  QuotaBoutique,
  ArchiveRow,
} from './documents'

export type Resultat<T> = { donnees: T | null; erreur: string | null }

function absent(): Resultat<never> {
  return { donnees: null, erreur: 'Supabase n’est pas configuré.' }
}

/** Postgres exceptions carry the sentence meant for the shop in `message`. */
export function messageErreur(error: { message?: string; hint?: string; code?: string } | null): string {
  if (!error) return 'Erreur inconnue.'
  const message = error.message?.trim() ?? ''
  if (error.code === 'P0001' && error.hint) return `${message} ${error.hint}`
  if (message.includes('duplicate key') && message.includes('documents_numero_unique')) {
    return 'Ce numéro de document existe déjà, réessayez.'
  }
  return message || 'Erreur inconnue.'
}

export async function chargerQuota(shopId: string): Promise<Resultat<QuotaBoutique>> {
  if (!supabase) return absent()
  const { data, error } = await supabase.rpc('quota_boutique', { p_shop: shopId })
  if (error) return { donnees: null, erreur: messageErreur(error) }
  return { donnees: data as QuotaBoutique, erreur: null }
}

export async function apercuNumero(shopId: string, type: TypeDocument): Promise<Resultat<string>> {
  if (!supabase) return absent()
  const { data, error } = await supabase.rpc('apercu_numero', { p_shop: shopId, p_type: type })
  if (error) return { donnees: null, erreur: messageErreur(error) }
  return { donnees: data as string, erreur: null }
}

export async function chargerDocuments(shopId: string): Promise<Resultat<DocumentRow[]>> {
  if (!supabase) return absent()
  const { data, error } = await supabase
    .from('documents')
    .select('*')
    .eq('shop_id', shopId)
    .order('created_at', { ascending: false })
  if (error) return { donnees: null, erreur: messageErreur(error) }
  return { donnees: (data as DocumentRow[]) ?? [], erreur: null }
}

export async function chargerPaiements(shopId: string): Promise<Resultat<PaiementRow[]>> {
  if (!supabase) return absent()
  // RLS limits this to the shop's documents; the embedded filter keeps the
  // payload small on a shop with years of history.
  const { data, error } = await supabase
    .from('payments')
    .select('*, documents!inner(shop_id)')
    .eq('documents.shop_id', shopId)
  if (error) return { donnees: null, erreur: messageErreur(error) }
  return { donnees: (data as PaiementRow[]) ?? [], erreur: null }
}

export async function chargerDocument(id: string): Promise<Resultat<DocumentRow>> {
  if (!supabase) return absent()
  const { data, error } = await supabase.from('documents').select('*').eq('id', id).maybeSingle()
  if (error) return { donnees: null, erreur: messageErreur(error) }
  if (!data) return { donnees: null, erreur: 'Document introuvable.' }
  return { donnees: data as DocumentRow, erreur: null }
}

export async function chargerLignes(documentId: string): Promise<Resultat<LigneRow[]>> {
  if (!supabase) return absent()
  const { data, error } = await supabase
    .from('document_lines')
    .select('*')
    .eq('document_id', documentId)
    .order('ordre', { ascending: true })
  if (error) return { donnees: null, erreur: messageErreur(error) }
  return { donnees: (data as LigneRow[]) ?? [], erreur: null }
}

export async function chargerPaiementsDocument(documentId: string): Promise<Resultat<PaiementRow[]>> {
  if (!supabase) return absent()
  const { data, error } = await supabase
    .from('payments')
    .select('*')
    .eq('document_id', documentId)
    .order('created_at', { ascending: true })
  if (error) return { donnees: null, erreur: messageErreur(error) }
  return { donnees: (data as PaiementRow[]) ?? [], erreur: null }
}

export async function enregistrerBrouillon(
  id: string | null,
  entete: Record<string, unknown>,
  lignes: Record<string, unknown>[],
): Promise<Resultat<string>> {
  if (!supabase) return absent()
  const { data, error } = await supabase.rpc('enregistrer_brouillon', {
    p_document: id,
    p_entete: entete,
    p_lignes: lignes,
  })
  if (error) return { donnees: null, erreur: messageErreur(error) }
  return { donnees: data as string, erreur: null }
}

export async function emettreDocument(
  id: string,
): Promise<Resultat<{ id: string; numero: string; date_emission: string; emis_le: string }>> {
  if (!supabase) return absent()
  const { data, error } = await supabase.rpc('emettre_document', { p_document: id })
  if (error) return { donnees: null, erreur: messageErreur(error) }
  return { donnees: data as { id: string; numero: string; date_emission: string; emis_le: string }, erreur: null }
}

export async function creerAvoir(id: string): Promise<Resultat<string>> {
  if (!supabase) return absent()
  const { data, error } = await supabase.rpc('creer_avoir', { p_document: id })
  if (error) return { donnees: null, erreur: messageErreur(error) }
  return { donnees: data as string, erreur: null }
}

export async function supprimerBrouillon(id: string): Promise<Resultat<true>> {
  if (!supabase) return absent()
  const { error } = await supabase.from('documents').delete().eq('id', id)
  if (error) return { donnees: null, erreur: messageErreur(error) }
  return { donnees: true, erreur: null }
}

export async function encaisser(
  documentId: string,
  montant: number,
  datePaiement: string,
  moyen: string,
  reference: string,
): Promise<Resultat<true>> {
  if (!supabase) return absent()
  const { error } = await supabase.from('payments').insert({
    document_id: documentId,
    montant,
    date_paiement: datePaiement,
    moyen,
    reference,
  })
  if (error) return { donnees: null, erreur: messageErreur(error) }
  return { donnees: true, erreur: null }
}

export async function annulerPaiement(id: string): Promise<Resultat<true>> {
  if (!supabase) return absent()
  const { error } = await supabase.from('payments').delete().eq('id', id)
  if (error) return { donnees: null, erreur: messageErreur(error) }
  return { donnees: true, erreur: null }
}

export async function chargerArchive(documentId: string): Promise<Resultat<ArchiveRow | null>> {
  if (!supabase) return absent()
  const { data, error } = await supabase
    .from('archives')
    .select('*')
    .eq('document_id', documentId)
    .maybeSingle()
  if (error) return { donnees: null, erreur: messageErreur(error) }
  return { donnees: (data as ArchiveRow | null) ?? null, erreur: null }
}

export async function enregistrerArchive(
  documentId: string,
  storagePath: string,
  sha256: string,
  taille: number,
): Promise<Resultat<true>> {
  if (!supabase) return absent()
  const { error } = await supabase
    .from('archives')
    .upsert({ document_id: documentId, storage_path: storagePath, sha256, taille })
  if (error) return { donnees: null, erreur: messageErreur(error) }
  return { donnees: true, erreur: null }
}

/** Continues a shop's numbering from an Android backup. */
export async function importerCompteurs(
  shopId: string,
  compteurs: Record<string, number>,
): Promise<Resultat<number>> {
  if (!supabase) return absent()
  const { data, error } = await supabase.rpc('importer_compteurs', {
    p_shop: shopId,
    p_compteurs: compteurs,
  })
  if (error) return { donnees: null, erreur: messageErreur(error) }
  return { donnees: data as number, erreur: null }
}
