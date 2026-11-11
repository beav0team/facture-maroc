<script lang="ts">
  import { auth, creerBoutique } from '../lib/session.svelte'
  import Barre from '../lib/ui/Barre.svelte'

  let nom = $state('')
  let erreur = $state<string | null>(null)
  let enCours = $state(false)

  const raccourcis = [
    {
      lien: '/app/clients',
      titre: 'Clients',
      texte: 'Les fiches clients et fournisseurs, avec ICE, IF et coordonnées.',
      action: 'Ouvrir le carnet',
    },
    {
      lien: '/app/articles',
      titre: 'Articles',
      texte: 'Produits et services, prix, dossiers et stock.',
      action: 'Ouvrir le catalogue',
    },
    {
      lien: '/app/parametres',
      titre: 'Paramètres',
      texte: 'Identifiants légaux, coordonnées bancaires et logo.',
      action: 'Régler la boutique',
    },
  ]

  async function creer(event: SubmitEvent) {
    event.preventDefault()
    erreur = null
    enCours = true
    try {
      erreur = await creerBoutique(nom.trim())
    } finally {
      enCours = false
    }
  }
</script>

<div class="page">
  <Barre />

  <main class="contenu">
    {#if !auth.boutique}
      <section class="carte bloc">
        <h1>Créez votre boutique</h1>
        <p class="bloc__texte">
          C’est le nom qui figurera sur vos documents. Vous pourrez ajouter l’ICE,
          l’IF, le RC et votre logo juste après.
        </p>
        <form onsubmit={creer}>
          <div class="champ">
            <label for="nom">Nom de la boutique</label>
            <input
              id="nom"
              required
              bind:value={nom}
              placeholder="Ex. Atelier Atlas"
              autocomplete="organization"
            />
          </div>
          {#if erreur}<p class="alerte" role="alert">{erreur}</p>{/if}
          <button class="btn" type="submit" disabled={enCours || nom.trim().length === 0}>
            {enCours ? 'Création…' : 'Créer ma boutique'}
          </button>
        </form>
      </section>
    {:else}
      <section class="entete">
        <div>
          <p class="entete__surtitre">Bonjour</p>
          <h1>{auth.boutique.nom}</h1>
        </div>
        <p class="etiquette">Plan gratuit · 5 factures / mois</p>
      </section>

      <section class="carte vide">
        <h2>Aucun document pour l’instant</h2>
        <p class="bloc__texte">
          L’éditeur de factures arrive à la prochaine étape. En attendant, votre
          compte, votre boutique et vos catalogues sont prêts.
        </p>
        <div class="vide__actions">
          <button class="btn" type="button" disabled>Nouvelle facture</button>
          <span class="note">Bientôt disponible</span>
        </div>
      </section>

      <section class="grille">
        {#each raccourcis as raccourci}
          <a class="carte raccourci" href={raccourci.lien}>
            <h3>{raccourci.titre}</h3>
            <p class="bloc__texte">{raccourci.texte}</p>
            <span class="raccourci__action">{raccourci.action} →</span>
          </a>
        {/each}
      </section>

      <section class="grille">
        <article class="carte info">
          <h3>Identifiants légaux</h3>
          <dl>
            <div><dt>ICE</dt><dd>{auth.boutique.ice ?? 'à compléter'}</dd></div>
            <div><dt>IF</dt><dd>{auth.boutique.if_fiscal ?? 'à compléter'}</dd></div>
          </dl>
        </article>
        <article class="carte info">
          <h3>Compte</h3>
          <dl>
            <div><dt>E-mail</dt><dd>{auth.user?.email ?? '—'}</dd></div>
            <div><dt>Boutique</dt><dd>{auth.boutique.nom}</dd></div>
          </dl>
        </article>
      </section>
    {/if}
  </main>
</div>

<style>
  .page {
    min-height: 100dvh;
    display: flex;
    flex-direction: column;
  }

  .contenu {
    width: min(960px, 100%);
    margin: 0 auto;
    padding: clamp(24px, 5vw, 48px) clamp(16px, 4vw, 24px);
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  .entete {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
  }

  .entete__surtitre {
    color: var(--ardoise);
    font-size: 0.85rem;
    text-transform: uppercase;
    letter-spacing: 0.08em;
  }

  .entete h1 {
    font-size: clamp(1.6rem, 3vw, 2.1rem);
    margin-top: 2px;
  }

  .bloc {
    padding: 28px;
    display: flex;
    flex-direction: column;
    gap: 16px;
    max-width: 560px;
  }

  .bloc h1 {
    font-size: 1.5rem;
  }

  .bloc__texte {
    color: var(--ardoise);
  }

  .bloc form {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .vide {
    padding: 32px 28px;
    display: flex;
    flex-direction: column;
    gap: 10px;
    align-items: flex-start;
  }

  .vide h2 {
    font-size: 1.2rem;
  }

  .vide__actions {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 4px;
  }

  .note {
    color: var(--ardoise);
    font-size: 0.85rem;
  }

  .grille {
    display: grid;
    gap: 16px;
    grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  }

  .raccourci {
    padding: 20px;
    display: flex;
    flex-direction: column;
    gap: 8px;
    color: inherit;
    text-decoration: none;
    transition: border-color 120ms ease, transform 120ms ease;
  }

  .raccourci:hover {
    border-color: var(--bleu);
    transform: translateY(-1px);
  }

  .raccourci h3 {
    font-size: 1.05rem;
  }

  .raccourci__action {
    margin-top: auto;
    padding-top: 8px;
    color: var(--bleu);
    font-weight: 600;
    font-size: 0.9rem;
  }

  .info {
    padding: 20px;
  }

  .info dl {
    margin: 10px 0 0;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .info dl > div {
    display: flex;
    justify-content: space-between;
    gap: 12px;
  }

  .info dt {
    color: var(--ardoise);
  }

  .info dd {
    margin: 0;
    text-align: right;
    font-weight: 600;
  }
</style>
