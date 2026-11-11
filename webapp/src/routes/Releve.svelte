<script lang="ts">
  import { onMount } from 'svelte'
  import { statutDocument, situationDeReglement, type DocumentRow, type PaiementRow, type TonStatut } from '../lib/documents'
  import type { ClientEnregistre } from '../lib/catalogue'
  import { dirhams } from '../lib/money'
  import { auth } from '../lib/session.svelte'
  import { supabase } from '../lib/supabase'
  import Barre from '../lib/ui/Barre.svelte'
  import SansBoutique from '../lib/ui/SansBoutique.svelte'

  let { id }: { id: string } = $props()

  let client = $state<ClientEnregistre | null>(null)
  let documents = $state<DocumentRow[]>([])
  let paiements = $state<PaiementRow[]>([])
  let chargement = $state(true)
  let erreur = $state<string | null>(null)

  type Ligne = {
    doc: DocumentRow
    net: number
    paye: number
    reste: number
    statut: { texte: string; ton: TonStatut }
  }

  onMount(() => {
    void charger()
  })

  async function charger() {
    if (!supabase || !auth.boutique) {
      chargement = false
      return
    }
    chargement = true
    erreur = null

    const [clientRes, docsRes, paiementsRes] = await Promise.all([
      supabase.from('clients').select('*').eq('id', id).maybeSingle(),
      supabase
        .from('documents')
        .select('*')
        .eq('client_id', id)
        .eq('statut', 'emis')
        .order('emis_le', { ascending: true }),
      supabase.from('payments').select('*, documents!inner(client_id)').eq('documents.client_id', id),
    ])

    chargement = false
    if (clientRes.error) {
      erreur = clientRes.error.message
      return
    }
    client = (clientRes.data as ClientEnregistre | null) ?? null
    documents = (docsRes.data as DocumentRow[]) ?? []
    paiements = (paiementsRes.data as PaiementRow[]) ?? []
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

  const lignes = $derived.by((): Ligne[] =>
    documents.map((doc) => {
      const reglements = paiementsParDocument.get(doc.id) ?? []
      const solde = situationDeReglement(doc, reglements)
      return {
        doc,
        net: Number(doc.net_a_payer) || 0,
        paye: solde.paye,
        reste: doc.type === 'avoir' ? 0 : solde.reste,
        statut: statutDocument(doc, solde),
      }
    }),
  )

  const totalFacture = $derived(
    lignes.filter((ligne) => ligne.doc.type !== 'avoir').reduce((somme, ligne) => somme + ligne.net, 0),
  )
  const totalAvoir = $derived(
    lignes.filter((ligne) => ligne.doc.type === 'avoir').reduce((somme, ligne) => somme + ligne.net, 0),
  )
  const totalPaye = $derived(paiements.reduce((somme, paiement) => somme + Number(paiement.montant), 0))
  const soldeDu = $derived(totalFacture - totalAvoir - totalPaye)

  function imprimer() {
    window.print()
  }
</script>

<div class="page">
  <div class="ecran">
    <Barre />
  </div>

  <main class="contenu">
    {#if !auth.boutique}
      <SansBoutique />
    {:else if chargement}
      <p class="note">Chargement…</p>
    {:else if !client}
      <p class="alerte" role="alert">{erreur ?? 'Client introuvable.'}</p>
    {:else}
      <div class="actions ecran">
        <a class="retour" href="/app/clients">← Clients</a>
        <button class="btn" type="button" onclick={imprimer}>Imprimer le relevé</button>
      </div>

      <article class="releve">
        <header class="releve__entete">
          <div>
            <h1>Relevé de compte</h1>
            <p class="note">Édité le {new Date().toLocaleDateString('fr-FR')}</p>
          </div>
          <div class="releve__boutique">
            <strong>{auth.boutique.nom}</strong>
            {#if auth.boutique.adresse}<span>{auth.boutique.adresse}</span>{/if}
            {#if auth.boutique.ice}<span>ICE {auth.boutique.ice}</span>{/if}
          </div>
        </header>

        <section class="releve__client">
          <h2>{client.nom}</h2>
          {#if client.adresse}<p>{client.adresse}</p>{/if}
          <p>{[client.telephone, client.email].filter(Boolean).join(' · ')}</p>
          {#if client.ice}<p>ICE {client.ice}</p>{/if}
        </section>

        {#if lignes.length === 0}
          <p class="note">Aucune facture émise pour ce client.</p>
        {:else}
          <table class="releve__table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Document</th>
                <th>Statut</th>
                <th class="droite">Net</th>
                <th class="droite">Payé</th>
                <th class="droite">Reste</th>
              </tr>
            </thead>
            <tbody>
              {#each lignes as ligne (ligne.doc.id)}
                <tr>
                  <td>{ligne.doc.date_emission}</td>
                  <td>
                    <a href={`/app/documents/${ligne.doc.id}`}>{ligne.doc.numero}</a>
                  </td>
                  <td>{ligne.statut.texte}</td>
                  <td class="droite">{dirhams(ligne.net)}</td>
                  <td class="droite">{dirhams(ligne.paye)}</td>
                  <td class="droite">{dirhams(ligne.reste)}</td>
                </tr>
              {/each}
            </tbody>
          </table>

          <dl class="releve__totaux">
            <div><dt>Total facturé</dt><dd>{dirhams(totalFacture)}</dd></div>
            {#if totalAvoir > 0}<div><dt>Avoirs</dt><dd>−{dirhams(totalAvoir)}</dd></div>{/if}
            <div><dt>Total encaissé</dt><dd>{dirhams(totalPaye)}</dd></div>
            <div class="releve__fort"><dt>Solde dû</dt><dd>{dirhams(soldeDu)}</dd></div>
          </dl>
        {/if}
      </article>
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
    width: min(900px, 100%);
    margin: 0 auto;
    padding: clamp(20px, 4vw, 40px) clamp(16px, 4vw, 24px);
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
  }

  .retour {
    color: var(--ardoise);
    font-size: 0.9rem;
    font-weight: 600;
    text-decoration: none;
  }

  .releve {
    background: var(--blanc);
    border: 1px solid var(--trait);
    border-radius: var(--rayon);
    padding: clamp(20px, 4vw, 36px);
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  .releve__entete {
    display: flex;
    justify-content: space-between;
    gap: 20px;
    flex-wrap: wrap;
    border-bottom: 1px solid var(--trait);
    padding-bottom: 16px;
  }

  .releve__entete h1 {
    font-size: 1.5rem;
  }

  .releve__boutique {
    display: flex;
    flex-direction: column;
    text-align: right;
    font-size: 0.85rem;
    color: var(--ardoise);
  }

  .releve__boutique strong {
    color: var(--nuit);
    font-size: 1rem;
  }

  .releve__client {
    display: flex;
    flex-direction: column;
    gap: 2px;
    font-size: 0.9rem;
    color: var(--ardoise);
  }

  .releve__client h2 {
    font-size: 1.1rem;
    color: var(--nuit);
  }

  .releve__table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.88rem;
  }

  .releve__table th,
  .releve__table td {
    padding: 8px 6px;
    border-bottom: 1px solid var(--trait);
    text-align: left;
  }

  .releve__table th {
    font-size: 0.75rem;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--ardoise);
  }

  .releve__table .droite {
    text-align: right;
    font-variant-numeric: tabular-nums;
  }

  .releve__totaux {
    margin: 0;
    margin-left: auto;
    width: min(320px, 100%);
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .releve__totaux > div {
    display: flex;
    justify-content: space-between;
    gap: 12px;
  }

  .releve__totaux dt {
    color: var(--ardoise);
  }

  .releve__totaux dd {
    margin: 0;
    font-variant-numeric: tabular-nums;
  }

  .releve__fort {
    padding-top: 6px;
    border-top: 1px solid var(--trait);
    font-weight: 700;
  }

  .note {
    color: var(--ardoise);
    font-size: 0.85rem;
  }

  @media print {
    .ecran {
      display: none !important;
    }

    .page {
      min-height: auto;
    }

    .contenu {
      padding: 0;
      width: 100%;
    }

    .releve {
      border: 0;
      box-shadow: none;
      padding: 0;
    }
  }
</style>
