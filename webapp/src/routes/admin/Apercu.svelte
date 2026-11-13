<script lang="ts">
  import { onMount } from 'svelte'
  import {
    alertesTriees,
    dateCourte,
    octets,
    type AlerteAdmin,
    type StatistiquesAdmin,
  } from '../../lib/admin'
  import { supabase } from '../../lib/supabase'

  let { stats }: { stats: StatistiquesAdmin | null } = $props()

  let alertes = $state<AlerteAdmin[]>([])
  let chargement = $state(true)
  let erreur = $state<string | null>(null)

  const GENRES: Record<AlerteAdmin['genre'], string> = {
    expire_bientot: 'Expire bientôt',
    expire: 'Abonnement expiré',
    sans_proprietaire: 'Sans propriétaire',
    quota_atteint: 'Quota atteint',
    suspendue: 'Suspendue',
    identite_incomplete: 'Identité incomplète',
  }

  const fortes: AlerteAdmin['genre'][] = ['suspendue', 'quota_atteint', 'expire']

  onMount(() => {
    void charger()
  })

  async function charger() {
    if (!supabase) return
    chargement = true
    erreur = null
    const { data, error } = await supabase.rpc('admin_alertes')
    chargement = false
    if (error) {
      erreur = error.message
      return
    }
    alertes = alertesTriees((data as AlerteAdmin[]) ?? [])
  }
</script>

<section class="grille" aria-label="Vue d’ensemble">
  <article class="carte chiffre">
    <p class="chiffre__valeur">{stats?.documents_emis ?? 0}</p>
    <p class="chiffre__libelle">Documents émis</p>
  </article>
  <article class="carte chiffre">
    <p class="chiffre__valeur">{stats?.documents ?? 0}</p>
    <p class="chiffre__libelle">Documents au total</p>
  </article>
  <article class="carte chiffre">
    <p class="chiffre__valeur">{stats?.archives ?? 0}</p>
    <p class="chiffre__libelle">PDF archivés · {octets(stats?.octets_archives)}</p>
  </article>
  <article class="carte chiffre">
    <p class="chiffre__valeur">{stats?.administrateurs ?? 0}</p>
    <p class="chiffre__libelle">Administrateur{(stats?.administrateurs ?? 0) > 1 ? 's' : ''}</p>
  </article>
</section>

<section class="carte bloc" aria-labelledby="titre-alertes">
  <header class="bloc__entete">
    <div>
      <h2 id="titre-alertes">À traiter</h2>
      <p class="note">
        Abonnements qui finissent, boutiques sans propriétaire, quotas épuisés, suspensions et
        identités incomplètes.
      </p>
    </div>
    <button class="btn btn--discret" type="button" onclick={() => void charger()} disabled={chargement}>
      {chargement ? 'Chargement…' : 'Actualiser'}
    </button>
  </header>

  {#if erreur}<p class="alerte" role="alert">{erreur}</p>{/if}

  {#if alertes.length === 0 && !chargement}
    <p class="vide">Rien à signaler : toutes les boutiques sont en ordre.</p>
  {:else}
    <ul class="liste">
      {#each alertes as alerte (`${alerte.genre}-${alerte.shop_id}`)}
        <li class="alerte-ligne" class:alerte-ligne--forte={fortes.includes(alerte.genre)}>
          <div class="ligne__texte">
            <h3>
              <span
                class="etiquette"
                class:etiquette--danger={fortes.includes(alerte.genre)}
                class:etiquette--alerte={!fortes.includes(alerte.genre)}
                >{GENRES[alerte.genre]}</span
              >
              <a href={`/app/admin/boutiques/${alerte.shop_id}`}>{alerte.nom}</a>
              {#if alerte.echeance}
                <span class="note">{dateCourte(alerte.echeance)}</span>
              {/if}
            </h3>
            <p class="note">{alerte.detail}</p>
          </div>
          <a class="lien-discret" href={`/app/admin/boutiques/${alerte.shop_id}`}>Ouvrir la fiche</a>
        </li>
      {/each}
    </ul>
  {/if}
</section>
