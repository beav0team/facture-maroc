<script lang="ts">
  import { onMount } from 'svelte'
  import { horodatage, libelleAction, resumeDetails, type LigneJournal } from '../../lib/admin'
  import { supabase } from '../../lib/supabase'

  let lignes = $state<LigneJournal[]>([])
  let recherche = $state('')
  let chargement = $state(true)
  let erreur = $state<string | null>(null)

  const visibles = $derived.by(() => {
    const texte = recherche.trim().toLowerCase()
    if (!texte) return lignes
    return lignes.filter((ligne) =>
      [ligne.acteur, ligne.cible_nom, ligne.action, resumeDetails(ligne.action, ligne.details)]
        .join(' ')
        .toLowerCase()
        .includes(texte),
    )
  })

  onMount(() => {
    void charger()
  })

  async function charger() {
    if (!supabase) return
    chargement = true
    erreur = null
    const { data, error } = await supabase.rpc('admin_journal', { p_limite: 200, p_shop: null })
    chargement = false
    if (error) {
      erreur = error.message
      return
    }
    lignes = (data as LigneJournal[]) ?? []
  }
</script>

<section class="carte bloc" aria-labelledby="titre-journal">
  <header class="bloc__entete">
    <div>
      <h2 id="titre-journal">Journal</h2>
      <p class="note">
        Chaque écriture de la console, avec son auteur. Les 200 dernières.
      </p>
    </div>
    <button class="btn btn--discret" type="button" onclick={() => void charger()} disabled={chargement}>
      {chargement ? 'Chargement…' : 'Recharger'}
    </button>
  </header>

  {#if erreur}<p class="alerte" role="alert">{erreur}</p>{/if}

  <div class="recherche">
    <div class="champ">
      <label for="filtre-journal">Chercher</label>
      <input
        id="filtre-journal"
        type="search"
        placeholder="Auteur, boutique, action"
        autocomplete="off"
        bind:value={recherche}
      />
    </div>
  </div>

  {#if chargement && lignes.length === 0}
    <p class="note">Chargement…</p>
  {:else if visibles.length === 0}
    <p class="vide">Aucune ligne.</p>
  {:else}
    <ul class="liste">
      {#each visibles as ligne (ligne.id)}
        <li class="ligne">
          <div class="ligne__texte">
            <h3>
              {libelleAction(ligne.action)}
              {#if ligne.cible_id && ligne.cible_type === 'boutique'}
                <a href={`/app/admin/boutiques/${ligne.cible_id}`}>{ligne.cible_nom || 'boutique'}</a>
              {:else}
                <span class="note">{ligne.cible_nom}</span>
              {/if}
            </h3>
            <p class="note">
              {horodatage(ligne.quand)} · {ligne.acteur}
              {#if resumeDetails(ligne.action, ligne.details)}
                · {resumeDetails(ligne.action, ligne.details)}
              {/if}
            </p>
          </div>
        </li>
      {/each}
    </ul>
  {/if}
</section>
