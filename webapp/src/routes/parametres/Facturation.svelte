<script lang="ts">
  import { DESIGNS, designDepuisId } from '../../lib/document'
  import { CONDITIONS, reglages } from '../../lib/reglages.svelte'
  import ReglagesPage from '../../lib/ui/ReglagesPage.svelte'

  function majDesign(valeur: string) {
    reglages.saisie.design_defaut = designDepuisId(valeur)
  }
</script>

<ReglagesPage
  titre="Facturation"
  texte="Les valeurs proposées par défaut dans un nouveau document. Chaque document peut ensuite les changer."
  groupe="facturation"
>
  <section class="carte bloc">
    <h2>TVA</h2>
    <div class="grille-champs">
      <div class="champ">
        <label for="b-tva">TVA par défaut (%)</label>
        <input id="b-tva" inputmode="decimal" bind:value={reglages.saisie.tva_defaut} />
        <p class="aide">Appliquée aux lignes d’un nouveau document, puis modifiable ligne par ligne.</p>
      </div>
      <div class="champ">
        <label for="b-conditions">Conditions de paiement</label>
        <select id="b-conditions" bind:value={reglages.saisie.conditions_paiement}>
          {#each CONDITIONS as condition}
            <option value={condition.valeur}>{condition.libelle}</option>
          {/each}
        </select>
      </div>
      <label class="champ champ--case champ--large">
        <input type="checkbox" bind:checked={reglages.saisie.tva_non_applicable} />
        <span>TVA non applicable (auto-entrepreneur)</span>
      </label>
      <label class="champ champ--case champ--large">
        <input type="checkbox" bind:checked={reglages.saisie.prix_ttc} />
        <span>Je saisis mes prix TTC, TVA comprise</span>
      </label>
    </div>
  </section>

  <section class="carte bloc">
    <h2>Mentions et modèle</h2>
    <div class="grille-champs">
      <div class="champ champ--large">
        <label for="b-clause">Clause de retard de paiement</label>
        <textarea id="b-clause" bind:value={reglages.saisie.clause_retard} rows="2"></textarea>
        <p class="aide">
          Imprimée sous les totaux quand le document n’est pas au comptant. Laissez
          vide pour n’imprimer aucune clause.
        </p>
      </div>

      <label class="champ champ--case champ--large">
        <input type="checkbox" bind:checked={reglages.saisie.montant_lettres} />
        <span>Imprimer le montant arrêté en toutes lettres</span>
      </label>
      <p class="bloc__texte">
        Sous les totaux : « ARRÊTÉE LA PRÉSENTE FACTURE À LA SOMME DE : … ». Un
        usage constant au Maroc, attendu par les administrations. Les documents
        déjà émis gardent leur PDF archivé.
      </p>

      <div class="champ champ--large">
        <label for="b-design">Modèle par défaut</label>
        <select
          id="b-design"
          value={reglages.saisie.design_defaut}
          onchange={(e) => majDesign(e.currentTarget.value)}
        >
          {#each Object.entries(DESIGNS) as [idDesign, libelle]}
            <option value={idDesign}>{libelle}</option>
          {/each}
        </select>
        <span class="aide">
          Cinq modèles A4 et un ticket de caisse 80 mm. Chaque document garde le
          modèle avec lequel il a été émis.
        </span>
      </div>
    </div>
  </section>
</ReglagesPage>
