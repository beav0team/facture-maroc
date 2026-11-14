<script lang="ts">
  import { onMount } from 'svelte'
  import { route } from '../lib/router.svelte'
  import { auth } from '../lib/session.svelte'
  import { supabase } from '../lib/supabase'
  import { aller } from '../lib/router.svelte'
  import { derniersMouvementsStock, stockSoldes, libelleMotif, formaterDelta, classeDelta, type MouvementStock } from '../lib/stock'
  import Barre from '../lib/ui/Barre.svelte'
  import SansBoutique from '../lib/ui/SansBoutique.svelte'

  let article = $state<{
    id: string
    libelle: string
    prix: number
    unite: string
    dossier: string
    prix_achat: number
    code_barre: string
    stock: number | null
    seuil_rea: number | null
    attributs: { key: string; value: string }[]
    paliers: { fromQuantity: number; unitPrice: number }[]
  } | null>(null)
  let mouvements = $state<MouvementStock[]>([])
  let chargement = $state(true)
  let erreur = $state<string | null>(null)
  let message = $state<string | null>(null)
  let edition = $state(false)
  let saisie = $state({
    libelle: '',
    prix: '',
    unite: '',
    dossier: '',
    prix_achat: '',
    code_barre: '',
    service: false,
    stock: '',
    seuil_rea: '',
  })
  let enregistrement = $state(false)

  const articleId = $derived(route.chemin.split('/articles/')[1]?.split('/')[0] ?? '')

  onMount(async () => {
    if (articleId) await charger()
  })

  async function charger() {
    if (!supabase || !auth.boutique) {
      chargement = false
      return
    }
    chargement = true
    erreur = null
    try {
      const [resArticle, resMouvements, resSoldes] = await Promise.all([
        supabase.from('articles').select('*').eq('shop_id', auth.boutique.id).eq('id', articleId).single(),
        derniersMouvementsStock(auth.boutique.id, articleId, 20),
        stockSoldes(auth.boutique.id),
      ])
      if (resArticle.error) throw new Error(resArticle.error.message)
      let a = resArticle.data as typeof article
      // Merge with ledger quantity
      const solde = resSoldes.find((s: { article_id: string; quantite: number }) => s.article_id === articleId)
      if (solde && a && a.stock !== null) {
        a = { ...a, stock: solde.quantite }
      }
      article = a
      mouvements = resMouvements
      if (article) {
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
      }
    } catch (e) {
      erreur = e instanceof Error ? e.message : 'Erreur de chargement'
    } finally {
      chargement = false
    }
  }

  function ouvrirEdition() {
    edition = true
    message = null
  }

  function annulerEdition() {
    edition = false
    if (article) {
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
    }
  }

  async function enregistrer(event: SubmitEvent) {
    event.preventDefault()
    if (!supabase || !auth.boutique || !article) return

    const valeurs = {
      libelle: saisie.libelle.trim(),
      prix: Number(saisie.prix.replace(',', '.')),
      unite: saisie.unite,
      dossier: saisie.dossier,
      prix_achat: saisie.prix_achat ? Number(saisie.prix_achat.replace(',', '.')) : 0,
      code_barre: saisie.code_barre || null,
      seuil_rea: saisie.seuil_rea ? Number(saisie.seuil_rea) : null,
    }

    // Stock is managed via the ledger (stock_movements), not direct writes.
    // The cache column (articles.stock) is updated by triggers/RPCs.

    if (!valeurs.libelle) {
      message = 'Le libellé est obligatoire.'
      return
    }

    enregistrement = true
    try {
      const { error } = await supabase
        .from('articles')
        .update(valeurs)
        .eq('id', article.id)
      if (error) throw new Error(error.message)

      message = 'Article mis à jour.'
      edition = false
      await charger()
    } catch (e) {
      message = e instanceof Error ? e.message : 'Échec de la mise à jour'
    } finally {
      enregistrement = false
    }
  }

  async function supprimerArticle() {
    if (!supabase || !article) return
    if (!confirm(`Supprimer « ${article.libelle} » du catalogue ?`)) return
    try {
      const { error } = await supabase.from('articles').delete().eq('id', article.id)
      if (error) throw new Error(error.message)
      aller('/articles')
    } catch (e) {
      message = e instanceof Error ? e.message : 'Échec de la suppression'
    }
  }

  function formatDate(dateStr: string): string {
    try {
      const d = new Date(dateStr)
      return d.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
    } catch {
      return dateStr
    }
  }

  const valeurStock = $derived(article && article.stock !== null && article.prix_achat > 0
    ? article.stock * article.prix_achat
    : 0)
