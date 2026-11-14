#!/usr/bin/env node
// Guards the marketing site: a page must keep the shared shell, link to the app,
// carry no Android, and print the paid prices only where they were injected.
//
// Run from webapp/: `node scripts/check-site.mjs`. `build.sh` runs
// `scripts/tarifs-site.mjs` first, then this guard, so Cloudflare refuses a
// deploy that would break the funnel or publish a price the console does not
// agree with.

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

// --------------------------------------------------------------- les prix
//
// Prices are data since migration 0016. `scripts/tarifs-site.mjs` writes them
// into the tags that carry `data-tarif="…"`, from `reglages_publics()`. This
// guard does not know the database: it checks that what is written there is
// well formed, identical on every page, and never duplicated by hand outside a
// marker. The free plan (0 DH) is not a setting and stays hard-coded.

const element = (cle) =>
  new RegExp(`<([a-zA-Z][\\w-]*)[^>]*\\bdata-tarif="${cle}"[^>]*>([\\s\\S]*?)</\\1>`, 'g')

const sansBalises = (texte) =>
  texte
    .replace(/<[^>]+>/g, ' ')
    .replace(/&#160;|&nbsp;/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()

/** Les clés `data-tarif="…"` de la page, avec leur contenu en texte. */
function marqueurs(html) {
  const valeurs = new Map()
  for (const [, cle] of html.matchAll(/data-tarif="([^"]+)"/g)) {
    const trouves = [...html.matchAll(element(cle))].map((m) => sansBalises(m[2]))
    if (trouves.length === 0) continue
    valeurs.set(cle, trouves)
  }
  return valeurs
}

/** Le HTML débarrassé des blocs injectés, pour y chercher un prix recopié. */
function sansMarqueurs(html) {
  return html
    .replace(/<([a-zA-Z][\w-]*)[^>]*\bdata-tarif="[^"]+"[^>]*>[\s\S]*?<\/\1>/g, '')
    .replace(/<ul[^>]*\bdata-tarif-liste="[^"]+"[^>]*>[\s\S]*?<\/ul>/g, '')
}

const PAYANTES = ['index.html', 'cgv.html']
// Chaque page qui vend doit porter les prix, le quota d'Essentiel et — sur
// l'accueil — les listes de prestations.
const ATTENDUS = new Map([
  ['index.html', ['essentiel-prix', 'pro-prix', 'essentiel-quota']],
  ['cgv.html', ['essentiel-prix', 'pro-prix', 'essentiel-quota']],
])
const LISTES = new Map([['index.html', ['essentiel', 'pro']]])

const prixParCle = new Map()

for (const nom of PAYANTES) {
  const html = await readFile(join(docs, nom), 'utf8')
  const trouves = marqueurs(html)

  for (const cle of ATTENDUS.get(nom) ?? []) {
    if (!trouves.has(cle)) {
      erreurs.push(`${nom}: marqueur data-tarif="${cle}" manquant`)
    }
  }

  for (const plan of LISTES.get(nom) ?? []) {
    const motif = new RegExp(`<ul[^>]*\\bdata-tarif-liste="${plan}"[^>]*>([\\s\\S]*?)</ul>`)
    const liste = html.match(motif)?.[1]
    if (!liste) {
      erreurs.push(`${nom}: liste data-tarif-liste="${plan}" manquante`)
      continue
    }
    const puces = [...liste.matchAll(/<li>([\s\S]*?)<\/li>/g)].length
    if (puces < 1 || puces > 8) {
      erreurs.push(`${nom}: la liste ${plan} compte ${puces} prestations (1 à 8 attendues)`)
    }
  }

  for (const [cle, valeurs] of trouves) {
    if (new Set(valeurs).size > 1) {
      erreurs.push(`${nom}: les marqueurs "${cle}" ne disent pas la même chose (${valeurs.join(' / ')})`)
    }
    const valeur = valeurs[0]

    if (cle.endsWith('-prix')) {
      const montant = Number(valeur.match(/^(\d{2,5}) DH$/)?.[1])
      if (!Number.isInteger(montant) || montant < 50 || montant > 50_000) {
        erreurs.push(`${nom}: ${cle} vaut « ${valeur} », pas un prix entier entre 50 et 50 000 DH`)
      }
    } else if (cle.endsWith('-quota')) {
      const quota = Number(valeur)
      if (!Number.isInteger(quota) || quota < 6 || quota > 100_000) {
        erreurs.push(`${nom}: ${cle} vaut « ${valeur} », pas un quota entier entre 6 et 100 000`)
      }
    }

    const deja = prixParCle.get(cle)
    if (deja && deja.valeur !== valeur) {
      erreurs.push(`le marqueur "${cle}" vaut « ${deja.valeur} » sur ${deja.nom} et « ${valeur} » sur ${nom}`)
    } else if (!deja) {
      prixParCle.set(cle, { valeur, nom })
    }
  }

  // A price typed by hand outside a marker would silently disagree with the
  // console. The mock invoices above print amounts like « 2 304,00 DH » or
  // « 1 × 480 DH », so only a bare amount followed by DH is suspicious, and 0
  // (the free plan) is legitimate.
  const restant = sansBalises(sansMarqueurs(html))
  for (const [, montant] of restant.matchAll(/(?<![\d,.])(\d{1,5})\s*DH/g)) {
    if (montant === '0') continue
    erreurs.push(`${nom}: prix écrit à la main « ${montant} DH » hors d'un marqueur data-tarif`)
  }
}

if (!/0\s*DH/.test(index)) {
  erreurs.push('index.html: le prix du plan gratuit (0 DH) a disparu')
}

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

const bareme = [...prixParCle]
  .filter(([cle]) => cle.endsWith('-prix'))
  .map(([cle, { valeur }]) => `${cle.replace('-prix', '')} ${valeur}`)
  .join(', ')
console.log(`Site marketing — ${fichiers.length} pages vérifiées, ${bareme}.`)
