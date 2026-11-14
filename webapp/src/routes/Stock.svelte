<script lang="ts">
  import { onMount } from 'svelte'
  import { auth } from '../lib/session.svelte'
  import { supabase } from '../lib/supabase'
  import { aller } from '../lib/router.svelte'
  import { stockSoldes, stockEcarts, enregistrerMouvementStock, type StockSolde, type StockEcart } from '../lib/stock'
  import Barre from '../lib/ui/Barre.svelte'
  import SansBoutique from '../lib/ui/SansBoutique.svelte'

  let articles = $state<{ id: string; libelle: string; stock: number | null; seuil_rea: number | null; prix_achat: number }[]>([])
  let soldes = $state<StockSolde[]>([])
  let ecarts = $state<StockEcart[]>([])
  let chargement = $state(true)
  let erreur = $state<string | null>(null)
  let message = $state<string | null>(null)
  let recherche = $state('')
  let occupation = $state<string | null>(null)

  const articlesAvecStock = $derived(
    articles.filter((a) => a.stock !== null).map((a) => {
      const solde = soldes.find((s) => s.article_id === a.id)
      const ecart = ecarts.find((e) => e.article_id === a.id)
      const reel = solde?.quantite ?? a.stock ?? 0
      const seuil = a.seuil_rea ?? 0
      const valeur = reel * a.prix_achat
      const aCommander = seuil > 0 && reel < seuil ? seuil - reel : 0
      return {
        ...a,
        reel,
        seuil,
        valeur,
        aCommander,
        ecart: ecart ? ecart.cache !== ecart.journal : false,
      }
    }),
  )

  const aCommander = $derived(
    articlesAvecStock
      .filter((a) => a.aCommander > 0)
      .sort((a, b) => b.aCommander - a.aCommander),
  )

  const valeurTotale = $derived(
    articlesAvecStock.reduce((sum, a) => sum + a.valeur, 0),
  )

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
    try {
      const [resArticles, resSoldes, resEcarts] = await Promise.all([
        supabase.from('articles').select('id, libelle, stock, seuil_rea, prix_achat').eq('shop_id', boutique).order('libelle', { ascending: true }),
        stockSoldes(boutique),
        stockEcarts(boutique),
      ])
      if (resArticles.error) throw new Error(resArticles.error.message)
      articles = (resArticles.data as typeof articles) ?? []
      soldes = resSoldes
      ecarts = resEcarts
    } catch (e) {
      erreur = e instanceof Error ? e.message : 'Erreur de chargement'
    } finally {
      chargement = false
    }
  }

  const filtres = $derived(
    recherche.trim() === ''
      ? articlesAvecStock
      : articlesAvecStock.filter((a) =>
          a.libelle.toLowerCase().includes(recherche.toLowerCase()),
        ),
  )

  async function recevoir(articleId: string, delta: number, note: string = '', cout: number = 0) {
    if (delta <= 0) return
    occupation = articleId
    try {
      await enregistrerMouvementStock(articleId, delta, 'purchase', note, cout)
      message = `Réception enregistrée : +${delta}.`
      await charger()
    } catch (e) {
      erreur = e instanceof Error ? e.message : 'Échec de la réception'
    } finally {
      occupation = null
    }
  }

  async function inventaire(articleId: string, nouveau: number, note: string = '') {
    if (nouveau < 0) return
    const article = articles.find((a) => a.id === articleId)
    if (!article || article.stock === null) return
    const delta = nouveau - (article.stock ?? 0)
    if (delta === 0) return
    occupation = articleId
    try {
      await enregistrerMouvementStock(articleId, delta, 'stocktake', note)
      message = `Inventaire enregistré : ${nouveau} (Δ${delta >= 0 ? '+' : ''}${delta}).`
      await charger()
    } catch (e) {
      erreur = e instanceof Error ? e.message : 'Échec de l\'inventaire'
    } finally {
      occupation = null
    }
  }

  async function corriger(articleId: string, delta: number, note: string = '') {
    if (delta === 0) return
    occupation = articleId
    try {
      await enregistrerMouvementStock(articleId, delta, 'adjustment', note)
      message = `Correction enregistrée : ${delta >= 0 ? '+' : ''}${delta}.`
      await charger()
    } catch (e) {
      erreur = e instanceof Error ? e.message : 'Échec de la correction'
    } finally {
      occupation = null
    }
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
          <h1>Stock</h1>
          <p class="entete__texte">
            {articlesAvecStock.length} article{articlesAvecStock.length > 1 ? 's' : ''} compté{articlesAvecStock.length > 1 ? 's' : ''} ·
            {aCommander.length} à commander ·
            {valeurTotale.toLocaleString('fr-MA', { style: 'currency', currency: 'MAD', minimumFractionDigits: 0 })} de stock
          </p>
        </div>
      </section>

      {#if message}
        <p class="alerte alerte--info" role="status">{message}</p>
      {/if}
      {#if erreur}
        <p class="alerte" role="alert">{erreur}</p>
      {/if}

      {#if chargement}
        <p class="vide-texte">Chargement…</p>
      {:else}
        <section class="carte outils">
          <div class="champ champ--recherche">
            <label class="sr-only" for="recherche">Rechercher un article</label>
            <input
              id="recherche"
              type="search"
              bind:value={recherche}
              placeholder="Libellé, code-barres…"
            />
          </div>
        </section>

        {#if aCommander.length > 0}
          <section class="carte alerte-stock">
            <h2>⚠ À commander ({aCommander.length})</h2>
            <ul class="liste-commandes">
              {#each aCommander as article}
                <li>
                  <div>
                    <strong>{article.libelle}</strong>
                    <span class="badge">{article.reel} en stock · seuil {article.seuil}</span>
                  </div>
                  <span class="manque">{article.aCommander} manquants</span>
                </li>
              {/each}
            </ul>
          </section>
        {/if}

        {#if filtres.length === 0}
          <section class="carte vide">
            <h2>Aucun article en stock</h2>
            <p class="vide-texte">Ajoutez des articles avec un stock depuis <a href="/app/articles">Articles</a>.</p>
          </section>
        {:else}
          <ul class="liste">
            {#each filtres as article}
              <li class="carte fiche {article.ecart ? 'fiche--ecart' : ''}">
                <div class="fiche__texte">
                  <div class="fiche__titre">
                    <h2>{article.libelle}</h2>
                    <span class="pastille {article.aCommander > 0 ? 'pastille--alerte' : 'pastille--ok'}">
                      {article.reel} en stock
                    </span>
                    {#if article.seuil > 0}
                      <span class="etiquette">seuil {article.seuil}</span>
                    {/if}
                    {#if article.ecart}
                      <span class="etiquette etiquette--alerte">⚠ Cache ≠ journal</span>
                    {/if}
                  </div>
                  <p class="fiche__prix">
                    Valeur : {article.valeur.toLocaleString('fr-MA', { style: 'currency', currency: 'MAD', minimumFractionDigits: 0 })}
                  </p>
                </div>
                <div class="fiche__actions">
                  <div class="groupe-actions">
                    <label>
                      <span class="action-label">Réception</span>
                      <input
                        type="number"
                        min="1"
                        step="1"
                        placeholder="+ qté"
                        onkeydown={(e) => e.key === 'Enter' && (e.target as HTMLInputElement).value && recevoir(article.id, Number((e.target as HTMLInputElement).value), '', 0)}
                      />
                    </label>
                    <label>
                      <span class="action-label">Inventaire</span>
                      <input
                        type="number"
                        min="0"
                        step="1"
                        placeholder="nouveau"
                        onkeydown={(e) => e.key === 'Enter' && (e.target as HTMLInputElement).value !== '' && inventaire(article.id, Number((e.target as HTMLInputElement).value))}
                      />
                    </label>
                    <label>
                      <span class="action-label">Corriger</span>
                      <input
                        type="number"
                        step="1"
                        placeholder="± qté"
                        onkeydown={(e) => e.key === 'Enter' && (e.target as HTMLInputElement).value && corriger(article.id, Number((e.target as HTMLInputElement).value))}
                      />
                    </label>
                  </div>
                  <a class="btn btn--discret" href="/app/articles/{article.id}">Détail</a>
                </div>
              </li>
            {/each}
          </ul>
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

  .carte {
    padding: 20px;
    background: var(--blanc);
    border: 1px solid var(--trait);
    border-radius: 12px;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .outils {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;
  }

  .champ--recherche {
    flex: 1 1 260px;
  }

  .alerte-stock {
    border-color: var(--rouge);
    background: #fef2f2;
  }

  .alerte-stock h2 {
    color: var(--rouge);
    font-size: 1.1rem;
    margin: 0;
  }

  .liste-commandes {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .liste-commandes li {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 12px;
    background: var(--blanc);
    border-radius: 8px;
    border: 1px solid #fecaca;
  }

  .liste-commandes .badge {
    font-size: 0.85rem;
    color: var(--ardoise);
  }

  .liste-commandes .manque {
    color: var(--rouge);
    font-weight: 600;
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
    border: 1px solid var(--trait);
    border-radius: 10px;
    background: var(--blanc);
  }

  .fiche--ecart {
    border-color: var(--orange);
    background: #fffbeb;
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
    margin: 0;
  }

  .fiche__prix {
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    margin: 0;
    color: var(--ardoise);
  }

  .fiche__actions {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  .groupe-actions {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }

  .groupe-actions label {
    display: flex;
    flex-direction: column;
    gap: 2px;
    font-size: 0.8rem;
  }

  .action-label {
    color: var(--ardoise);
    font-weight: 500;
  }

  .groupe-actions input {
    width: 80px;
    padding: 6px 8px;
    border: 1px solid var(--trait);
    border-radius: 6px;
    font: inherit;
  }

  .groupe-actions input:focus {
    outline: 2px solid var(--bleu);
    outline-offset: 1px;
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

  .pastille--alerte {
    background: #fdeceb;
    color: var(--rouge);
  }

  .etiquette {
    font-size: 0.75rem;
    padding: 1px 8px;
    border-radius: 999px;
    background: var(--gris-clair);
    color: var(--ardoise);
  }

  .etiquette--alerte {
    background: #fef3c7;
    color: var(--orange);
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