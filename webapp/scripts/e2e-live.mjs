// Test de bout en bout sur le site déployé.
//
// Prérequis : Brave lancé avec --remote-debugging-port=9222, et un compte de
// test confirmé dans Supabase. Les identifiants ne sont jamais committés :
//
//   E2E_EMAIL=... E2E_PASS=... node webapp/scripts/e2e-live.mjs
//
// Le script se connecte, crée une facture, vérifie les totaux, l'émet et
// télécharge le PDF archivé (en-tête %PDF). Il ne supprime rien : le compte de
// test et ses documents restent dans la base.
import { chromium } from 'playwright-core'
import fs from 'node:fs'

const URL = process.env.E2E_URL ?? 'https://factureapp.ma/app/'
const EMAIL = process.env.E2E_EMAIL
const PASS = process.env.E2E_PASS
const CDP = process.env.E2E_CDP ?? 'http://127.0.0.1:9222'
if (!EMAIL || !PASS) {
  console.error('Définir E2E_EMAIL et E2E_PASS.')
  process.exit(2)
}

const browser = await chromium.connectOverCDP(CDP)
const context = browser.contexts()[0] ?? (await browser.newContext())
const page = await context.newPage()

const erreurs = []
page.on('pageerror', (e) => erreurs.push(String(e)))
page.on('console', (m) => {
  if (m.type() === 'error') erreurs.push(m.text())
})
page.on('dialog', (d) => void d.accept())

await page.goto(URL, { waitUntil: 'networkidle' })

if (await page.locator('input#email').count()) {
  await page.fill('input#email', EMAIL)
  await page.fill('input#mdp', PASS)
  await page.getByRole('button', { name: 'Se connecter' }).click()
}

await page.waitForURL(/\/(app\/)?$/, { timeout: 20000 })
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
// Sur un nouveau document, `aller()` remonte l'écran et le message transitoire
// disparaît : l'archive se vérifie par le PDF téléchargé et la table archives.

const texteEmis = await page.locator('body').innerText()
const numero = texteEmis.match(/[A-Z]+-\d{8}-\d{4}/)?.[0]
if (!numero) throw new Error('Numéro émis introuvable')

const telechargement = page.waitForEvent('download', { timeout: 30000 })
await page.getByRole('button', { name: 'Télécharger le PDF' }).click()
const fichier = await telechargement
const chemin = process.env.E2E_PDF ?? '/tmp/facture-e2e.pdf'
await fichier.saveAs(chemin)
const octets = fs.readFileSync(chemin)
if (octets.subarray(0, 5).toString() !== '%PDF-') throw new Error('Le PDF téléchargé n’est pas un PDF')

console.log(JSON.stringify({
  ok: true,
  numero,
  pdf: chemin,
  taille: octets.length,
  erreurs: erreurs.slice(0, 5),
}, null, 2))

await browser.close()
