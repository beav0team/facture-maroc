// App paths. Pure module: importable from tests without Svelte runes.

export const BASE = '/app'

/** Maps a browser pathname to the internal route. */
export function cheminDepuis(pathname: string, base: string = BASE): string {
  let brut = pathname
  if (pathname === base) brut = ''
  else if (pathname.startsWith(`${base}/`)) brut = pathname.slice(base.length)

  const propre = brut.replace(/\/+$/, '')
  return propre === '' ? '/' : propre
}

/** Builds the real URL for an internal route. */
export function urlDe(vers: string, base: string = BASE): string {
  return base + (vers.startsWith('/') ? vers : `/${vers}`)
}
