<script lang="ts">
  import { normaliser } from '../lib/catalogue'
  import { enteteDeFacture, lignesDeFacture } from '../lib/documents'
  import * as api from '../lib/documentsApi'
  import { lireSauvegarde, type SauvegardeAndroid } from '../lib/importAndroid'
  import { auth, chargerBoutique, enregistrerBoutique } from '../lib/session.svelte'
  import { supabase } from '../lib/supabase'
  import Barre from '../lib/ui/Barre.svelte'
  import SansBoutique from '../lib/ui/SansBoutique.svelte'

  let sauvegarde = $state<SauvegardeAndroid | null>(null)
  let nomFichier = $state('')
  let inclureDocuments = $state(true)
  let importation = $state(false)
  let erreur = $state<string | null>(null)
  let message = $state<string | null>(null)
  let rapport = $state<string[]>([])

  async function choisir(event: Event) {
    const champ = event.currentTarget as HTMLInputElement
    const fichier = champ.files?.[0]
    champ.value = ''
    sauvegarde = null
    nomFichier = ''
    erreur = null
    message = null
    rapport = []
    if (!fichier) return

    const contenu = await fichier.text()
    const lu = lireSauvegarde(contenu)
    if (lu.erreur || !lu.donnees) {
      erreur = lu.erreur
      return
    }
    sauvegarde = lu.donnees
    nomFichier = fichier.name
  }

  function remplirActuel<T>(actuel: T | null | undefined, importe: T): T {
    if (typeof actuel === 'string' && actuel.trim() !== '') return actuel
    return importe
  }

  async function importer() {
    if (!supabase || !sauvegarde || !auth.boutique) return
    importation = true
    erreur = null
    message = null
    rapport = []

    const shop = auth.boutique
    const importe = sauvegarde.boutique

    try {
      // ------------------------------------------------------------- boutique
      const probleme = await enregistrerBoutique({
        nom: remplirActuel(shop.nom, importe.nom),
        adresse: remplirActuel(shop.adresse, importe.adresse) || null,
        telephone: remplirActuel(shop.telephone, importe.telephone) || null,
        email: remplirActuel(shop.email, importe.email) || null,
        ice: remplirActuel(shop.ice, importe.ice) || null,
        if_fiscal: remplirActuel(shop.if_fiscal, importe.if_fiscal) || null,
        rc: remplirActuel(shop.rc, importe.rc) || null,
        patente: remplirActuel(shop.patente, importe.patente) || null,
        cnss: remplirActuel(shop.cnss, importe.cnss) || null,
        tva_defaut: shop.tva_defaut ?? importe.tva_defaut,
        tva_non_applicable: shop.tva_non_applicable || importe.tva_non_applicable,
        prix_ttc: shop.prix_ttc || importe.prix_ttc,
      })
      if (probleme) {
        erreur = probleme
        return
      }

      // -------------------------------------------------------------- clients
      const clientsExistants = await supabase.from('clients').select('*').eq('shop_id', shop.id)
      const vus = new Set(
        ((clientsExistants.data as Array<{ nom: string; role: string }>) ?? []).map(
          (client) => `${client.role}:${normaliser(client.nom)}`,
        ),
      )
      const nouveauxClients = sauvegarde.clients.filter(
        (client) => !vus.has(`${client.role}:${normaliser(client.nom)}`),
      )
      if (nouveauxClients.length > 0) {
        const { error } = await supabase.from('clients').insert(
          nouveauxClients.map((client) => ({
            shop_id: shop.id,
            nom: client.nom,
            role: client.role,
            adresse: client.adresse || null,
            ice: client.ice || null,
            if_fiscal: client.if_fiscal || null,
            rc: client.rc || null,
            patente: client.patente || null,
            cnss: client.cnss || null,
            telephone: client.telephone || null,
            email: client.email || null,
            exonere_tva: client.exonere_tva,
            note: null,
          })),
        )
        if (error) {
          erreur = `Clients : ${error.message}`
          return
        }
      }
      rapport.push(`${nouveauxClients.length} client(s) importé(s)`)

      // ------------------------------------------------------------- articles
      const articlesExistants = await supabase.from('articles').select('*').eq('shop_id', shop.id)
      const libelles = new Set(
        ((articlesExistants.data as Array<{ libelle: string }>) ?? []).map((article) =>
          normaliser(article.libelle),
        ),
      )
      const nouveauxArticles = sauvegarde.articles.filter(
        (article) => !libelles.has(normaliser(article.libelle)),
      )
      if (nouveauxArticles.length > 0) {
        const { error } = await supabase.from('articles').insert(
          nouveauxArticles.map((article) => ({
            shop_id: shop.id,
            libelle: article.libelle,
            prix: article.prix,
            unite: article.unite,
            dossier: article.dossier,
            code_barre: article.code_barre,
            prix_achat: article.prix_achat,
            stock: article.stock,
            seuil_rea: article.seuil_rea,
            attributs: article.attributs,
            paliers: article.paliers,
          })),
        )
        if (error) {
          erreur = `Articles : ${error.message}`
          return
        }
      }
      rapport.push(`${nouveauxArticles.length} article(s) importé(s)`)

      // ------------------------------------------------------------ compteurs
      const compteurs = await api.importerCompteurs(shop.id, sauvegarde.compteurs)
      if (compteurs.erreur) {
        erreur = `Numérotation : ${compteurs.erreur}`
        return
      }
      rapport.push(`numérotation reprise sur ${compteurs.donnees ?? 0} type(s)`)

      // ------------------------------------------------------------ documents
      let documentsImportes = 0
      if (inclureDocuments) {
        for (const document of sauvegarde.documents) {
          const facture = { ...document.facture }
          // The Android number is not reissued: a number belongs to the app
          // that printed it. It is kept as a reference so the shop can find
          // the paper trail.
          if (document.numero && !facture.reference?.trim()) {
            facture.reference = `Android ${document.numero}`
          }
          const enregistre = await api.enregistrerBrouillon(
            null,
            enteteDeFacture(shop.id, facture, shop.design_defaut ?? 'classique'),
            lignesDeFacture(facture.lignes),
          )
          if (enregistre.erreur) {
            rapport.push(`document « ${document.numero || 'sans numéro'} » ignoré : ${enregistre.erreur}`)
          } else {
            documentsImportes += 1
          }
        }
        rapport.push(`${documentsImportes} document(s) importé(s) en brouillon`)
      }

      await chargerBoutique()
      message = 'Import terminé. Les documents importés sont des brouillons : aucun numéro déjà imprimé n’a été réutilisé.'
    } finally {
      importation = false
    }
  }
