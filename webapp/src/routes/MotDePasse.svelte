<script lang="ts">
  import { aller } from '../lib/router.svelte'
  import {
    auth,
    changerMotDePasse,
    deconnexion,
    terminerRecuperation,
  } from '../lib/session.svelte'
  import { validerNouveau } from '../lib/recuperation'

  // A recovery link opens a real session; without one the link was already
  // used, expired, or the browser dropped the fragment.
  const sessionOuverte = $derived(Boolean(auth.user))

  let motDePasse = $state('')
  let confirmation = $state('')
  let erreur = $state<string | null>(null)
  let enregistre = $state(false)
  let enCours = $state(false)

  async function soumettre(event: SubmitEvent) {
    event.preventDefault()
    erreur = null
    const validation = validerNouveau(motDePasse, confirmation)
    if ('erreur' in validation) {
      erreur = validation.erreur
      return
    }
    enCours = true
    try {
      const message = await changerMotDePasse(validation.motDePasse)
      if (message) {
        erreur = message
        return
      }
      enregistre = true
      motDePasse = ''
      confirmation = ''
    } finally {
      enCours = false
    }
  }

  async function entrer() {
    terminerRecuperation()
    aller('/', { remplacer: true })
  }

  async function recommencer() {
    await deconnexion()
    terminerRecuperation()
    aller('/', { remplacer: true })
  }
</script>

<div class="centre">
  <main class="carte recuperation">
    <header class="recuperation__entete">
      <img src="/app/icons/icone.svg" alt="" width="44" height="44" />
      <div>
        <h1>Nouveau mot de passe</h1>
        <p>
          {#if auth.user?.email}
            Compte {auth.user.email}
          {:else}
            FactureApp
          {/if}
        </p>
      </div>
    </header>

    {#if !sessionOuverte}
      <p class="alerte" role="alert">
        Ce lien de réinitialisation a expiré ou a déjà servi. Demande-en un
        nouveau depuis l’écran de connexion.
      </p>
      <button class="btn" type="button" onclick={recommencer}>
        Revenir à la connexion
      </button>
    {:else if enregistre}
      <p class="alerte alerte--info" role="status">
        Mot de passe enregistré. Il sera demandé à la prochaine connexion.
      </p>
      <button class="btn" type="button" onclick={entrer}>
        Entrer dans l’application
      </button>
    {:else}
      <p class="recuperation__aide">
        Choisis un mot de passe d’au moins 6 caractères. Il remplace l’ancien
        immédiatement.
      </p>

      <form onsubmit={soumettre}>
        <div class="champ">
          <label for="nouveau">Nouveau mot de passe</label>
          <input
            id="nouveau"
            type="password"
            autocomplete="new-password"
            required
            minlength="6"
            bind:value={motDePasse}
          />
        </div>

        <div class="champ">
          <label for="confirmation">Confirme le mot de passe</label>
          <input
            id="confirmation"
            type="password"
            autocomplete="new-password"
            required
            minlength="6"
            bind:value={confirmation}
          />
        </div>

        {#if erreur}<p class="alerte" role="alert">{erreur}</p>{/if}

        <button class="btn" type="submit" disabled={enCours}>
          {enCours ? 'Enregistrement…' : 'Enregistrer le mot de passe'}
        </button>
      </form>

      <button type="button" class="lien-discret" onclick={recommencer}>
        Ce n’est pas mon compte
      </button>
    {/if}
  </main>
</div>

<style>
  .recuperation {
    width: min(440px, 100%);
    padding: 28px;
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .recuperation__entete {
    display: flex;
    align-items: center;
    gap: 14px;
  }

  .recuperation__entete h1 {
    font-size: 1.3rem;
  }

  .recuperation__entete p {
    color: var(--ardoise);
    font-size: 0.875rem;
    margin-top: 2px;
  }

  .recuperation__aide {
    color: var(--ardoise);
    font-size: 0.875rem;
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
</style>
