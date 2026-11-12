<script lang="ts">
  import { auth, chargerBoutique } from '../../lib/session.svelte'
  import { reglages } from '../../lib/reglages.svelte'
  import { BUCKET_LOGOS, cheminLogo, urlLogo } from '../../lib/storage'
  import { supabase } from '../../lib/supabase'
  import ReglagesPage from '../../lib/ui/ReglagesPage.svelte'

  let envoiLogo = $state(false)
  let messageLogo = $state<string | null>(null)
  let erreurLogo = $state<string | null>(null)

  // Read from the shop, not from the form: the upload writes the row directly,
  // so the preview must follow the row and not a value waiting for "Enregistrer".
  const logo = $derived(urlLogo(auth.boutique?.logo_path ?? null))

  async function envoyerLogo(evenement: Event) {
    const champ = evenement.target as HTMLInputElement
    const fichier = champ.files?.[0]
    champ.value = ''
    const boutique = auth.boutique
    if (!fichier || !supabase || !boutique) return

    erreurLogo = null
    messageLogo = null

    if (!fichier.type.startsWith('image/')) {
      erreurLogo = 'Le logo doit être une image (PNG, JPG, SVG…).'
      return
    }
    if (fichier.size > 2 * 1024 * 1024) {
      erreurLogo = 'Le logo ne doit pas dépasser 2 Mo.'
      return
    }

    const ancien = boutique.logo_path
    const chemin = cheminLogo(boutique.id, fichier.name)
    envoiLogo = true
    const envoi = await supabase.storage
      .from(BUCKET_LOGOS)
      .upload(chemin, fichier, { contentType: fichier.type, upsert: false })
    if (envoi.error) {
      envoiLogo = false
      erreurLogo = `Envoi du logo impossible : ${envoi.error.message}`
      return
    }

    const { error } = await supabase.from('shops').update({ logo_path: chemin }).eq('id', boutique.id)
    envoiLogo = false

    if (error) {
      erreurLogo = error.message
      return
    }

    // The old file is best-effort: a failure here must not undo the new logo.
    if (ancien && ancien !== chemin) {
      void supabase.storage.from(BUCKET_LOGOS).remove([ancien])
    }

    await chargerBoutique()
    messageLogo = 'Logo mis à jour.'
  }

  async function retirerLogo() {
    const boutique = auth.boutique
    if (!supabase || !boutique?.logo_path) return
    if (!confirm('Retirer le logo de vos documents ?')) return

    erreurLogo = null
    messageLogo = null
    const chemin = boutique.logo_path
    const { error } = await supabase.from('shops').update({ logo_path: null }).eq('id', boutique.id)
    if (error) {
      erreurLogo = error.message
      return
    }
    void supabase.storage.from(BUCKET_LOGOS).remove([chemin])
    await chargerBoutique()
    messageLogo = 'Logo retiré.'
  }
</script>

<ReglagesPage
  titre="Boutique et logo"
  texte="Votre identité imprimée sur les documents : nom, adresse, contacts et identifiants légaux."
  groupe="boutique"
>
  <section class="carte bloc">
    <h2>Identité</h2>
    <div class="grille-champs">
      <div class="champ champ--large">
        <label for="b-nom">Nom <span aria-hidden="true">*</span></label>
        <input
          id="b-nom"
          required
          bind:value={reglages.saisie.nom}
          autocomplete="organization"
        />
      </div>
      <div class="champ champ--large">
        <label for="b-adresse">Adresse</label>
        <textarea id="b-adresse" bind:value={reglages.saisie.adresse} rows="2"></textarea>
      </div>
      <div class="champ">
        <label for="b-tel">Téléphone</label>
        <input id="b-tel" type="tel" bind:value={reglages.saisie.telephone} autocomplete="tel" />
      </div>
      <div class="champ">
        <label for="b-email">E-mail</label>
        <input id="b-email" type="email" bind:value={reglages.saisie.email} autocomplete="email" />
      </div>
    </div>
  </section>

  <section class="carte bloc">
    <h2>Identifiants légaux</h2>
    <p class="bloc__texte">Imprimés sous votre adresse. Laissez vide ce qui ne s’applique pas.</p>
    <div class="grille-champs">
      <div class="champ">
        <label for="b-ice">ICE</label>
        <input id="b-ice" bind:value={reglages.saisie.ice} inputmode="numeric" />
      </div>
      <div class="champ">
        <label for="b-if">IF</label>
        <input id="b-if" bind:value={reglages.saisie.if_fiscal} />
      </div>
      <div class="champ">
        <label for="b-rc">RC</label>
        <input id="b-rc" bind:value={reglages.saisie.rc} />
      </div>
      <div class="champ">
        <label for="b-patente">Patente</label>
        <input id="b-patente" bind:value={reglages.saisie.patente} />
      </div>
      <div class="champ">
        <label for="b-cnss">CNSS</label>
        <input id="b-cnss" bind:value={reglages.saisie.cnss} />
      </div>
    </div>
  </section>

  <section class="carte bloc">
    <h2>Logo</h2>
    <p class="bloc__texte">
      Affiché en haut de vos documents. PNG, JPG ou SVG, 2 Mo maximum. Le logo
      s’enregistre immédiatement, sans le bouton du bas.
    </p>

    {#if messageLogo}
      <p class="alerte alerte--info" role="status">{messageLogo}</p>
    {/if}
    {#if erreurLogo}
      <p class="alerte" role="alert">{erreurLogo}</p>
    {/if}

    <div class="logo">
      {#if logo}
        <img src={logo} alt="Logo de la boutique" />
      {:else}
        <div class="logo__vide" aria-hidden="true">Aucun logo</div>
      {/if}
      <div class="logo__actions">
        <label class="btn btn--discret fichier">
          {envoiLogo ? 'Envoi…' : logo ? 'Remplacer' : 'Choisir une image'}
          <input type="file" accept="image/*" onchange={envoyerLogo} disabled={envoiLogo} />
        </label>
        {#if logo}
          <button class="lien-discret lien-discret--danger" type="button" onclick={() => void retirerLogo()}>
            Retirer
          </button>
        {/if}
      </div>
    </div>
  </section>
</ReglagesPage>

<style>
  .logo {
    display: flex;
    align-items: center;
    gap: 18px;
    flex-wrap: wrap;
  }

  .logo img {
    max-width: 180px;
    max-height: 90px;
    object-fit: contain;
    border: 1px solid var(--trait);
    border-radius: 10px;
    background: var(--blanc);
    padding: 8px;
  }

  .logo__vide {
    width: 180px;
    height: 90px;
    display: grid;
    place-items: center;
    border: 1px dashed var(--trait);
    border-radius: 10px;
    color: var(--ardoise);
    font-size: 0.85rem;
  }

  .logo__actions {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  .fichier input {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
  }
</style>
