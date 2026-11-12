<script lang="ts">
  import { onMount } from 'svelte'
  import {
    dateCourte,
    derniereConnexion,
    finDansUnAn,
    isoLocal,
    octets,
    planLisible,
    validerAbonnement,
    type BoutiqueAdmin,
    type StatistiquesAdmin,
    type UtilisateurAdmin,
  } from '../lib/admin'
  import { auth } from '../lib/session.svelte'
  import { supabase } from '../lib/supabase'
  import Barre from '../lib/ui/Barre.svelte'

  let admin = $state(false)
  let verifie = $state(false)
  let chargement = $state(true)
  let stats = $state<StatistiquesAdmin | null>(null)
  let boutiques = $state<BoutiqueAdmin[]>([])
  let comptes = $state<UtilisateurAdmin[]>([])
  let erreur = $state<string | null>(null)
  let message = $state<string | null>(null)
  let enCours = $state<string | null>(null)
  let nouvelAdmin = $state('')

  /** « Autre durée » : the same row, opened into a date and a note. */
  let edition = $state<{ boutique: BoutiqueAdmin; date: string; note: string } | null>(null)

  const nombreAdmin = $derived(comptes.filter((compte) => compte.est_admin).length)

  onMount(() => {
    void demarrer()
  })

  async function demarrer() {
    if (!supabase || !auth.user) {
      chargement = false
      verifie = true
      return
    }
    const { data, error } = await supabase.rpc('est_admin_plateforme')
    verifie = true
    if (error) {
      erreur = error.message
      chargement = false
      return
    }
    admin = data === true
    if (admin) await charger()
    else chargement = false
  }

  /*
   * Three reads, no table query: the e-mails of every account and the figures
   * across all shops come from security-definer functions (0005). A console
   * screen that queries `auth.users` would need the service role in the tab.
   */
  async function charger() {
    if (!supabase) return
    chargement = true
    erreur = null
    const [statsRes, boutiquesRes, comptesRes] = await Promise.all([
      supabase.rpc('admin_statistiques'),
      supabase.rpc('admin_boutiques'),
      supabase.rpc('admin_utilisateurs'),
    ])
    chargement = false
    const echec = statsRes.error ?? boutiquesRes.error ?? comptesRes.error
    if (echec) {
      erreur = echec.message
      return
    }
    stats = statsRes.data as StatistiquesAdmin
    boutiques = (boutiquesRes.data as BoutiqueAdmin[]) ?? []
    comptes = (comptesRes.data as UtilisateurAdmin[]) ?? []
  }

  async function ecrireAbonnement(
    boutique: BoutiqueAdmin,
    plan: 'gratuit' | 'pro',
    jusquAu: string | null,
    note: string,
  ): Promise<boolean> {
    if (!supabase) return false
    const invalide = validerAbonnement(plan, jusquAu)
    if (invalide) {
      erreur = invalide
      return false
    }
    enCours = boutique.id
    erreur = null
    message = null
    const { error } = await supabase.rpc('admin_abonnement', {
      p_shop: boutique.id,
      p_plan: plan,
      p_jusqu_au: plan === 'pro' ? jusquAu : null,
      p_note: note,
    })
    enCours = null
    if (error) {
      erreur = error.message
      return false
    }
    message =
      plan === 'pro'
        ? `« ${boutique.nom} » est en Pro jusqu’au ${dateCourte(jusquAu)}.`
        : `« ${boutique.nom} » est repassée au plan gratuit.`
    await charger()
    return true
  }

  function activerUnAn(boutique: BoutiqueAdmin) {
    void ecrireAbonnement(boutique, 'pro', finDansUnAn(), 'Activation d’un an depuis l’administration')
  }

  function desactiver(boutique: BoutiqueAdmin) {
    if (!confirm(`Repasser « ${boutique.nom} » au plan gratuit ?`)) return
    void ecrireAbonnement(boutique, 'gratuit', null, 'Retour au plan gratuit depuis l’administration')
  }

  function ouvrirEdition(boutique: BoutiqueAdmin) {
    edition = {
      boutique,
      date: boutique.pro && boutique.jusqu_au ? boutique.jusqu_au : finDansUnAn(),
      note: boutique.note,
    }
  }

  async function enregistrerEdition(event: SubmitEvent) {
    event.preventDefault()
    if (!edition) return
    const enregistre = await ecrireAbonnement(edition.boutique, 'pro', edition.date, edition.note)
    if (enregistre) edition = null
  }

  async function nommer(event: SubmitEvent) {
    event.preventDefault()
    const email = nouvelAdmin.trim()
    if (!email || !supabase) return
    enCours = 'nommer'
    erreur = null
    message = null
    const { error } = await supabase.rpc('admin_nommer', { p_email: email })
    enCours = null
    if (error) {
      erreur = error.message
      return
    }
    message = `${email} peut maintenant ouvrir /app/admin.`
    nouvelAdmin = ''
    await charger()
  }

  async function nommerCompte(compte: UtilisateurAdmin) {
    if (!compte.email || !supabase) return
    enCours = compte.id
    erreur = null
    message = null
    const { error } = await supabase.rpc('admin_nommer', { p_email: compte.email })
    enCours = null
    if (error) {
      erreur = error.message
      return
    }
    message = `${compte.email} peut maintenant ouvrir /app/admin.`
    await charger()
  }

  async function retirer(compte: UtilisateurAdmin) {
    const soi = compte.id === auth.user?.id
    const avertissement = soi
      ? `Vous allez retirer VOTRE propre accès à cette console. Continuer ?`
      : `Retirer l’accès administration à ${compte.email} ?`
    if (!confirm(avertissement)) return
    if (!supabase) return
    enCours = compte.id
    erreur = null
    message = null
    const { error } = await supabase.rpc('admin_retirer', { p_user: compte.id })
    enCours = null
    if (error) {
      erreur = error.message
      return
    }
    message = `${compte.email} n’est plus administrateur.`
    await charger()
  }
