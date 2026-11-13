<script lang="ts">
  import { onMount } from 'svelte'
  import {
    champsModifies,
    dateCourte,
    derniereConnexion,
    estActive,
    finDansUnAn,
    horodatage,
    isoLocal,
    libelleAction,
    libelleTypeDocument,
    nomPlan,
    octets,
    planLisible,
    quotaLisible,
    resumeDetails,
    validerAbonnement,
    validerChampsBoutique,
    type ChampsBoutique,
    type DocumentAdmin,
    type FicheBoutique,
    type LigneJournal,
    type MembreAdmin,
    type Plan,
  } from '../../lib/admin'
  import { dirhams } from '../../lib/money'
  import { aller } from '../../lib/router.svelte'
  import { supabase } from '../../lib/supabase'

  let { id }: { id: string } = $props()

  type Saisie = {
    nom: string
    ice: string
    if_fiscal: string
    rc: string
    patente: string
    cnss: string
    adresse: string
    telephone: string
    email: string
    banque: string
    rib: string
    rib_titulaire: string
    conditions_paiement: string
    clause_retard: string
    tva_defaut: number
    arrondi_dirham: boolean
    prix_ttc: boolean
    tva_non_applicable: boolean
    montant_lettres: boolean
    numerotation_annuelle: boolean
    design_defaut: 'classique' | 'facture_maroc'
  }

  let fiche = $state<FicheBoutique | null>(null)
  const abonne = $derived(
    fiche
      ? estActive({
          pro: fiche.pro,
          plan: fiche.abonnement.plan,
          jusqu_au: fiche.abonnement.jusqu_au,
        })
      : false,
  )
  const palierActif = $derived<Plan>(fiche && abonne ? fiche.abonnement.plan : 'gratuit')
  let membres = $state<MembreAdmin[]>([])
  let documents = $state<DocumentAdmin[]>([])
  let journal = $state<LigneJournal[]>([])
  let note = $state('')
  let bonusDocs = $state(0)
  let bonusRaison = $state('')
  let transfert = $state('')
  let suspensionRaison = $state('')
  let abonnementOuvert = $state(false)
  let palier = $state<Plan>('pro')
  let abonnementDate = $state(finDansUnAn())
  let abonnementNote = $state('')
  let purgeDate = $state('')
  let nomConfirme = $state('')
  let saisie = $state<Saisie>({
    nom: '',
    ice: '',
    if_fiscal: '',
    rc: '',
    patente: '',
    cnss: '',
    adresse: '',
    telephone: '',
    email: '',
    banque: '',
    rib: '',
    rib_titulaire: '',
    conditions_paiement: '30j',
    clause_retard: '',
    tva_defaut: 20,
    arrondi_dirham: false,
    prix_ttc: false,
    tva_non_applicable: false,
    montant_lettres: true,
    numerotation_annuelle: false,
    design_defaut: 'classique',
  })
  let chargement = $state(true)
  let enCours = $state<string | null>(null)
  let erreur = $state<string | null>(null)
  let message = $state<string | null>(null)

  onMount(() => {
    void charger()
  })

  async function charger() {
    if (!supabase) return
    chargement = true
    erreur = null
    const [ficheRes, membresRes, documentsRes, journalRes] = await Promise.all([
      supabase.rpc('admin_boutique', { p_shop: id }),
      supabase.rpc('admin_membres', { p_shop: id }),
      supabase.rpc('admin_documents', { p_shop: id, p_limite: 40, p_avant: null }),
      supabase.rpc('admin_journal', { p_limite: 40, p_shop: id }),
    ])
    chargement = false
    const echec = ficheRes.error ?? membresRes.error ?? documentsRes.error ?? journalRes.error
    if (echec) {
      erreur = echec.message
      fiche = null
      return
    }
    fiche = ficheRes.data as FicheBoutique
    membres = (membresRes.data as MembreAdmin[]) ?? []
    documents = (documentsRes.data as DocumentAdmin[]) ?? []
    journal = (journalRes.data as LigneJournal[]) ?? []
    remplirSaisie(fiche)
    bonusDocs = fiche.quota.bonus
    bonusRaison = ''
    purgeDate = isoLocal(new Date(Date.now() - 365 * 24 * 3600 * 1000))
  }

  function remplirSaisie(f: FicheBoutique) {
    const b = f.boutique
    saisie = {
      nom: b.nom ?? '',
      ice: b.ice ?? '',
      if_fiscal: b.if_fiscal ?? '',
      rc: b.rc ?? '',
      patente: b.patente ?? '',
      cnss: b.cnss ?? '',
      adresse: b.adresse ?? '',
      telephone: b.telephone ?? '',
      email: b.email ?? '',
      banque: b.banque ?? '',
      rib: b.rib ?? '',
      rib_titulaire: b.rib_titulaire ?? '',
      conditions_paiement: b.conditions_paiement ?? '30j',
      clause_retard: b.clause_retard ?? '',
      tva_defaut: Number(b.tva_defaut ?? 20),
      arrondi_dirham: Boolean(b.arrondi_dirham),
      prix_ttc: Boolean(b.prix_ttc),
      tva_non_applicable: Boolean(b.tva_non_applicable),
      montant_lettres: Boolean(b.montant_lettres),
      numerotation_annuelle: Boolean(b.numerotation_annuelle),
      design_defaut: b.design_defaut === 'facture_maroc' ? 'facture_maroc' : 'classique',
    }
  }

  function retablir() {
    if (fiche) remplirSaisie(fiche)
  }

  async function appeler(
    cle: string,
    nom: string,
    args: Record<string, unknown>,
    succes: string,
  ): Promise<boolean> {
    if (!supabase) return false
    enCours = cle
    erreur = null
    message = null
    const { error } = await supabase.rpc(nom, args)
    enCours = null
    if (error) {
      erreur = error.message
      return false
    }
    message = succes
    await charger()
    return true
  }

  async function enregistrerIdentite(event: SubmitEvent) {    event.preventDefault()
    if (!fiche) return
    const champs = champsModifies(fiche.boutique, saisie as ChampsBoutique)
    if (Object.keys(champs).length === 0) {
      message = 'Aucun changement.'
      return
    }
    const invalide = validerChampsBoutique(champs)
    if (invalide) {
      erreur = invalide
      return
    }
    await appeler('identite', 'admin_modifier_boutique', { p_shop: id, p_champs: champs }, 'Fiche enregistrée.')
  }

  async function activer(plan: Plan, date: string = finDansUnAn(), note = 'Activation depuis l’administration') {
    if (!fiche) return
    await appeler(
      'abonnement',
      'admin_abonnement',
      { p_shop: id, p_plan: plan, p_jusqu_au: date, p_note: note },
      `${nomPlan(plan)} activé jusqu’au ${dateCourte(date)}.`,
    )
  }

  async function enregistrerAbonnement(event: SubmitEvent) {
    event.preventDefault()
    const invalide = validerAbonnement(palier, palier === 'gratuit' ? null : abonnementDate)
    if (invalide) {
      erreur = invalide
      return
    }
    const ok = await appeler(
      'abonnement',
      'admin_abonnement',
      {
        p_shop: id,
        p_plan: palier,
        p_jusqu_au: palier === 'gratuit' ? null : abonnementDate,
        p_note: abonnementNote,
      },
      palier === 'gratuit'
        ? 'Boutique repassée au plan gratuit.'
        : `${nomPlan(palier)} jusqu’au ${dateCourte(abonnementDate)}.`,
    )
    if (ok) abonnementOuvert = false
  }

  async function desactiver() {
    if (!fiche) return
    if (!confirm(`Repasser « ${fiche.boutique.nom} » au plan gratuit ?`)) return
    await appeler(
      'abonnement',
      'admin_abonnement',
      { p_shop: id, p_plan: 'gratuit', p_jusqu_au: null, p_note: 'Retour au plan gratuit depuis l’administration' },
      'Boutique repassée au plan gratuit.',
    )
  }

  async function suspendre(event: SubmitEvent) {
    event.preventDefault()
    if (suspensionRaison.trim().length === 0) {
      erreur = 'Indiquez la raison de la suspension.'
      return
    }
    const ok = await appeler(
      'suspension',
      'admin_suspendre',
      { p_shop: id, p_raison: suspensionRaison.trim() },
      'Boutique suspendue : elle ne peut plus émettre.',
    )
    if (ok) suspensionRaison = ''
  }

  async function reprendre() {
    const ok = await appeler('suspension', 'admin_reprendre', { p_shop: id }, 'Suspension levée.')
    if (ok) suspensionRaison = ''
  }

  async function enregistrerBonus(event: SubmitEvent) {
    event.preventDefault()
    await appeler(
      'bonus',
      'admin_quota_bonus',
      { p_shop: id, p_documents: Math.max(0, Math.round(bonusDocs)), p_raison: bonusRaison },
      'Bonus de quota enregistré.',
    )
  }

  async function ajouterNote(event: SubmitEvent) {
    event.preventDefault()
    if (note.trim().length === 0) return
    const ok = await appeler('note', 'admin_note', { p_shop: id, p_note: note.trim() }, 'Note ajoutée.')
    if (ok) note = ''
  }

  async function changerRole(membre: MembreAdmin, role: string) {
    await appeler(
      membre.user_id,
      'admin_role_membre',
      { p_shop: id, p_user: membre.user_id, p_role: role },
      `Rôle de ${membre.email ?? 'ce compte'} : ${role}.`,
    )
  }

  async function detacher(membre: MembreAdmin) {
    if (!confirm(`Détacher ${membre.email ?? 'ce compte'} de la boutique ?`)) return
    await appeler(
      membre.user_id,
      'admin_detacher',
      { p_shop: id, p_user: membre.user_id },
      'Compte détaché.',
    )
  }

  async function transferer(event: SubmitEvent) {
    event.preventDefault()
    const email = transfert.trim()
    if (!email) return
    if (!confirm(`Transférer la propriété à ${email} ?`)) return
    const ok = await appeler(
      'transfert',
      'admin_transferer',
      { p_shop: id, p_email: email },
      `Propriété transférée à ${email}.`,
    )
    if (ok) transfert = ''
  }

  async function purger(event: SubmitEvent) {
    event.preventDefault()
    if (!purgeDate) return
    if (!confirm(`Supprimer les PDF archivés avant le ${dateCourte(purgeDate)} ? Les documents restent.`))
      return
    if (!supabase) return
    enCours = 'purge'
    erreur = null
    const { data, error } = await supabase.rpc('admin_purger_archives', {
      p_shop: id,
      p_avant: purgeDate,
    })
    enCours = null
    if (error) {
      erreur = error.message
      return
    }
    const chemins = (data as string[]) ?? []
    await effacerDuStockage(chemins)
    message = `${chemins.length} PDF supprimé${chemins.length > 1 ? 's' : ''}.`
    await charger()
  }

  async function supprimer(event: SubmitEvent) {
    event.preventDefault()
    if (!fiche) return
    if (nomConfirme.trim() !== fiche.boutique.nom) {
      erreur = 'Le nom tapé ne correspond pas exactement au nom de la boutique.'
      return
    }
    if (!confirm(`Supprimer définitivement « ${fiche.boutique.nom} » et tous ses documents ?`)) return
    if (!supabase) return
    enCours = 'suppression'
    erreur = null
    const { data, error } = await supabase.rpc('admin_supprimer_boutique', {
      p_shop: id,
      p_nom_confirme: nomConfirme.trim(),
    })
    enCours = null
    if (error) {
      erreur = error.message
      return
    }
    await effacerDuStockage((data as string[]) ?? [])
    aller('/admin/boutiques')
  }

  /** The bucket does not cascade with the shop row: the RPC returns the paths. */
  async function effacerDuStockage(chemins: string[]) {
    if (!supabase || chemins.length === 0) return
    const { error } = await supabase.storage.from('archives').remove(chemins)
    if (error) erreur = `PDF non supprimés du stockage : ${error.message}`
  }

  async function ouvrirPdf(doc: DocumentAdmin) {
    if (!supabase || !doc.archive_path) return
    enCours = doc.id
    erreur = null
    const { data, error } = await supabase.storage.from('archives').download(doc.archive_path)
    enCours = null
    if (error || !data) {
      erreur = error?.message ?? 'PDF introuvable dans le stockage.'
      return
    }
    const url = URL.createObjectURL(data)
    const lien = document.createElement('a')
    lien.href = url
    lien.download = `${doc.numero || doc.type}.pdf`
    lien.click()
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  }
</script>

