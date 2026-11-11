<script lang="ts">
  import { auth, creerBoutique, deconnexion } from '../lib/session.svelte'

  let nom = $state('')
  let erreur = $state<string | null>(null)
  let enCours = $state(false)

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
  <header class="barre">
    <a class="barre__marque" href="/app/">
      <img src="/app/icons/icone.svg" alt="" width="28" height="28" />
      <span>FactureApp</span>
    </a>
    <div class="barre__droite">
      {#if auth.boutique}
        <span class="etiquette">{auth.boutique.nom}</span>
      {/if}
      <button class="lien-discret" type="button" onclick={() => void deconnexion()}>
        Se déconnecter
      </button>
    </div>
  </header>

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
          compte et votre boutique sont prêts.
        </p>
        <div class="vide__actions">
          <button class="btn" type="button" disabled>Nouvelle facture</button>
          <span class="note">Bientôt disponible</span>
        </div>
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

  .barre {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 10px clamp(16px, 4vw, 32px);
    background: var(--blanc);
    border-bottom: 1px solid var(--trait);
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

  .barre__droite {
    display: flex;
    align-items: center;
    gap: 14px;
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
    margin-top: 6px;
  }

  .note {
    color: var(--ardoise);
    font-size: 0.85rem;
  }

  .grille {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
    gap: 16px;
  }

  .info {
    padding: 20px;
  }

  .info h3 {
    font-size: 1rem;
    margin-bottom: 12px;
  }

  dl {
    margin: 0;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  dl div {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    border-bottom: 1px dashed var(--trait);
    padding-bottom: 6px;
  }

  dt {
    color: var(--ardoise);
    font-size: 0.9rem;
  }

  dd {
    margin: 0;
    font-weight: 600;
    text-align: right;
    overflow-wrap: anywhere;
  }
</style>
