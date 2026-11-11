import { createClient, type SupabaseClient } from '@supabase/supabase-js'

const url = import.meta.env.VITE_SUPABASE_URL as string | undefined
const cle = import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined

/**
 * The "anon" key is public: RLS protects the data, not the secrecy of this
 * key. Never put a service-role key here.
 */
export const estConfigure = Boolean(url && cle)

export const supabase: SupabaseClient | null = estConfigure
  ? createClient(url as string, cle as string)
  : null

export function client(): SupabaseClient {
  if (!supabase) {
    throw new Error(
      'Supabase n’est pas configuré : renseigne VITE_SUPABASE_URL et VITE_SUPABASE_ANON_KEY dans webapp/.env.',
    )
  }
  return supabase
}