{#if chargement && !fiche}
  <p class="note">Chargement…</p>
{:else if !fiche}
  <section class="carte bloc">
    <h1>Boutique introuvable</h1>
    {#if erreur}<p class="alerte" role="alert">{erreur}</p>{/if}
    <a class="btn btn--discret" href="/app/admin/boutiques">Retour à la liste</a>
  </section>
{:else}
  <section class="console__entete">
    <div>
      <p class="note"><a href="/app/admin/boutiques">← Toutes les boutiques</a></p>
      <h1>
        {fiche.boutique.nom}
        <span class="etiquette" class:etiquette--pro={fiche.pro}>{planLisible({
          pro: fiche.pro,
          jusqu_au: fiche.abonnement.jusqu_au,
          plan: fiche.abonnement.plan,
        })}</span>
        {#if fiche.suspension}<span class="etiquette etiquette--danger">Suspendue</span>{/if}
      </h1>
      <p class="note">
        {fiche.proprietaire ?? 'aucun propriétaire'} · {fiche.membres}
        membre{fiche.membres > 1 ? 's' : ''} · créée le {dateCourte(
          String(fiche.boutique.created_at ?? ''),
        )}
      </p>
    </div>
    <div class="ligne__actions">
      {#if abonne}
        <button class="btn btn--discret" type="button" onclick={() => (abonnementOuvert = !abonnementOuvert)}>
          Prolonger ou changer…
        </button>
        <button class="lien-discret lien-discret--danger" type="button" onclick={desactiver}>
          Repasser en gratuit
        </button>
      {:else}
        <button class="btn" type="button" disabled={enCours === 'abonnement'} onclick={() => void activer('essentiel')}>
          {enCours === 'abonnement' ? 'Activation…' : 'Essentiel 1 an'}
        </button>
        <button class="btn" type="button" disabled={enCours === 'abonnement'} onclick={() => void activer('pro')}>
          Pro 1 an
        </button>
        <button class="btn btn--discret" type="button" onclick={() => (abonnementOuvert = !abonnementOuvert)}>
          Autre durée…
        </button>
      {/if}
      {#if fiche.suspension}
        <button class="btn btn--discret" type="button" onclick={() => void reprendre()}>Reprendre</button>
      {:else}
        <button class="lien-discret lien-discret--danger" type="button" onclick={() => (suspensionRaison = ' ')}>
          Suspendre…
        </button>
      {/if}
    </div>
  </section>

  {#if message}<p class="alerte alerte--info" role="status">{message}</p>{/if}
  {#if erreur}<p class="alerte" role="alert">{erreur}</p>{/if}

  {#if fiche.suspension}
    <p class="alerte" role="status">
      Boutique suspendue le {horodatage(fiche.suspension.depuis)} — {fiche.suspension.raison}. Elle ne
      peut plus émettre aucun document.
    </p>
  {/if}

  {#if suspensionRaison !== ''}
    <section class="carte bloc">
      <form class="formulaire" onsubmit={suspendre}>
        <div class="champ">
          <label for="raison-suspension">Raison de la suspension</label>
          <input
            id="raison-suspension"
            type="text"
            maxlength="300"
            placeholder="Impayé, fraude signalée, demande du client…"
            bind:value={suspensionRaison}
          />
        </div>
        <p class="note">
          La suspension empêche d’émettre ; les documents déjà émis restent lisibles et
          téléchargeables.
        </p>
        <div class="formulaire__actions">
          <button class="btn btn--discret" type="button" onclick={() => (suspensionRaison = '')}>
            Annuler
          </button>
          <button class="btn" type="submit" disabled={enCours === 'suspension'}>Suspendre</button>
        </div>
      </form>
    </section>
  {/if}

  {#if abonnementOuvert}
    <section class="carte bloc">
      <form class="formulaire" onsubmit={enregistrerAbonnement}>
        <h2>Abonnement de « {fiche.boutique.nom} »</h2>
        <div class="formulaire__grille">
          <div class="champ">
            <label for="palier-abonnement">Palier</label>
            <select id="palier-abonnement" bind:value={palier}>
              <option value="gratuit">Gratuit — 5 documents par mois</option>
              <option value="essentiel">Essentiel — 50 documents par mois</option>
              <option value="pro">Pro — documents illimités</option>
            </select>
          </div>
          <div class="champ">
            <label for="fin-abonnement">Fin de l’abonnement</label>
            <input
              id="fin-abonnement"
              type="date"
              min={isoLocal()}
              required={palier !== 'gratuit'}
              disabled={palier === 'gratuit'}
              bind:value={abonnementDate}
            />
          </div>
          <div class="champ">
            <label for="note-abonnement">Note (facultative)</label>
            <input
              id="note-abonnement"
              type="text"
              maxlength="120"
              placeholder="Virement reçu le 11/11, chèque n°…"
              bind:value={abonnementNote}
            />
          </div>
        </div>
        <p class="note">
          Un palier payant exige une date future : sans elle, il vaudrait pour toujours. Le
          changement est inscrit au journal.
        </p>
        <div class="formulaire__actions">
          <button class="btn btn--discret" type="button" onclick={() => (abonnementOuvert = false)}>Annuler</button>
          <button class="btn" type="submit" disabled={enCours === 'abonnement'}>Enregistrer</button>
        </div>
      </form>
    </section>
  {/if}

  <section class="grille" aria-label="Usage de la boutique">
    <article class="carte chiffre">
      <p class="chiffre__valeur">{quotaLisible(fiche)}</p>
      <p class="chiffre__libelle">
        {nomPlan(palierActif)}{palierActif === 'gratuit' || palierActif === 'essentiel'
          ? fiche.quota.bonus
            ? ` (dont ${fiche.quota.bonus} offerts)`
            : ''
          : ''}
      </p>
    </article>
    <article class="carte chiffre">
      <p class="chiffre__valeur">{fiche.documents}</p>
      <p class="chiffre__libelle">Documents</p>
    </article>
    <article class="carte chiffre">
      <p class="chiffre__valeur">{fiche.clients}</p>
      <p class="chiffre__libelle">Clients</p>
    </article>
    <article class="carte chiffre">
      <p class="chiffre__valeur">{fiche.articles}</p>
      <p class="chiffre__libelle">Articles</p>
    </article>
    <article class="carte chiffre">
      <p class="chiffre__valeur">{fiche.archives.nombre}</p>
      <p class="chiffre__libelle">PDF · {octets(fiche.archives.octets)}</p>
    </article>
  </section>

  <section class="carte bloc" aria-labelledby="titre-identite">
    <header class="bloc__entete">
      <div>
        <h2 id="titre-identite">Identité</h2>
        <p class="note">
          Ce que la facture imprime. L’ICE et l’IF sont obligatoires à l’émission d’un document
          comptable.
        </p>
      </div>
    </header>
    <form class="formulaire" onsubmit={enregistrerIdentite}>
      <div class="formulaire__grille">
        <div class="champ">
          <label for="b-nom">Nom</label>
          <input id="b-nom" type="text" maxlength="120" required bind:value={saisie.nom} />
        </div>
        <div class="champ">
          <label for="b-ice">ICE</label>
          <input id="b-ice" type="text" inputmode="numeric" bind:value={saisie.ice} />
        </div>
        <div class="champ">
          <label for="b-if">Identifiant fiscal (IF)</label>
          <input id="b-if" type="text" bind:value={saisie.if_fiscal} />
        </div>
        <div class="champ">
          <label for="b-rc">RC</label>
          <input id="b-rc" type="text" bind:value={saisie.rc} />
        </div>
        <div class="champ">
          <label for="b-patente">Patente</label>
          <input id="b-patente" type="text" bind:value={saisie.patente} />
        </div>
        <div class="champ">
          <label for="b-cnss">CNSS</label>
          <input id="b-cnss" type="text" bind:value={saisie.cnss} />
        </div>
        <div class="champ">
          <label for="b-adresse">Adresse</label>
          <input id="b-adresse" type="text" bind:value={saisie.adresse} />
        </div>
        <div class="champ">
          <label for="b-telephone">Téléphone</label>
          <input id="b-telephone" type="tel" bind:value={saisie.telephone} />
        </div>
        <div class="champ">
          <label for="b-email">E-mail</label>
          <input id="b-email" type="email" bind:value={saisie.email} />
        </div>
        <div class="champ">
          <label for="b-banque">Banque</label>
          <input id="b-banque" type="text" bind:value={saisie.banque} />
        </div>
        <div class="champ">
          <label for="b-rib">RIB</label>
          <input id="b-rib" type="text" bind:value={saisie.rib} />
        </div>
        <div class="champ">
          <label for="b-rib-titulaire">Titulaire du RIB</label>
          <input id="b-rib-titulaire" type="text" bind:value={saisie.rib_titulaire} />
        </div>
        <div class="champ">
          <label for="b-tva">TVA par défaut (%)</label>
          <input id="b-tva" type="number" min="0" max="100" step="0.01" bind:value={saisie.tva_defaut} />
        </div>
        <div class="champ">
          <label for="b-conditions">Conditions de paiement</label>
          <input id="b-conditions" type="text" bind:value={saisie.conditions_paiement} />
        </div>
        <div class="champ">
          <label for="b-retard">Clause de retard</label>
          <input id="b-retard" type="text" bind:value={saisie.clause_retard} />
        </div>
        <div class="champ">
          <label for="b-design">Modèle par défaut</label>
          <select id="b-design" bind:value={saisie.design_defaut}>
            <option value="classique">Classique</option>
            <option value="facture_maroc">Facture Maroc</option>
          </select>
        </div>
      </div>

      <div class="formulaire__grille">
        <label class="champ champ--case"
          ><input type="checkbox" bind:checked={saisie.arrondi_dirham} /> Arrondi au dirham</label
        >
        <label class="champ champ--case"
          ><input type="checkbox" bind:checked={saisie.prix_ttc} /> Prix saisis TTC</label
        >
        <label class="champ champ--case"
          ><input type="checkbox" bind:checked={saisie.tva_non_applicable} /> TVA non applicable</label
        >
        <label class="champ champ--case"
          ><input type="checkbox" bind:checked={saisie.montant_lettres} /> Montant en lettres</label
        >
        <label class="champ champ--case"
          ><input type="checkbox" bind:checked={saisie.numerotation_annuelle} /> Numérotation annuelle</label
        >
      </div>

      <div class="formulaire__actions">
        <button class="btn btn--discret" type="button" onclick={retablir}> Rétablir </button>
        <button class="btn" type="submit" disabled={enCours === 'identite'}>
          {enCours === 'identite' ? 'Enregistrement…' : 'Enregistrer la fiche'}
        </button>
      </div>
    </form>
  </section>

  <section class="carte bloc" aria-labelledby="titre-quota">
    <header class="bloc__entete">
      <div>
        <h2 id="titre-quota">Quota</h2>
        <p class="note">
          Offrir des documents comptables au-delà du quota du palier (5 en gratuit, 50 en
          Essentiel), sans changer d’abonnement.
        </p>
      </div>
    </header>
    <form class="formulaire" onsubmit={enregistrerBonus}>
      <div class="formulaire__grille">
        <div class="champ">
          <label for="bonus-docs">Documents offerts par mois</label>
          <input id="bonus-docs" type="number" min="0" max="500" step="1" bind:value={bonusDocs} />
        </div>
        <div class="champ">
          <label for="bonus-raison">Raison</label>
          <input id="bonus-raison" type="text" maxlength="120" bind:value={bonusRaison} />
        </div>
      </div>
      <div class="formulaire__actions">
        <button class="btn" type="submit" disabled={enCours === 'bonus'}>Enregistrer le bonus</button>
      </div>
    </form>
  </section>

  <section class="carte bloc" aria-labelledby="titre-membres">
    <header class="bloc__entete">
      <div>
        <h2 id="titre-membres">Membres</h2>
        <p class="note">Qui accède à cette boutique, et avec quel rôle.</p>
      </div>
    </header>

    <table class="tableau">
      <thead>
        <tr>
          <th scope="col">Compte</th>
          <th scope="col">Rôle</th>
          <th scope="col">Depuis</th>
          <th scope="col">Dernière connexion</th>
          <th scope="col"><span class="sr-only">Actions</span></th>
        </tr>
      </thead>
      <tbody>
        {#each membres as membre (membre.user_id)}
          <tr>
            <td>{membre.email ?? membre.user_id}</td>
            <td>
              <select
                aria-label={`Rôle de ${membre.email ?? membre.user_id}`}
                value={membre.role}
                disabled={enCours === membre.user_id}
                onchange={(event) => void changerRole(membre, event.currentTarget.value)}
              >
                <option value="owner">Propriétaire</option>
                <option value="admin">Admin</option>
                <option value="membre">Membre</option>
              </select>
            </td>
            <td>{dateCourte(membre.depuis)}</td>
            <td>{derniereConnexion(membre.derniere_connexion)}</td>
            <td>
              <button
                class="lien-discret lien-discret--danger"
                type="button"
                disabled={enCours === membre.user_id}
                onclick={() => void detacher(membre)}
              >
                Détacher
              </button>
            </td>
          </tr>
        {/each}
      </tbody>
    </table>

    <form class="recherche" onsubmit={transferer}>
      <div class="champ">
        <label for="transfert-email">Transférer la propriété par e-mail</label>
        <input
          id="transfert-email"
          type="email"
          placeholder="nouveau@exemple.ma"
          autocomplete="off"
          bind:value={transfert}
        />
      </div>
      <button class="btn btn--discret" type="submit" disabled={enCours === 'transfert'}>
        Transférer
      </button>
    </form>
  </section>

  <section class="carte bloc" aria-labelledby="titre-documents">
    <header class="bloc__entete">
      <div>
        <h2 id="titre-documents">Documents</h2>
        <p class="note">Les 40 derniers. « PDF » lit l’archive telle qu’elle a été émise.</p>
      </div>
    </header>

    {#if documents.length === 0}
      <p class="vide">Aucun document.</p>
    {:else}
      <table class="tableau">
        <thead>
          <tr>
            <th scope="col">Quand</th>
            <th scope="col">Numéro</th>
            <th scope="col">Client</th>
            <th scope="col">Total</th>
            <th scope="col"><span class="sr-only">PDF</span></th>
          </tr>
        </thead>
        <tbody>
          {#each documents as document (document.id)}
            <tr>
              <td>
                {dateCourte(document.quand)}
                <span class="note">{libelleTypeDocument(document.type)}</span>
              </td>
              <td>{document.numero || '—'}</td>
              <td>{document.client || '—'}</td>
              <td>
                {dirhams(Number(document.total_ttc))}
                {#if document.statut === 'brouillon'}<span class="etiquette">brouillon</span>{/if}
              </td>
              <td>
                {#if document.archive_path}
                  <button
                    class="lien-discret"
                    type="button"
                    disabled={enCours === document.id}
                    onclick={() => void ouvrirPdf(document)}
                  >
                    PDF
                  </button>
                {:else}
                  <span class="note">sans archive</span>
                {/if}
              </td>
            </tr>
          {/each}
        </tbody>
      </table>
    {/if}
  </section>

  <section class="carte bloc" aria-labelledby="titre-notes">
    <header class="bloc__entete">
      <div>
        <h2 id="titre-notes">Notes internes</h2>
        <p class="note">Datées et signées. Elles ne sont jamais visibles par la boutique.</p>
      </div>
    </header>

    <form class="formulaire" onsubmit={ajouterNote}>
      <div class="champ">
        <label for="note-interne">Nouvelle note</label>
        <textarea
          id="note-interne"
          rows="3"
          maxlength="1000"
          placeholder="Appel du 13/11, renouvellement en janvier…"
          bind:value={note}
        ></textarea>
      </div>
      <div class="formulaire__actions">
        <button class="btn" type="submit" disabled={enCours === 'note' || note.trim().length === 0}>
          Ajouter la note
        </button>
      </div>
    </form>

    {#if fiche.notes.length === 0}
      <p class="vide">Aucune note.</p>
    {:else}
      <ul class="liste">
        {#each fiche.notes as entree (entree.id)}
          <li class="ligne">
            <div class="ligne__texte">
              <p>{entree.note}</p>
              <p class="note">{horodatage(entree.quand)} · {entree.par || '—'}</p>
            </div>
          </li>
        {/each}
      </ul>
    {/if}
  </section>

  <section class="carte bloc" aria-labelledby="titre-historique">
    <header class="bloc__entete">
      <div>
        <h2 id="titre-historique">Historique</h2>
        <p class="note">Ce que l’administration a fait sur cette boutique.</p>
      </div>
    </header>
    {#if journal.length === 0}
      <p class="vide">Aucune action enregistrée.</p>
    {:else}
      <ul class="liste">
        {#each journal as ligne (ligne.id)}
          <li class="ligne">
            <div class="ligne__texte">
              <p>{libelleAction(ligne.action)}{#if resumeDetails(ligne.action, ligne.details)} — {resumeDetails(ligne.action, ligne.details)}{/if}</p>
              <p class="note">{horodatage(ligne.quand)} · {ligne.acteur}</p>
            </div>
          </li>
        {/each}
      </ul>
    {/if}
  </section>

  <section class="carte bloc danger" aria-labelledby="titre-danger">
    <header class="bloc__entete">
      <div>
        <h2 id="titre-danger">Zone dangereuse</h2>
        <p class="note">Ces gestes ne se défont pas.</p>
      </div>
    </header>

    <form class="formulaire" onsubmit={purger}>
      <h3>Purger les PDF archivés</h3>
      <p class="note">
        Les documents restent, leur PDF est reconstruit à l’ouverture. L’archive d’origine, elle, est
        perdue.
      </p>
      <div class="formulaire__grille">
        <div class="champ">
          <label for="purge-date">Supprimer les archives antérieures au</label>
          <input id="purge-date" type="date" max={isoLocal()} bind:value={purgeDate} />
        </div>
      </div>
      <div class="formulaire__actions">
        <button class="btn btn--discret" type="submit" disabled={enCours === 'purge'}>
          {enCours === 'purge' ? 'Purge…' : 'Purger ces archives'}
        </button>
      </div>
    </form>

    <form class="formulaire" onsubmit={supprimer}>
      <h3>Supprimer la boutique</h3>
      <p class="note">
        Documents, clients, articles, archives et appartenances disparaissent. Tapez « {fiche.boutique
          .nom} » pour confirmer.
      </p>
      <div class="formulaire__grille">
        <div class="champ">
          <label for="confirmation-nom">Nom de la boutique</label>
          <input id="confirmation-nom" type="text" autocomplete="off" bind:value={nomConfirme} />
        </div>
      </div>
      <div class="formulaire__actions">
        <button
          class="btn"
          type="submit"
          disabled={enCours === 'suppression' || nomConfirme.trim() !== fiche.boutique.nom}
        >
          {enCours === 'suppression' ? 'Suppression…' : 'Supprimer définitivement'}
        </button>
      </div>
    </form>
  </section>
{/if}
