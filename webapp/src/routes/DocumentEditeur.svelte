<script lang="ts">
  import { onMount } from 'svelte'
  import {
    CONDITIONS_PAIEMENT,
    DESIGNS,
    METHODES_PAIEMENT,
    ORDRE_TYPES,
    TAUX_TVA,
    TYPES_DOCUMENT,
    type ConditionsPaiement,
    type DesignDocument,
    type MethodePaiement,
    type TypeDocument,
  } from '../lib/document'
  import {
    brouillonVide,
    enteteDeFacture,
    lignesDeFacture,
    quotaAtteint,
    saisieDeDocument,
    situationDeReglement,
    snapshotBoutique,
    type DocumentRow,
    type PaiementRow,
    type QuotaBoutique,
    type SaisieDocument,
  } from '../lib/documents'
  import * as api from '../lib/documentsApi'
  import { archiverDocument, nomFichier, telechargerArchive, telechargerBlob } from '../lib/archive'
  import { reprendreBrouillon } from '../lib/brouillonPublic'
  import { calculer, totalLigne, type Ligne } from '../lib/invoice'
  import { analyserDate } from '../lib/dates'
  import { analyserMontant, arrondi, dirhams, montant, quantiteValeur } from '../lib/money'
  import { avecExoneration, avecTaux, avecTva, horsTva, ligneDepuisArticle, reevaluerLignesTtc } from '../lib/prix'
  import { aller } from '../lib/router.svelte'
  import { auth } from '../lib/session.svelte'
  import { supabase } from '../lib/supabase'
  import type { Article, ClientEnregistre } from '../lib/catalogue'
  import Barre from '../lib/ui/Barre.svelte'
  import SansBoutique from '../lib/ui/SansBoutique.svelte'

  let { id = null }: { id?: string | null } = $props()

  let chargement = $state(true)
  let erreur = $state<string | null>(null)
  let message = $state<string | null>(null)
  let saisie = $state<SaisieDocument>(brouillonVide('facture', 20, 'classique'))
  let numeroApercu = $state('')
  let clients = $state<ClientEnregistre[]>([])
  let articles = $state<Article[]>([])
  let paiements = $state<PaiementRow[]>([])
  let quota = $state<QuotaBoutique | null>(null)
  let enregistrement = $state(false)
  let emission = $state(false)
  let clientSelection = $state('')
  let documentRow = $state<DocumentRow | null>(null)
  let travailPdf = $state(false)
  let paiementMontant = $state('')
  let paiementDate = $state('')
  let paiementMoyen = $state<MethodePaiement>('cash')
  let paiementReference = $state('')
  let encaissement = $state(false)

  const facture = $derived(saisie.facture)
  const totaux = $derived(calculer(facture))
  const estEmis = $derived(saisie.statut === 'emis')
  const modifiable = $derived(!estEmis)
  const boutique = $derived(auth.boutique)
  const prixTtc = $derived(Boolean(boutique?.prix_ttc))
  const tauxDocument = $derived(Math.max(facture.tauxTva ?? 20, 0))
  const quotaBloque = $derived(quotaAtteint(quota) && facture.type === 'facture')
  const titre = $derived(estEmis ? facture.numero || 'Document' : saisie.id ? 'Modifier le brouillon' : 'Nouveau document')

  function tauxDeLigne(ligne: Ligne): number {
    if (ligne.exoneree || facture.tvaNonApplicable || facture.client.exonereTva) return 0
    return Math.max(ligne.tauxTva ?? tauxDocument, 0)
  }

  function prixAffiche(ligne: Ligne): number {
    if (!prixTtc) return ligne.prixUnitaire
    if (ligne.prixTtc !== null && ligne.prixTtc !== undefined) return ligne.prixTtc
    return avecTva(ligne.prixUnitaire, tauxDeLigne(ligne))
  }

  onMount(() => {
    void demarrer()
  })

  async function demarrer() {
    chargement = true
    erreur = null
    const shop = auth.boutique
    if (!shop || !supabase) {
      chargement = false
      return
    }

    const [clientsRes, articlesRes, quotaRes] = await Promise.all([
      supabase.from('clients').select('*').eq('shop_id', shop.id).eq('role', 'client').order('nom'),
      supabase.from('articles').select('*').eq('shop_id', shop.id).order('libelle'),
      api.chargerQuota(shop.id),
    ])
    clients = (clientsRes.data as ClientEnregistre[]) ?? []
    articles = (articlesRes.data as Article[]) ?? []
    quota = quotaRes.donnees

    if (!id) {
      saisie = brouillonVide('facture', shop.tva_defaut ?? 20, shop.design_defaut ?? 'classique')
      const reprise = reprendreBrouillon()
      if (reprise) {
        saisie.facture.client = { nom: reprise.client }
        saisie.facture.tauxTva = reprise.taux
        saisie.facture.lignes = reprise.lignes.map((ligne) => ({ ...ligne, tauxTva: reprise.taux }))
        message =
          reprise.boutique === '' || reprise.boutique === shop.nom
            ? 'Le brouillon du générateur gratuit a été repris.'
            : `Le brouillon du générateur gratuit a été repris. Vérifiez le nom de la boutique (« ${reprise.boutique} »).`
      }
      await rafraichirNumero()
      chargement = false
      return
    }

    const docRes = await api.chargerDocument(id)
    if (docRes.erreur || !docRes.donnees) {
      erreur = docRes.erreur
      chargement = false
      return
    }
    const lignesRes = await api.chargerLignes(id)
    const paiementsRes = await api.chargerPaiementsDocument(id)
    if (lignesRes.erreur) erreur = lignesRes.erreur

    saisie = saisieDeDocument(docRes.donnees, lignesRes.donnees ?? [], paiementsRes.donnees ?? [])
    documentRow = docRes.donnees
    paiements = paiementsRes.donnees ?? []
    clientSelection = docRes.donnees.client_id ?? ''
    if (!estEmis) await rafraichirNumero()
    chargement = false
  }

  async function rafraichirNumero() {
    const shop = auth.boutique
    if (!shop || estEmis) return
    const res = await api.apercuNumero(shop.id, facture.type ?? 'facture')
    if (res.donnees) numeroApercu = res.donnees
  }

  function changerType(type: TypeDocument) {
    if (!modifiable) return
    saisie.facture = {
      ...facture,
      type,
      dateValidite: type === 'devis' ? facture.dateValidite : null,
      echeance: type === 'avoir' ? null : facture.echeance,
    }
    void rafraichirNumero()
  }

  function choisirClient(valeur: string) {
    clientSelection = valeur
    if (valeur === '') {
      saisie.facture.client = { nom: facture.client.nom }
      reajusterPrix()
      return
    }
    const client = clients.find((c) => c.id === valeur)
    if (!client) return
    saisie.facture.client = {
      id: client.id,
      nom: client.nom,
      adresse: client.adresse ?? undefined,
      telephone: client.telephone ?? undefined,
      email: client.email ?? undefined,
      ice: client.ice ?? undefined,
      ifFiscal: client.if_fiscal ?? undefined,
      rc: client.rc ?? undefined,
      patente: client.patente ?? undefined,
      cnss: client.cnss ?? undefined,
      exonereTva: client.exonere_tva,
    }
    reajusterPrix()
  }

  /** Same rule as the Android editor: a TTC-quoted price follows the buyer. */
  function reajusterPrix() {
    const shop = auth.boutique
    if (!shop?.prix_ttc) return
    const taux = shop.tva_non_applicable || facture.client.exonereTva ? 0 : shop.tva_defaut
    saisie.facture.lignes = reevaluerLignesTtc(facture.lignes, taux)
  }

  function modifierLigne(index: number, modif: Partial<Ligne>) {
    const lignes = [...facture.lignes]
    const courante = lignes[index]
    if (!courante) return
    lignes[index] = { ...courante, ...modif }
    saisie.facture.lignes = lignes
  }

  function ajouterLigne() {
    saisie.facture.lignes = [
      ...facture.lignes,
      { description: '', quantite: 1, prixUnitaire: 0, tauxTva: tauxDocument || undefined },
    ]
  }

  function supprimerLigne(index: number) {
    saisie.facture.lignes = facture.lignes.filter((_, position) => position !== index)
  }

  function ajouterArticle(index: number, articleId: string) {
    const article = articles.find((a) => a.id === articleId)
    if (!article) return
    const ligne = ligneDepuisArticle(article, prixTtc, tauxDeLigne(facture.lignes[index] ?? { description: '', quantite: 1, prixUnitaire: 0 }))
    modifierLigne(index, ligne)
  }

  function majQuantite(index: number, texte: string) {
    const valeur = analyserMontant(texte)
    if (valeur !== null && valeur >= 0) modifierLigne(index, { quantite: valeur })
  }

  function majPrix(index: number, texte: string) {
    const valeur = analyserMontant(texte)
    if (valeur === null || valeur < 0) return
    const ligne = facture.lignes[index]
    if (!ligne) return
    if (prixTtc) {
      modifierLigne(index, { prixTtc: valeur, prixUnitaire: horsTva(valeur, tauxDeLigne(ligne)) })
    } else {
      modifierLigne(index, { prixUnitaire: valeur, prixTtc: null })
    }
  }

  function majRemiseLigne(index: number, texte: string) {
    const valeur = analyserMontant(texte)
    if (valeur !== null && valeur >= 0 && valeur <= 100) modifierLigne(index, { remisePourcent: valeur })
  }

  function majTauxLigne(index: number, texte: string) {
    const ligne = facture.lignes[index]
    if (!ligne) return
    const valeur = texte === '' ? null : analyserMontant(texte)
    if (valeur === null && texte !== '') return
    const miseAJour = avecTaux(ligne, valeur)
    modifierLigne(index, miseAJour)
  }

  function exonerver(index: number, exoneree: boolean) {
    const ligne = facture.lignes[index]
    if (!ligne) return
    modifierLigne(index, avecExoneration(ligne, exoneree, tauxDocument))
  }

  function majRemiseGlobale(texte: string) {
    const valeur = analyserMontant(texte)
    if (valeur !== null && valeur >= 0 && valeur <= 100) {
      saisie.facture.remisePourcent = valeur
      if (valeur > 0) saisie.facture.remiseMontant = 0
    }
  }

  function majRemiseMontant(texte: string) {
    const valeur = analyserMontant(texte)
    if (valeur !== null && valeur >= 0) {
      saisie.facture.remiseMontant = valeur
      if (valeur > 0) saisie.facture.remisePourcent = 0
    }
  }

  function majSurchargeMontant(texte: string) {
    const valeur = analyserMontant(texte)
    if (valeur !== null && valeur >= 0) saisie.facture.surchargeMontant = valeur
  }

  function majSurchargePourcent(texte: string) {
    const valeur = analyserMontant(texte)
    if (valeur !== null && valeur >= 0 && valeur <= 100) saisie.facture.surchargePourcent = valeur
  }

  function majAcompte(texte: string) {
    const valeur = analyserMontant(texte)
    if (valeur !== null && valeur >= 0) saisie.facture.acompte = valeur
  }

  function majTauxDocument(texte: string) {
    const valeur = analyserMontant(texte)
    if (valeur !== null && valeur >= 0 && valeur <= 100) saisie.facture.tauxTva = valeur
  }

  function changerConditions(valeur: string) {
    saisie.facture.conditionsPaiement = valeur as ConditionsPaiement
  }

  function changerDesign(valeur: string) {
    saisie.design = (valeur === 'facture_maroc' ? 'facture_maroc' : 'classique') as DesignDocument
  }

  async function enregistrer(naviguer = true): Promise<string | null> {
    const shop = auth.boutique
    if (!shop) return null
    if (facture.client.nom.trim() === '') {
      erreur = 'Le nom du client est obligatoire.'
      return null
    }
    if (facture.lignes.length === 0) {
      erreur = 'Ajoutez au moins une ligne.'
      return null
    }
    if (facture.lignes.some((ligne) => ligne.description.trim() === '')) {
      erreur = 'Chaque ligne doit avoir une désignation.'
      return null
    }

    erreur = null
    message = null
    enregistrement = true
    const res = await api.enregistrerBrouillon(
      saisie.id,
      enteteDeFacture(shop.id, facture, saisie.design, totaux),
      lignesDeFacture(facture.lignes),
    )
    enregistrement = false

    if (res.erreur || !res.donnees) {
      erreur = res.erreur
      return null
    }
    saisie.id = res.donnees
    if (!id && naviguer) aller(`/documents/${res.donnees}`, { remplacer: true })
    return res.donnees
  }

  async function emettre() {
    if (!modifiable) return
    if (quotaBloque) {
      erreur = 'Le quota de 5 factures gratuites est atteint pour ce mois.'
      return
    }
    const documentId = await enregistrer(false)
    if (!documentId) return

    if (!confirm(`Émettre définitivement ce document ? Son numéro sera pris et il ne pourra plus être modifié.`)) {
      return
    }

    emission = true
    const res = await api.emettreDocument(documentId)
    emission = false
    if (res.erreur || !res.donnees) {
      erreur = res.erreur
      return
    }

    saisie.statut = 'emis'
    saisie.facture = { ...facture, numero: res.donnees.numero, date: res.donnees.date_emission }
    message = `Document émis : ${res.donnees.numero}`

    // The archive is the copy that will be reprinted. It is written now, from
    // the frozen snapshot, and never re-rendered on demand afterwards.
    const planGratuit = quota?.plan !== 'pro'
    const docRes = await api.chargerDocument(documentId)
    if (docRes.donnees) {
      documentRow = docRes.donnees
      const archive = await archiverDocument({
        documentId,
        facture: saisie.facture,
        boutique: docRes.donnees.shop_snapshot ?? snapshotBoutique(boutique ?? {}),
        design: saisie.design,
        filigrane: planGratuit ? 'FACTUREAPP · GRATUIT' : null,
      })
      if (archive.erreur) {
        message = `Document émis : ${res.donnees.numero}. Le PDF n’a pas pu être archivé (${archive.erreur}).`
      } else {
        message = `Document émis : ${res.donnees.numero}. PDF archivé.`
      }
    }

    const shop = auth.boutique
    if (shop) quota = (await api.chargerQuota(shop.id)).donnees
    if (!id) aller(`/documents/${documentId}`, { remplacer: true })
  }

  async function creerAvoir() {
    if (!saisie.id || !confirm('Créer un avoir qui crédite ce document ?')) return
    const res = await api.creerAvoir(saisie.id)
    if (res.erreur || !res.donnees) {
      erreur = res.erreur
      return
    }
    aller(`/documents/${res.donnees}`)
  }

  async function supprimer() {
    if (!saisie.id || !confirm('Supprimer ce brouillon ?')) return
    const res = await api.supprimerBrouillon(saisie.id)
    if (res.erreur) {
      erreur = res.erreur
      return
    }
    aller('/documents')
  }

  function filigrane(): string | null {
    return quota?.plan === 'pro' ? null : 'FACTUREAPP · GRATUIT'
  }

  async function genererPdfBytes(): Promise<Uint8Array | null> {
    const shop = documentRow?.shop_snapshot ?? (boutique ? snapshotBoutique(boutique) : null)
    if (!shop) return null
    const { creerPdf } = await import('../lib/pdf')
    return creerPdf({
      facture,
      boutique: shop,
      design: saisie.design,
      totaux,
      filigrane: filigrane(),
    })
  }

  async function telecharger() {
    travailPdf = true
    erreur = null
    try {
      let blob: Blob | null = null
      if (saisie.id && estEmis) {
        const archive = await telechargerArchive(saisie.id, facture.numero)
        if (archive.donnees) blob = archive.donnees
      }
      if (!blob) {
        const bytes = await genererPdfBytes()
        if (!bytes) {
          erreur = 'La boutique n’est pas chargée.'
          return
        }
        blob = new Blob([new Uint8Array(bytes)], { type: 'application/pdf' })
      }
      telechargerBlob(blob, nomFichier(facture.numero || 'apercu'))
    } finally {
      travailPdf = false
    }
  }

  async function partager() {
    travailPdf = true
    erreur = null
    try {
      const bytes = await genererPdfBytes()
      if (!bytes) return
      const nom = nomFichier(facture.numero || 'document')
      const resume = `${TYPES_DOCUMENT[facture.type ?? 'facture'].libelle} ${facture.numero || ''} — ${dirhams(totaux.netAPayer)}`
      const fichier = new File([new Uint8Array(bytes)], nom, { type: 'application/pdf' })
      if (typeof navigator.canShare === 'function' && navigator.canShare({ files: [fichier] })) {
        await navigator.share({ files: [fichier], title: nom, text: resume })
        return
      }
      const telephone = (facture.client.telephone ?? '').replace(/\D/g, '')
      const url = `https://wa.me/${telephone}?text=${encodeURIComponent(resume)}`
      window.open(url, '_blank', 'noopener')
    } finally {
      travailPdf = false
    }
  }

  const solde = $derived(situationDeReglement({ net_a_payer: totaux.netAPayer }, paiements))
  const payable = $derived(Boolean(TYPES_DOCUMENT[facture.type ?? 'facture'].payable))

  function aujourdHui(): string {
    const date = new Date()
    return `${String(date.getDate()).padStart(2, '0')}/${String(date.getMonth() + 1).padStart(2, '0')}/${date.getFullYear()}`
  }

  async function encaisserPaiement(event: SubmitEvent) {
    event.preventDefault()
    if (!saisie.id) return
    const montant = analyserMontant(paiementMontant)
    if (montant === null || montant <= 0) {
      erreur = 'Le montant encaissé doit être supérieur à zéro.'
      return
    }
    erreur = null
    encaissement = true
    const res = await api.encaisser(saisie.id, montant, paiementDate || aujourdHui(), paiementMoyen, paiementReference)
    encaissement = false
    if (res.erreur) {
      erreur = res.erreur
      return
    }
    const releve = await api.chargerPaiementsDocument(saisie.id)
    paiements = releve.donnees ?? []
    paiementMontant = ''
    paiementReference = ''
    message = 'Paiement enregistré.'
  }

  async function annulerPaiement(id: string) {
    if (!confirm('Annuler ce paiement ?')) return
    const res = await api.annulerPaiement(id)
    if (res.erreur) {
      erreur = res.erreur
      return
    }
    if (saisie.id) paiements = (await api.chargerPaiementsDocument(saisie.id)).donnees ?? []
    message = 'Paiement annulé.'
  }

  async function telechargerRecu(paiement: PaiementRow) {
    if (!boutique) return
    const shop = documentRow?.shop_snapshot ?? snapshotBoutique(boutique)
    travailPdf = true
    try {
      const { creerRecuPdf } = await import('../lib/pdf')
      const bytes = await creerRecuPdf({
        facture,
        boutique: shop,
        paiement: {
          montant: Number(paiement.montant),
          date: paiement.date_paiement,
          moyen: paiement.moyen,
          reference: paiement.reference,
        },
      })
      telechargerBlob(
        new Blob([new Uint8Array(bytes)], { type: 'application/pdf' }),
        nomFichier(`recu-${facture.numero || 'document'}-${paiement.date_paiement.replace(/\//g, '-')}`),
      )
    } finally {
      travailPdf = false
    }
  }
