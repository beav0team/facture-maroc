// Stock RPC calls — Phase 2 of the stock ledger (migration 0017).
// These mirror the SQL functions added in 0017_stock.sql.

import { supabase } from './supabase'
import type { Article } from './catalogue'

export interface StockSolde {
  article_id: string
  quantite: number
  dernier_mouvement: string | null
}

export interface StockEcart {
  article_id: string
  libelle: string
  cache: number
  journal: number
}

export interface MouvementStock {
  id: string
  shop_id: string
  article_id: string
  document_id: string | null
  delta: number
  motif: 'invoice' | 'credit' | 'purchase' | 'stocktake' | 'adjustment'
  reference: string
  note: string
  cout_unitaire: number
  cree_le: string
  cree_par: string | null
}

/** Reads the real quantities from the ledger for a shop. */
export async function stockSoldes(shopId: string): Promise<StockSolde[]> {
  if (!supabase) return []
  const { data, error } = await supabase.rpc('stock_soldes', { p_shop: shopId })
  if (error) throw new Error(error.message)
  return (data as StockSolde[]) ?? []
}

/** Finds articles where the cache (articles.stock) disagrees with the ledger. */
export async function stockEcarts(shopId: string): Promise<StockEcart[]> {
  if (!supabase) return []
  const { data, error } = await supabase.rpc('stock_ecarts', { p_shop: shopId })
  if (error) throw new Error(error.message)
  return (data as StockEcart[]) ?? []
}

/** Records a human gesture (receive, stocktake, correction). Returns the new quantity. */
export async function enregistrerMouvementStock(
  articleId: string,
  delta: number,
  motif: 'purchase' | 'stocktake' | 'adjustment',
  note: string = '',
  coutUnitaire: number = 0,
): Promise<number> {
  if (!supabase) throw new Error('Supabase non configuré')
  const { data, error } = await supabase.rpc('enregistrer_mouvement_stock', {
    p_article: articleId,
    p_delta: delta,
    p_motif: motif,
    p_note: note,
    p_cout: coutUnitaire,
  })
  if (error) throw new Error(error.message)
  return (data as { quantite: number } | null)?.quantite ?? 0
}

/** Fetches the last N movements for one article (newest first). */
export async function derniersMouvementsStock(
  shopId: string,
  articleId: string,
  limite: number = 6,
): Promise<MouvementStock[]> {
  if (!supabase) return []
  // The ledger is ordered by cree_le desc, so we just slice.
  const { data, error } = await supabase
    .from('stock_movements')
    .select('*')
    .eq('shop_id', shopId)
    .eq('article_id', articleId)
    .order('cree_le', { ascending: false })
    .limit(limite)
  if (error) throw new Error(error.message)
  return (data as MouvementStock[]) ?? []
}

/**
 * Merges ledger quantities into the article list.
 * Articles without ledger entries keep their cache (or 0 for new ones).
 * Services (stock === null) are never in the ledger.
 */
export function articlesAvecSoldes(
  articles: Article[],
  soldes: StockSolde[],
): Article[] {
  const parArticle = new Map(soldes.map((s) => [s.article_id, s.quantite]))
  return articles.map((a) => {
    if (a.stock === null) return a // service
    const reel = parArticle.get(a.id)
    return reel !== undefined ? { ...a, stock: reel } : a
  })
}

/** Formats a motive for display. */
export function libelleMotif(motif: MouvementStock['motif']): string {
  switch (motif) {
    case 'invoice':
      return 'Facture'
    case 'credit':
      return 'Avoir'
    case 'purchase':
      return 'Réception'
    case 'stocktake':
      return 'Inventaire'
    case 'adjustment':
      return 'Ajustement'
  }
}

/** Formats a signed delta for display. */
export function formaterDelta(delta: number): string {
  const signe = delta > 0 ? '+' : ''
  return `${signe}${delta}`
}

/** Returns the CSS class for a delta's color. */
export function classeDelta(delta: number): string {
  return delta > 0 ? 'delta--entree' : 'delta--sortie'
}