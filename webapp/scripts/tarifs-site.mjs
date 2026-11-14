#!/usr/bin/env node
// Injecte le barème réglé dans la console (migration 0016) dans les blocs
// balisés du site statique.
//
// Le site marketing est du HTML écrit à la main : les prix y étaient recopiés,
// donc faux dès le premier changement décidé dans la console. Ici, seuls les
// endroits balisés sont réécrits, le reste de la page reste éditorial :
//
//   <span data-tarif="essentiel-prix">240 DH</span>
//   <span data-tarif="essentiel-quota">50</span>
//   <ul data-tarif-liste="pro"> … </ul>
//
// Trois choix :
//   · la base est lue au build via `reglages_publics()`, la fonction publique de
//     la 0015/0016 — pas de clé de service, pas de table lue directement ;
//   · si la lecture échoue (réseau, Supabase indisponible), le fichier n'est
//     pas touché : le site garde les valeurs du dernier déploiement, qui sont
//     celles visibles en production. Un déploiement ne doit pas casser pour une
//     panne de lecture ;
//   · si un tarif réglé est illisible (prix hors bornes, quota absurde), on
//     échoue bruyamment plutôt que de publier un barème incohérent.
//
// Lancé par `scripts/build.sh` avant `check-site.mjs`, qui vérifie ensuite que
// le HTML obtenu est cohérent.

