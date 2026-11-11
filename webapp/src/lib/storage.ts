// Supabase Storage, kept out of the screens.
//
// The bucket is `logos` and a shop writes under its own id (see 0002). A logo
// is public to read: it appears on documents meant for customers.

import { supabase } from './supabase'

export const BUCKET_LOGOS = 'logos'

export function urlLogo(chemin: string | null | undefined): string | null {
  if (!supabase || !chemin) return null
  return supabase.storage.from(BUCKET_LOGOS).getPublicUrl(chemin).data.publicUrl
}

/** `logo-1720000000000.png` — a new name per upload, so no stale cache. */
export function cheminLogo(shopId: string, nomFichier: string): string {
  const extension = nomFichier.includes('.') ? nomFichier.split('.').pop()?.toLowerCase() : 'png'
  return `${shopId}/logo-${Date.now()}.${extension}`
}
