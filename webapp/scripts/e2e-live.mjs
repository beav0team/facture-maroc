// Test de bout en bout sur le site déployé.
//
// Prérequis : Brave lancé avec --remote-debugging-port=9223, et le compte de
// test de l'agent (voir scripts/compte-test.sh et ~/.config/factureapp/test.env).
// Aucun identifiant n'est committé : le fichier local reste hors du dépôt.
//
//   node webapp/scripts/e2e-live.mjs
//   E2E_CDP=http://127.0.0.1:9223 node webapp/scripts/e2e-live.mjs
//
// Le script se connecte, crée une facture dans la boutique de test, vérifie les
// totaux, l'émet, ouvre l'aperçu du PDF et télécharge l'archive (%PDF-).
// Il ne touche à aucune boutique réelle. Pour vider la boutique de test :
//
//   webapp/scripts/compte-test.sh reinitialiser
import { chromium } from 'playwright-core'
import fs from 'node:fs'
import os from 'node:os'

function lireEnv(chemin) {
  const valeurs = {}
  if (!fs.existsSync(chemin)) return valeurs
  for (const ligne of fs.readFileSync(chemin, 'utf8').split('\n')) {
    const m = /^([A-Z0-9_]+)="?(.*?)"?$/.exec(ligne.trim())
    if (m) valeurs[m[1]] = m[2]
  }
  return valeurs
}

const test = lireEnv(process.env.E2E_ENV ?? `${os.homedir()}/.config/factureapp/test.env`)
const URL = process.env.E2E_URL ?? 'https://factureapp.ma/app/'
const EMAIL = process.env.E2E_EMAIL ?? test.TEST_EMAIL
const PASS = process.env.E2E_PASS ?? test.TEST_PASSWORD
const CDP = process.env.E2E_CDP ?? 'http://127.0.0.1:9223'

if (!EMAIL || !PASS) {
  console.error('Définir E2E_EMAIL/E2E_PASS, ou créer le compte de test : scripts/compte-test.sh creer')
  process.exit(2)
}

const navigateur = await chromium.connectOverCDP(CDP)
const contexte = await navigateur.newContext({ acceptDownloads: true })
const page = await contexte.newPage()

const erreurs = []
page.on('pageerror', (e) => erreurs.push(String(e)))
page.on('console', (m) => {
  if (m.type() === 'error') erreurs.push(m.text())
})
page.on('dialog', (d) => void d.accept())

try {
  await page.goto(URL, { waitUntil: 'networkidle' })

  if (await page.locator('input#email').count()) {
    await page.fill('input#email', EMAIL)
    await page.fill('input#mdp', PASS)
    await page.getByRole('button', { name: 'Se connecter' }).click()
  }

  await page.locator('.barre__droite .etiquette').waitFor({ timeout: 20000 })
  await page.getByRole('link', { name: 'Nouvelle facture' }).click()
  await page.waitForURL(/documents\/nouveau/)

  await page.fill('#d-nom', 'Client E2E')
  await page.getByRole('button', { name: 'Ajouter une ligne' }).click()
  await page.fill('input[aria-label="Désignation"]', 'Prestation E2E')
  await page.fill('input[aria-label="Quantité"]', '2')
  await page.fill('input[aria-label="Prix unitaire HT"]', '500')

  const texte = await page.locator('body').innerText()
  const attenduHT = '1\u202F000,00 DH'
  const attenduTTC = '1\u202F200,00 DH'
  if (!texte.includes(attenduHT) || !texte.includes(attenduTTC)) {
    throw new Error(`Totaux incorrects : HT=${texte.includes(attenduHT)} TTC=${texte.includes(attenduTTC)}`)
  }

  await page.getByRole('button', { name: 'Émettre' }).click()
  await page.getByText(/Document émis :/).waitFor({ timeout: 20000 })
  // Émettre remonte l'écran sur /documents/<id> : cliquer avant ce remontage
  // ouvrirait l'aperçu d'une instance qui va disparaître.
  await page.waitForURL(/\/app\/documents\/[0-9a-f-]{36}/, { timeout: 20000 })

  const texteEmis = await page.getByRole('heading', { level: 1 }).innerText()
  const numero =
    (await page.locator('.entete .etiquette').first().innerText()).match(/[A-Z]+-\d{8}-\d{4}/)?.[0] ??
    texteEmis.match(/[A-Z]+-\d{8}-\d{4}/)?.[0]
  if (!numero) throw new Error('Numéro émis introuvable')

  await page.getByRole('button', { name: 'PDF du document' }).click()
  const dialogue = page.getByRole('dialog')
  await dialogue.waitFor({ timeout: 20000 })
  if (!(await dialogue.innerText()).includes('archive du document émis')) {
    throw new Error('L’aperçu n’a pas ouvert l’archive')
  }

  const attente = page.waitForEvent('download', { timeout: 30000 })
  await dialogue.getByRole('link', { name: 'Télécharger' }).click()
  const fichier = await attente
  const chemin = process.env.E2E_PDF ?? '/tmp/facture-e2e.pdf'
  await fichier.saveAs(chemin)
  const octets = fs.readFileSync(chemin)
  if (octets.subarray(0, 5).toString() !== '%PDF-') throw new Error('Le PDF téléchargé n’est pas un PDF')

  console.log(
    JSON.stringify(
      { ok: true, numero, pdf: chemin, taille: octets.length, erreurs: erreurs.slice(0, 5) },
      null,
      2,
    ),
  )
} finally {
  await page.close()
  await contexte.close()
}
process.exit(0)