import { readFile, writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const ici = dirname(fileURLToPath(import.meta.url))
const racine = join(ici, '..')
const docs = join(racine, '..', 'docs')

const PAGES = ['index.html', 'cgv.html']
const PLANS = ['essentiel', 'pro']

// --------------------------------------------------------------- réglages lus

async function lireEnv() {
  const valeurs = {}
  try {
    const texte = await readFile(join(racine, '.env'), 'utf8')
    for (const ligne of texte.split('\n')) {
      const m = ligne.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/)
      if (m) valeurs[m[1]] = m[2].replace(/^["']|["']$/g, '')
    }
  } catch {
    // pas de .env : on se rabat sur l'environnement du build
  }
  return {
    url: process.env.VITE_SUPABASE_URL || process.env.SUPABASE_URL || valeurs.VITE_SUPABASE_URL,
    cle: process.env.VITE_SUPABASE_ANON_KEY || valeurs.VITE_SUPABASE_ANON_KEY,
  }
}

async function lireTarifs() {
  const { url, cle } = await lireEnv()
  if (!url || !cle) {
    console.log('tarifs-site : ni .env ni variables Supabase, site laissé tel quel.')
    return null
  }
  let reponse
  try {
    reponse = await fetch(`${url.replace(/\/$/, '')}/rest/v1/rpc/reglages_publics`, {
      method: 'POST',
      headers: {
        apikey: cle,
        Authorization: `Bearer ${cle}`,
        'Content-Type': 'application/json',
      },
      body: '{}',
      signal: AbortSignal.timeout(10_000),
    })
  } catch (erreur) {
    console.log(`tarifs-site : base injoignable (${erreur.message}), site laissé tel quel.`)
    return null
  }
  if (!reponse.ok) {
    console.log(`tarifs-site : lecture refusée (HTTP ${reponse.status}), site laissé tel quel.`)
    return null
  }
  const données = await reponse.json().catch(() => null)
  const tarifs = (données && typeof données === 'object' && données.tarifs) || {}
  return tarifs
}

// ---------------------------------------------------------------- mise en forme

const echapper = (texte) =>
  String(texte).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

/**
 * Les valeurs à écrire, par marqueur. Un plan absent de `tarifs` n'apparaît
 * pas : ses marqueurs gardent le HTML déjà publié.
 */
function valeursDepuis(tarifs) {
  const texte = new Map()
  const listes = new Map()

  for (const plan of PLANS) {
    const regle = tarifs[plan]
    if (!regle || typeof regle !== 'object') continue

    const prix = Number(regle.prix)
    if (!Number.isInteger(prix) || prix < 50 || prix > 50_000) {
      throw new Error(`tarif ${plan} : prix illisible (${JSON.stringify(regle.prix)}).`)
    }
    texte.set(`${plan}-prix`, `${prix} DH`)

    if (plan === 'essentiel') {
      const quota = Number(regle.quota)
      if (!Number.isInteger(quota) || quota < 6 || quota > 100_000) {
        throw new Error(`tarif essentiel : quota illisible (${JSON.stringify(regle.quota)}).`)
      }
      texte.set('essentiel-quota', String(quota))
    }

    if (Array.isArray(regle.points)) {
      const points = regle.points
        .filter((p) => typeof p === 'string')
        .map((p) => p.trim())
        .filter((p) => p.length >= 3 && p.length <= 140)
        .slice(0, 8)
      if (points.length) listes.set(plan, points)
    }
  }
  return { texte, listes }
}

// ------------------------------------------------------------------ réécriture

/** Remplace le contenu d'un élément balisé, sans toucher au reste de la page. */
function remplacerTexte(html, cle, valeur, nomFichier) {
  const motif = new RegExp(
    `(<([a-zA-Z][\\w-]*)[^>]*\\bdata-tarif="${cle}"[^>]*>)([\\s\\S]*?)(</\\2>)`,
    'g',
  )
  let trouve = 0
  const sortie = html.replace(motif, (_tout, ouverture, _balise, _ancien, fermeture) => {
    trouve += 1
    return `${ouverture}${echapper(valeur)}${fermeture}`
  })
  if (trouve === 0) {
    throw new Error(`${nomFichier} : aucun élément data-tarif="${cle}".`)
  }
  return sortie
}

function remplacerListe(html, plan, points, nomFichier) {
  const motif = new RegExp(`(<ul[^>]*\\bdata-tarif-liste="${plan}"[^>]*>)([\\s\\S]*?)(</ul>)`, 'g')
  let trouve = 0
  const sortie = html.replace(motif, (_tout, ouverture, interieur, fermeture) => {
    trouve += 1
    // On reprend l'indentation du premier <li> existant pour que le diff reste
    // lisible quand seules les valeurs changent.
    const retrait = interieur.match(/\n([ \t]+)<li/)?.[1] ?? '            '
    const retraitFin = interieur.match(/\n([ \t]+)$/)?.[1] ?? retrait.replace(/ {1,2}$/, '')
    const lignes = points.map((point) => `${retrait}<li>${echapper(point)}</li>`).join('\n')
    return `${ouverture}\n${lignes}\n${retraitFin}${fermeture}`
  })
  if (trouve === 0) {
    throw new Error(`${nomFichier} : aucune liste data-tarif-liste="${plan}".`)
  }
  return sortie
}

// ------------------------------------------------------------------------ main

const tarifs = await lireTarifs()
if (!tarifs) process.exit(0)

const { texte, listes } = valeursDepuis(tarifs)
if (texte.size === 0) {
  console.log('tarifs-site : aucun tarif réglé en base, site laissé tel quel.')
  process.exit(0)
}

for (const nom of PAGES) {
  const chemin = join(docs, nom)
  const avant = await readFile(chemin, 'utf8')
  let apres = avant

  for (const [cle, valeur] of texte) {
    // Une page peut ne pas porter un marqueur (par exemple `pro-quota`, qui
    // n'existe pas) : on ne remplace que ceux qu'elle a.
    if (!apres.includes(`data-tarif="${cle}"`)) continue
    apres = remplacerTexte(apres, cle, valeur, nom)
  }
  for (const [plan, points] of listes) {
    if (!apres.includes(`data-tarif-liste="${plan}"`)) continue
    apres = remplacerListe(apres, plan, points, nom)
  }

  if (apres !== avant) {
    await writeFile(chemin, apres, 'utf8')
    console.log(`tarifs-site : ${nom} mis à jour.`)
  } else {
    console.log(`tarifs-site : ${nom} déjà à jour.`)
  }
}
