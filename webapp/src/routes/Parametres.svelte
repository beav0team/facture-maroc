<script lang="ts">
  import { auth } from '../lib/session.svelte'
  import { DESIGNS, designDepuisId } from '../lib/document'
  import Barre from '../lib/ui/Barre.svelte'
  import SansBoutique from '../lib/ui/SansBoutique.svelte'
  import '../lib/ui/reglages.css'

  // Summaries read the shop, not a local copy: they must be right the moment
  // the row changes, including on the way back from a group screen.
  const boutique = $derived(auth.boutique)

  const resumeBoutique = $derived.by(() => {
    if (!boutique) return ''
    const morceaux = [boutique.nom]
    if (boutique.ice) morceaux.push(`ICE ${boutique.ice}`)
    if (boutique.logo_path) morceaux.push('logo')
    return morceaux.join(' · ')
  })

  const resumeFacturation = $derived.by(() => {
    if (!boutique) return ''
    const taux = boutique.tva_non_applicable
      ? 'TVA non applicable'
      : `TVA ${String(boutique.tva_defaut).replace('.', ',')} %`
    const modele = DESIGNS[designDepuisId(boutique.design_defaut)]
    const lettres = boutique.montant_lettres === false ? 'sans montant en lettres' : 'montant en lettres'
    return `${taux} · ${modele} · ${lettres}`
  })

  const resumeNumerotation = $derived.by(() => {
    if (!boutique) return ''
    const prefixes = Object.values(boutique.prefixes ?? {}).filter((p) => p.trim() !== '')
    return [
      prefixes.length > 0 ? `${prefixes.length} préfixe${prefixes.length > 1 ? 's' : ''} personnalisé${prefixes.length > 1 ? 's' : ''}` : 'préfixes par défaut',
      boutique.numerotation_annuelle ? 'remise à zéro chaque année' : 'série continue',
    ].join(' · ')
  })

  const resumeBanque = $derived.by(() => {
    if (!boutique) return ''
    const rib = (boutique.rib ?? '').replace(/\s/g, '')
    if (rib === '') return 'espèces uniquement'
    const fin = rib.slice(-4)
    return `${boutique.banque ?? 'Banque non nommée'} · RIB •••• ${fin}`
  })
</script>

<div class="reglages">
  <Barre />

  <main class="contenu">
    {#if !auth.boutique}
      <SansBoutique />
    {:else}
      <section class="entete">
        <h1>Paramètres</h1>
        <p class="entete__texte">
          Cinq groupes. Ce que vous modifiez ici s’imprime sur les documents
          émis ensuite ; une facture déjà émise garde son PDF archivé.
        </p>
      </section>

      <nav class="index" aria-label="Groupes de réglages">
        <a class="carte index__lien" href="/app/parametres/boutique">
          <span class="index__texte">
            <span class="index__titre">Boutique et logo</span>
            <span class="index__resume">{resumeBoutique}</span>
          </span>
          <span class="index__fleche" aria-hidden="true">›</span>
        </a>

        <a class="carte index__lien" href="/app/parametres/facturation">
          <span class="index__texte">
            <span class="index__titre">Facturation</span>
            <span class="index__resume">{resumeFacturation}</span>
          </span>
          <span class="index__fleche" aria-hidden="true">›</span>
        </a>

        <a class="carte index__lien" href="/app/parametres/numerotation">
          <span class="index__texte">
            <span class="index__titre">Numérotation</span>
            <span class="index__resume">{resumeNumerotation}</span>
          </span>
          <span class="index__fleche" aria-hidden="true">›</span>
        </a>

        <a class="carte index__lien" href="/app/parametres/banque">
          <span class="index__texte">
            <span class="index__titre">Coordonnées bancaires</span>
            <span class="index__resume">{resumeBanque}</span>
          </span>
          <span class="index__fleche" aria-hidden="true">›</span>
        </a>

        <a class="carte index__lien" href="/app/parametres/donnees">
          <span class="index__texte">
            <span class="index__titre">Données</span>
            <span class="index__resume">Import d’une sauvegarde Android · export de vos données</span>
          </span>
          <span class="index__fleche" aria-hidden="true">›</span>
        </a>
      </nav>
    {/if}
  </main>
</div>
