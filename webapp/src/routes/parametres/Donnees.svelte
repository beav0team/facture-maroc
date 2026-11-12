<script lang="ts">
  import { normaliser } from '../../lib/catalogue'
  import { enteteDeFacture, lignesDeFacture } from '../../lib/documents'
  import * as api from '../../lib/documentsApi'
  import { assemblerExport, erreurExport, lots, nomFichierExport } from '../../lib/export'
  import {
    lireExport,
    preparerRestauration,
    resumeRestauration,
    TAILLE_MAX_EXPORT,
    type Restauration,
  } from '../../lib/restauration'
  import { auth, chargerBoutique, enregistrerBoutique } from '../../lib/session.svelte'
  import { supabase } from '../../lib/supabase'
  import ReglagesPage from '../../lib/ui/ReglagesPage.svelte'

  let exportation = $state(false)
  let message = $state<string | null>(null)
  let erreur = $state<string | null>(null)

  let restauration = $state(false)
  let planRestauration = $state<Restauration | null>(null)
  let nomRestauration = $state('')
  let rErreur = $state<string | null>(null)
  let rMessage = $state<string | null>(null)
  let rRapport = $state<string[]>([])

  async function exporter() {
    const boutique = auth.boutique
    if (!supabase || !boutique) return

    exportation = true
    message = null
    erreur = null

    const [clients, articles, documents, compteurs] = await Promise.all([
      supabase.from('clients').select('*').eq('shop_id', boutique.id),
      supabase.from('articles').select('*').eq('shop_id', boutique.id),
      supabase.from('documents').select('*').eq('shop_id', boutique.id),
      supabase.from('numerotation').select('*').eq('shop_id', boutique.id),
    ])

    const entete = clients.error ?? articles.error ?? documents.error ?? compteurs.error
    if (entete) {
      exportation = false
      erreur = `Export impossible : ${entete.message}`
      return
    }

    // document_lines and payments have no shop_id: they belong to a document.
    const ids = (documents.data ?? []).map((document) => document.id as string)
    const lignes: unknown[] = []
    const paiements: unknown[] = []
    for (const lot of lots(ids)) {
      const [l, p] = await Promise.all([
        supabase.from('document_lines').select('*').in('document_id', lot),
        supabase.from('payments').select('*').in('document_id', lot),
      ])
      const probleme = l.error ?? p.error
      if (probleme) {
        exportation = false
        erreur = `Export impossible : ${probleme.message}`
        return
      }
      lignes.push(...(l.data ?? []))
      paiements.push(...(p.data ?? []))
    }

    const maintenant = new Date()
    const sources = {
      boutique,
      clients: clients.data ?? [],
      articles: articles.data ?? [],
      documents: documents.data ?? [],
      lignes,
      paiements,
      // `{ facture: 12, devis: 4 }` : exactement ce que `importer_compteurs()`
    // sait relire, donc l’export se restaure sans conversion.
    compteurs: Object.fromEntries(
      (compteurs.data ?? []).map((ligne) => [ligne.type as string, ligne.prochaine_valeur as number]),
    ),
    }

    const refus = erreurExport(sources)
    if (refus) {
      exportation = false
      erreur = refus
      return
    }

    const contenu = assemblerExport(sources, maintenant)
    const blob = new Blob([JSON.stringify(contenu, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const lien = document.createElement('a')
    lien.href = url
    lien.download = nomFichierExport(boutique.nom, maintenant)
    lien.click()
    URL.revokeObjectURL(url)

    exportation = false
    message = `Export écrit : ${lien.download} (${sources.documents.length} document${sources.documents.length > 1 ? 's' : ''}, ${sources.clients.length} client${sources.clients.length > 1 ? 's' : ''}).`
  }

  async function choisirRestauration(event: Event) {
    const champ = event.currentTarget as HTMLInputElement
    const fichier = champ.files?.[0]
    champ.value = ''
    planRestauration = null
    nomRestauration = ''
    rErreur = null
    rMessage = null
    rRapport = []
    if (!fichier) return

    if (fichier.size > TAILLE_MAX_EXPORT) {
      rErreur = 'Ce fichier dépasse 32 Mo : il ne ressemble pas à un export FactureApp.'
      return
    }

    const lu = lireExport(await fichier.text())
    if (lu.erreur || !lu.donnees) {
      rErreur = lu.erreur
      return
    }

    const prepare = preparerRestauration(lu.donnees)
    if (prepare.erreur || !prepare.donnees) {
      rErreur = prepare.erreur
      return
    }

    planRestauration = prepare.donnees
    nomRestauration = fichier.name
  }

  function remplirActuel<T>(actuel: T | null | undefined, importe: T): T {
    if (typeof actuel === 'string' && actuel.trim() !== '') return actuel
    return importe
  }

  /**
   * Remet l’export dans la boutique. Trois règles :
   *   - l’identité de la boutique ne remplit que les champs vides, comme
   *     l’import Android : un export ancien ne réécrit pas une fiche à jour ;
   *   - clients et articles se fusionnent par nom normalisé, jamais dupliqués ;
   *   - les documents reviennent en brouillons, sans leur numéro d’origine.
   */
  async function restaurer() {
    if (!supabase || !planRestauration || !auth.boutique) return
    restauration = true
    rErreur = null
    rMessage = null
    rRapport = []

    const shop = auth.boutique
    const ancien = planRestauration.boutique

    try {
      const probleme = await enregistrerBoutique({
        nom: remplirActuel(shop.nom, ancien.nom),
        adresse: shop.adresse || ancien.adresse || null,
        telephone: shop.telephone || ancien.telephone || null,
        email: shop.email || ancien.email || null,
        ice: shop.ice || ancien.ice || null,
        if_fiscal: shop.if_fiscal || ancien.if_fiscal || null,
        rc: shop.rc || ancien.rc || null,
        patente: shop.patente || ancien.patente || null,
        cnss: shop.cnss || ancien.cnss || null,
      })
      if (probleme) {
        rErreur = probleme
        return
      }

      // -------------------------------------------------------------- clients
      const clientsExistants = await supabase.from('clients').select('nom, role').eq('shop_id', shop.id)
      if (clientsExistants.error) {
        rErreur = `Clients : ${clientsExistants.error.message}`
        return
      }
      const vus = new Set(
        ((clientsExistants.data as Array<{ nom: string; role: string }>) ?? []).map(
          (client) => `${client.role}:${normaliser(client.nom)}`,
        ),
      )
      const nouveauxClients = planRestauration.clients.filter((client) => {
        const nom = typeof client.nom === 'string' ? client.nom : ''
        const role = client.role === 'fournisseur' ? 'fournisseur' : 'client'
        return nom.trim() !== '' && !vus.has(`${role}:${normaliser(nom)}`)
      })
      if (nouveauxClients.length > 0) {
        const { error } = await supabase.from('clients').insert(
          nouveauxClients.map((client) => ({
            shop_id: shop.id,
            nom: client.nom,
            role: client.role === 'fournisseur' ? 'fournisseur' : 'client',
            adresse: client.adresse || null,
            ice: client.ice || null,
            if_fiscal: client.if_fiscal || null,
            rc: client.rc || null,
            patente: client.patente || null,
            cnss: client.cnss || null,
            telephone: client.telephone || null,
            email: client.email || null,
            exonere_tva: Boolean(client.exonere_tva),
            note: client.note || null,
          })),
        )
        if (error) {
          rErreur = `Clients : ${error.message}`
          return
        }
      }
      rRapport.push(`${nouveauxClients.length} client(s) restauré(s)`)

      // ------------------------------------------------------------- articles
      const articlesExistants = await supabase.from('articles').select('libelle').eq('shop_id', shop.id)
      if (articlesExistants.error) {
        rErreur = `Articles : ${articlesExistants.error.message}`
        return
      }
      const libelles = new Set(
        ((articlesExistants.data as Array<{ libelle: string }>) ?? []).map((article) =>
          normaliser(article.libelle),
        ),
      )
      const nouveauxArticles = planRestauration.articles.filter(
        (article) =>
          typeof article.libelle === 'string' &&
          article.libelle.trim() !== '' &&
          !libelles.has(normaliser(article.libelle)),
      )
      if (nouveauxArticles.length > 0) {
        const { error } = await supabase.from('articles').insert(
          nouveauxArticles.map((article) => ({
            shop_id: shop.id,
            libelle: article.libelle,
            prix: Number(article.prix ?? 0),
            unite: article.unite ?? '',
            dossier: article.dossier ?? '',
            code_barre: article.code_barre ?? '',
            prix_achat: Number(article.prix_achat ?? 0),
            stock: article.stock === null || article.stock === undefined ? null : Number(article.stock),
            seuil_rea:
              article.seuil_rea === null || article.seuil_rea === undefined
                ? null
                : Number(article.seuil_rea),
            attributs: Array.isArray(article.attributs) ? article.attributs : [],
            paliers: Array.isArray(article.paliers) ? article.paliers : [],
          })),
        )
        if (error) {
          rErreur = `Articles : ${error.message}`
          return
        }
      }
      rRapport.push(`${nouveauxArticles.length} article(s) restauré(s)`)

      // ------------------------------------------------------------ compteurs
      const numeric = Object.fromEntries(
        Object.entries(planRestauration.compteurs).filter(
          ([, valeur]) => typeof valeur === 'number' && Number.isFinite(valeur),
        ),
      ) as Record<string, number>
      if (Object.keys(numeric).length > 0) {
        const compteurs = await api.importerCompteurs(shop.id, numeric)
        if (compteurs.erreur) {
          rErreur = `Numérotation : ${compteurs.erreur}`
          return
        }
        rRapport.push(`numérotation reprise sur ${compteurs.donnees ?? 0} type(s)`)
      }

      // ------------------------------------------------------------ documents
      let restaurés = 0
      for (const document of planRestauration.documents) {
        const enregistre = await api.enregistrerBrouillon(
          null,
          enteteDeFacture(shop.id, document.facture, shop.design_defaut ?? 'classique'),
          lignesDeFacture(document.facture.lignes),
        )
        if (enregistre.erreur) {
          rRapport.push(`document « ${document.numero || 'sans numéro'} » ignoré : ${enregistre.erreur}`)
        } else {
          restaurés += 1
        }
      }
      rRapport.push(`${restaurés} document(s) restauré(s) en brouillon`)

      await chargerBoutique()
      rMessage =
        'Restauration terminée. Les documents sont des brouillons : aucun numéro déjà ' +
        'imprimé n’a été réutilisé, et les PDF archivés restent à recharger depuis l’export.'
    } finally {
      restauration = false
    }
  }
</script>

<ReglagesPage
  titre="Données"
  texte="Reprendre une sauvegarde Android, ou récupérer une copie de vos données."
  enregistrable={false}
>
  <section class="carte bloc">
    <h2>Reprendre une sauvegarde Android</h2>
    <p class="bloc__texte">
      Clients, articles et numérotation sont importés. Les documents arrivent en
      brouillons : un numéro déjà imprimé n’est jamais réutilisé.
    </p>
    <a class="btn btn--discret" href="/app/import">Importer une sauvegarde</a>
  </section>

  <section class="carte bloc">
    <h2>Exporter mes données</h2>
    <p class="bloc__texte">
      Un fichier JSON avec la boutique, les clients, les articles, les documents,
      leurs lignes et leurs paiements. Vos PDF restent téléchargeables depuis
      chaque document.
    </p>

    {#if message}
      <p class="alerte alerte--info" role="status">{message}</p>
    {/if}
    {#if erreur}
      <p class="alerte" role="alert">{erreur}</p>
    {/if}

    <div>
      <button class="btn btn--discret" type="button" onclick={() => void exporter()} disabled={exportation}>
        {exportation ? 'Export en cours…' : 'Exporter mes données'}
      </button>
    </div>
  </section>

  <section class="carte bloc">
    <h2>Restaurer un export</h2>
    <p class="bloc__texte">
      Reprend un fichier <code>factureapp-….json</code> écrit par « Exporter mes
      données ». L’identité de la boutique ne remplit que les champs vides,
      clients et articles se fusionnent par nom, et les documents reviennent en
      brouillons : un numéro déjà imprimé n’est jamais réémis. Les paiements et
      les PDF archivés ne reviennent pas — le fichier les conserve.
    </p>

    <div>
      <label class="btn btn--discret fichier">
        {planRestauration ? 'Choisir un autre fichier' : 'Choisir un fichier'}
        <input type="file" accept="application/json,.json" onchange={(event) => void choisirRestauration(event)} />
      </label>
    </div>

    {#if nomRestauration && planRestauration}
      <p class="alerte alerte--info" role="status">
        {nomRestauration} — {resumeRestauration(planRestauration)}
        {#if planRestauration.exporteLe}
          · exporté le {planRestauration.exporteLe.slice(0, 10)}
        {/if}
      </p>
      <div>
        <button class="btn" type="button" onclick={() => void restaurer()} disabled={restauration}>
          {restauration ? 'Restauration…' : 'Restaurer dans cette boutique'}
        </button>
      </div>
    {/if}

    {#if rMessage}
      <p class="alerte alerte--info" role="status">{rMessage}</p>
    {/if}
    {#if rErreur}
      <p class="alerte" role="alert">{rErreur}</p>
    {/if}
    {#if rRapport.length > 0}
      <ul class="rapport">
        {#each rRapport as ligne}
          <li>{ligne}</li>
        {/each}
      </ul>
    {/if}
  </section>
</ReglagesPage>

<style>
  .fichier input {
    display: none;
  }

  .rapport {
    margin: 8px 0 0;
    padding-left: 20px;
    font-size: 0.9rem;
  }
</style>
