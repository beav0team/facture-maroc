<script lang="ts">
  import { route } from '../router.svelte'
  import { auth, deconnexion } from '../session.svelte'

  const liens = [
    { chemin: '/', libelle: 'Accueil' },
    { chemin: '/documents', libelle: 'Documents' },
    { chemin: '/clients', libelle: 'Clients' },
    { chemin: '/articles', libelle: 'Articles' },
    { chemin: '/parametres', libelle: 'Paramètres' },
  ]

  function actif(chemin: string): boolean {
    if (chemin === '/') return route.chemin === '/'
    return route.chemin === chemin || route.chemin.startsWith(`${chemin}/`)
  }

  const href = (chemin: string) => (chemin === '/' ? '/app/' : `/app${chemin}`)
</script>

<header class="barre">
  <a class="barre__marque" href="/app/">
    <img src="/app/icons/icone.svg" alt="" width="28" height="28" />
    <span>FactureApp</span>
  </a>

  <nav class="barre__nav" aria-label="Navigation principale">
    {#each liens as lien}
      <a
        href={href(lien.chemin)}
        class:actif={actif(lien.chemin)}
        aria-current={actif(lien.chemin) ? 'page' : undefined}>{lien.libelle}</a
      >
    {/each}
  </nav>

  <div class="barre__droite">
    {#if auth.boutique}
      <span class="etiquette">{auth.boutique.nom}</span>
    {/if}
    <button class="lien-discret" type="button" onclick={() => void deconnexion()}>
      Se déconnecter
    </button>
  </div>
</header>

<style>
  .barre {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 10px clamp(16px, 4vw, 32px);
    background: var(--blanc);
    border-bottom: 1px solid var(--trait);
    flex-wrap: wrap;
  }

  .barre__marque {
    display: inline-flex;
    align-items: center;
    gap: 10px;
    color: var(--nuit);
    font-family: var(--titre);
    font-weight: 700;
    font-size: 1.05rem;
    text-decoration: none;
  }

  .barre__nav {
    display: flex;
    align-items: center;
    gap: 2px;
    overflow-x: auto;
    scrollbar-width: none;
    order: 3;
    width: 100%;
  }

  .barre__nav::-webkit-scrollbar {
    display: none;
  }

  .barre__nav a {
    padding: 6px 12px;
    border-radius: 999px;
    color: var(--ardoise);
    font-size: 0.92rem;
    font-weight: 600;
    text-decoration: none;
    white-space: nowrap;
  }

  .barre__nav a:hover {
    background: var(--papier);
    color: var(--nuit);
  }

  .barre__nav a.actif {
    background: #eef2ff;
    color: var(--bleu-fonce);
  }

  .barre__droite {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-left: auto;
  }

  @media (min-width: 720px) {
    .barre__nav {
      order: 0;
      width: auto;
      margin-left: 12px;
    }
  }
</style>
