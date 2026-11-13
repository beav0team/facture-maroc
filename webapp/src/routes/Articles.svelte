<script lang="ts">
  import { onMount } from 'svelte'
  import {
    etatStock,
    filtrerArticles,
    libellePrix,
    libelleStock,
    valeursArticle,
    type Article,
    type SaisieArticle,
  } from '../lib/catalogue'
  import {
    afficher,
    aDuStock,
    articlesDans,
    articlesDirects,
    avecAncetres,
    descendantsDe,
    enfantsDe,
    erreurChemin,
    erreurNom,
    estDans,
    nomDe,
    normaliserChemin,
    parentDe,
    quantiteDans,
    rebase,
    segmentsDe,
    suggestionCopie,
  } from '../lib/dossiers'
  import { urlDe } from '../lib/chemin'
  import { aller } from '../lib/router.svelte'
  import { auth } from '../lib/session.svelte'
  import { supabase } from '../lib/supabase'
  import Barre from '../lib/ui/Barre.svelte'
  import SansBoutique from '../lib/ui/SansBoutique.svelte'

  let { dossier = '' }: { dossier?: string } = $props()

  type ModePanneau = 'creer' | 'renommer' | 'deplacer' | 'copier'
  type Panneau = { mode: ModePanneau; cible: string; valeur: string }

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
  let dossiers = $state<string[]>([])
  let chargement = $state(true)
  let erreur = $state<string | null>(null)
  let message = $state<string | null>(null)
  let recherche = $state('')
  let ouvert = $state(false)
  let edition = $state<Article | null>(null)
  let saisie = $state<SaisieArticle>(saisieVide())
  let enregistrement = $state(false)
  let panneau = $state<Panneau | null>(null)
  let occupe = $state(false)

  /** The folder being browsed, as written in the database. */
  const courant = $derived(normaliserChemin(dossier))
  /** Every folder, plus the parents an article's path implies. */
  const arbre = $derived(avecAncetres([...dossiers, ...articles.map((article) => article.dossier)]))
  const tuiles = $derived(enfantsDe(courant, arbre))
  const directs = $derived(articlesDirects(courant, articles))
  const sansDossier = $derived(articlesDirects('', articles))
  const fil = $derived(segmentsDe(courant))
  const nombreServices = $derived(articles.filter((article) => article.stock === null).length)
  /** Units on the shelf in the folder on screen, and in the whole catalogue. */
  const quantiteCourante = $derived(quantiteDans(courant, articles))
  const quantiteTotale = $derived(quantiteDans('', articles))
  const stockCourant = $derived(aDuStock(courant, articles))

  /** A search asks "is it here?", so it reaches the sub-folders. */
  const resultats = $derived(
    recherche.trim() === ''
      ? []
      : filtrerArticles(articlesDans(courant, articles), recherche, null),
  )
  const enRecherche = $derived(recherche.trim() !== '')

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
    const boutique = auth.boutique.id
    const [listeArticles, listeDossiers] = await Promise.all([
      supabase.from('articles').select('*').eq('shop_id', boutique).order('libelle', { ascending: true }),
      supabase.from('article_folders').select('chemin').eq('shop_id', boutique).order('chemin', { ascending: true }),
    ])
    chargement = false
    if (listeArticles.error) {
      erreur = listeArticles.error.message
      return
    }
    articles = (listeArticles.data as Article[]) ?? []
    // A missing article_folders table (migration not applied) must not empty the
    // screen: the tree falls back to the folders the articles themselves carry.
    dossiers = listeDossiers.error
      ? []
      : ((listeDossiers.data as { chemin: string }[]) ?? []).map((ligne) => ligne.chemin)
  }

  // -------------------------------------------------------------- les gestes

  function raisonDuRefus(resultat: Record<string, unknown> | null): string {
    switch (resultat?.raison) {
      case 'nom_vide':
        return 'Le nom du dossier est obligatoire.'
      case 'nom_invalide':
        return 'Un nom de dossier ne contient pas de « / ».'
      case 'nom_trop_long':
        return 'Ce nom de dossier est trop long.'
      case 'nom_pris':
        return 'Un dossier porte déjà ce nom à cet endroit.'
      case 'parent_inconnu':
        return 'Le dossier de destination n’existe pas.'
      case 'dossier_inexistant':
        return 'Ce dossier n’existe plus.'
      case 'cible_dans_source':
        return 'Un dossier ne peut pas aller dans lui-même.'
      case 'chemin_trop_long':
        return 'Le chemin du dossier serait trop long.'
      case 'confirmation_requise':
        return 'Le nom tapé ne correspond pas : rien n’a été supprimé.'
      default:
        return 'L’opération a été refusée.'
    }
  }

  async function appeler(
    nom: string,
    parametres: Record<string, unknown>,
  ): Promise<Record<string, unknown> | null> {
    if (!supabase || !auth.boutique) return null
    const { data, error } = await supabase.rpc(nom, {
      p_shop: auth.boutique.id,
      ...parametres,
    })
    if (error) {
      erreur = error.message
      return null
    }
    if (data === null || data === undefined) {
      erreur = 'La base n’a pas répondu. Réessayez.'
      return null
    }
    return data as Record<string, unknown>
  }

  function ouvrirPanneau(mode: ModePanneau, cible: string) {
    erreur = null
    message = null
    const valeur =
      mode === 'renommer'
        ? nomDe(cible)
        : mode === 'copier'
          ? suggestionCopie(cible, parentDe(cible), arbre)
          : ''
    panneau = { mode, cible, valeur }
  }

  function fermerPanneau() {
    panneau = null
  }

  function titrePanneau(mode: ModePanneau): string {
    switch (mode) {
      case 'creer':
        return 'Nouveau dossier'
      case 'renommer':
        return 'Renommer le dossier'
      case 'deplacer':
        return 'Déplacer le dossier'
      default:
        return 'Copier le dossier'
    }
  }

  async function validerPanneau(event: SubmitEvent) {
    event.preventDefault()
    if (!panneau) return

    const { mode, cible } = panneau
    const valeur = panneau.valeur.trim()
    erreur = null

    let resultat: Record<string, unknown> | null
    occupe = true

    if (mode === 'creer' || mode === 'renommer') {
      const probleme = erreurNom(valeur)
      if (probleme) {
        erreur = probleme
        occupe = false
        return
      }
      resultat =
        mode === 'creer'
          ? await appeler('creer_dossier', { p_parent: cible, p_nom: valeur })
          : await appeler('renommer_dossier', { p_chemin: cible, p_nom: valeur })
    } else {
      const destination = normaliserChemin(valeur)
      const probleme = erreurChemin(destination)
      if (probleme) {
        erreur = probleme
        occupe = false
        return
      }
      resultat =
        mode === 'deplacer'
          ? await appeler('deplacer_dossier', { p_chemin: cible, p_parent: destination })
          : await appeler('copier_dossier', { p_chemin: cible, p_parent: destination })
    }

    occupe = false
    if (!resultat) return
    if (!resultat.ok) {
      erreur = raisonDuRefus(resultat)
      return
    }

    const nouveau = typeof resultat.chemin === 'string' ? resultat.chemin : cible

    if (mode === 'creer') {
      message = `Dossier « ${nomDe(nouveau)} » créé.`
    } else if (mode === 'renommer') {
      message = `Dossier renommé en « ${nomDe(nouveau)} ».`
      // Standing inside the renamed folder: the URL must follow, or the next
      // refresh lands on a path that no longer exists.
      if (estDans(courant, cible)) aller(`/articles/${encodeURIComponent(rebase(courant, cible, nouveau))}`)
    } else if (mode === 'deplacer') {
      message = `Dossier déplacé vers « ${afficher(parentDe(nouveau))} ».`
      if (estDans(courant, cible)) aller(`/articles/${encodeURIComponent(rebase(courant, cible, nouveau))}`)
    } else {
      const copies = Number(resultat.articles ?? 0)
      const codes = Number(resultat.codes_retires ?? 0)
      message =
        `« ${nomDe(nouveau)} » copié : ${copies} article${copies > 1 ? 's' : ''}` +
        (codes > 0
          ? ` · ${codes} code${codes > 1 ? 's' : ''}-barre${codes > 1 ? 's' : ''} retiré${codes > 1 ? 's' : ''} pour éviter les doublons.`
          : '.')
    }

    panneau = null
    await charger()
  }

  /**
   * Deleting the folder, not its contents: articles return to the parent.
   *
   * Refused when sub-folders remain — the database decides that, so a tab left
   * open for an hour cannot flatten a tree that grew since it was drawn.
   */
  async function supprimerDossier(chemin: string) {
    erreur = null
    message = null
    const apercu = await appeler('dossier_apercu', { p_chemin: chemin })
    if (!apercu) return
    if (!apercu.ok) {
      erreur = raisonDuRefus(apercu)
      return
    }

    const nom = String(apercu.nom ?? nomDe(chemin))
    const nombre = Number(apercu.articles ?? 0)
    const sous = Number(apercu.sous_dossiers ?? 0)

    if (sous > 0) {
      erreur =
        `« ${nom} » contient ${sous} sous-dossier${sous > 1 ? 's' : ''}. ` +
        'Déplacez-les ou supprimez-les d’abord, ou utilisez « Supprimer définitivement ».'
      return
    }

    const destination = afficher(String(apercu.parent ?? ''))
    const texte =
      nombre === 0
        ? `Supprimer le dossier « ${nom} » ?`
        : `Supprimer le dossier « ${nom} » ?\n\nLes ${nombre} article${nombre > 1 ? 's' : ''} ne sont pas supprimés : ils reviendront dans « ${destination} ».`
    if (!confirm(texte)) return

    occupe = true
    const resultat = await appeler('supprimer_dossier', { p_chemin: chemin, p_mode: 'remonter' })
    occupe = false
    if (!resultat) return
    if (!resultat.ok) {
      erreur = raisonDuRefus(resultat)
      return
    }

    const remontes = Number(resultat.articles ?? 0)
    message =
      remontes === 0
        ? `Dossier « ${nom} » supprimé.`
        : `Dossier « ${nom} » supprimé : ${remontes} article${remontes > 1 ? 's' : ''} dans « ${destination} ».`
    await charger()
  }

  /**
   * The explicit destructive path: the folder, its sub-folders and their
   * articles go. The name has to be typed, and the database checks it too, not
   * just this dialog.
   */
  async function supprimerDefinitivement(chemin: string) {
    erreur = null
    message = null
    const apercu = await appeler('dossier_apercu', { p_chemin: chemin })
    if (!apercu) return
    if (!apercu.ok) {
      erreur = raisonDuRefus(apercu)
      return
    }

    const nom = String(apercu.nom ?? nomDe(chemin))
    const nombre = Number(apercu.articles ?? 0)
    const sous = Number(apercu.sous_dossiers ?? 0)

    const texte =
      `Supprimer définitivement « ${nom} » ?\n\n` +
      `${sous} sous-dossier${sous > 1 ? 's' : ''} et ${nombre} article${nombre > 1 ? 's' : ''} seront supprimés. ` +
      `Les factures déjà émises ne changent pas.\n\nTapez ${nom} pour confirmer.`
    const confirmation = prompt(texte)
    if (confirmation === null) return

    occupe = true
    const resultat = await appeler('supprimer_dossier', {
      p_chemin: chemin,
      p_mode: 'recursif',
      p_confirmation: confirmation,
    })
    occupe = false
    if (!resultat) return
    if (!resultat.ok) {
      erreur = raisonDuRefus(resultat)
      return
    }

    message = `« ${nom} » supprimé définitivement : ${nombre} article${nombre > 1 ? 's' : ''}, ${sous} sous-dossier${sous > 1 ? 's' : ''}.`
    // The folder we were standing in is gone: back to the root.
    if (estDans(courant, chemin)) aller('/articles')
    await charger()
  }

  // --------------------------------------------------------------- l'article

  function ouvrirNouveau() {
    edition = null
    saisie = saisieVide()
    saisie.dossier = courant
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

    const problemeDossier = erreurChemin(saisie.dossier)
    if (problemeDossier) {
      erreur = problemeDossier
      return
    }

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

  function lienDossier(chemin: string): string {
    return chemin === '' ? urlDe('/articles') : urlDe(`/articles/${encodeURIComponent(chemin)}`)
  }

  function resumeTuile(chemin: string): string {
    const dedans = articlesDans(chemin, articles)
    const sous = descendantsDe(chemin, arbre).length
    // Quantity first: it is what the shop came for. The article count follows
    // because "12 en stock" could be one tyre or twelve references.
    const parties: string[] = []
    if (aDuStock(chemin, articles)) parties.push(`${quantiteDans(chemin, articles)} en stock`)
    parties.push(`${dedans.length} article${dedans.length > 1 ? 's' : ''}`)
    if (sous > 0) parties.push(`${sous} sous-dossier${sous > 1 ? 's' : ''}`)
    return parties.join(' · ')
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
            {#if courant === ''}
              {articles.length} article{articles.length > 1 ? 's' : ''} ·
              {quantiteTotale} en stock ·
              {nombreServices} service{nombreServices > 1 ? 's' : ''} ·
              {arbre.length} dossier{arbre.length > 1 ? 's' : ''}
            {:else}
              {articlesDans(courant, articles).length} article{articlesDans(courant, articles)
                .length > 1
                ? 's'
                : ''}{#if stockCourant} ·
                {quantiteCourante} en stock{/if}
              dans « {afficher(courant)} »
            {/if}
          </p>
        </div>
        <div class="entete__actions">
          <button class="btn btn--discret" type="button" onclick={() => ouvrirPanneau('creer', courant)}>
            Nouveau dossier
          </button>
          <button class="btn" type="button" onclick={ouvrirNouveau}>Nouvel article</button>
        </div>
      </section>

      {#if message}
        <p class="alerte alerte--info" role="status">{message}</p>
      {/if}
      {#if erreur}
        <p class="alerte" role="alert">{erreur}</p>
      {/if}

      <!-- Le fil d'Ariane : le seul chemin de retour, il doit être cliquable. -->
      {#if fil.length > 0}
        <nav class="fil" aria-label="Chemin du dossier">
          <a href={lienDossier('')}>Tous les dossiers</a>
          {#each fil as segment, index (segment)}
            <span class="fil__sep" aria-hidden="true">›</span>
            <a href={lienDossier(fil.slice(0, index + 1).join('/'))}>{segment}</a>
          {/each}
        </nav>
      {/if}

      {#if panneau}
        <section class="carte formulaire">
          <div class="formulaire__entete">
            <h2>{titrePanneau(panneau.mode)}</h2>
            <button class="lien-discret" type="button" onclick={fermerPanneau}>Fermer</button>
          </div>

          <form onsubmit={validerPanneau}>
            <div class="grille-champs">
              {#if panneau.mode === 'creer' || panneau.mode === 'renommer'}
                <div class="champ champ--large">
                  <label for="d-nom">Nom du dossier <span aria-hidden="true">*</span></label>
                  <input
                    id="d-nom"
                    required
                    bind:value={panneau.valeur}
                    placeholder="Ex. Chaises"
                  />
                  <p class="champ__aide">
                    {#if panneau.mode === 'creer'}
                      Dans « {afficher(panneau.cible)} ».
                    {:else}
                      Les sous-dossiers et les articles suivent le renommage.
                    {/if}
                  </p>
                </div>
              {:else}
                <div class="champ champ--large">
                  <label for="d-destination">Dossier de destination</label>
                  <input
                    id="d-destination"
                    bind:value={panneau.valeur}
                    list="dossiers-connus"
                    placeholder="Vide = Sans dossier"
                  />
                  <p class="champ__aide">
                    Laissez vide pour la racine. {panneau.mode === 'copier'
                      ? 'La copie arrive sans code-barres et avec « (copie) » dans le libellé.'
                      : 'Tout le contenu suit.'}
                  </p>
                </div>
              {/if}
            </div>

            <div class="actions">
              <button class="btn" type="submit" disabled={occupe}>
                {occupe ? 'Enregistrement…' : 'Enregistrer'}
              </button>
              <button class="btn btn--discret" type="button" onclick={fermerPanneau}>
                Annuler
              </button>
            </div>
          </form>
        </section>
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
                <input
                  id="a-dossier"
                  bind:value={saisie.dossier}
                  placeholder="Sans dossier"
                  list="dossiers-connus"
                />
                <p class="champ__aide">« / » sépare les niveaux ; un dossier manquant est créé.</p>
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

      <datalist id="dossiers-connus">
        {#each arbre as chemin (chemin)}
          <option value={chemin}></option>
        {/each}
      </datalist>

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
      </section>

      {#if chargement}
        <p class="vide-texte">Chargement…</p>
      {:else if enRecherche}
        {#if resultats.length === 0}
          <section class="carte vide">
            <h2>Aucun résultat</h2>
            <p class="vide-texte">
              Rien ne correspond à « {recherche} » dans « {afficher(courant)} ».
            </p>
          </section>
        {:else}
          <p class="vide-texte">
            {resultats.length} résultat{resultats.length > 1 ? 's' : ''} dans « {afficher(courant)} »
          </p>
          <ul class="liste">
            {#each resultats as article (article.id)}
              {@const etat = etatStock(article)}
              <li class="carte fiche">
                <div class="fiche__texte">
                  <div class="fiche__titre">
                    <h2>{article.libelle}</h2>
                    <span
                      class="pastille {etat === 'ok' ? 'pastille--ok' : etat === 'service' ? 'pastille--service' : 'pastille--alerte'}"
                    >
                      {libelleStock(article)}
                    </span>
                    {#if article.dossier}
                      <span class="etiquette">{afficher(article.dossier)}</span>
                    {/if}
                  </div>
                  <p class="fiche__prix">{libellePrix(article)}</p>
                  {#if article.code_barre}<p class="fiche__code">{article.code_barre}</p>{/if}
                </div>
                <div class="fiche__actions">
                  <button class="btn btn--discret" type="button" onclick={() => ouvrirEdition(article)}>
                    Modifier
                  </button>
                  <button
                    class="lien-discret lien-discret--danger"
                    type="button"
                    onclick={() => void supprimer(article)}
                  >
                    Supprimer
                  </button>
                </div>
              </li>
            {/each}
          </ul>
        {/if}
      {:else}
        {#if courant === '' && arbre.length === 0 && articles.length === 0}
          <section class="carte vide">
            <h2>Aucun article pour l’instant</h2>
            <p class="vide-texte">
              Ajoutez ce que vous vendez : le prix et l’unité suivront sur chaque facture.
            </p>
            <button class="btn" type="button" onclick={ouvrirNouveau}>Ajouter un article</button>
          </section>
        {:else}
          <ul class="grille">
            {#each tuiles as chemin (chemin)}
              <li class="carte tuile">
                <a class="tuile__lien" href={lienDossier(chemin)}>
                  <span class="tuile__nom">{nomDe(chemin)}</span>
                  <span class="tuile__resume">{resumeTuile(chemin)}</span>
                </a>
                <details class="menu">
                  <summary aria-label={`Actions sur ${nomDe(chemin)}`}>⋯</summary>
                  <div class="menu__liste">
                    <button type="button" onclick={() => ouvrirPanneau('creer', chemin)}>
                      Nouveau sous-dossier
                    </button>
                    <button type="button" onclick={() => ouvrirPanneau('renommer', chemin)}>
                      Renommer
                    </button>
                    <button type="button" onclick={() => ouvrirPanneau('deplacer', chemin)}>
                      Déplacer
                    </button>
                    <button type="button" onclick={() => ouvrirPanneau('copier', chemin)}>
                      Copier
                    </button>
                    <button type="button" onclick={() => void supprimerDossier(chemin)}>
                      Supprimer
                    </button>
                    <button
                      class="menu__danger"
                      type="button"
                      onclick={() => void supprimerDefinitivement(chemin)}
                    >
                      Supprimer définitivement…
                    </button>
                  </div>
                </details>
              </li>
            {/each}
          </ul>

          {#if courant === '' && directs.length > 0}
            <h2 class="section-titre">Sans dossier</h2>
          {/if}

          {#if directs.length > 0}
            <ul class="liste">
              {#each directs as article (article.id)}
                {@const etat = etatStock(article)}
                <li class="carte fiche">
                  <div class="fiche__texte">
                    <div class="fiche__titre">
                      <h2>{article.libelle}</h2>
                      <span
                        class="pastille {etat === 'ok' ? 'pastille--ok' : etat === 'service' ? 'pastille--service' : 'pastille--alerte'}"
                      >
                        {libelleStock(article)}
                      </span>
                    </div>
                    <p class="fiche__prix">{libellePrix(article)}</p>
                    {#if article.code_barre}<p class="fiche__code">{article.code_barre}</p>{/if}
                  </div>
                  <div class="fiche__actions">
                    <button class="btn btn--discret" type="button" onclick={() => ouvrirEdition(article)}>
                      Modifier
                    </button>
                    <button
                      class="lien-discret lien-discret--danger"
                      type="button"
                      onclick={() => void supprimer(article)}
                    >
                      Supprimer
                    </button>
                  </div>
                </li>
              {/each}
            </ul>
          {:else if tuiles.length === 0}
            <section class="carte vide">
              <h2>Ce dossier est vide</h2>
              <p class="vide-texte">
                Ajoutez un article ici, ou un sous-dossier pour ranger ce qui viendra.
              </p>
              <button class="btn" type="button" onclick={ouvrirNouveau}>Ajouter un article</button>
            </section>
          {/if}

          {#if sansDossier.length > 0 && courant !== ''}
            <p class="vide-texte">
              {sansDossier.length} article{sansDossier.length > 1 ? 's' : ''} sans dossier
              <a href={urlDe('/articles')}>à classer depuis la racine</a>.
            </p>
          {/if}
        {/if}
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

  .entete__actions {
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
  }

  .fil {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
    font-size: 0.92rem;
    color: var(--ardoise);
  }

  .fil a {
    color: inherit;
    text-underline-offset: 3px;
  }

  .fil a:last-child {
    color: var(--encre);
    font-weight: 600;
    text-decoration: none;
  }

  .fil__sep {
    color: var(--ardoise);
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

  .champ__aide {
    margin-top: 4px;
    font-size: 0.82rem;
    color: var(--ardoise);
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

  .grille {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  }

  .tuile {
    position: relative;
    padding: 0;
    display: flex;
    align-items: stretch;
  }

  .tuile__lien {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 16px 18px;
    color: inherit;
    text-decoration: none;
    border-radius: inherit;
  }

  .tuile__nom {
    font-weight: 600;
    font-size: 1rem;
  }

  .tuile__resume {
    font-size: 0.84rem;
    color: var(--ardoise);
  }

  .section-titre {
    font-size: 1.05rem;
    margin-top: 4px;
  }

  .menu {
    position: absolute;
    top: 8px;
    right: 8px;
  }

  .menu summary {
    list-style: none;
    cursor: pointer;
    width: 30px;
    height: 30px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 8px;
    color: var(--ardoise);
  }

  .menu summary::-webkit-details-marker {
    display: none;
  }

  .menu summary:hover {
    background: rgba(0, 0, 0, 0.06);
  }

  .menu__liste {
    position: absolute;
    right: 0;
    top: 34px;
    z-index: 5;
    min-width: 210px;
    display: flex;
    flex-direction: column;
    padding: 6px;
    background: var(--blanc, #fff);
    border: 1px solid rgba(0, 0, 0, 0.1);
    border-radius: 10px;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  }

  .menu__liste button {
    text-align: left;
    background: none;
    border: 0;
    padding: 8px 10px;
    font: inherit;
    font-size: 0.9rem;
    border-radius: 6px;
    cursor: pointer;
    color: inherit;
  }

  .menu__liste button:hover {
    background: rgba(0, 0, 0, 0.05);
  }

  .menu__liste .menu__danger {
    color: var(--rouge);
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
