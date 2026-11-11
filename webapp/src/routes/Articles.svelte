<script lang="ts">
  import { onMount } from 'svelte'
  import {
    dossiersDe,
    etatStock,
    filtrerArticles,
    libellePrix,
    libelleStock,
    valeursArticle,
    type Article,
    type SaisieArticle,
  } from '../lib/catalogue'
  import { auth } from '../lib/session.svelte'
  import { supabase } from '../lib/supabase'
  import Barre from '../lib/ui/Barre.svelte'
  import SansBoutique from '../lib/ui/SansBoutique.svelte'

  function saisieVide(): SaisieArticle {
    return {
      libelle: '',
      prix: '',
      unite: '',
      dossier: '',
      prix_achat: '',
      code_barre: '',
      service: false,
      stock: '',
      seuil_rea: '',
    }
  }

  let articles = $state<Article[]>([])
  let chargement = $state(true)
  let erreur = $state<string | null>(null)
  let message = $state<string | null>(null)
  let recherche = $state('')
  let dossierFiltre = $state<string | null>(null)
  let ouvert = $state(false)
  let edition = $state<Article | null>(null)
  let saisie = $state<SaisieArticle>(saisieVide())
  let enregistrement = $state(false)

  const dossiers = $derived(dossiersDe(articles))
  const visibles = $derived(filtrerArticles(articles, recherche, dossierFiltre))
  const nombreServices = $derived(articles.filter((article) => article.stock === null).length)

  onMount(async () => {
    await charger()
  })

  async function charger() {
    if (!supabase || !auth.boutique) {
      chargement = false
      return
    }
    chargement = true
    erreur = null
    const { data, error } = await supabase
      .from('articles')
      .select('*')
      .eq('shop_id', auth.boutique.id)
      .order('libelle', { ascending: true })
    chargement = false
    if (error) {
      erreur = error.message
      return
    }
    articles = (data as Article[]) ?? []
  }

  function ouvrirNouveau() {
    edition = null
    saisie = saisieVide()
    saisie.dossier = dossierFiltre && dossierFiltre !== '' ? dossierFiltre : ''
    ouvert = true
    message = null
  }

  function ouvrirEdition(article: Article) {
    edition = article
    saisie = {
      libelle: article.libelle,
      prix: String(article.prix).replace('.', ','),
      unite: article.unite,
      dossier: article.dossier,
      prix_achat: article.prix_achat === 0 ? '' : String(article.prix_achat).replace('.', ','),
      code_barre: article.code_barre,
      service: article.stock === null,
      stock: article.stock === null ? '' : String(article.stock),
      seuil_rea: article.seuil_rea === null ? '' : String(article.seuil_rea),
    }
    ouvert = true
    message = null
  }

  function fermer() {
    ouvert = false
    edition = null
  }

  async function enregistrer(event: SubmitEvent) {
    event.preventDefault()
    if (!supabase || !auth.boutique) return

    const valeurs = valeursArticle(saisie)
    if ('erreur' in valeurs) {
      erreur = valeurs.erreur
      return
    }

    erreur = null
    enregistrement = true
    const requete = edition
      ? supabase.from('articles').update(valeurs).eq('id', edition.id)
      : supabase.from('articles').insert({ ...valeurs, shop_id: auth.boutique.id })
    const { error } = await requete
    enregistrement = false

    if (error) {
      erreur =
        error.code === '23505'
          ? 'Ce code-barres est déjà utilisé par un autre article.'
          : error.message
      return
    }

    if (dossierFiltre !== null && valeurs.dossier !== dossierFiltre) dossierFiltre = null
    message = edition ? 'Article mis à jour.' : 'Article ajouté.'
    fermer()
    await charger()
  }

  async function supprimer(article: Article) {
    if (!supabase) return
    if (!confirm(`Supprimer « ${article.libelle} » du catalogue ?`)) return
    const { error } = await supabase.from('articles').delete().eq('id', article.id)
    if (error) {
      erreur = error.message
      return
    }
    message = 'Article supprimé.'
    await charger()
  }

  function changerDossier(event: Event) {
    const valeur = (event.target as HTMLSelectElement).value
    dossierFiltre = valeur === '__tous__' ? null : valeur
  }
