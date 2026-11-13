<script lang="ts">
  import { onMount } from 'svelte'
  import {
    dateCourte,
    derniereConnexion,
    filtrerComptes,
    type UtilisateurAdmin,
  } from '../../lib/admin'
  import { auth } from '../../lib/session.svelte'
  import { supabase } from '../../lib/supabase'

  let comptes = $state<UtilisateurAdmin[]>([])
  let recherche = $state('')
  let nouvelAdmin = $state('')
  let chargement = $state(true)
  let enCours = $state<string | null>(null)
  let erreur = $state<string | null>(null)
  let message = $state<string | null>(null)

  const visibles = $derived(filtrerComptes(comptes, recherche))
  const nombreAdmin = $derived(comptes.filter((compte) => compte.est_admin).length)

  onMount(() => {
    void charger()
  })

  async function charger() {
    if (!supabase) return
    chargement = true
    erreur = null
    const { data, error } = await supabase.rpc('admin_utilisateurs')
    chargement = false
    if (error) {
      erreur = error.message
      return
    }
    comptes = (data as UtilisateurAdmin[]) ?? []
  }

  async function nommer(event: SubmitEvent) {
    event.preventDefault()
    const email = nouvelAdmin.trim()
    if (!email || !supabase) return
    enCours = 'nommer'
    erreur = null
    message = null
    const { error } = await supabase.rpc('admin_nommer', { p_email: email })
    enCours = null
    if (error) {
      erreur = error.message
      return
    }
    message = `${email} peut maintenant ouvrir /app/admin.`
    nouvelAdmin = ''
    await charger()
  }

  async function nommerCompte(compte: UtilisateurAdmin) {
    if (!compte.email || !supabase) return
    enCours = compte.id
    erreur = null
    message = null
    const { error } = await supabase.rpc('admin_nommer', { p_email: compte.email })
    enCours = null
    if (error) {
      erreur = error.message
      return
    }
    message = `${compte.email} peut maintenant ouvrir /app/admin.`
    await charger()
  }

  async function retirer(compte: UtilisateurAdmin) {
    const soi = compte.id === auth.user?.id
    const avertissement = soi
      ? `Vous allez retirer VOTRE propre accès à cette console. Continuer ?`
      : `Retirer l’accès administration à ${compte.email} ?`
    if (!confirm(avertissement)) return
    if (!supabase) return
    enCours = compte.id
    erreur = null
    message = null
    const { error } = await supabase.rpc('admin_retirer', { p_user: compte.id })
    enCours = null
    if (error) {
      erreur = error.message
      return
    }
    message = `${compte.email} n’est plus administrateur.`
    await charger()
  }
</script>

<section class="carte bloc" aria-labelledby="titre-comptes">
  <header class="bloc__entete">
    <div>
      <h2 id="titre-comptes">Comptes</h2>
      <p class="note">
        {comptes.length} compte{comptes.length > 1 ? 's' : ''} · {nombreAdmin} administrateur{nombreAdmin >
        1
          ? 's'
          : ''}. Un administrateur voit cette console et peut activer un plan.
      </p>
    </div>
    <button class="btn btn--discret" type="button" onclick={() => void charger()} disabled={chargement}>
      {chargement ? 'Chargement…' : 'Recharger'}
    </button>
  </header>

  {#if message}<p class="alerte alerte--info" role="status">{message}</p>{/if}
  {#if erreur}<p class="alerte" role="alert">{erreur}</p>{/if}

  <div class="recherche">
    <div class="champ">
      <label for="filtre-comptes">Chercher</label>
      <input
        id="filtre-comptes"
        type="search"
        placeholder="E-mail ou boutique"
        autocomplete="off"
        bind:value={recherche}
      />
    </div>
  </div>

  <form class="recherche" onsubmit={nommer}>
    <div class="champ">
      <label for="admin-email">Nommer par e-mail</label>
      <input
        id="admin-email"
        type="email"
        autocomplete="off"
        placeholder="compte@exemple.ma"
        bind:value={nouvelAdmin}
      />
    </div>
    <button class="btn" type="submit" disabled={enCours === 'nommer' || nouvelAdmin.trim().length === 0}>
      {enCours === 'nommer' ? 'Nomination…' : 'Nommer administrateur'}
    </button>
  </form>

  <ul class="liste">
    {#each visibles as compte (compte.id)}
      <li class="ligne">
        <div class="ligne__texte">
          <h3>
            {compte.email ?? 'compte sans e-mail'}
            {#if compte.id === auth.user?.id}<span class="etiquette">vous</span>{/if}
            {#if compte.est_admin}<span class="etiquette etiquette--pro">administrateur</span>{/if}
            {#if !compte.confirme}<span class="etiquette etiquette--alerte">e-mail non confirmé</span>{/if}
          </h3>
          <p class="note">
            {compte.boutique ?? 'sans boutique'} · créé le {dateCourte(compte.cree_le)} · dernière
            connexion : {derniereConnexion(compte.derniere_connexion)}
          </p>
        </div>
        <div class="ligne__actions">
          {#if compte.est_admin}
            <button
              class="lien-discret lien-discret--danger"
              type="button"
              disabled={enCours === compte.id}
              onclick={() => retirer(compte)}
            >
              Retirer
            </button>
          {:else}
            <button
              class="lien-discret"
              type="button"
              disabled={enCours === compte.id}
              onclick={() => nommerCompte(compte)}
            >
              Nommer
            </button>
          {/if}
        </div>
      </li>
    {/each}
  </ul>
</section>