</script>

<div class="page">
  <Barre />

  <main class="contenu">
    {#if !auth.boutique}
      <SansBoutique />
    {:else if chargement}
      <p class="vide-texte">Chargement…</p>
    {:else}
      <a class="retour" href="/app/documents">← Documents</a>

      <section class="entete">
        <div>
          <p class="surtitre">
            {TYPES_DOCUMENT[facture.type ?? 'facture'].libelle}
            {#if estEmis}<span class="etiquette etiquette--emis">Émis</span>{/if}
            {#if !estEmis && numeroApercu}<span class="etiquette">{numeroApercu}</span>{/if}
          </p>
          <h1>{titre}</h1>
          {#if !estEmis && numeroApercu}
            <p class="entete__note">Numéro réservé à l’émission : {numeroApercu}</p>
          {/if}
        </div>
        <div class="entete__actions">
          <button class="btn btn--discret" type="button" onclick={() => void telecharger()} disabled={travailPdf}>
            {travailPdf ? 'PDF…' : estEmis ? 'Télécharger le PDF' : 'Aperçu PDF'}
          </button>
          {#if estEmis}
            <button class="btn btn--discret" type="button" onclick={() => void partager()} disabled={travailPdf}>
              Partager
            </button>
          {/if}
          {#if modifiable}
            <button class="btn btn--discret" type="button" onclick={() => void enregistrer()} disabled={enregistrement}>
              {enregistrement ? 'Enregistrement…' : 'Enregistrer le brouillon'}
            </button>
            <button class="btn" type="button" onclick={() => void emettre()} disabled={emission || quotaBloque}>
              {emission ? 'Émission…' : 'Émettre'}
            </button>
          {:else}
            <button class="btn btn--discret" type="button" onclick={() => void creerAvoir()}>Créer un avoir</button>
          {/if}
        </div>
      </section>

      {#if quotaBloque && modifiable}
        <p class="alerte" role="alert">
          Le quota de 5 factures gratuites est atteint pour ce mois.
          <a href="/app/tarifs">Passer au plan Pro</a> pour émettre cette facture.
        </p>
      {/if}
      {#if message}<p class="alerte alerte--info" role="status">{message}</p>{/if}
      {#if erreur}<p class="alerte" role="alert">{erreur}</p>{/if}

      <div class="editeur">
        <div class="principal">
          <section class="carte bloc">
            <h2>En-tête</h2>
            <div class="grille-champs">
              <div class="champ">
                <label for="d-type">Type de document</label>
                <select id="d-type" value={facture.type ?? 'facture'} disabled={!modifiable} onchange={(e) => changerType((e.currentTarget.value as TypeDocument))}>
                  {#each ORDRE_TYPES as type}
                    <option value={type}>{TYPES_DOCUMENT[type].libelle}</option>
                  {/each}
                </select>
              </div>
              <div class="champ">
                <label for="d-date">Date d’émission</label>
                <input id="d-date" bind:value={saisie.facture.date} disabled={!modifiable} placeholder="JJ/MM/AAAA" />
              </div>
              <div class="champ">
                <label for="d-client">Client</label>
                <select id="d-client" value={clientSelection} disabled={!modifiable} onchange={(e) => choisirClient(e.currentTarget.value)}>
                  <option value="">Sans fiche (nom libre)</option>
                  {#each clients as client}
                    <option value={client.id}>{client.nom}</option>
                  {/each}
                </select>
              </div>
              <div class="champ">
                <label for="d-nom">Nom sur le document</label>
                <input id="d-nom" bind:value={saisie.facture.client.nom} disabled={!modifiable} autocomplete="organization" />
              </div>
              <div class="champ champ--large">
                <label for="d-adresse">Adresse</label>
                <textarea id="d-adresse" rows="2" bind:value={saisie.facture.client.adresse} disabled={!modifiable}></textarea>
              </div>
              <div class="champ">
                <label for="d-ice">ICE</label>
                <input id="d-ice" bind:value={saisie.facture.client.ice} disabled={!modifiable} inputmode="numeric" />
              </div>
              <div class="champ">
                <label for="d-if">IF</label>
                <input id="d-if" bind:value={saisie.facture.client.ifFiscal} disabled={!modifiable} />
              </div>
              <div class="champ">
                <label for="d-rc">RC</label>
                <input id="d-rc" bind:value={saisie.facture.client.rc} disabled={!modifiable} />
              </div>
              <div class="champ">
                <label for="d-tel">Téléphone</label>
                <input id="d-tel" bind:value={saisie.facture.client.telephone} disabled={!modifiable} />
              </div>
              <label class="champ champ--case champ--large">
                <input type="checkbox" bind:checked={saisie.facture.client.exonereTva} disabled={!modifiable} onchange={reajusterPrix} />
                <span>Client exonéré de TVA</span>
              </label>
            </div>
          </section>

          <section class="carte bloc">
            <div class="bloc__entete">
              <h2>Lignes</h2>
              {#if modifiable}
                <button class="btn btn--discret" type="button" onclick={ajouterLigne}>Ajouter une ligne</button>
              {/if}
            </div>

            {#if facture.lignes.length === 0}
              <p class="vide-texte">Aucune ligne. Ajoutez un article ou une prestation.</p>
            {:else}
              <div class="lignes" class:lignes--lecture={!modifiable}>
                <div class="ligne ligne--entete" aria-hidden="true">
                  <span>Désignation</span>
                  <span>Qté</span>
                  <span>{prixTtc ? 'P.U. TTC' : 'P.U. HT'}</span>
                  <span>Rem. %</span>
                  <span>TVA %</span>
                  <span>Total HT</span>
                  <span></span>
                </div>
                {#each facture.lignes as ligne, index (index)}
                  <div class="ligne">
                    <div class="ligne__designation">
                      {#if modifiable}
                        <select aria-label="Choisir un article" value="" onchange={(e) => { ajouterArticle(index, e.currentTarget.value); e.currentTarget.value = '' }}>
                          <option value="">Article…</option>
                          {#each articles as article}
                            <option value={article.id}>{article.libelle}</option>
                          {/each}
                        </select>
                      {/if}
                      <input
                        aria-label="Désignation"
                        bind:value={ligne.description}
                        disabled={!modifiable}
                        placeholder="Désignation"
                      />
                    </div>
                    <input
                      class="nombre"
                      aria-label="Quantité"
                      value={quantiteValeur(ligne.quantite)}
                      disabled={!modifiable}
                      inputmode="decimal"
                      onchange={(e) => majQuantite(index, e.currentTarget.value)}
                    />
                    <input
                      class="nombre"
                      aria-label={prixTtc ? 'Prix unitaire TTC' : 'Prix unitaire HT'}
                      value={prixAffiche(ligne)}
                      disabled={!modifiable}
                      inputmode="decimal"
                      onchange={(e) => majPrix(index, e.currentTarget.value)}
                    />
                    <input
                      class="nombre"
                      aria-label="Remise de la ligne en pourcentage"
                      value={ligne.remisePourcent ?? 0}
                      disabled={!modifiable}
                      inputmode="decimal"
                      onchange={(e) => majRemiseLigne(index, e.currentTarget.value)}
                    />
                    <input
                      class="nombre"
                      aria-label="Taux de TVA de la ligne"
                      value={ligne.tauxTva ?? ''}
                      disabled={!modifiable}
                      inputmode="decimal"
                      placeholder={String(tauxDocument)}
                      onchange={(e) => majTauxLigne(index, e.currentTarget.value)}
                    />
                    <span class="ligne__total">{dirhams(arrondi(totalLigne(ligne)))}</span>
                    <div class="ligne__outils">
                      <label class="case-mini" title="Ligne exonérée de TVA">
                        <input
                          type="checkbox"
                          checked={Boolean(ligne.exoneree)}
                          disabled={!modifiable}
                          onchange={(e) => exonerver(index, e.currentTarget.checked)}
                        />
                        <span>Exo.</span>
                      </label>
                      {#if modifiable}
                        <button class="lien-discret lien-discret--danger" type="button" onclick={() => supprimerLigne(index)} aria-label="Supprimer la ligne">
                          ✕
                        </button>
                      {/if}
                    </div>
                  </div>
                {/each}
              </div>
            {/if}
          </section>

          <section class="carte bloc">
            <h2>Dates et conditions</h2>
            <div class="grille-champs">
              <div class="champ">
                <label for="d-echeance">Échéance</label>
                <input id="d-echeance" bind:value={saisie.facture.echeance} disabled={!modifiable || facture.type === 'avoir'} placeholder="JJ/MM/AAAA" />
                {#if saisie.facture.echeance && !analyserDate(saisie.facture.echeance)}
                  <span class="note note--erreur">Date invalide.</span>
                {/if}
              </div>
              {#if facture.type === 'devis'}
                <div class="champ">
                  <label for="d-validite">Valable jusqu’au</label>
                  <input id="d-validite" bind:value={saisie.facture.dateValidite} disabled={!modifiable} placeholder="JJ/MM/AAAA" />
                </div>
              {/if}
              <div class="champ">
                <label for="d-conditions">Conditions de paiement</label>
                <select id="d-conditions" value={facture.conditionsPaiement ?? '30j'} disabled={!modifiable} onchange={(e) => changerConditions(e.currentTarget.value)}>
                  {#each Object.entries(CONDITIONS_PAIEMENT) as [idCondition, meta]}
                    <option value={idCondition}>{meta.libelle}</option>
                  {/each}
                </select>
              </div>
              <div class="champ">
                <label for="d-reference">Référence</label>
                <input id="d-reference" bind:value={saisie.facture.reference} disabled={!modifiable} />
              </div>
              <div class="champ">
                <label for="d-design">Modèle</label>
                <select id="d-design" value={saisie.design} disabled={!modifiable} onchange={(e) => changerDesign(e.currentTarget.value)}>
                  {#each Object.entries(DESIGNS) as [idDesign, libelle]}
                    <option value={idDesign}>{libelle}</option>
                  {/each}
                </select>
              </div>
              <div class="champ">
                <label for="d-taux">TVA par défaut</label>
                <select id="d-taux" value={String(facture.tauxTva ?? 20)} disabled={!modifiable} onchange={(e) => majTauxDocument(e.currentTarget.value)}>
                  {#each TAUX_TVA as taux}
                    <option value={String(taux)}>{taux} %</option>
                  {/each}
                </select>
              </div>
              <label class="champ champ--case">
                <input type="checkbox" bind:checked={saisie.facture.tvaNonApplicable} disabled={!modifiable} />
                <span>TVA non applicable</span>
              </label>
              <label class="champ champ--case">
                <input type="checkbox" bind:checked={saisie.facture.arrondiDirham} disabled={!modifiable} />
                <span>Arrondir le total au dirham</span>
              </label>
            </div>
          </section>

          <details class="carte bloc" open={!estEmis}>
            <summary><h2>Remise, frais et notes</h2></summary>
            <div class="grille-champs">
              <div class="champ">
                <label for="d-remise">Remise globale (%)</label>
                <input id="d-remise" value={facture.remisePourcent ?? 0} disabled={!modifiable} inputmode="decimal" onchange={(e) => majRemiseGlobale(e.currentTarget.value)} />
              </div>
              <div class="champ">
                <label for="d-remise-montant">Remise globale (DH)</label>
                <input id="d-remise-montant" value={facture.remiseMontant ?? 0} disabled={!modifiable} inputmode="decimal" onchange={(e) => majRemiseMontant(e.currentTarget.value)} />
              </div>
              <div class="champ">
                <label for="d-surcharge">Frais (DH)</label>
                <input id="d-surcharge" value={facture.surchargeMontant ?? 0} disabled={!modifiable} inputmode="decimal" onchange={(e) => majSurchargeMontant(e.currentTarget.value)} />
              </div>
              <div class="champ">
                <label for="d-surcharge-libelle">Libellé des frais</label>
                <input id="d-surcharge-libelle" bind:value={saisie.facture.surchargeLibelle} disabled={!modifiable} placeholder="Livraison" />
              </div>
              <div class="champ">
                <label for="d-acompte">Acompte (DH)</label>
                <input id="d-acompte" value={facture.acompte ?? 0} disabled={!modifiable} inputmode="decimal" onchange={(e) => majAcompte(e.currentTarget.value)} />
              </div>
              <div class="champ">
                <label for="d-acompte-ref">Référence de l’acompte</label>
                <input id="d-acompte-ref" bind:value={saisie.facture.acompteReference} disabled={!modifiable} />
              </div>
              <div class="champ champ--large">
                <label for="d-note">Note au client</label>
                <textarea id="d-note" rows="2" bind:value={saisie.facture.noteClient} disabled={!modifiable}></textarea>
              </div>
              <div class="champ champ--large">
                <label for="d-merci">Remerciement</label>
                <input id="d-merci" bind:value={saisie.facture.noteRemerciement} disabled={!modifiable} />
              </div>
              <div class="champ champ--large">
                <label for="d-retard">Clause de retard</label>
                <textarea id="d-retard" rows="2" bind:value={saisie.facture.clauseRetard} disabled={!modifiable}></textarea>
              </div>
            </div>
          </details>
        </div>

        <aside class="cote">
          <section class="carte totaux">
            <h2>Totaux</h2>
            <dl>
              <div><dt>Sous-total</dt><dd>{dirhams(totaux.sousTotal)}</dd></div>
              {#if totaux.afficheRemise}
                <div><dt>Remise</dt><dd>−{dirhams(totaux.remise)}</dd></div>
              {/if}
              <div><dt>Total HT</dt><dd>{dirhams(totaux.totalHt)}</dd></div>
              {#if totaux.surcharge > 0}
                <div><dt>Frais</dt><dd>{dirhams(totaux.surcharge)}</dd></div>
              {/if}
              {#each totaux.lignesTvaImprimees as tva}
                <div><dt>TVA {tva.taux} %</dt><dd>{dirhams(tva.montant)}</dd></div>
              {/each}
              <div class="totaux__fort"><dt>Total TTC</dt><dd>{dirhams(totaux.total)}</dd></div>
              {#if totaux.acompte > 0}
                <div><dt>Acompte</dt><dd>−{dirhams(totaux.acompte)}</dd></div>
              {/if}
              <div class="totaux__fort"><dt>Net à payer</dt><dd>{dirhams(totaux.netAPayer)}</dd></div>
            </dl>

            {#if estEmis}
              <div class="reglement">
                <span class="etiquette" class:etiquette--paye={solde.etatPaiement === 'paid'}>
                  {solde.etatPaiement === 'paid' ? 'Payée' : solde.etatPaiement === 'partial' ? `${dirhams(solde.reste)} dû` : 'Impayée'}
                </span>
              </div>
            {/if}

            {#if quota && !estEmis}
              <p class="note">{quota.plan === 'pro' ? 'Plan Pro' : `${quota.utilise} / ${quota.limite} factures ce mois`}</p>
            {/if}

            {#if modifiable}
              <div class="totaux__actions">
                <button class="btn" type="button" onclick={() => void enregistrer()} disabled={enregistrement}>Enregistrer</button>
                {#if saisie.id}
                  <button class="lien-discret lien-discret--danger" type="button" onclick={() => void supprimer()}>
                    Supprimer le brouillon
                  </button>
                {/if}
              </div>
            {/if}
          </section>

          {#if estEmis && payable && saisie.id}
            <section class="carte totaux">
              <h2>Paiements</h2>
              {#if paiements.length === 0}
                <p class="note">Aucun encaissement pour l’instant.</p>
              {:else}
                <ul class="paiements">
                  {#each paiements as paiement (paiement.id)}
                    <li>
                      <div>
                        <strong>{dirhams(Number(paiement.montant))}</strong>
                        <span>{paiement.date_paiement} · {METHODES_PAIEMENT[paiement.moyen] ?? paiement.moyen}</span>
                        {#if paiement.reference}<span>{paiement.reference}</span>{/if}
                      </div>
                      <button class="lien-discret lien-discret--danger" type="button" onclick={() => void annulerPaiement(paiement.id)}>
                        Annuler
                      </button>
                      <button class="lien-discret" type="button" onclick={() => void telechargerRecu(paiement)}>
                        Reçu
                      </button>
                    </li>
                  {/each}
                </ul>
              {/if}

              {#if solde.reste > 0}
                <form class="paiement-form" onsubmit={encaisserPaiement}>
                  <div class="champ">
                    <label for="p-montant">Montant encaissé (DH)</label>
                    <input id="p-montant" bind:value={paiementMontant} placeholder={montant(solde.reste)} inputmode="decimal" required />
                  </div>
                  <div class="champ">
                    <label for="p-date">Date</label>
                    <input id="p-date" bind:value={paiementDate} placeholder={aujourdHui()} />
                  </div>
                  <div class="champ">
                    <label for="p-moyen">Moyen</label>
                    <select id="p-moyen" bind:value={paiementMoyen}>
                      {#each Object.entries(METHODES_PAIEMENT) as [idMoyen, libelle]}
                        <option value={idMoyen}>{libelle}</option>
                      {/each}
                    </select>
                  </div>
                  <div class="champ">
                    <label for="p-reference">Référence</label>
                    <input id="p-reference" bind:value={paiementReference} />
                  </div>
                  <button class="btn" type="submit" disabled={encaissement}>
                    {encaissement ? 'Enregistrement…' : 'Encaisser'}
                  </button>
                </form>
              {:else}
                <p class="note">Document entièrement payé.</p>
              {/if}
            </section>
          {/if}
        </aside>
      </div>
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
    width: min(1100px, 100%);
    margin: 0 auto;
    padding: clamp(20px, 4vw, 40px) clamp(16px, 4vw, 24px);
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .retour {
    font-size: 0.9rem;
    font-weight: 600;
    text-decoration: none;
    color: var(--ardoise);
  }

  .entete {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
  }

  .surtitre {
    display: flex;
    align-items: center;
    gap: 8px;
    color: var(--ardoise);
    font-size: 0.85rem;
    text-transform: uppercase;
    letter-spacing: 0.06em;
  }

  .entete h1 {
    font-size: clamp(1.5rem, 3vw, 1.9rem);
    margin-top: 2px;
  }

  .entete__note {
    color: var(--ardoise);
    font-size: 0.85rem;
    margin-top: 4px;
  }

  .entete__actions {
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
  }

  .etiquette--emis {
    background: #e6f5ee;
    color: var(--vert);
  }

  .etiquette--paye {
    background: #e6f5ee;
    color: var(--vert);
  }

  .editeur {
    display: grid;
    gap: 16px;
    grid-template-columns: 1fr;
    align-items: start;
  }

  .principal {
    display: flex;
    flex-direction: column;
    gap: 16px;
    min-width: 0;
  }

  .bloc {
    padding: 20px;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .bloc h2 {
    font-size: 1.05rem;
  }

  .bloc__entete {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
  }

  summary {
    cursor: pointer;
    list-style: none;
  }

  summary::-webkit-details-marker {
    display: none;
  }

  summary h2 {
    display: inline;
  }

  .grille-champs {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  }

  .champ--large {
    grid-column: 1 / -1;
  }

  .lignes {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .ligne {
    display: grid;
    gap: 8px;
    align-items: center;
    grid-template-columns: 1fr;
    padding: 10px;
    border: 1px solid var(--trait);
    border-radius: 10px;
    background: var(--papier);
  }

  .ligne--entete {
    display: none;
  }

  .ligne__designation {
    display: flex;
    gap: 6px;
    min-width: 0;
  }

  .ligne__designation input {
    flex: 1;
    min-width: 0;
  }

  .ligne select,
  .ligne input {
    min-height: 40px;
    padding: 0 10px;
    border: 1px solid var(--trait);
    border-radius: 8px;
    background: var(--blanc);
    color: var(--nuit);
    font: inherit;
    width: 100%;
  }

  .ligne input.nombre {
    text-align: right;
    font-variant-numeric: tabular-nums;
  }

  .ligne__total {
    font-weight: 700;
    font-variant-numeric: tabular-nums;
    text-align: right;
  }

  .ligne__outils {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  .case-mini {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 0.78rem;
    color: var(--ardoise);
  }

  .case-mini input {
    width: 16px;
    height: 16px;
    accent-color: var(--bleu);
  }

  .lien-discret {
    border: 0;
    background: none;
    color: var(--bleu);
    font: inherit;
    font-size: 0.88rem;
    font-weight: 600;
    cursor: pointer;
    padding: 4px;
  }

  .lien-discret--danger {
    color: var(--rouge);
  }

  .cote {
    min-width: 0;
  }

  .totaux {
    padding: 20px;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .totaux h2 {
    font-size: 1.05rem;
  }

  .totaux dl {
    margin: 0;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .totaux dl > div {
    display: flex;
    justify-content: space-between;
    gap: 12px;
  }

  .totaux dt {
    color: var(--ardoise);
  }

  .totaux dd {
    margin: 0;
    font-variant-numeric: tabular-nums;
  }

  .totaux__fort {
    padding-top: 6px;
    border-top: 1px solid var(--trait);
    font-weight: 700;
  }

  .reglement {
    padding-top: 8px;
    border-top: 1px solid var(--trait);
  }

  .totaux__actions {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  .paiements {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .paiements li {
    display: flex;
    justify-content: space-between;
    gap: 8px;
    align-items: flex-start;
    padding-bottom: 6px;
    border-bottom: 1px solid var(--trait);
  }

  .paiements li > div {
    display: flex;
    flex-direction: column;
    font-size: 0.85rem;
    color: var(--ardoise);
  }

  .paiements strong {
    color: var(--nuit);
    font-variant-numeric: tabular-nums;
  }

  .paiement-form {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .note {
    color: var(--ardoise);
    font-size: 0.82rem;
  }

  .note--erreur {
    color: var(--rouge);
  }

  .vide-texte {
    color: var(--ardoise);
  }

  @media (min-width: 700px) {
    .editeur {
      grid-template-columns: minmax(0, 1fr) 280px;
    }

    .ligne {
      border: 0;
      background: none;
      padding: 0;
      grid-template-columns: minmax(0, 1fr) 64px 92px 64px 64px 100px 74px;
    }

    .ligne--entete {
      display: grid;
      font-size: 0.75rem;
      font-weight: 600;
      color: var(--ardoise);
      padding-bottom: 2px;
      border-bottom: 1px solid var(--trait);
    }

    .ligne--entete span {
      padding: 0 4px;
    }

    .ligne--entete span:nth-child(n + 2) {
      text-align: right;
    }

    .cote {
      position: sticky;
      top: 16px;
    }
  }
</style>
