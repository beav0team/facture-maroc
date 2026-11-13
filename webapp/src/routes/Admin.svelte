<script lang="ts">
  import { onMount } from 'svelte'
  import type { StatistiquesAdmin } from '../lib/admin'
  import { route } from '../lib/router.svelte'
  import { auth } from '../lib/session.svelte'
  import { supabase } from '../lib/supabase'
  import Barre from '../lib/ui/Barre.svelte'
  import Apercu from './admin/Apercu.svelte'
  import Boutiques from './admin/Boutiques.svelte'
  import Comptes from './admin/Comptes.svelte'
  import Fiche from './admin/Fiche.svelte'
  import Journal from './admin/Journal.svelte'

  let admin = $state(false)
  let verifie = $state(false)
  let chargement = $state(true)
  let stats = $state<StatistiquesAdmin | null>(null)
  let erreur = $state<string | null>(null)

  const PREFIXE_FICHE = '/admin/boutiques/'

  const liens = [
    { chemin: '/admin', libelle: 'Vue d’ensemble' },
    { chemin: '/admin/boutiques', libelle: 'Boutiques' },
    { chemin: '/admin/comptes', libelle: 'Comptes' },
    { chemin: '/admin/journal', libelle: 'Journal' },
  ] as const

  /** `/admin/boutiques/<uuid>`; the id is a uuid so no decoding is needed. */
  const ficheId = $derived(
    route.chemin.startsWith(PREFIXE_FICHE) ? route.chemin.slice(PREFIXE_FICHE.length) : '',
  )

  const section = $derived(
    route.chemin === '/admin/comptes'
      ? 'comptes'
      : route.chemin === '/admin/journal'
        ? 'journal'
        : route.chemin === '/admin/boutiques' || ficheId
          ? 'boutiques'
          : 'apercu',
  )

  function actif(chemin: string): boolean {
    if (chemin === '/admin') return section === 'apercu'
    return route.chemin === chemin || route.chemin.startsWith(`${chemin}/`)
  }

  const href = (chemin: string) => `/app${chemin}`

  onMount(() => {
    void demarrer()
  })

  async function demarrer() {
    if (!supabase || !auth.user) {
      chargement = false
      verifie = true
      return
    }
    const { data, error } = await supabase.rpc('est_admin_plateforme')
    verifie = true
    if (error) {
      erreur = error.message
      chargement = false
      return
    }
    admin = data === true
    if (admin) await chargerStats()
    else chargement = false
  }

  async function chargerStats() {
    if (!supabase) return
    chargement = true
    const { data, error } = await supabase.rpc('admin_statistiques')
    chargement = false
    if (error) {
      erreur = error.message
      return
    }
    stats = data as StatistiquesAdmin
  }
</script>

<div class="page">
  <Barre />

  <main class="console">
    {#if !verifie || (chargement && !stats)}
      <p class="note">Chargement…</p>
    {:else if !admin}
      <section class="carte bloc">
        <h1>Administration</h1>
        <p class="note">Cet espace est réservé aux administrateurs de la plateforme.</p>
        {#if erreur}<p class="alerte" role="alert">{erreur}</p>{/if}
      </section>
    {:else}
      <section class="console__entete">
        <div>
          {#if ficheId}
            <!-- La fiche porte son propre titre de boutique : deux h1 sur la
                 page brouilleraient la navigation au lecteur d'écran. -->
            <p class="console__surtitre">Administration</p>
          {:else}
            <h1>Administration</h1>
          {/if}
          <p class="note">
            {stats?.boutiques ?? 0} boutique{(stats?.boutiques ?? 0) > 1 ? 's' : ''} ·
            {stats?.utilisateurs ?? 0} compte{(stats?.utilisateurs ?? 0) > 1 ? 's' : ''} ·
            {stats?.essentiel ?? 0} en Essentiel · {stats?.pro ?? 0} en Pro · {stats?.administrateurs ?? 0} administrateur{(stats
              ?.administrateurs ?? 0) > 1
              ? 's'
              : ''}
          </p>
        </div>
      </section>

      {#if erreur}<p class="alerte" role="alert">{erreur}</p>{/if}

      <nav class="console__nav" aria-label="Sections de la console">
        {#each liens as lien}
          <a
            href={href(lien.chemin)}
            class:actif={actif(lien.chemin)}
            aria-current={actif(lien.chemin) ? 'page' : undefined}>{lien.libelle}</a
          >
        {/each}
      </nav>

      {#if section === 'apercu'}
        <Apercu {stats} />
      {:else if section === 'comptes'}
        <Comptes />
      {:else if section === 'journal'}
        <Journal />
      {:else if ficheId}
        <Fiche id={ficheId} />
      {:else}
        <Boutiques />
      {/if}
    {/if}
  </main>
</div>

<style>
  .page {
    min-height: 100dvh;
    display: flex;
    flex-direction: column;
  }
</style>
