<script lang="ts">
  import { onMount } from 'svelte'
  import { auth, demarrer } from './lib/session.svelte'
  import { route, demarrerRouteur } from './lib/router.svelte'
  import Login from './routes/Login.svelte'
  import Accueil from './routes/Accueil.svelte'
  import Clients from './routes/Clients.svelte'
  import Articles from './routes/Articles.svelte'
  import Parametres from './routes/Parametres.svelte'
  import ParametresBoutique from './routes/parametres/Boutique.svelte'
  import ParametresFacturation from './routes/parametres/Facturation.svelte'
  import ParametresNumerotation from './routes/parametres/Numerotation.svelte'
  import ParametresBanque from './routes/parametres/Banque.svelte'
  import ParametresDonnees from './routes/parametres/Donnees.svelte'
  import Documents from './routes/Documents.svelte'
  import DocumentEditeur from './routes/DocumentEditeur.svelte'
  import Releve from './routes/Releve.svelte'
  import Generateur from './routes/Generateur.svelte'
  import Tarifs from './routes/Tarifs.svelte'
  import Admin from './routes/Admin.svelte'
  import Import from './routes/Import.svelte'
  import Introuvable from './routes/Introuvable.svelte'
  import MotDePasse from './routes/MotDePasse.svelte'

  onMount(() => {
    demarrerRouteur()
    void demarrer()
  })

  const PREFIXE_DOCUMENT = '/documents/'
  const PREFIXE_ARTICLES = '/articles/'
  const ROUTE_RELEVE = /^\/clients\/([^/]+)\/releve$/
  const releve = $derived(ROUTE_RELEVE.exec(route.chemin))

  /**
   * The folder a URL points at. The path is encoded as a whole, so a folder
   * named "Mobilier/Chaises" keeps its slash inside one segment and cannot turn
   * into two routes.
   */
  const dossierArticles = $derived.by(() => {
    if (!route.chemin.startsWith(PREFIXE_ARTICLES)) return ''
    const encode = route.chemin.slice(PREFIXE_ARTICLES.length)
    try {
      return decodeURIComponent(encode)
    } catch {
      return ''
    }
  })
</script>

{#if !auth.pret}
  <div class="chargement">Chargement…</div>
{:else if auth.recuperation}
  <MotDePasse />
{:else if route.chemin === '/gratuit'}
  <Generateur />
{:else if route.chemin === '/tarifs'}
  <Tarifs />
{:else if !auth.user}
  <Login />
{:else}
  {#key route.chemin}
    {#if route.chemin === '/'}
      <Accueil />
    {:else if route.chemin === '/documents'}
      <Documents />
    {:else if route.chemin === '/documents/nouveau'}
      <DocumentEditeur id={null} />
    {:else if route.chemin.startsWith(PREFIXE_DOCUMENT)}
      <DocumentEditeur id={route.chemin.slice(PREFIXE_DOCUMENT.length)} />
    {:else if route.chemin === '/clients'}
      <Clients />
    {:else if releve?.[1]}
      <Releve id={releve[1]} />
    {:else if route.chemin === '/articles'}
      <Articles />
    {:else if route.chemin.startsWith(PREFIXE_ARTICLES)}
      <Articles dossier={dossierArticles} />
    {:else if route.chemin === '/parametres'}
      <Parametres />
    {:else if route.chemin === '/parametres/boutique'}
      <ParametresBoutique />
    {:else if route.chemin === '/parametres/facturation'}
      <ParametresFacturation />
    {:else if route.chemin === '/parametres/numerotation'}
      <ParametresNumerotation />
    {:else if route.chemin === '/parametres/banque'}
      <ParametresBanque />
    {:else if route.chemin === '/parametres/donnees'}
      <ParametresDonnees />
    {:else if route.chemin === '/admin' || route.chemin.startsWith('/admin/')}
      <Admin />
    {:else if route.chemin === '/import'}
      <Import />
    {:else}
      <Introuvable />
    {/if}
  {/key}
{/if}
