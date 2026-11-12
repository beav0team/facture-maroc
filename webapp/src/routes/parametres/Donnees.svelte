<script lang="ts">
  import { auth } from '../../lib/session.svelte'
  import { supabase } from '../../lib/supabase'
  import { assemblerExport, erreurExport, lots, nomFichierExport } from '../../lib/export'
  import ReglagesPage from '../../lib/ui/ReglagesPage.svelte'

  let exportation = $state(false)
  let message = $state<string | null>(null)
  let erreur = $state<string | null>(null)

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
      compteurs: Object.fromEntries((compteurs.data ?? []).map((ligne) => [ligne.id, ligne])),
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
</ReglagesPage>
