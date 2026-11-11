<script lang="ts">
  import { onMount } from 'svelte'
  import { auth, demarrer } from './lib/session.svelte'
  import { route, demarrerRouteur } from './lib/router.svelte'
  import Login from './routes/Login.svelte'
  import Accueil from './routes/Accueil.svelte'
  import Introuvable from './routes/Introuvable.svelte'

  onMount(() => {
    demarrerRouteur()
    void demarrer()
  })
</script>

{#if !auth.pret}
  <div class="chargement">Chargement…</div>
{:else if !auth.user}
  <Login />
{:else if route.chemin === '/'}
  <Accueil />
{:else}
  <Introuvable />
{/if}
