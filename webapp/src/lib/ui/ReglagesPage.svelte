<script lang="ts">
  import { onMount, type Snippet } from 'svelte'
  import Barre from './Barre.svelte'
  import './reglages.css'
  import {
    chargerReglages,
    enregistrerGroupe,
    groupeModifie,
    reglages,
    type Groupe,
  } from '../reglages.svelte'
  import { auth } from '../session.svelte'

  let {
    titre,
    texte,
    groupe,
    enregistrable = true,
    children,
  }: {
    titre: string
    texte: string
    groupe?: Groupe
    enregistrable?: boolean
    children: Snippet
  } = $props()

  const modifie = $derived(!!groupe && reglages.reference !== '' && groupeModifie(groupe))

  // The shop can arrive after mount (OAuth return, slow session restore).
  // Guarded on an empty reference: once something is loaded, a shop identity
  // change must not wipe what the user typed — the save path reloads
  // explicitly through `enregistrerGroupe()`.
  $effect(() => {
    if (auth.boutique?.id && reglages.reference === '') chargerReglages()
  })

  onMount(() => {
    // A reload or a closed tab loses the typed values; the in-app exits are
    // handled by `quitter()` below.
    const garde = (evenement: BeforeUnloadEvent) => {
      if (!modifie) return
      evenement.preventDefault()
      evenement.returnValue = ''
    }
    window.addEventListener('beforeunload', garde)
    return () => window.removeEventListener('beforeunload', garde)
  })

  function quitter(evenement: MouseEvent) {
    if (!modifie) return
    if (!window.confirm('Des modifications ne sont pas enregistrées. Quitter quand même ?')) {
      evenement.preventDefault()
    }
  }

  async function soumettre(evenement: SubmitEvent) {
    evenement.preventDefault()
    if (groupe) await enregistrerGroupe(groupe)
  }
</script>

<div class="reglages">
  <Barre />

  <main class="contenu">
    <a class="retour" href="/app/parametres" onclick={quitter}>← Paramètres</a>

    <section class="entete">
      <h1>{titre}</h1>
      <p class="entete__texte">{texte}</p>
    </section>

    {#if reglages.message}
      <p class="alerte alerte--info" role="status">{reglages.message}</p>
    {/if}
    {#if reglages.erreur}
      <p class="alerte" role="alert">{reglages.erreur}</p>
    {/if}

    {#if enregistrable}
      <form onsubmit={soumettre} class="pile">
        {@render children()}

        <div class="barre-actions">
          <button class="btn" type="submit" disabled={reglages.enregistrement}>
            {reglages.enregistrement ? 'Enregistrement…' : 'Enregistrer'}
          </button>
          {#if modifie}
            <span class="etat">Modifications non enregistrées</span>
          {:else if !reglages.erreur}
            <span class="etat">À jour</span>
          {/if}
        </div>
      </form>
    {:else}
      <div class="pile">
        {@render children()}
      </div>
    {/if}
  </main>
</div>