</script>

<div class="page">
  <Barre />

  <main class="contenu">
    {#if !verifie || (chargement && !stats)}
      <p class="note">Chargement…</p>
    {:else if !admin}
      <section class="carte bloc">
        <h1>Administration</h1>
        <p class="note">Cet espace est réservé aux administrateurs de la plateforme.</p>
        {#if erreur}<p class="alerte" role="alert">{erreur}</p>{/if}
      </section>
    {:else}
      <section class="entete">
        <div>
          <h1>Administration</h1>
          <p class="note">
            {stats?.boutiques ?? 0} boutique{(stats?.boutiques ?? 0) > 1 ? 's' : ''} ·
            {stats?.utilisateurs ?? 0} compte{(stats?.utilisateurs ?? 0) > 1 ? 's' : ''} ·
            {stats?.pro ?? 0} en Pro
          </p>
        </div>
        <button class="btn btn--discret" type="button" onclick={() => void charger()} disabled={chargement}>
          {chargement ? 'Chargement…' : 'Recharger'}
        </button>
      </section>

      {#if message}<p class="alerte alerte--info" role="status">{message}</p>{/if}
      {#if erreur}<p class="alerte" role="alert">{erreur}</p>{/if}

      <section class="grille" aria-label="Vue d’ensemble">
        <article class="carte chiffre">
          <p class="chiffre__valeur">{stats?.documents_emis ?? 0}</p>
          <p class="chiffre__libelle">Documents émis</p>
        </article>
        <article class="carte chiffre">
          <p class="chiffre__valeur">{stats?.documents ?? 0}</p>
          <p class="chiffre__libelle">Documents au total</p>
        </article>
        <article class="carte chiffre">
          <p class="chiffre__valeur">{stats?.archives ?? 0}</p>
          <p class="chiffre__libelle">PDF archivés · {octets(stats?.octets_archives)}</p>
        </article>
        <article class="carte chiffre">
          <p class="chiffre__valeur">{stats?.administrateurs ?? 0}</p>
          <p class="chiffre__libelle">Administrateur{(stats?.administrateurs ?? 0) > 1 ? 's' : ''}</p>
        </article>
      </section>

      <section class="carte bloc" aria-labelledby="titre-boutiques">
        <header class="bloc__entete">
          <div>
            <h2 id="titre-boutiques">Boutiques</h2>
            <p class="note">Le plan décide du quota (5 documents facturés par mois).</p>
          </div>
        </header>

        <ul class="liste">
          {#each boutiques as boutique (boutique.id)}
            <li class="boutique">
              <div class="boutique__texte">
                <h3>
                  {boutique.nom}
                  <span class="etiquette" class:etiquette--pro={boutique.pro}>{planLisible(boutique)}</span>
                </h3>
                <p class="note">
                  {boutique.proprietaire ?? 'propriétaire inconnu'} · créée le {dateCourte(boutique.cree_le)}
                </p>
                <p class="note">
                  {boutique.documents} document{boutique.documents > 1 ? 's' : ''} ·
                  {boutique.clients} client{boutique.clients > 1 ? 's' : ''} ·
                  {boutique.articles} article{boutique.articles > 1 ? 's' : ''}
                  {#if boutique.note} · {boutique.note}{/if}
                </p>
              </div>
              <div class="boutique__actions">
                {#if boutique.pro}
                  <button
                    class="btn btn--discret"
                    type="button"
                    disabled={enCours === boutique.id}
                    onclick={() => ouvrirEdition(boutique)}
                  >
                    Prolonger…
                  </button>
                  <button
                    class="lien-discret lien-discret--danger"
                    type="button"
                    disabled={enCours === boutique.id}
                    onclick={() => desactiver(boutique)}
                  >
                    Repasser en gratuit
                  </button>
                {:else}
                  <button
                    class="btn"
                    type="button"
                    disabled={enCours === boutique.id}
                    onclick={() => activerUnAn(boutique)}
                  >
                    {enCours === boutique.id ? 'Activation…' : 'Pro 1 an'}
                  </button>
                  <button
                    class="btn btn--discret"
                    type="button"
                    disabled={enCours === boutique.id}
                    onclick={() => ouvrirEdition(boutique)}
                  >
                    Autre durée…
                  </button>
                {/if}
              </div>
            </li>
          {/each}
        </ul>
      </section>

      <section class="carte bloc" aria-labelledby="titre-comptes">
        <header class="bloc__entete">
          <div>
            <h2 id="titre-comptes">Comptes</h2>
            <p class="note">
              {comptes.length} compte{comptes.length > 1 ? 's' : ''} · {nombreAdmin}
              administrateur{nombreAdmin > 1 ? 's' : ''}. Un administrateur voit cette console et peut
              activer un plan.
            </p>
          </div>
        </header>

        <form class="nommer" onsubmit={nommer}>
          <div class="champ">
            <label for="admin-email">Nommer par e-mail</label>
            <input
              id="admin-email"
              type="email"
              autocomplete="off"
              placeholder="compte@exemple.ma"
              bind:value={nouvelAdmin}
            />
          </div>
          <button
            class="btn"
            type="submit"
            disabled={enCours === 'nommer' || nouvelAdmin.trim().length === 0}
          >
            {enCours === 'nommer' ? 'Nomination…' : 'Nommer administrateur'}
          </button>
        </form>

        <ul class="liste">
          {#each comptes as compte (compte.id)}
            <li class="compte">
              <div class="compte__texte">
                <h3>
                  {compte.email ?? 'compte sans e-mail'}
                  {#if compte.id === auth.user?.id}<span class="etiquette">vous</span>{/if}
                  {#if compte.est_admin}<span class="etiquette etiquette--pro">administrateur</span>{/if}
                </h3>
                <p class="note">
                  {compte.boutique ?? 'sans boutique'} · créé le {dateCourte(compte.cree_le)} ·
                  dernière connexion : {derniereConnexion(compte.derniere_connexion)}
                </p>
              </div>
              <div class="compte__actions">
                {#if compte.est_admin}
                  <button
                    class="lien-discret lien-discret--danger"
                    type="button"
                    disabled={enCours === compte.id}
                    onclick={() => retirer(compte)}
                  >
                    Retirer
                  </button>
                {:else}
                  <button
                    class="lien-discret"
                    type="button"
                    disabled={enCours === compte.id}
                    onclick={() => nommerCompte(compte)}
                  >
                    Nommer
                  </button>
                {/if}
              </div>
            </li>
          {/each}
        </ul>
      </section>
    {/if}
  </main>
</div>

{#if edition}
  <div class="voile">
    <div class="carte dialogue" role="dialog" aria-modal="true" aria-labelledby="titre-edition">
      <form onsubmit={enregistrerEdition}>
        <h2 id="titre-edition">Pro pour « {edition.boutique.nom} »</h2>
        <div class="champ">
          <label for="fin-pro">Fin du plan Pro</label>
          <input id="fin-pro" type="date" min={isoLocal()} required bind:value={edition.date} />
        </div>
        <div class="champ">
          <label for="note-pro">Note (facultative)</label>
          <input
            id="note-pro"
            type="text"
            maxlength="120"
            placeholder="Virement reçu le 11/11, chèque n°…"
            bind:value={edition.note}
          />
        </div>
        {#if erreur}<p class="alerte" role="alert">{erreur}</p>{/if}
        <div class="dialogue__actions">
          <button class="btn btn--discret" type="button" onclick={() => (edition = null)}>Annuler</button>
          <button class="btn" type="submit">Enregistrer</button>
        </div>
      </form>
    </div>
  </div>
{/if}

<style>
  .page {
    min-height: 100dvh;
    display: flex;
    flex-direction: column;
  }

  .contenu {
    width: min(980px, 100%);
    margin: 0 auto;
    padding: clamp(24px, 5vw, 48px) clamp(16px, 4vw, 24px);
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .entete,
  .bloc__entete {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
  }

  .entete h1 {
    font-size: clamp(1.5rem, 3vw, 1.9rem);
  }

  .bloc__entete h2 {
    font-size: 1.15rem;
  }

  .bloc {
    padding: 20px 22px;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .grille {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
    gap: 10px;
  }

  .chiffre {
    padding: 14px 16px;
  }

  .chiffre__valeur {
    font-family: var(--titre);
    font-size: 1.6rem;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
  }

  .chiffre__libelle {
    color: var(--ardoise);
    font-size: 0.82rem;
  }

  .liste {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .boutique,
  .compte {
    padding: 12px 4px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
    border-top: 1px solid var(--trait);
  }

  .boutique:first-child,
  .compte:first-child {
    border-top: 0;
  }

  .boutique__texte h3,
  .compte__texte h3 {
    font-size: 1rem;
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .boutique__actions,
  .compte__actions {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .etiquette {
    font-family: var(--texte);
    font-size: 0.72rem;
    font-weight: 700;
    letter-spacing: 0.02em;
    text-transform: uppercase;
    color: var(--ardoise);
    background: var(--papier);
    border: 1px solid var(--trait);
    border-radius: 999px;
    padding: 2px 8px;
  }

  .etiquette--pro {
    color: var(--vert);
    border-color: color-mix(in srgb, var(--vert) 40%, var(--trait));
    background: color-mix(in srgb, var(--vert) 8%, var(--blanc));
  }

  .note {
    color: var(--ardoise);
    font-size: 0.88rem;
  }

  .nommer {
    display: flex;
    align-items: flex-end;
    gap: 12px;
    flex-wrap: wrap;
    padding-bottom: 4px;
  }

  .nommer .champ {
    flex: 1 1 220px;
  }

  .lien-discret {
    border: 0;
    background: none;
    font: inherit;
    font-size: 0.88rem;
    font-weight: 600;
    cursor: pointer;
    padding: 4px;
    color: var(--bleu);
  }

  .lien-discret--danger {
    color: var(--rouge);
  }

  .voile {
    position: fixed;
    inset: 0;
    background: rgb(11 27 77 / 45%);
    display: grid;
    place-items: center;
    padding: 20px;
    z-index: 20;
  }

  .dialogue {
    width: min(440px, 100%);
    padding: 22px;
  }

  .dialogue form {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .dialogue h2 {
    font-size: 1.15rem;
  }

  .dialogue__actions {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
  }
</style>