</script>

<div class="page">
  <Barre />

  <main class="contenu">
    {#if !auth.boutique}
      <SansBoutique />
    {:else}
      <section class="entete">
        <div>
          <h1>Importer une sauvegarde Android</h1>
          <p class="texte">
            Le fichier vient de l’application Android : Réglages → Sauvegarde.
            Les clients, les articles et la numérotation sont repris. Les
            documents arrivent en brouillons, sans réutiliser un numéro déjà
            imprimé.
          </p>
        </div>
      </section>

      {#if message}<p class="alerte alerte--info" role="status">{message}</p>{/if}
      {#if erreur}<p class="alerte" role="alert">{erreur}</p>{/if}

      <section class="carte bloc">
        <label class="depot">
          <span class="depot__titre">Choisir le fichier de sauvegarde</span>
          <span class="depot__aide">JSON, 16 Mo maximum</span>
          <input type="file" accept="application/json,.json" onchange={choisir} />
        </label>

        {#if sauvegarde}
          <div class="apercu">
            <h2>{nomFichier}</h2>
            <ul>
              <li>{sauvegarde.clients.length} client(s) et fournisseur(s)</li>
              <li>{sauvegarde.articles.length} article(s)</li>
              <li>{sauvegarde.documents.length} document(s)</li>
              <li>{Object.keys(sauvegarde.compteurs).length} compteur(s) de numérotation</li>
            </ul>
            <label class="champ champ--case">
              <input type="checkbox" bind:checked={inclureDocuments} />
              <span>Importer aussi les documents (en brouillons)</span>
            </label>
            <button class="btn" type="button" onclick={() => void importer()} disabled={importation}>
              {importation ? 'Import…' : 'Importer cette sauvegarde'}
            </button>
          </div>
        {/if}

        {#if rapport.length > 0}
          <ul class="rapport">
            {#each rapport as ligne}
              <li>{ligne}</li>
            {/each}
          </ul>
        {/if}
      </section>
    {/if}
  </main>
</div>

<style>
  .page {
    min-height: 100dvh;
    display: flex;
    flex-direction: column;
  }

  .contenu {
    width: min(760px, 100%);
    margin: 0 auto;
    padding: clamp(24px, 5vw, 48px) clamp(16px, 4vw, 24px);
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .entete h1 {
    font-size: clamp(1.5rem, 3vw, 1.9rem);
  }

  .texte {
    color: var(--ardoise);
    margin-top: 6px;
  }

  .bloc {
    padding: 22px;
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .depot {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 22px;
    border: 1px dashed var(--trait);
    border-radius: var(--rayon);
    background: var(--papier);
    cursor: pointer;
  }

  .depot__titre {
    font-weight: 600;
  }

  .depot__aide {
    color: var(--ardoise);
    font-size: 0.85rem;
  }

  .depot input {
    margin-top: 10px;
  }

  .apercu {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .apercu h2 {
    font-size: 1.05rem;
  }

  .apercu ul,
  .rapport {
    margin: 0;
    padding-left: 18px;
    color: var(--ardoise);
    font-size: 0.92rem;
  }

  .apercu .btn {
    align-self: flex-start;
  }
</style>
