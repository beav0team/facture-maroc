// Tiny router: the app lives under /app/ and only has a handful of screens.
// No dependency, no reload, and the host returns index.html for any /app/*
// (see docs/_redirects).

import { BASE, cheminDepuis, urlDe } from './chemin'

export const route = $state<{ chemin: string }>({ chemin: '/' })

function lire(): string {
  if (typeof location === 'undefined') return '/'
  return cheminDepuis(location.pathname)
}

route.chemin = lire()

export function aller(vers: string, options: { remplacer?: boolean } = {}): void {
  const cible = urlDe(vers)
  if (cible === location.pathname) {
    route.chemin = lire()
    return
  }
  if (options.remplacer) history.replaceState({}, '', cible)
  else history.pushState({}, '', cible)
  route.chemin = lire()
}

function surClic(event: MouseEvent): void {
  if (
    event.defaultPrevented ||
    event.button !== 0 ||
    event.metaKey ||
    event.ctrlKey ||
    event.shiftKey ||
    event.altKey
  ) {
    return
  }
  const ancre = (event.target as HTMLElement | null)?.closest('a')
  if (!ancre) return
  const href = ancre.getAttribute('href')
  if (!href || !href.startsWith(BASE)) return
  if (ancre.target === '_blank' || ancre.hasAttribute('download')) return
  event.preventDefault()
  aller(href.slice(BASE.length) || '/')
}

export function demarrerRouteur(): void {
  route.chemin = lire()
  addEventListener('popstate', () => {
    route.chemin = lire()
  })
  document.addEventListener('click', surClic)
}
