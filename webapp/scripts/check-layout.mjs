#!/usr/bin/env node
// Guards the marketing layout at phone widths. The header once collapsed into
// a one-link-per-line column between the logo and the CTA, and the CTA hung off
// the right edge — nothing in check-site.mjs can see that, because it never
// renders. This one does, with the system Chromium.
//
// Run from webapp/: `node scripts/check-layout.mjs`
// Local only: Cloudflare's build image has no browser, so build.sh does not
// call it.

import { readdir, readFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { chromium } from 'playwright-core'

const ici = dirname(fileURLToPath(import.meta.url))
const docs = join(ici, '..', '..', 'docs')

// 320 is the narrowest phone still in use; 720 is the breakpoint where the
// header goes back to one row; 1440 is the desktop reference.
const LARGEURS = [320, 360, 390, 414, 520, 720, 900, 1440]

const NAVIGATEUR = process.env.LAYOUT_CHROME ?? '/usr/bin/chromium'

const fichiers = (await readdir(docs))
  .filter((nom) => nom.endsWith('.html') && nom !== 'privacy.html')
  .sort()

// Expected nav size comes from each page's own markup, so a page that ships
// two links (test.html, the legacy Android testers page) is not a failure and
// a silently hidden one still is.
async function liensAttendus(nom) {
  const html = await readFile(join(docs, nom), 'utf8')
  const nav = html.match(/<nav[^>]*aria-label="Sections"[\s\S]*?<\/nav>/)
  return nav ? (nav[0].match(/<a\b/g) ?? []).length : 0
}


const navigateur = await chromium.launch({ executablePath: NAVIGATEUR, args: ['--no-sandbox'] })
const erreurs = []

try {
  for (const largeur of LARGEURS) {
    const page = await navigateur.newPage({ viewport: { width: largeur, height: 900 } })
    for (const nom of fichiers) {
      await page.goto(pathToFileURL(join(docs, nom)).href, { waitUntil: 'load' })

      const rapport = await page.evaluate(() => {
        const doc = document.documentElement
        const barre = document.querySelector('header.barre')
        const liens = [...document.querySelectorAll('header.barre nav a')]
        return {
          debordement: doc.scrollWidth - window.innerWidth,
          hauteurBarre: barre ? Math.round(barre.getBoundingClientRect().height) : null,
          liensHorsEcran: liens
            .filter((a) => {
              const r = a.getBoundingClientRect()
              return r.width === 0 || r.right > window.innerWidth + 1 || r.left < -1
            })
            .map((a) => a.textContent?.trim() ?? '?'),
          liens: liens.length,
        }
      })

      if (rapport.hauteurBarre === null) {
        erreurs.push(`${nom} @${largeur}: pas d'en-tête .barre`)
        continue
      }
      if (rapport.debordement > 1) {
        erreurs.push(`${nom} @${largeur}: débordement horizontal de ${rapport.debordement} px`)
      }
      if (rapport.hauteurBarre > 200) {
        erreurs.push(`${nom} @${largeur}: en-tête haut de ${rapport.hauteurBarre} px`)
      }
      if (rapport.liensHorsEcran.length > 0) {
        erreurs.push(
          `${nom} @${largeur}: liens hors écran — ${rapport.liensHorsEcran.join(', ')}`,
        )
      }
      if (rapport.liens < (await liensAttendus(nom))) {
        erreurs.push(
          `${nom} @${largeur}: ${rapport.liens} lien(s) de navigation rendus au lieu de ${await liensAttendus(nom)}`,
        )
      }
    }
    await page.close()
  }
} finally {
  await navigateur.close()
}

if (erreurs.length > 0) {
  console.error(erreurs.join('\n'))
  process.exit(1)
}
console.log(`mise en page vérifiée : ${fichiers.length} pages × ${LARGEURS.length} largeurs`)
