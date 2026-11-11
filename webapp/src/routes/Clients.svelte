<script lang="ts">
  import { onMount } from 'svelte'
  import {
    erreurClient,
    filtrerClients,
    roleClient,
    valeursClient,
    type ClientEnregistre,
    type FiltreRole,
    type SaisieClient,
  } from '../lib/catalogue'
  import { auth } from '../lib/session.svelte'
  import { supabase } from '../lib/supabase'
  import Barre from '../lib/ui/Barre.svelte'
  import SansBoutique from '../lib/ui/SansBoutique.svelte'

  type Filtre = { valeur: FiltreRole; libelle: string }
  const filtres: Filtre[] = [
    { valeur: 'tous', libelle: 'Tous' },
    { valeur: 'client', libelle: 'Clients' },
    { valeur: 'fournisseur', libelle: 'Fournisseurs' },
  ]

  function saisieVide(role: 'client' | 'fournisseur' = 'client'): SaisieClient {
    return {
      nom: '',
      role,
      adresse: '',
      ice: '',
      if_fiscal: '',
      rc: '',
      patente: '',
      cnss: '',
      telephone: '',
      email: '',
      exonere_tva: false,
      note: '',
    }
  }

  let clients = $state<ClientEnregistre[]>([])
  let chargement = $state(true)
  let erreur = $state<string | null>(null)
  let message = $state<string | null>(null)
  let recherche = $state('')
  let filtreRole = $state<FiltreRole>('tous')
  let ouvert = $state(false)
  let edition = $state<ClientEnregistre | null>(null)
  let saisie = $state<SaisieClient>(saisieVide())
  let enregistrement = $state(false)

  const visibles = $derived(filtrerClients(clients, recherche, filtreRole))
  const nombreClients = $derived(clients.filter((c) => c.role === 'client').length)
  const nombreFournisseurs = $derived(clients.filter((c) => c.role === 'fournisseur').length)

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
      .from('clients')
      .select('*')
      .eq('shop_id', auth.boutique.id)
      .order('nom', { ascending: true })
    chargement = false
    if (error) {
      erreur = error.message
      return
    }
    clients = (data as ClientEnregistre[]) ?? []
  }

  function ouvrirNouveau() {
    edition = null
    saisie = saisieVide(filtreRole === 'fournisseur' ? 'fournisseur' : 'client')
    ouvert = true
    message = null
  }

  function ouvrirEdition(client: ClientEnregistre) {
    edition = client
    saisie = {
      nom: client.nom,
      role: client.role,
      adresse: client.adresse ?? '',
      ice: client.ice ?? '',
      if_fiscal: client.if_fiscal ?? '',
      rc: client.rc ?? '',
      patente: client.patente ?? '',
      cnss: client.cnss ?? '',
      telephone: client.telephone ?? '',
      email: client.email ?? '',
      exonere_tva: client.exonere_tva,
      note: client.note ?? '',
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

    const probleme = erreurClient(saisie)
    if (probleme) {
      erreur = probleme
      return
    }

    erreur = null
    enregistrement = true
    const valeurs = valeursClient(saisie)
    const requete = edition
      ? supabase.from('clients').update(valeurs).eq('id', edition.id)
      : supabase.from('clients').insert({ ...valeurs, shop_id: auth.boutique.id })
    const { error } = await requete
    enregistrement = false

    if (error) {
      erreur =
        error.code === '23505'
          ? `Une fiche « ${valeurs.nom} » existe déjà dans cette boutique.`
          : error.message
      return
    }

    message = edition ? 'Fiche mise à jour.' : 'Fiche ajoutée.'
    fermer()
    await charger()
  }

  async function supprimer(client: ClientEnregistre) {
    if (!supabase) return
    if (!confirm(`Supprimer la fiche « ${client.nom} » ?`)) return
    const { error } = await supabase.from('clients').delete().eq('id', client.id)
    if (error) {
      erreur = error.message
      return
    }
    message = 'Fiche supprimée.'
    await charger()
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
          <h1>Clients et fournisseurs</h1>
          <p class="entete__texte">
            {nombreClients} client{nombreClients > 1 ? 's' : ''} ·
            {nombreFournisseurs} fournisseur{nombreFournisseurs > 1 ? 's' : ''}
          </p>
        </div>
        <button class="btn" type="button" onclick={ouvrirNouveau}>Nouvelle fiche</button>
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
            <h2>{edition ? 'Modifier la fiche' : 'Nouvelle fiche'}</h2>
            <button class="lien-discret" type="button" onclick={fermer}>Fermer</button>
          </div>

          <form onsubmit={enregistrer}>
            <div class="grille-champs">
              <div class="champ">
                <label for="f-nom">Nom <span aria-hidden="true">*</span></label>
                <input id="f-nom" required bind:value={saisie.nom} autocomplete="organization" />
              </div>
              <div class="champ">
                <label for="f-role">Type de fiche</label>
                <select id="f-role" bind:value={saisie.role}>
                  <option value="client">Client</option>
                  <option value="fournisseur">Fournisseur</option>
                </select>
              </div>
              <div class="champ">
                <label for="f-tel">Téléphone</label>
                <input id="f-tel" type="tel" bind:value={saisie.telephone} autocomplete="tel" />
              </div>
              <div class="champ">
                <label for="f-email">E-mail</label>
                <input id="f-email" type="email" bind:value={saisie.email} autocomplete="email" />
              </div>
              <div class="champ champ--large">
                <label for="f-adresse">Adresse</label>
                <textarea id="f-adresse" bind:value={saisie.adresse} rows="2"></textarea>
              </div>
              <div class="champ">
                <label for="f-ice">ICE</label>
                <input id="f-ice" bind:value={saisie.ice} inputmode="numeric" />
              </div>
              <div class="champ">
                <label for="f-if">IF</label>
                <input id="f-if" bind:value={saisie.if_fiscal} />
              </div>
              <div class="champ">
                <label for="f-rc">RC</label>
                <input id="f-rc" bind:value={saisie.rc} />
              </div>
              <div class="champ">
                <label for="f-patente">Patente</label>
                <input id="f-patente" bind:value={saisie.patente} />
              </div>
              <div class="champ">
                <label for="f-cnss">CNSS</label>
                <input id="f-cnss" bind:value={saisie.cnss} />
              </div>
              <div class="champ champ--large">
                <label for="f-note">Note</label>
                <textarea id="f-note" bind:value={saisie.note} rows="2"></textarea>
              </div>
              <label class="champ champ--case champ--large">
                <input type="checkbox" bind:checked={saisie.exonere_tva} />
                <span>Exonéré de TVA sur ses documents</span>
              </label>
            </div>

            <div class="actions">
              <button class="btn" type="submit" disabled={enregistrement || saisie.nom.trim() === ''}>
                {enregistrement ? 'Enregistrement…' : 'Enregistrer'}
              </button>
              <button class="btn btn--discret" type="button" onclick={fermer}>Annuler</button>
            </div>
          </form>
        </section>
      {/if}

      <section class="outils">
        <div class="champ champ--recherche">
          <label class="sr-only" for="recherche">Rechercher une fiche</label>
          <input
            id="recherche"
            type="search"
            bind:value={recherche}
            placeholder="Nom, téléphone, ICE…"
          />
        </div>
        <div class="segments" role="group" aria-label="Filtrer par type">
          {#each filtres as filtre}
            <button
              type="button"
              class:actif={filtreRole === filtre.valeur}
              aria-pressed={filtreRole === filtre.valeur}
              onclick={() => (filtreRole = filtre.valeur)}>{filtre.libelle}</button
            >
          {/each}
        </div>
      </section>

      {#if chargement}
        <p class="vide-texte">Chargement…</p>
      {:else if visibles.length === 0}
        <section class="carte vide">
          <h2>{clients.length === 0 ? 'Aucune fiche pour l’instant' : 'Aucun résultat'}</h2>
          <p class="vide-texte">
            {clients.length === 0
              ? 'Ajoutez un client pour le retrouver en un clic sur vos factures.'
              : 'Essayez un autre nom, ou changez de filtre.'}
          </p>
          {#if clients.length === 0}
            <button class="btn" type="button" onclick={ouvrirNouveau}>Ajouter un client</button>
          {/if}
        </section>
      {:else}
        <ul class="liste">
          {#each visibles as client (client.id)}
            <li class="carte fiche">
              <div class="fiche__texte">
                <div class="fiche__titre">
                  <h2>{client.nom}</h2>
                  <span class="etiquette" class:etiquette--fournisseur={client.role === 'fournisseur'}>
                    {roleClient(client.role)}
                  </span>
                  {#if client.exonere_tva}<span class="etiquette">Exonéré TVA</span>{/if}
                </div>
                {#if client.telephone || client.email}
                  <p class="fiche__contact">
                    {[client.telephone, client.email].filter(Boolean).join(' · ')}
                  </p>
                {/if}
                {#if client.adresse}
                  <p class="fiche__contact">{client.adresse}</p>
                {/if}
                {#if client.ice || client.if_fiscal}
                  <p class="fiche__legal">
                    {[client.ice ? `ICE ${client.ice}` : '', client.if_fiscal ? `IF ${client.if_fiscal}` : '']
                      .filter(Boolean)
                      .join(' · ')}
                  </p>
                {/if}
              </div>
              <div class="fiche__actions">
                <button class="btn btn--discret" type="button" onclick={() => ouvrirEdition(client)}>
                  Modifier
                </button>
                <button class="lien-discret lien-discret--danger" type="button" onclick={() => void supprimer(client)}>
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
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
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

  .fiche__contact,
  .fiche__legal {
    color: var(--ardoise);
    font-size: 0.9rem;
  }

  .fiche__legal {
    font-variant-numeric: tabular-nums;
  }

  .fiche__actions {
    display: flex;
    align-items: center;
    gap: 14px;
  }

  .etiquette--fournisseur {
    background: #fff4d6;
    color: #7a5300;
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
