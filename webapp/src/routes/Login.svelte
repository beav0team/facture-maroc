<script lang="ts">
  import { connexion, connexionGoogle, inscription, motDePasseOublie } from '../lib/session.svelte'
  import { estConfigure } from '../lib/supabase'

  type Mode = 'connexion' | 'inscription'

  /** Google/Supabase can send the failure back in the query or the hash. */
  function erreurDuRetour(): string | null {
    const requete = new URLSearchParams(location.search)
    const hash = new URLSearchParams(location.hash.replace(/^#/, ''))
    const code = hash.get('error') ?? requete.get('error')
    if (!code) return null
    const description = hash.get('error_description') ?? requete.get('error_description')
    if (code === 'access_denied') return 'Connexion annulée.'
    return description?.trim() || 'La connexion a échoué. Réessaie.'
  }

  const params = new URLSearchParams(location.search)
  const retour = erreurDuRetour()
  let mode = $state<Mode>(params.get('mode') === 'signup' ? 'inscription' : 'connexion')
  let email = $state('')
  let motDePasse = $state('')
  let enCours = $state(false)
  let erreur = $state<string | null>(retour)
  let info = $state<string | null>(null)

  // Ne pas laisser l'erreur dans l'URL : un rechargement la rejouerait.
  if (retour) history.replaceState({}, '', location.pathname)

  async function soumettre(event: SubmitEvent) {
    event.preventDefault()
    erreur = null
    info = null
    enCours = true
    try {
      const message =
        mode === 'connexion'
          ? await connexion(email.trim(), motDePasse)
          : await inscription(email.trim(), motDePasse)
      if (message) {
        // An informational message is not an error.
        if (message.startsWith('Compte créé')) info = message
        else erreur = message
      }
    } finally {
      enCours = false
    }
  }

  async function avecGoogle() {
    erreur = null
    info = null
    enCours = true
    const message = await connexionGoogle()
    if (message) {
      erreur = message
      enCours = false
      return
    }
    // On success the browser leaves for Google: keep the button disabled to
    // avoid a double submit.
  }

  async function oubli() {
    erreur = null
    info = null
    if (!email.trim()) {
      erreur = 'Saisis d’abord ton adresse e-mail.'
      return
    }
    const message = await motDePasseOublie(email.trim())
    if (message) erreur = message
    else info = 'Si un compte existe pour cette adresse, un lien de réinitialisation vient de partir.'
  }
</script>

<div class="centre">
  <main class="carte connexion">
    <header class="connexion__entete">
      <img src="/app/icons/icone.svg" alt="" width="44" height="44" />
      <div>
        <h1>FactureApp</h1>
        <p>Facturation conforme pour les commerces et TPE au Maroc.</p>
      </div>
    </header>

    {#if !estConfigure}
      <p class="alerte">
        Supabase n’est pas configuré sur cet environnement. Renseigne
        <code>VITE_SUPABASE_URL</code> et <code>VITE_SUPABASE_ANON_KEY</code> dans
        <code>webapp/.env</code>, puis relance <code>pnpm dev</code>.
      </p>
    {/if}

    <nav class="onglets" aria-label="Choisir une action">
      <button
        type="button"
        class:actif={mode === 'connexion'}
        onclick={() => {
          mode = 'connexion'
          erreur = null
          info = null
        }}>Connexion</button
      >
      <button
        type="button"
        class:actif={mode === 'inscription'}
        onclick={() => {
          mode = 'inscription'
          erreur = null
          info = null
        }}>Créer un compte</button
      >
    </nav>

    <button
      class="btn btn--google"
      type="button"
      onclick={avecGoogle}
      disabled={enCours || !estConfigure}
    >
      <svg viewBox="0 0 18 18" width="18" height="18" aria-hidden="true" focusable="false">
        <path
          fill="#4285F4"
          d="M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.92c1.7-1.57 2.68-3.88 2.68-6.62z"
        />
        <path
          fill="#34A853"
          d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.92-2.26c-.81.54-1.84.86-3.04.86-2.34 0-4.32-1.58-5.03-3.7H.96v2.33A9 9 0 0 0 9 18z"
        />
        <path
          fill="#FBBC05"
          d="M3.97 10.72a5.4 5.4 0 0 1 0-3.44V4.95H.96a9 9 0 0 0 0 8.1l3.01-2.33z"
        />
        <path
          fill="#EA4335"
          d="M9 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.58A9 9 0 0 0 .96 4.95l3.01 2.33C4.68 5.16 6.66 3.58 9 3.58z"
        />
      </svg>
      Continuer avec Google
    </button>

    <p class="ou"><span>ou par e-mail</span></p>

    <form onsubmit={soumettre}>
      <div class="champ">
        <label for="email">Adresse e-mail</label>
        <input
          id="email"
          type="email"
          autocomplete="email"
          required
          bind:value={email}
          placeholder="boutique@exemple.ma"
        />
      </div>

      <div class="champ">
        <label for="mdp">Mot de passe</label>
        <input
          id="mdp"
          type="password"
          autocomplete={mode === 'connexion' ? 'current-password' : 'new-password'}
          required
          minlength="6"
          bind:value={motDePasse}
        />
      </div>

      {#if erreur}<p class="alerte" role="alert">{erreur}</p>{/if}
      {#if info}<p class="alerte alerte--info" role="status">{info}</p>{/if}

      <button class="btn" type="submit" disabled={enCours || !estConfigure}>
        {enCours
          ? 'Patientez…'
          : mode === 'connexion'
            ? 'Se connecter'
            : 'Créer mon compte'}
      </button>

      {#if mode === 'connexion'}
        <button type="button" class="lien-discret" onclick={oubli}>
          Mot de passe oublié ?
        </button>
      {/if}
    </form>

    <p class="connexion__pied">
      Pas encore de compte ? C’est gratuit jusqu’à 5 documents facturés par mois, sans carte
      bancaire.
    </p>

    <p class="connexion__liens">
      <a href="/app/gratuit">Essayer sans compte</a>
      <span aria-hidden="true">·</span>
      <a href="/app/tarifs">Voir les tarifs</a>
    </p>
  </main>
</div>

<style>
  .connexion {
    width: min(440px, 100%);
    padding: 28px;
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .connexion__entete {
    display: flex;
    align-items: center;
    gap: 14px;
  }

  .connexion__entete h1 {
    font-size: 1.4rem;
  }

  .connexion__entete p {
    color: var(--ardoise);
    font-size: 0.875rem;
    margin-top: 2px;
  }

  .onglets {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 4px;
    padding: 4px;
    background: var(--papier);
    border: 1px solid var(--trait);
    border-radius: 12px;
  }

  .onglets button {
    min-height: 38px;
    border: 0;
    border-radius: 9px;
    background: transparent;
    color: var(--ardoise);
    font: inherit;
    font-weight: 600;
    cursor: pointer;
  }

  .onglets button.actif {
    background: var(--blanc);
    color: var(--nuit);
    box-shadow: 0 1px 2px rgb(11 27 77 / 12%);
  }

  .btn--google {
    width: 100%;
    background: var(--blanc);
    border-color: var(--trait);
    color: var(--nuit);
    font-weight: 600;
  }

  .btn--google:hover:not(:disabled) {
    background: var(--papier);
  }

  .ou {
    display: flex;
    align-items: center;
    gap: 12px;
    color: var(--ardoise);
    font-size: 0.8rem;
  }

  .ou::before,
  .ou::after {
    content: '';
    flex: 1;
    height: 1px;
    background: var(--trait);
  }

  form {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  form .btn {
    width: 100%;
    margin-top: 4px;
  }

  .connexion__pied {
    color: var(--ardoise);
    font-size: 0.82rem;
    text-align: center;
    border-top: 1px solid var(--trait);
    padding-top: 14px;
  }

  .connexion__liens {
    display: flex;
    justify-content: center;
    gap: 8px;
    font-size: 0.82rem;
    color: var(--ardoise);
  }

  code {
    font-size: 0.85em;
    background: rgb(11 27 77 / 6%);
    padding: 1px 4px;
    border-radius: 4px;
  }
</style>
