<script lang="ts">
  import { onMount } from 'svelte'
  import {
    dateCourte,
    derniereConnexion,
    FILTRES_BOUTIQUE,
    filtrerBoutiques,
    planLisible,
    type BoutiqueAdmin,
    type FiltreBoutique,
  } from '../../lib/admin'
  import { supabase } from '../../lib/supabase'

  let boutiques = $state<BoutiqueAdmin[]>([])
  let recherche = $state('')
  let filtre = $state<FiltreBoutique>('toutes')
  let chargement = $state(true)
  let erreur = $state<string | null>(null)

  const visibles = $derived(filtrerBoutiques(boutiques, recherche, filtre))

  onMount(() => {
    void charger()
  })

  async function charger() {
    if (!supabase) return
    chargement = true
    erreur = null
    const { data, error } = await supabase.rpc('admin_boutiques')
    chargement = false
    if (error) {
      erreur = error.message
      return
    }
    boutiques = (data as BoutiqueAdmin[]) ?? []
  }
</script>

<section class="carte bloc" aria-labelledby="titre-boutiques">
  <header class="bloc__entete">
    <div>
      <h2 id="titre-boutiques">Boutiques</h2>
      <p class="note">
        {boutiques.length} boutique{boutiques.length > 1 ? 's' : ''} · le palier décide du quota
        (5, 50 ou illimité, bonus compris).
      </p>
    </div>
    <button class="btn btn--discret" type="button" onclick={() => void charger()} disabled={chargement}>
      {chargement ? 'Chargement…' : 'Recharger'}
    </button>
  </header>

  <div class="recherche">
    <div class="champ">
      <label for="filtre-boutiques">Chercher</label>
      <input
        id="filtre-boutiques"
        type="search"
        placeholder="Nom, e-mail du propriétaire, ICE, IF…"
        autocomplete="off"
        bind:value={recherche}
      />
    </div>
  </div>

  <div class="filtres" role="group" aria-label="Filtrer les boutiques">
    {#each FILTRES_BOUTIQUE as choix}
      <button
        type="button"
        class:actif={filtre === choix.valeur}
        aria-pressed={filtre === choix.valeur}
        onclick={() => (filtre = choix.valeur)}>{choix.libelle}</button
      >
    {/each}
  </div>

  {#if erreur}<p class="alerte" role="alert">{erreur}</p>{/if}

  {#if chargement && boutiques.length === 0}
    <p class="note">Chargement…</p>
  {:else if visibles.length === 0}
    <p class="vide">Aucune boutique ne correspond.</p>
  {:else}
    <ul class="liste">
      {#each visibles as boutique (boutique.id)}
        <li class="ligne">
          <div class="ligne__texte">
            <h3>
              <a href={`/app/admin/boutiques/${boutique.id}`}>{boutique.nom}</a>
              <span class="etiquette" class:etiquette--pro={boutique.pro}>{planLisible(boutique)}</span>
              {#if boutique.suspendue}<span class="etiquette etiquette--danger">Suspendue</span>{/if}
              {#if !boutique.ice || !boutique.if_fiscal}
                <span class="etiquette etiquette--alerte">ICE / IF manquant</span>
              {/if}
            </h3>
            <p class="note">
              {boutique.proprietaire ?? 'aucun propriétaire'} · {boutique.membres}
              membre{boutique.membres > 1 ? 's' : ''} · créée le {dateCourte(boutique.cree_le)}
            </p>
            <p class="note">
              {boutique.documents} document{boutique.documents > 1 ? 's' : ''} ·
              {boutique.clients} client{boutique.clients > 1 ? 's' : ''} ·
              {boutique.articles} article{boutique.articles > 1 ? 's' : ''} ·
              dernière activité : {derniereConnexion(boutique.derniere_activite)}
            </p>
          </div>
          <div class="ligne__actions">
            <a class="btn btn--discret" href={`/app/admin/boutiques/${boutique.id}`}>Ouvrir</a>
          </div>
        </li>
      {/each}
    </ul>
  {/if}
</section>
