<script lang="ts">
  import { ORDRE_TYPES, TYPES_DOCUMENT } from '../../lib/document'
  import { reglages } from '../../lib/reglages.svelte'
  import ReglagesPage from '../../lib/ui/ReglagesPage.svelte'

  function majPrefixe(type: string, valeur: string) {
    reglages.saisie.prefixes = { ...reglages.saisie.prefixes, [type]: valeur }
  }
</script>

<ReglagesPage
  titre="Numérotation"
  texte="Chaque type de document a sa propre série. Le numéro est pris à l’émission, jamais à l’enregistrement d’un brouillon."
  groupe="numerotation"
>
  <section class="carte bloc">
    <h2>Séries</h2>
    <p class="bloc__texte">
      Le numéro complet ressemble à <strong>F-20261111-0001</strong> : préfixe,
      date du jour, rang. Laissez vide pour reprendre le préfixe par défaut entre
      parenthèses.
    </p>

    <div class="grille-champs grille-champs--prefixes">
      {#each ORDRE_TYPES as type}
        <div class="champ">
          <label for={`b-prefixe-${type}`}>
            {TYPES_DOCUMENT[type].libelle}
            <span class="aide">({TYPES_DOCUMENT[type].prefixe})</span>
          </label>
          <input
            id={`b-prefixe-${type}`}
            value={reglages.saisie.prefixes[type] ?? ''}
            placeholder={TYPES_DOCUMENT[type].prefixe}
            oninput={(e) => majPrefixe(type, e.currentTarget.value)}
          />
        </div>
      {/each}
    </div>

    <label class="champ champ--case">
      <input type="checkbox" bind:checked={reglages.saisie.numerotation_annuelle} />
      <span>Repartir de 0001 chaque année</span>
    </label>
    <p class="aide">
      Décochez pour une série continue. Un numéro déjà attribué n’est jamais
      réutilisé, même après une remise à zéro.
    </p>
  </section>
</ReglagesPage>