</script>

<div class="page">
  <Barre />

  <main class="contenu">
    {#if !auth.boutique}
      <SansBoutique />
    {:else}
      <section class="entete">
        <div>
          <h1>Articles et services</h1>
          <p class="entete__texte">
            {articles.length} article{articles.length > 1 ? 's' : ''} ·
            {nombreServices} service{nombreServices > 1 ? 's' : ''}
          </p>
        </div>
        <button class="btn" type="button" onclick={ouvrirNouveau}>Nouvel article</button>
      </section>

      {#if message}
        <p class="alerte alerte--info" role="status">{message}</p>
      {/if}
      {#if erreur}
        <p class="alerte" role="alert">{erreur}</p>
      {/if}

      {#if ouvert}
        <section class="carte formulaire">
          <div class="formulaire__entete">
            <h2>{edition ? 'Modifier l’article' : 'Nouvel article'}</h2>
            <button class="lien-discret" type="button" onclick={fermer}>Fermer</button>
          </div>

          <form onsubmit={enregistrer}>
            <div class="grille-champs">
              <div class="champ champ--large">
                <label for="a-libelle">Libellé <span aria-hidden="true">*</span></label>
                <input id="a-libelle" required bind:value={saisie.libelle} placeholder="Ex. Huile 5W30" />
              </div>
              <div class="champ">
                <label for="a-prix">Prix de vente (DH) <span aria-hidden="true">*</span></label>
                <input id="a-prix" required inputmode="decimal" bind:value={saisie.prix} placeholder="0,00" />
              </div>
              <div class="champ">
                <label for="a-unite">Unité</label>
                <input id="a-unite" bind:value={saisie.unite} placeholder="bidon, heure, kg…" list="unites" />
                <datalist id="unites">
                  <option value="unité"></option>
                  <option value="heure"></option>
                  <option value="jour"></option>
                  <option value="kg"></option>
                  <option value="m"></option>
                  <option value="litre"></option>
                </datalist>
              </div>
              <div class="champ">
                <label for="a-dossier">Dossier</label>
                <input id="a-dossier" bind:value={saisie.dossier} placeholder="Sans dossier" list="dossiers" />
                <datalist id="dossiers">
                  {#each dossiers as dossier}
                    <option value={dossier}></option>
                  {/each}
                </datalist>
              </div>
              <div class="champ">
                <label for="a-achat">Prix d’achat HT (DH)</label>
                <input id="a-achat" inputmode="decimal" bind:value={saisie.prix_achat} placeholder="0,00" />
              </div>
              <div class="champ">
                <label for="a-code">Code-barres</label>
                <input id="a-code" bind:value={saisie.code_barre} inputmode="numeric" />
              </div>

              <label class="champ champ--case champ--large">
                <input type="checkbox" bind:checked={saisie.service} />
                <span>C’est un service : rien à compter en stock</span>
              </label>

              {#if !saisie.service}
                <div class="champ">
                  <label for="a-stock">Quantité en stock</label>
                  <input id="a-stock" inputmode="numeric" bind:value={saisie.stock} placeholder="0" />
                </div>
                <div class="champ">
                  <label for="a-seuil">Seuil de réapprovisionnement</label>
                  <input id="a-seuil" inputmode="numeric" bind:value={saisie.seuil_rea} placeholder="Aucun" />
                </div>
              {/if}
            </div>

            <div class="actions">
              <button class="btn" type="submit" disabled={enregistrement || saisie.libelle.trim() === ''}>
                {enregistrement ? 'Enregistrement…' : 'Enregistrer'}
              </button>
              <button class="btn btn--discret" type="button" onclick={fermer}>Annuler</button>
            </div>
          </form>
        </section>
      {/if}

      <section class="outils">
        <div class="champ champ--recherche">
          <label class="sr-only" for="recherche">Rechercher un article</label>
          <input
            id="recherche"
            type="search"
            bind:value={recherche}
            placeholder="Libellé, code-barres, dossier…"
          />
        </div>
        <div class="champ champ--dossier">
          <label class="sr-only" for="dossier-filtre">Filtrer par dossier</label>
          <select id="dossier-filtre" value={dossierFiltre ?? '__tous__'} onchange={changerDossier}>
            <option value="__tous__">Tous les dossiers</option>
            <option value="">Sans dossier</option>
            {#each dossiers as dossier}
              <option value={dossier}>{dossier}</option>
            {/each}
          </select>
        </div>
      </section>

      {#if chargement}
        <p class="vide-texte">Chargement…</p>
      {:else if visibles.length === 0}
        <section class="carte vide">
          <h2>{articles.length === 0 ? 'Aucun article pour l’instant' : 'Aucun résultat'}</h2>
          <p class="vide-texte">
            {articles.length === 0
              ? 'Ajoutez ce que vous vendez : le prix et l’unité suivront sur chaque facture.'
              : 'Essayez un autre mot, ou choisissez un autre dossier.'}
          </p>
          {#if articles.length === 0}
            <button class="btn" type="button" onclick={ouvrirNouveau}>Ajouter un article</button>
          {/if}
        </section>
      {:else}
        <ul class="liste">
          {#each visibles as article (article.id)}
            {@const etat = etatStock(article)}
            <li class="carte fiche">
              <div class="fiche__texte">
                <div class="fiche__titre">
                  <h2>{article.libelle}</h2>
                  <span class="pastille {etat === 'ok' ? 'pastille--ok' : etat === 'service' ? 'pastille--service' : 'pastille--alerte'}">
                    {libelleStock(article)}
                  </span>
                  {#if article.dossier}
                    <span class="etiquette">{article.dossier}</span>
                  {/if}
                </div>
                <p class="fiche__prix">{libellePrix(article)}</p>
                {#if article.code_barre}<p class="fiche__code">{article.code_barre}</p>{/if}
              </div>
              <div class="fiche__actions">
                <button class="btn btn--discret" type="button" onclick={() => ouvrirEdition(article)}>
                  Modifier
                </button>
                <button class="lien-discret lien-discret--danger" type="button" onclick={() => void supprimer(article)}>
                  Supprimer
                </button>
              </div>
            </li>
          {/each}
        </ul>
      {/if}
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
    gap: 18px;
  }

  .entete {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
  }

  .entete h1 {
    font-size: clamp(1.5rem, 3vw, 1.9rem);
  }

  .entete__texte {
    color: var(--ardoise);
    margin-top: 4px;
  }

  .formulaire {
    padding: 22px;
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .formulaire__entete {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .formulaire h2 {
    font-size: 1.15rem;
  }

  .formulaire form {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .grille-champs {
    display: grid;
    gap: 14px;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  }

  .champ--large {
    grid-column: 1 / -1;
  }

  .actions {
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
  }

  .outils {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  .champ--recherche {
    flex: 1 1 260px;
  }

  .champ--dossier {
    flex: 0 1 220px;
  }

  .liste {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .fiche {
    padding: 16px 18px;
    display: flex;
    gap: 16px;
    align-items: flex-start;
    justify-content: space-between;
    flex-wrap: wrap;
  }

  .fiche__texte {
    display: flex;
    flex-direction: column;
    gap: 4px;
    min-width: 220px;
    flex: 1;
  }

  .fiche__titre {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .fiche__titre h2 {
    font-size: 1.05rem;
  }

  .fiche__prix {
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }

  .fiche__code {
    color: var(--ardoise);
    font-size: 0.85rem;
    font-variant-numeric: tabular-nums;
  }

  .fiche__actions {
    display: flex;
    align-items: center;
    gap: 14px;
  }

  .pastille {
    display: inline-flex;
    padding: 1px 9px;
    border-radius: 999px;
    font-size: 0.76rem;
    font-weight: 600;
  }

  .pastille--ok {
    background: #e5f5ee;
    color: var(--vert);
  }

  .pastille--service {
    background: #eef1f7;
    color: var(--ardoise);
  }

  .pastille--alerte {
    background: #fdeceb;
    color: var(--rouge);
  }

  .lien-discret--danger {
    color: var(--rouge);
  }

  .vide {
    padding: 28px;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .vide h2 {
    font-size: 1.15rem;
  }

  .vide-texte {
    color: var(--ardoise);
  }

  .sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
  }
</style>
