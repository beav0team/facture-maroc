#!/usr/bin/env node
// Guards the marketing site: a page must keep the shared shell, link to the app,
// carry no Android, and print the Pro price only where offres.ts says so.
//
// Run from webapp/: `node scripts/check-site.mjs`. build.sh calls it before
// `pnpm build` so Cloudflare refuses a deploy that would break the funnel.

import { readdir, readFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const ici = dirname(fileURLToPath(import.meta.url))
const docs = join(ici, '..', '..', 'docs')

const erreurs = []

// privacy.html is a separate legacy template: it is not part of the funnel and
// must not be touched by this guard.
const fichiers = (await readdir(docs))
  .filter((nom) => nom.endsWith('.html') && nom !== 'privacy.html')
  .sort()

const index = await readFile(join(docs, 'index.html'), 'utf8')
const idsIndex = new Set([...index.matchAll(/\bid="([^"]+)"/g)].map((m) => m[1]))

// The Pro price exists once, in webapp/src/lib/offres.ts. The homepage may
// print it, but only the exact value found there: a stale copy fails the build
// instead of quietly diverging from what /app/tarifs charges.
const offres = await readFile(join(ici, '..', 'src', 'lib', 'offres.ts'), 'utf8')
const offrePro = offres.match(
  /nom:\s*'Pro'[\s\S]*?prix:\s*'([^']+)'[\s\S]*?periode:\s*'([^']+)'/,
)
let prixPro = null
let periodePro = null
if (!offrePro) {
  erreurs.push('offres.ts: l’offre Pro est introuvable')
} else {
  prixPro = offrePro[1]
  periodePro = offrePro[2]
}
const montantPro = prixPro?.match(/\d+/)?.[0] ?? null

for (const nom of fichiers) {
  const html = await readFile(join(docs, nom), 'utf8')

  if (!html.includes('<header class="barre">')) {
    erreurs.push(`${nom}: pas d'en-tête .barre`)
  }
  if (!html.includes('class="marque"')) {
    erreurs.push(`${nom}: pas de marque dans l'en-tête`)
  }
  if (!/href="\/?app\//.test(html)) {
    erreurs.push(`${nom}: aucun lien vers /app/`)
  }

  // The homepage carries the price; every other page links to /app/tarifs
  // instead of copying it.
  if (montantPro) {
    // The homepage advertises the price and the terms page has to state it;
    // both must match offres.ts.
    if (nom === 'index.html' || nom === 'cgv.html') {
      if (!html.includes(prixPro)) {
        erreurs.push(`${nom}: le prix du Pro (${prixPro}) manque ou ne correspond plus à offres.ts`)
      }
      if (!html.includes(periodePro)) {
        erreurs.push(`${nom}: la période du Pro (${periodePro}) ne correspond plus à offres.ts`)
      }
    } else if (html.includes(montantPro)) {
      erreurs.push(`${nom}: le prix du Pro est recopié (il vit dans offres.ts)`)
    }
  }

  // The site sells the web app. test.html is the legacy Android install page,
  // kept in noindex for testers who already have the address. The Android app
  // is now advertised on /testeurs, so a marketing page may talk about it —
  // but only by sending the reader there, never straight to test.html.
  if (nom !== 'test.html') {
    if (/href="[^"]*test\.html/.test(html)) {
      erreurs.push(`${nom}: lien vers test.html (passer par testeurs.html)`)
    }
    if (/href="[^"]*privacy\.html/.test(html)) {
      erreurs.push(`${nom}: lien vers privacy.html (politique Android ; le web utilise confidentialite.html)`)
    }
    if (/\bAndroid\b|Play Store|Google Play/.test(html) && !/href="\/?testeurs\.html"|href="testeurs"/.test(html)) {
      erreurs.push(`${nom}: mentionne Android sans lien vers testeurs`)
    }
  } else if (!/<meta name="robots" content="noindex/.test(html)) {
    erreurs.push('test.html: la page Android doit rester en noindex')
  }

  // ./#x points at the homepage, #x at the page itself.
  for (const [, ancre] of html.matchAll(/href="\.\/#([^"]+)"/g)) {
    if (!idsIndex.has(ancre)) {
      erreurs.push(`${nom}: ancre ./#${ancre} absente de index.html`)
    }
  }
  for (const [, ancre] of html.matchAll(/href="#([^"]+)"/g)) {
    if (!new RegExp(`\\bid="${ancre}"`).test(html)) {
      erreurs.push(`${nom}: ancre #${ancre} absente de la page`)
    }
  }
}

const redirects = await readFile(join(docs, '_redirects'), 'utf8')
if (/^\/app\/\*\s+\/app\/\s+200/m.test(redirects)) {
  erreurs.push('_redirects: la règle attrape-tout /app/* casserait les assets')
}
if (!/^\/tarifs\s+\/app\/tarifs\s+301/m.test(redirects)) {
  erreurs.push('_redirects: la redirection /tarifs → /app/tarifs manque')
}

if (erreurs.length) {
  console.error('Site marketing — vérification échouée :')
  for (const e of erreurs) console.error(`  · ${e}`)
  process.exit(1)
}

console.log(`Site marketing — ${fichiers.length} pages vérifiées.`)
