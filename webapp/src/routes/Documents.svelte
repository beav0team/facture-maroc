<script lang="ts">
  import { onMount } from 'svelte'
  import { TYPES_DOCUMENT, type TypeDocument } from '../lib/document'
  import {
    statutDocument,
    situationDeReglement,
    type DocumentRow,
    type PaiementRow,
    type QuotaBoutique,
    type TonStatut,
  } from '../lib/documents'
  import * as api from '../lib/documentsApi'
  import { dirhams } from '../lib/money'
  import { auth } from '../lib/session.svelte'
  import Barre from '../lib/ui/Barre.svelte'
  import SansBoutique from '../lib/ui/SansBoutique.svelte'

  type LigneListe = {
    doc: DocumentRow
    statut: { texte: string; ton: TonStatut }
    total: number
    reste: number
    dernierPaiement: string
  }

  let documents = $state<DocumentRow[]>([])
  let paiements = $state<PaiementRow[]>([])
  let quota = $state<QuotaBoutique | null>(null)
  let chargement = $state(true)
  let erreur = $state<string | null>(null)
  let recherche = $state('')
  let filtre = $state<'tous' | TypeDocument>('tous')

  const filtres: { valeur: 'tous' | TypeDocument; libelle: string }[] = [
    { valeur: 'tous', libelle: 'Tous' },
    { valeur: 'facture', libelle: 'Factures' },
    { valeur: 'devis', libelle: 'Devis' },
    { valeur: 'avoir', libelle: 'Avoirs' },
  ]

  onMount(() => {
    void charger()
  })

  async function charger() {
    const shop = auth.boutique
    if (!shop) {
      chargement = false
      return
    }
    chargement = true
    erreur = null
    const [docsRes, paiementsRes, quotaRes] = await Promise.all([
      api.chargerDocuments(shop.id),
      api.chargerPaiements(shop.id),
      api.chargerQuota(shop.id),
    ])
    chargement = false
    if (docsRes.erreur) {
      erreur = docsRes.erreur
      return
    }
    documents = docsRes.donnees ?? []
    paiements = paiementsRes.donnees ?? []
    quota = quotaRes.donnees
  }

  const paiementsParDocument = $derived.by(() => {
    const carte = new Map<string, PaiementRow[]>()
    for (const paiement of paiements) {
      const liste = carte.get(paiement.document_id) ?? []
      liste.push(paiement)
      carte.set(paiement.document_id, liste)
    }
    return carte
  })

  const lignes = $derived.by((): LigneListe[] => {
    const terme = recherche.trim().toLowerCase()
    return documents
      .filter((doc) => filtre === 'tous' || doc.type === filtre)
      .filter((doc) => {
        if (terme === '') return true
        const client = doc.client_snapshot?.nom ?? ''
        return (
          client.toLowerCase().includes(terme) ||
          doc.numero.toLowerCase().includes(terme) ||
          doc.reference.toLowerCase().includes(terme)
        )
      })
      .map((doc) => {
        const reglements = paiementsParDocument.get(doc.id) ?? []
        const solde = situationDeReglement(doc, reglements)
        const dernier = [...reglements].sort((a, b) => (a.created_at < b.created_at ? 1 : -1))[0]
        return {
          doc,
          statut: statutDocument(doc, solde, new Date(), dernier?.date_paiement ?? ''),
          total: Number(doc.total_ttc) || 0,
          reste: solde.reste,
          dernierPaiement: dernier?.date_paiement ?? '',
        }
      })
  })

  function lien(doc: DocumentRow): string {
    return `/app/documents/${doc.id}`
  }

  function montantAffiche(ligne: LigneListe): string {
    const valeur = ligne.doc.type === 'avoir' ? -Math.abs(ligne.total) : ligne.total
    return dirhams(valeur)
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
          <h1>Documents</h1>
          <p class="entete__texte">
            {documents.length} document{documents.length > 1 ? 's' : ''}
            {#if quota}
              · {quota.plan === 'pro' ? 'Plan Pro' : `${quota.utilise} / ${quota.limite} factures ce mois`}
            {/if}
          </p>
        </div>
        <a class="btn" href="/app/documents/nouveau">Nouveau document</a>
      </section>

      {#if erreur}<p class="alerte" role="alert">{erreur}</p>{/if}

      {#if quota && quota.plan === 'gratuit' && quota.limite !== null}
        <div class="quota" role="status">
          <div class="quota__barre">
            <span style={`width: ${Math.min(100, (quota.utilise / quota.limite) * 100)}%`}></span>
          </div>
          <p class="quota__texte">
            Plan gratuit : {quota.utilise} facture{quota.utilise > 1 ? 's' : ''} émise{quota.utilise > 1 ? 's' : ''}
            sur {quota.limite} ce mois. <a href="/app/tarifs">Passer au plan Pro</a>
          </p>
        </div>
      {/if}

      <section class="outils">
        <div class="champ champ--recherche">
          <label class="sr-only" for="recherche">Rechercher un document</label>
          <input id="recherche" type="search" bind:value={recherche} placeholder="Client, numéro, référence…" />
        </div>
        <div class="segments" role="group" aria-label="Filtrer par type">
          {#each filtres as choix}
            <button type="button" class:actif={filtre === choix.valeur} aria-pressed={filtre === choix.valeur} onclick={() => (filtre = choix.valeur)}>
              {choix.libelle}
            </button>
          {/each}
        </div>
      </section>

      {#if chargement}
        <p class="vide-texte">Chargement…</p>
      {:else if lignes.length === 0}
        <section class="carte vide">
          <h2>{documents.length === 0 ? 'Aucun document pour l’instant' : 'Aucun résultat'}</h2>
          <p class="vide-texte">
            {documents.length === 0
              ? 'Créez une facture, un devis ou un bon de commande : le numéro est pris à l’émission.'
              : 'Essayez un autre nom, un autre numéro, ou changez de filtre.'}
          </p>
          {#if documents.length === 0}
            <a class="btn" href="/app/documents/nouveau">Créer un document</a>
          {/if}
        </section>
      {:else}
        <ul class="liste">
          {#each lignes as ligne (ligne.doc.id)}
            <li>
              <a class="carte document" href={lien(ligne.doc)}>
                <div class="document__principal">
                  <div class="document__titre">
                    <span class="document__type">{TYPES_DOCUMENT[ligne.doc.type].libelle}</span>
                    <strong>{ligne.doc.numero || 'Brouillon'}</strong>
                    <span class="document__date">{ligne.doc.date_emission}</span>
                  </div>
                  <p class="document__client">{ligne.doc.client_snapshot?.nom || 'Sans client'}</p>
                  {#if ligne.statut.texte}
                    <span class="statut statut--{ligne.statut.ton}">{ligne.statut.texte}</span>
                  {/if}
                </div>
                <div class="document__montant">
                  <strong>{montantAffiche(ligne)}</strong>
                  {#if ligne.doc.type !== 'avoir' && ligne.reste > 0 && ligne.doc.statut === 'emis'}
                    <span class="document__reste">{dirhams(ligne.reste)} restant</span>
                  {/if}
                </div>
              </a>
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
    gap: 16px;
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

  .quota {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .quota__barre {
    height: 6px;
    border-radius: 999px;
    background: var(--trait);
    overflow: hidden;
  }

  .quota__barre span {
    display: block;
    height: 100%;
    background: var(--bleu);
  }

  .quota__texte {
    color: var(--ardoise);
    font-size: 0.85rem;
  }

  .outils {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  .champ--recherche {
    flex: 1 1 240px;
  }

  .champ--recherche input {
    min-height: 44px;
    padding: 0 12px;
    border: 1px solid var(--trait);
    border-radius: 10px;
    background: var(--blanc);
    font: inherit;
    width: 100%;
  }

  .segments {
    display: inline-flex;
    padding: 3px;
    border: 1px solid var(--trait);
    border-radius: 999px;
    background: var(--blanc);
  }

  .segments button {
    border: 0;
    background: none;
    padding: 7px 14px;
    border-radius: 999px;
    font: inherit;
    font-size: 0.9rem;
    color: var(--ardoise);
    cursor: pointer;
  }

  .segments button.actif {
    background: var(--bleu);
    color: var(--blanc);
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

  .document {
    display: flex;
    justify-content: space-between;
    gap: 16px;
    padding: 16px 18px;
    color: inherit;
    text-decoration: none;
    transition: border-color 120ms ease, transform 120ms ease;
  }

  .document:hover {
    border-color: var(--bleu);
    transform: translateY(-1px);
  }

  .document__principal {
    display: flex;
    flex-direction: column;
    gap: 4px;
    min-width: 0;
  }

  .document__titre {
    display: flex;
    align-items: baseline;
    gap: 8px;
    flex-wrap: wrap;
  }

  .document__type {
    font-size: 0.78rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--ardoise);
  }

  .document__date {
    color: var(--ardoise);
    font-size: 0.85rem;
  }

  .document__client {
    color: var(--ardoise);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .document__montant {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 2px;
    white-space: nowrap;
    font-variant-numeric: tabular-nums;
  }

  .document__reste {
    color: var(--ardoise);
    font-size: 0.8rem;
  }

  .statut {
    align-self: flex-start;
    padding: 1px 8px;
    border-radius: 999px;
    background: var(--papier);
    border: 1px solid var(--trait);
    font-size: 0.76rem;
    font-weight: 600;
    color: var(--ardoise);
  }

  .statut--paid {
    background: #e6f5ee;
    border-color: #b7e0cd;
    color: var(--vert);
  }

  .statut--due {
    background: #fff4d6;
    border-color: #f0dfa8;
    color: #7a5300;
  }

  .statut--overdue {
    background: #fdeceb;
    border-color: #f3c2bf;
    color: var(--rouge);
  }

  .statut--draft {
    background: #eef2ff;
    border-color: #c7d2fe;
    color: var(--bleu-fonce);
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
