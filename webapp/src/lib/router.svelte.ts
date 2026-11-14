// Tiny router: the app lives under /app/ and only has a handful of screens.
// No dependency, no reload, and the host returns index.html for any /app/*
// (see docs/_redirects).

import { BASE, cheminDepuis, urlDe } from './chemin'

export const route = $state<{ chemin: string }>({ chemin: '/' })

const TITRES: Record<string, string> = {
  '/': 'Tableau de bord — FactureApp',
  '/documents': 'Documents — FactureApp',
  '/documents/nouveau': 'Nouveau document — FactureApp',
  '/clients': 'Clients — FactureApp',
  '/articles': 'Articles — FactureApp',
  '/stock': 'Stock — FactureApp',
  '/parametres': 'Paramètres — FactureApp',
  '/gratuit': 'Générateur gratuit — FactureApp',
  '/tarifs': 'Tarifs — FactureApp',
  '/admin': 'Administration — FactureApp',
  '/admin/boutiques': 'Boutiques — Administration — FactureApp',
  '/admin/comptes': 'Comptes — Administration — FactureApp',
  '/admin/journal': 'Journal — Administration — FactureApp',
  '/import': 'Import Android — FactureApp',
}

function titrePour(chemin: string): string {
  if (chemin.startsWith('/documents/') && chemin !== '/documents/nouveau') return 'Document — FactureApp'
  if (chemin.startsWith('/clients/')) return 'Relevé client — FactureApp'
  if (chemin.startsWith('/articles/') && chemin !== '/articles') return 'Article — FactureApp'
  if (chemin.startsWith('/admin/boutiques/')) return 'Boutique — Administration — FactureApp'
  return TITRES[chemin] ?? 'FactureApp'
}

function lire(): string {
  if (typeof location === 'undefined') return '/'
  return cheminDepuis(location.pathname)
}

route.chemin = lire()
if (typeof document !== 'undefined') document.title = titrePour(route.chemin)

export function aller(vers: string, options: { remplacer?: boolean } = {}): void {
  const cible = urlDe(vers)
  if (cible === location.pathname) {
    route.chemin = lire()
    return
  }
  if (options.remplacer) history.replaceState({}, '', cible)
  else history.pushState({}, '', cible)
  const prochain = lire()
  route.chemin = prochain
  if (typeof document !== 'undefined') document.title = titrePour(prochain)
  // Focus le h1 de la nouvelle page pour l'accessibilité
  if (typeof document !== 'undefined') {
    requestAnimationFrame(() => {
      const h1 = document.querySelector('main h1, section h1, h1')
      if (h1 instanceof HTMLElement) {
        h1.setAttribute('tabindex', '-1')
        h1.focus({ preventScroll: true })
        h1.removeAttribute('tabindex')
      }
    })
  }
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
  if (typeof document !== 'undefined') document.title = titrePour(route.chemin)
  addEventListener('popstate', () => {
    const chemin = lire()
    route.chemin = chemin
    if (typeof document !== 'undefined') {
      document.title = titrePour(chemin)
      requestAnimationFrame(() => {
        const h1 = document.querySelector('main h1, section h1, h1')
        if (h1 instanceof HTMLElement) {
          h1.setAttribute('tabindex', '-1')
          h1.focus({ preventScroll: true })
          h1.removeAttribute('tabindex')
        }
      })
    }
  })
  document.addEventListener('click', surClic)
}
