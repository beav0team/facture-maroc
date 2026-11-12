<script lang="ts">
  import { onMount } from 'svelte'
  import { DESIGNS, ORDRE_TYPES, TYPES_DOCUMENT, designDepuisId, type DesignDocument } from '../lib/document'
  import { prefixeValide } from '../lib/numbering'
  import { analyserMontant } from '../lib/money'
  import { auth, chargerBoutique, enregistrerBoutique } from '../lib/session.svelte'
  import { BUCKET_LOGOS, cheminLogo, urlLogo } from '../lib/storage'
  import { supabase } from '../lib/supabase'
  import Barre from '../lib/ui/Barre.svelte'
  import SansBoutique from '../lib/ui/SansBoutique.svelte'

  let exportation = $state(false)

  type Saisie = {
    nom: string
    adresse: string
    telephone: string
    email: string
    ice: string
    if_fiscal: string
    rc: string
    patente: string
    cnss: string
    banque: string
    rib: string
    rib_titulaire: string
    clause_retard: string
    tva_defaut: string
    tva_non_applicable: boolean
    prix_ttc: boolean
    conditions_paiement: string
    numerotation_annuelle: boolean
    design_defaut: DesignDocument
    prefixes: Record<string, string>
  }

  const conditions = [
    { valeur: 'cash', libelle: 'Comptant' },
    { valeur: '30j', libelle: '30 jours' },
    { valeur: '60j', libelle: '60 jours' },
    { valeur: 'fin_de_mois', libelle: 'Fin de mois' },
    { valeur: 'custom', libelle: 'Texte libre' },
  ]

  let saisie = $state<Saisie>({
    nom: '',
    adresse: '',
    telephone: '',
    email: '',
    ice: '',
    if_fiscal: '',
    rc: '',
    patente: '',
    cnss: '',
    banque: '',
    rib: '',
    rib_titulaire: '',
    clause_retard: '',
    tva_defaut: '20',
    tva_non_applicable: false,
    prix_ttc: false,
    conditions_paiement: '30j',
    numerotation_annuelle: false,
    design_defaut: 'classique',
    prefixes: {},
  })
  let erreur = $state<string | null>(null)
  let message = $state<string | null>(null)
  let enregistrement = $state(false)
  let envoiLogo = $state(false)
  let logo = $state<string | null>(null)

  onMount(() => {
    remplir()
  })

  function remplir() {
    const boutique = auth.boutique
    if (!boutique) return
    saisie = {
      nom: boutique.nom,
      adresse: boutique.adresse ?? '',
      telephone: boutique.telephone ?? '',
      email: boutique.email ?? '',
      ice: boutique.ice ?? '',
      if_fiscal: boutique.if_fiscal ?? '',
      rc: boutique.rc ?? '',
      patente: boutique.patente ?? '',
      cnss: boutique.cnss ?? '',
      banque: boutique.banque ?? '',
      rib: boutique.rib ?? '',
      rib_titulaire: boutique.rib_titulaire ?? '',
      clause_retard: boutique.clause_retard ?? '',
      tva_defaut: String(boutique.tva_defaut).replace('.', ','),
      tva_non_applicable: boutique.tva_non_applicable,
      prix_ttc: boutique.prix_ttc,
      conditions_paiement: boutique.conditions_paiement,
      numerotation_annuelle: Boolean(boutique.numerotation_annuelle),
      design_defaut: designDepuisId(boutique.design_defaut),
      prefixes: { ...(boutique.prefixes ?? {}) },
    }
    logo = urlLogo(boutique.logo_path)
  }

  function texte(valeur: string): string | null {
    const propre = valeur.trim()
    return propre === '' ? null : propre
  }

  async function enregistrer(event: SubmitEvent) {
    event.preventDefault()
    erreur = null
    message = null

    const nom = saisie.nom.trim()
    if (nom === '') {
      erreur = 'Le nom de la boutique est obligatoire.'
      return
    }

    const tva = analyserMontant(saisie.tva_defaut)
    if (tva === null || tva < 0 || tva > 100) {
      erreur = 'Le taux de TVA doit être un nombre entre 0 et 100.'
      return
    }

    for (const type of ORDRE_TYPES) {
      const prefixe = saisie.prefixes[type]?.trim() ?? ''
      if (prefixe !== '' && !prefixeValide(prefixe.replace(/-+$/, ''))) {
        erreur = `Le préfixe « ${prefixe} » du document « ${TYPES_DOCUMENT[type].libelle} » contient un caractère interdit.`
        return
      }
    }

    enregistrement = true
    const probleme = await enregistrerBoutique({
      nom,
      adresse: texte(saisie.adresse),
      telephone: texte(saisie.telephone),
      email: texte(saisie.email),
      ice: texte(saisie.ice),
      if_fiscal: texte(saisie.if_fiscal),
      rc: texte(saisie.rc),
      patente: texte(saisie.patente),
      cnss: texte(saisie.cnss),
      banque: texte(saisie.banque),
      rib: texte(saisie.rib),
      rib_titulaire: texte(saisie.rib_titulaire),
      clause_retard: texte(saisie.clause_retard),
      tva_defaut: Math.round(tva * 100) / 100,
      tva_non_applicable: saisie.tva_non_applicable,
      prix_ttc: saisie.prix_ttc,
      conditions_paiement: saisie.conditions_paiement,
      numerotation_annuelle: saisie.numerotation_annuelle,
      design_defaut: saisie.design_defaut,
      prefixes: prefixesPropres(),
    })
    enregistrement = false

    if (probleme) {
      erreur = probleme
      return
    }
    remplir()
    message = 'Réglages enregistrés.'
  }

  async function envoyerLogo(event: Event) {
    const champ = event.target as HTMLInputElement
    const fichier = champ.files?.[0]
    champ.value = ''
    const boutique = auth.boutique
    if (!fichier || !supabase || !boutique) return

    erreur = null
    message = null

    if (!fichier.type.startsWith('image/')) {
      erreur = 'Le logo doit être une image (PNG, JPG, SVG…).'
      return
    }
    if (fichier.size > 2 * 1024 * 1024) {
      erreur = 'Le logo ne doit pas dépasser 2 Mo.'
      return
    }

    const ancien = boutique.logo_path
    const chemin = cheminLogo(boutique.id, fichier.name)
    envoiLogo = true
    const envoi = await supabase.storage
      .from(BUCKET_LOGOS)
      .upload(chemin, fichier, { contentType: fichier.type, upsert: false })
    if (envoi.error) {
      envoiLogo = false
      erreur = `Envoi du logo impossible : ${envoi.error.message}`
      return
    }

    const { error } = await supabase.from('shops').update({ logo_path: chemin }).eq('id', boutique.id)
    envoiLogo = false

    if (error) {
      erreur = error.message
      return
    }

    if (ancien && ancien !== chemin) {
      void supabase.storage.from(BUCKET_LOGOS).remove([ancien])
    }

    await rafraichir()
    message = 'Logo mis à jour.'
  }

  async function retirerLogo() {
    const boutique = auth.boutique
    if (!supabase || !boutique || !boutique.logo_path) return
    if (!confirm('Retirer le logo de vos documents ?')) return

    const chemin = boutique.logo_path
    const { error } = await supabase.from('shops').update({ logo_path: null }).eq('id', boutique.id)
    if (error) {
      erreur = error.message
      return
    }
    void supabase.storage.from(BUCKET_LOGOS).remove([chemin])
    await rafraichir()
    message = 'Logo retiré.'
  }

  async function rafraichir() {
    await chargerBoutique()
    remplir()
  }

  const ribChiffres = $derived(saisie.rib.replace(/\D/g, '').length)

  function prefixesPropres(): Record<string, string> {
    const sortie: Record<string, string> = {}
    for (const [type, valeur] of Object.entries(saisie.prefixes)) {
      const propre = valeur.trim()
      if (propre !== '') sortie[type] = propre
    }
    return sortie
  }

  function majPrefixe(type: string, valeur: string) {
    saisie.prefixes = { ...saisie.prefixes, [type]: valeur }
  }

  function majDesign(valeur: string) {
    saisie.design_defaut = designDepuisId(valeur)
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
          <h1>Paramètres de la boutique</h1>
          <p class="entete__texte">
            Ces informations s'impriment sur vos documents. Modifier la boutique ne
            réécrit pas une facture déjà émise.
          </p>
        </div>
      </section>

      {#if message}
        <p class="alerte alerte--info" role="status">{message}</p>
      {/if}
      {#if erreur}
        <p class="alerte" role="alert">{erreur}</p>
      {/if}

      <form onsubmit={enregistrer} class="pile">
        <section class="carte bloc">
          <h2>Identité</h2>
          <div class="grille-champs">
            <div class="champ champ--large">
              <label for="b-nom">Nom <span aria-hidden="true">*</span></label>
              <input id="b-nom" required bind:value={saisie.nom} autocomplete="organization" />
            </div>
            <div class="champ champ--large">
              <label for="b-adresse">Adresse</label>
              <textarea id="b-adresse" bind:value={saisie.adresse} rows="2"></textarea>
            </div>
            <div class="champ">
              <label for="b-tel">Téléphone</label>
              <input id="b-tel" type="tel" bind:value={saisie.telephone} autocomplete="tel" />
            </div>
            <div class="champ">
              <label for="b-email">E-mail</label>
              <input id="b-email" type="email" bind:value={saisie.email} autocomplete="email" />
            </div>
          </div>
        </section>

        <section class="carte bloc">
          <h2>Identifiants légaux</h2>
          <p class="bloc__texte">Imprimés sous votre adresse. Laissez vide ce qui ne s'applique pas.</p>
          <div class="grille-champs">
            <div class="champ">
              <label for="b-ice">ICE</label>
              <input id="b-ice" bind:value={saisie.ice} inputmode="numeric" />
            </div>
            <div class="champ">
              <label for="b-if">IF</label>
              <input id="b-if" bind:value={saisie.if_fiscal} />
            </div>
            <div class="champ">
              <label for="b-rc">RC</label>
              <input id="b-rc" bind:value={saisie.rc} />
            </div>
            <div class="champ">
              <label for="b-patente">Patente</label>
              <input id="b-patente" bind:value={saisie.patente} />
            </div>
            <div class="champ">
              <label for="b-cnss">CNSS</label>
              <input id="b-cnss" bind:value={saisie.cnss} />
            </div>
          </div>
        </section>

        <section class="carte bloc">
          <h2>Logo</h2>
          <p class="bloc__texte">
            Affiché en haut de vos documents. PNG, JPG ou SVG, 2 Mo maximum.
          </p>
          <div class="logo">
            {#if logo}
              <img src={logo} alt="Logo de la boutique" />
            {:else}
              <div class="logo__vide" aria-hidden="true">Aucun logo</div>
            {/if}
            <div class="logo__actions">
              <label class="btn btn--discret fichier">
                {envoiLogo ? 'Envoi…' : logo ? 'Remplacer' : 'Choisir une image'}
                <input type="file" accept="image/*" onchange={envoyerLogo} disabled={envoiLogo} />
              </label>
              {#if logo}
                <button class="lien-discret lien-discret--danger" type="button" onclick={() => void retirerLogo()}>
                  Retirer
                </button>
              {/if}
            </div>
          </div>
        </section>

        <section class="carte bloc">
          <h2>Facturation</h2>
          <div class="grille-champs">
            <div class="champ">
              <label for="b-tva">TVA par défaut (%)</label>
              <input id="b-tva" inputmode="decimal" bind:value={saisie.tva_defaut} />
            </div>
            <div class="champ">
              <label for="b-conditions">Conditions de paiement</label>
              <select id="b-conditions" bind:value={saisie.conditions_paiement}>
                {#each conditions as condition}
                  <option value={condition.valeur}>{condition.libelle}</option>
                {/each}
              </select>
            </div>
            <label class="champ champ--case champ--large">
              <input type="checkbox" bind:checked={saisie.tva_non_applicable} />
              <span>TVA non applicable (auto-entrepreneur)</span>
            </label>
            <label class="champ champ--case champ--large">
              <input type="checkbox" bind:checked={saisie.prix_ttc} />
              <span>Je saisis mes prix TTC, TVA comprise</span>
            </label>
            <div class="champ champ--large">
              <label for="b-clause">Clause de retard de paiement</label>
              <textarea id="b-clause" bind:value={saisie.clause_retard} rows="2"></textarea>
            </div>
          </div>
        </section>

        <section class="carte bloc">
          <h2>Numérotation</h2>
          <p class="bloc__texte">
            Chaque type de document a sa propre série. Le numéro complet ressemble
            à <strong>F-20261111-0001</strong> : préfixe, date du jour, rang. Laissez
            vide pour reprendre le préfixe par défaut.
          </p>
          <div class="grille-champs grille-champs--prefixes">
            {#each ORDRE_TYPES as type}
              <div class="champ">
                <label for={`b-prefixe-${type}`}>
                  {TYPES_DOCUMENT[type].libelle}
                  <span class="aide">({TYPES_DOCUMENT[type].prefixe})</span>
                </label>
                <input
                  id={`b-prefixe-${type}`}
                  value={saisie.prefixes[type] ?? ''}
                  placeholder={TYPES_DOCUMENT[type].prefixe}
                  oninput={(e) => majPrefixe(type, e.currentTarget.value)}
                />
              </div>
            {/each}
          </div>
          <div class="grille-champs">
            <div class="champ">
              <label for="b-design">Modèle par défaut</label>
              <select id="b-design" value={saisie.design_defaut} onchange={(e) => majDesign(e.currentTarget.value)}>
                {#each Object.entries(DESIGNS) as [idDesign, libelle]}
                  <option value={idDesign}>{libelle}</option>
                {/each}
              </select>
              <span class="aide">Cinq modèles A4 et un ticket de caisse 80 mm. Chaque document garde le modèle avec lequel il a été émis.</span>
            </div>
            <label class="champ champ--case">
              <input type="checkbox" bind:checked={saisie.numerotation_annuelle} />
              <span>Repartir de 0001 chaque année</span>
            </label>
          </div>
        </section>

        <section class="carte bloc">
          <h2>Coordonnées bancaires</h2>
          <p class="bloc__texte">
            Imprimées sur les documents à payer par virement. Laissez vide pour un
            paiement en espèces.
          </p>
          <div class="grille-champs">
            <div class="champ">
              <label for="b-banque">Banque</label>
              <input id="b-banque" bind:value={saisie.banque} />
            </div>
            <div class="champ">
              <label for="b-titulaire">Titulaire du compte</label>
              <input id="b-titulaire" bind:value={saisie.rib_titulaire} />
            </div>
            <div class="champ champ--large">
              <label for="b-rib">RIB</label>
              <input id="b-rib" bind:value={saisie.rib} inputmode="numeric" placeholder="24 chiffres" />
              {#if ribChiffres > 0}
                <p class="aide" class:aide--alerte={ribChiffres !== 24}>
                  {ribChiffres} chiffre{ribChiffres > 1 ? 's' : ''}
                  {ribChiffres === 24 ? ' · longueur correcte' : ' · un RIB marocain en compte 24'}
                </p>
              {/if}
            </div>
          </div>
        </section>

        <div class="barre-actions">
          <button class="btn" type="submit" disabled={enregistrement || saisie.nom.trim() === ''}>
            {enregistrement ? 'Enregistrement…' : 'Enregistrer les réglages'}
          </button>
        </div>
      </form>

      <section class="carte bloc">
        <h2>Sauvegarde Android</h2>
        <p class="bloc__texte">
          Reprenez les clients, les articles et la numérotation de l'application
          hors ligne. Les documents arrivent en brouillons : un numéro déjà
          imprimé n'est jamais réutilisé.
        </p>
        <a class="btn btn--discret" href="/app/import">Importer une sauvegarde</a>
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
    width: min(880px, 100%);
    margin: 0 auto;
    padding: clamp(24px, 5vw, 48px) clamp(16px, 4vw, 24px);
    display: flex;
    flex-direction: column;
    gap: 18px;
  }

  .entete h1 {
    font-size: clamp(1.5rem, 3vw, 1.9rem);
  }

  .entete__texte {
    color: var(--ardoise);
    margin-top: 6px;
    max-width: 60ch;
  }

  .pile {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .bloc {
    padding: 22px;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .bloc h2 {
    font-size: 1.1rem;
  }

  .bloc__texte {
    color: var(--ardoise);
    font-size: 0.92rem;
  }

  .grille-champs {
    display: grid;
    gap: 14px;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  }

  .champ--large {
    grid-column: 1 / -1;
  }

  .logo {
    display: flex;
    align-items: center;
    gap: 18px;
    flex-wrap: wrap;
  }

  .logo img {
    max-width: 180px;
    max-height: 90px;
    object-fit: contain;
    border: 1px solid var(--trait);
    border-radius: 10px;
    background: var(--blanc);
    padding: 8px;
  }

  .logo__vide {
    width: 180px;
    height: 90px;
    display: grid;
    place-items: center;
    border: 1px dashed var(--trait);
    border-radius: 10px;
    color: var(--ardoise);
    font-size: 0.85rem;
  }

  .logo__actions {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .fichier {
    position: relative;
    overflow: hidden;
  }

  .fichier input {
    position: absolute;
    inset: 0;
    opacity: 0;
    cursor: pointer;
  }

  .aide {
    color: var(--ardoise);
    font-size: 0.82rem;
  }

  .aide--alerte {
    color: var(--rouge);
  }

  .barre-actions {
    display: flex;
    justify-content: flex-end;
  }

  .lien-discret--danger {
    color: var(--rouge);
  }
</style>