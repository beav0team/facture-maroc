<script lang="ts">
  import { reglages } from '../../lib/reglages.svelte'
  import ReglagesPage from '../../lib/ui/ReglagesPage.svelte'

  const ribChiffres = $derived(reglages.saisie.rib.replace(/\D/g, '').length)
</script>

<ReglagesPage
  titre="Coordonnées bancaires"
  texte="Imprimées sur les documents à payer par virement. Laissez vide pour un paiement en espèces."
  groupe="banque"
>
  <section class="carte bloc">
    <h2>Compte à créditer</h2>
    <div class="grille-champs">
      <div class="champ">
        <label for="b-banque">Banque</label>
        <input id="b-banque" bind:value={reglages.saisie.banque} />
      </div>
      <div class="champ">
        <label for="b-titulaire">Titulaire du compte</label>
        <input id="b-titulaire" bind:value={reglages.saisie.rib_titulaire} />
      </div>
      <div class="champ champ--large">
        <label for="b-rib">RIB</label>
        <input
          id="b-rib"
          bind:value={reglages.saisie.rib}
          inputmode="numeric"
          placeholder="24 chiffres"
        />
        {#if ribChiffres > 0}
          <p class="aide" class:aide--alerte={ribChiffres !== 24}>
            {ribChiffres} chiffre{ribChiffres > 1 ? 's' : ''}
            {ribChiffres === 24 ? ' · longueur correcte' : ' · un RIB marocain en compte 24'}
          </p>
        {/if}
      </div>
    </div>
  </section>
</ReglagesPage>
