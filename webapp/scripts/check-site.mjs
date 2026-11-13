#!/usr/bin/env node
// Guards the marketing site: a page must keep the shared shell, link to the app,
// carry no Android, and print a price only where offres.ts says so.
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

// The prices exist once, in webapp/src/lib/offres.ts. The homepage may print
// them, and the terms page has to state them, but only the exact values found
// there: a stale copy fails the build instead of quietly diverging from what
// /app/tarifs charges.
const offres = await readFile(join(ici, '..', 'src', 'lib', 'offres.ts'), 'utf8')

function lireOffre(cle) {
  const debut = offres.indexOf(`cle: '${cle}'`)
  if (debut < 0) return null
  const fin = offres.indexOf('\n  },', debut)
  const bloc = offres.slice(debut, fin < 0 ? undefined : fin)
  return {
    cle,
    nom: bloc.match(/nom:\s*'([^']+)'/)?.[1] ?? null,
    prix: bloc.match(/prix:\s*'([^']+)'/)?.[1] ?? null,
    periode: bloc.match(/periode:\s*'([^']+)'/)?.[1] ?? null,
  }
}

const CLES = ['gratuit', 'essentiel', 'pro']
const offresLues = CLES.map(lireOffre)
for (const offre of offresLues) {
  if (!offre || !offre.prix || !offre.periode) {
    erreurs.push(`offres.ts: le palier « ${offre?.cle ?? '?'} » est incomplet`)
  }
}

// Every price the site is allowed to print, per page.
const autorisees = new Map([
  ['index.html', offresLues],
  ['cgv.html', offresLues.filter((offre) => offre?.cle !== 'gratuit')],
])

// The old single Pro price must never come back.
const ANCIEN_PRIX = '990 DH'

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

  if (html.includes(ANCIEN_PRIX)) {
    erreurs.push(`${nom}: l'ancien prix de ${ANCIEN_PRIX} traîne encore`)
  }

  const attendues = autorisees.get(nom)
  for (const offre of offresLues) {
    if (!offre?.prix) continue
    const montant = offre.prix.match(/\d+/)?.[0]
    if (!montant || montant === '0') continue
    if (attendues?.includes(offre)) {
      if (!html.includes(offre.prix)) {
        erreurs.push(
          `${nom}: le prix ${offre.nom} (${offre.prix}) manque ou ne correspond plus à offres.ts`,
        )
      }
      if (!html.includes(offre.periode)) {
        erreurs.push(
          `${nom}: la période ${offre.nom} (${offre.periode}) ne correspond plus à offres.ts`,
        )
      }
    } else if (
      new RegExp(`${montant}\\s*DH\\s*(?:<small|${offre.periode.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`).test(
        html,
      )
    ) {
      erreurs.push(`${nom}: le prix ${offre.nom} est recopié (il vit dans offres.ts)`)
    }
  }

  // The homepage also prints the free tier; its wording must match too.
  if (attendues?.includes(offresLues[0])) {
    const gratuit = offresLues[0]
    if (!html.includes(gratuit.prix)) {
      erreurs.push(`${nom}: le prix ${gratuit.nom} (${gratuit.prix}) manque`)
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

console.log(`Site marketing — ${fichiers.length} pages vérifiées, ${CLES.length} paliers.`)