</script>

<div class="page">
  <Barre />

  <main class="contenu">
    {#if !auth.boutique}
      <SansBoutique />
    {:else if chargement}
      <p class="vide-texte">Chargement…</p>
    {:else if !article}
      <section class="carte vide">
        <h2>Article introuvable</h2>
        <p class="vide-texte">Cet article n'existe plus ou vous n'y avez pas accès.</p>
        <a class="btn" href="/app/articles">Retour aux articles</a>
      </section>
    {:else}
      <section class="entete">
        <div>
          <h1>{article.libelle}</h1>
          <p class="entete__texte">
            {#if article.dossier}
              <span class="etiquette">{article.dossier}</span>
            {/if}
            {#if article.code_barre}
              <span class="etiquette">{article.code_barre}</span>
            {/if}
            <span class="etiquette">{article.unite || 'unité'}</span>
          </p>
        </div>
        <div class="entete__actions">
          <button class="btn btn--discret" type="button" onclick={ouvrirEdition} disabled={edition}>
            Modifier
          </button>
          <button class="lien-discret lien-discret--danger" type="button" onclick={supprimerArticle}>
            Supprimer
          </button>
        </div>
      </section>

      {#if message}
        <p class="alerte {message.startsWith('Article') ? 'alerte--info' : ''}" role="status">{message}</p>
      {/if}
      {#if erreur}
        <p class="alerte" role="alert">{erreur}</p>
      {/if}

      {#if edition}
        <section class="carte formulaire">
          <div class="formulaire__entete">
            <h2>Modifier l'article</h2>
            <button class="lien-discret" type="button" onclick={annulerEdition}>Annuler</button>
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
                <input id="a-dossier" bind:value={saisie.dossier} placeholder="Sans dossier" list="dossiers-connus" />
                <p class="champ__aide">« / » sépare les niveaux ; un dossier manquant est créé.</p>
              </div>
              <div class="champ">
                <label for="a-achat">Prix d'achat HT (DH)</label>
                <input id="a-achat" inputmode="decimal" bind:value={saisie.prix_achat} placeholder="0,00" />
              </div>
              <div class="champ">
                <label for="a-code">Code-barres</label>
                <input id="a-code" bind:value={saisie.code_barre} inputmode="numeric" />
              </div>

              <label class="champ champ--case champ--large">
                <input type="checkbox" bind:checked={saisie.service} />
                <span>C'est un service : rien à compter en stock</span>
              </label>

              {#if !saisie.service}
                <div class="champ">
                  <label for="a-stock">Quantité en stock (journal)</label>
                  <input id="a-stock" inputmode="numeric" value={article?.stock !== null ? String(article.stock) : '0'} readonly />
                  <p class="champ__aide">Modifiez le stock depuis l'onglet <a href="/app/stock">Stock</a> (réception, inventaire, correction).</p>
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
              <button class="btn btn--discret" type="button" onclick={annulerEdition}>Annuler</button>
            </div>
          </form>
        </section>
      {/if}

      <section class="carte infos">
        <h2>Informations</h2>
        <dl class="grille-infos">
          <dt>Prix de vente</dt>
          <dd>{article.prix.toLocaleString('fr-MA', { style: 'currency', currency: 'MAD', minimumFractionDigits: 2 })}</dd>
          <dt>Prix d'achat HT</dt>
          <dd>{article.prix_achat > 0 ? article.prix_achat.toLocaleString('fr-MA', { style: 'currency', currency: 'MAD', minimumFractionDigits: 2 }) : '—'}</dd>
          {#if article.stock !== null}
            <dt>Stock (journal)</dt>
            <dd>{article.stock} {article.unite || 'unité'}</dd>
            {#if article.seuil_rea !== null && article.seuil_rea > 0}
              <dt>Seuil de réapprovisionnement</dt>
              <dd>{article.seuil_rea} {article.unite || 'unité'}</dd>
            {/if}
            {#if valeurStock > 0}
              <dt>Valeur du stock</dt>
              <dd>{valeurStock.toLocaleString('fr-MA', { style: 'currency', currency: 'MAD', minimumFractionDigits: 0 })}</dd>
            {/if}
          {:else}
            <dt>Type</dt>
            <dd>Service (pas de stock)</dd>
          {/if}
        </dl>
      </section>

      <section class="carte historique">
        <h2>Derniers mouvements ({mouvements.length})</h2>
        {#if mouvements.length === 0}
          <p class="vide-texte">Aucun mouvement dans le journal pour cet article.</p>
        {:else}
          <table class="tableau-mouvements">
            <thead>
              <tr>
                <th>Date</th>
                <th>Motif</th>
                <th>Δ</th>
                <th>Référence</th>
                <th>Note</th>
                <th>Coût unitaire</th>
              </tr>
            </thead>
            <tbody>
              {#each mouvements as m}
                <tr>
                  <td>{formatDate(m.cree_le)}</td>
                  <td>{libelleMotif(m.motif)}</td>
                  <td class="{classeDelta(m.delta)}">{formaterDelta(m.delta)}</td>
                  <td>{m.reference || '—'}</td>
                  <td>{m.note || '—'}</td>
                  <td>{m.cout_unitaire > 0 ? m.cout_unitaire.toLocaleString('fr-MA', { style: 'currency', currency: 'MAD', minimumFractionDigits: 2 }) : '—'}</td>
                </tr>
              {/each}
            </tbody>
          </table>
        {/if}
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
    display: flex;
    gap: 8px;
    margin-top: 4px;
    flex-wrap: wrap;
  }

  .entete__actions {
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
  }

  .etiquette {
    font-size: 0.75rem;
    padding: 1px 8px;
    border-radius: 999px;
    background: var(--gris-clair);
    color: var(--ardoise);
  }

  .carte {
    padding: 20px;
    background: var(--blanc);
    border: 1px solid var(--trait);
    border-radius: 12px;
    display: flex;
    flex-direction: column;
    gap: 14px;
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
    margin: 0;
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

  .infos h2, .historique h2 {
    font-size: 1.15rem;
    margin: 0 0 12px 0;
  }

  .grille-infos {
    display: grid;
    grid-template-columns: 180px 1fr;
    gap: 8px 16px;
    font-size: 0.95rem;
  }

  .grille-infos dt {
    color: var(--ardoise);
    font-weight: 500;
  }

  .grille-infos dd {
    margin: 0;
  }

  .tableau-mouvements {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.9rem;
  }

  .tableau-mouvements th,
  .tableau-mouvements td {
    padding: 10px 12px;
    text-align: left;
    border-bottom: 1px solid var(--trait);
  }

  .tableau-mouvements th {
    font-weight: 600;
    color: var(--ardoise);
    background: var(--gris-fonce);
  }

  .tableau-mouvements tbody tr:hover {
    background: var(--gris-fonce);
  }

  .delta--entree {
    color: var(--vert);
    font-weight: 600;
  }

  .delta--sortie {
    color: var(--rouge);
    font-weight: 600;
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
    margin: 0;
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