<script lang="ts">
  import { DESIGNS, TAUX_TVA, designDepuisId, type DesignDocument } from '../lib/document'
  import { calculer, libelleTotal, totalLigne, type Facture, type Ligne } from '../lib/invoice'
  import { analyserMontant, dirhams } from '../lib/money'
  import { nomFichier, telechargerBlob } from '../lib/archive'
  import { memoriserBrouillon } from '../lib/brouillonPublic'

  let nomBoutique = $state('')
  let nomClient = $state('')
  let taux = $state(20)
  let modele = $state<DesignDocument>('classique')
  let lignes = $state<Ligne[]>([{ description: '', quantite: 1, prixUnitaire: 0 }])
  let travail = $state(false)
  let erreur = $state<string | null>(null)

  const facture = $derived<Facture>({
    numero: 'APERÇU',
    date: new Date().toLocaleDateString('fr-FR'),
    client: { nom: nomClient || 'Client' },
    lignes,
    tauxTva: taux,
  })
  const totaux = $derived(calculer(facture))

  function ajouter() {
    lignes = [...lignes, { description: '', quantite: 1, prixUnitaire: 0 }]
  }

  /** Keeps the draft for the sign-up: the editor picks it up once. */
  function preparerCompte() {
    memoriserBrouillon({
      boutique: nomBoutique,
      client: nomClient,
      taux,
      lignes: lignes
        .filter((ligne) => ligne.description.trim() !== '')
        .map((ligne) => ({
          description: ligne.description,
          quantite: ligne.quantite,
          prixUnitaire: ligne.prixUnitaire,
        })),
    })
  }

  function supprimer(index: number) {
    lignes = lignes.filter((_, position) => position !== index)
  }

  function majQuantite(index: number, texte: string) {
    if (texte.trim() === '' || /[.,]$/.test(texte.trim())) return
    const valeur = analyserMontant(texte)
    if (valeur === null || valeur < 0) return
    const copie = [...lignes]
    const ligne = copie[index]
    if (!ligne) return
    copie[index] = { ...ligne, quantite: valeur }
    lignes = copie
  }

  function majPrix(index: number, texte: string) {
    if (texte.trim() === '' || /[.,]$/.test(texte.trim())) return
    const valeur = analyserMontant(texte)
    if (valeur === null || valeur < 0) return
    const copie = [...lignes]
    const ligne = copie[index]
    if (!ligne) return
    copie[index] = { ...ligne, prixUnitaire: valeur }
    lignes = copie
  }

  async function telecharger() {
    erreur = null
    if (lignes.every((ligne) => ligne.description.trim() === '')) {
      erreur = 'Ajoutez au moins une ligne avec une désignation.'
      return
    }
    travail = true
    try {
      const { creerPdf } = await import('../lib/pdf')
      const bytes = await creerPdf({
        facture,
        design: modele,
        boutique: {
          nom: nomBoutique.trim() || 'Votre boutique',
          adresse: null,
          telephone: null,
          email: null,
          ice: null,
          if_fiscal: null,
          rc: null,
          patente: null,
          cnss: null,
          banque: null,
          rib: null,
          rib_titulaire: null,
          tva_defaut: taux,
          tva_non_applicable: false,
          prix_ttc: false,
          logo_path: null,
        },
        filigrane: 'FACTUREAPP · GRATUIT',
        // No shop to read here: the free generator always prints the words, so
        // a visitor sees the real document, not a reduced one.
        montantLettres: true,
      })
      telechargerBlob(new Blob([new Uint8Array(bytes)], { type: 'application/pdf' }), nomFichier('apercu-factureapp'))
    } finally {
      travail = false
    }
  }
</script>

<div class="page">
  <header class="barre-publique">
    <a class="marque" href="/app/">Facture<span>App</span></a>
    <nav aria-label="Navigation">
      <a href="/app/gratuit" aria-current="page">Générateur gratuit</a>
      <a href="/app/tarifs">Tarifs</a>
      <a class="btn" href="/app/">Se connecter</a>
    </nav>
  </header>

  <main class="contenu">
    <section class="entete">
      <p class="surtitre">Sans compte, sans installation</p>
      <h1>Créez une facture marocaine en deux minutes</h1>
      <p class="texte">
        Remplissez, vérifiez les totaux, téléchargez le PDF. Le fichier porte un
        filigrane et n’est pas numéroté : un compte FactureApp prend le numéro à
        l’émission et archive le PDF définitif.
      </p>
    </section>

    <div class="editeur">
      <div class="principal">
        <section class="carte bloc">
          <div class="grille">
            <div class="champ">
              <label for="g-boutique">Votre boutique</label>
              <input id="g-boutique" bind:value={nomBoutique} placeholder="Ex. Atelier Atlas" />
            </div>
            <div class="champ">
              <label for="g-client">Client</label>
              <input id="g-client" bind:value={nomClient} placeholder="Ex. Garage Atlas" />
            </div>
            <div class="champ">
              <label for="g-taux">TVA</label>
              <select id="g-taux" bind:value={taux}>
                {#each TAUX_TVA as valeur}
                  <option value={valeur}>{valeur} %</option>
                {/each}
              </select>
            </div>

          </div>
        </section>

        <section class="carte bloc">
          <div class="bloc__entete">
            <h2>Lignes</h2>
            <button class="btn btn--discret" type="button" onclick={ajouter}>Ajouter une ligne</button>
          </div>

          <div class="ligne ligne--entete" aria-hidden="true">
            <span>Désignation</span>
            <span>Qté</span>
            <span>P.U. HT</span>
            <span>Total HT</span>
            <span></span>
          </div>

          {#each lignes as ligne, index (index)}
            <div class="ligne">
              <input aria-label="Désignation" bind:value={ligne.description} placeholder="Désignation" />
              <div class="ligne__nombres">
                <label class="ligne__champ">
                  <span>Qté</span>
                  <input
                    aria-label="Quantité"
                    value={ligne.quantite}
                    inputmode="decimal"
                    oninput={(e) => majQuantite(index, e.currentTarget.value)}
                  />
                </label>
                <label class="ligne__champ">
                  <span>P.U. HT</span>
                  <input
                    aria-label="Prix unitaire HT"
                    value={ligne.prixUnitaire}
                    inputmode="decimal"
                    oninput={(e) => majPrix(index, e.currentTarget.value)}
                  />
                </label>
              </div>
              <span class="ligne__total">
                <span class="ligne__total-libelle">Total HT</span>
                {dirhams(totalLigne(ligne))}
              </span>
              {#if lignes.length > 1}
                <button class="lien-discret lien-discret--danger" type="button" onclick={() => supprimer(index)} aria-label="Supprimer la ligne">✕</button>
              {/if}
            </div>
          {/each}
        </section>
      </div>

      <aside class="cote">
        <section class="carte totaux">
          <h2>Totaux</h2>
          <dl>
            <div><dt>Total HT</dt><dd>{dirhams(totaux.totalHt)}</dd></div>
            {#each totaux.lignesTvaImprimees as tva}
              <div><dt>TVA {tva.taux} %</dt><dd>{dirhams(tva.montant)}</dd></div>
            {/each}
            <div class="totaux__fort"><dt>{libelleTotal(totaux)}</dt><dd>{dirhams(totaux.total)}</dd></div>
          </dl>
          <div class="champ">
            <label for="g-modele">Modèle du PDF</label>
            <select id="g-modele" value={modele} onchange={(e) => (modele = designDepuisId(e.currentTarget.value))}>
              {#each Object.entries(DESIGNS) as [idModele, libelle]}
                <option value={idModele}>{libelle}</option>
              {/each}
            </select>
          </div>
          {#if erreur}<p class="alerte" role="alert">{erreur}</p>{/if}
          <button class="btn" type="button" onclick={() => void telecharger()} disabled={travail}>
            {travail ? 'Génération…' : 'Télécharger le PDF'}
          </button>
          <a class="lien" href="/app/?mode=signup" onclick={preparerCompte}>Créer un compte pour numéroter et archiver</a>
        </section>
      </aside>
    </div>

    <section class="carte appel">
      <div>
        <h2>Ce générateur ne garde rien</h2>
        <p class="texte">
          Un compte FactureApp donne la numérotation automatique, le quota de
          5 factures gratuites par mois, l’archivage du PDF émis et l’accès depuis
          n’importe quel appareil.
        </p>
        <p class="texte">
          <strong>Rétractation :</strong> le plan Pro peut être résilié dans les
          7 jours suivant l’activation, à condition qu’aucune facture n’ait été
          émise. Le remboursement est intégral, sous 14 jours.
        </p>
      </div>
      <a class="btn" href="/app/?mode=signup" onclick={preparerCompte}>Ouvrir mon compte</a>
    </section>
  </main>
</div>

<style>
  .page {
    min-height: 100dvh;
    background: var(--papier);
  }

  .barre-publique {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 12px clamp(16px, 4vw, 32px);
    background: var(--blanc);
    border-bottom: 1px solid var(--trait);
    flex-wrap: wrap;
  }

  .marque {
    font-family: var(--titre);
    font-size: 1.15rem;
    font-weight: 700;
    color: var(--nuit);
    text-decoration: none;
  }

  .marque span {
    color: var(--bleu);
  }

  nav {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;
  }

  nav a {
    color: var(--ardoise);
    font-size: 0.9rem;
    font-weight: 600;
    text-decoration: none;
  }

  nav a[aria-current='page'] {
    color: var(--bleu);
  }

  /* Même piège que dans Tarifs.svelte : le sélecteur scopé `nav a` gagne
     contre le .btn global et rend le libellé illisible sur le fond bleu. */
  nav a.btn {
    color: var(--blanc);
  }

  /* Même mise en page mobile que Tarifs.svelte : la nav occupe sa ligne. */
  @media (max-width: 560px) {
    .barre-publique {
      gap: 10px;
    }

    nav {
      width: 100%;
      justify-content: space-between;
      gap: 10px;
    }
  }

  .contenu {
    width: min(1000px, 100%);
    margin: 0 auto;
    padding: clamp(24px, 5vw, 48px) clamp(16px, 4vw, 24px);
    display: flex;
    flex-direction: column;
    gap: 18px;
  }

  .entete {
    display: flex;
    flex-direction: column;
    gap: 8px;
    max-width: 680px;
  }

  .surtitre {
    color: var(--ardoise);
    font-size: 0.8rem;
    text-transform: uppercase;
    letter-spacing: 0.08em;
  }

  .entete h1 {
    font-size: clamp(1.6rem, 3.4vw, 2.2rem);
  }

  .texte {
    color: var(--ardoise);
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
  }

  .grille {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  }

  .ligne {
    display: grid;
    gap: 8px;
    grid-template-columns: 1fr;
    padding: 10px;
    border: 1px solid var(--trait);
    border-radius: 10px;
    background: var(--papier);
  }

  .ligne--entete {
    display: none;
  }

  .ligne__nombres {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .ligne__champ {
    display: flex;
    flex-direction: column;
    gap: 4px;
    min-width: 0;
  }

  .ligne__champ span {
    font-size: 0.75rem;
    font-weight: 600;
    color: var(--ardoise);
  }

  .ligne input {
    min-height: 40px;
    padding: 0 10px;
    border: 1px solid var(--trait);
    border-radius: 8px;
    background: var(--blanc);
    font: inherit;
    width: 100%;
  }

  .ligne__total {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 8px;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
  }

  .ligne__total-libelle {
    font-size: 0.75rem;
    font-weight: 600;
    color: var(--ardoise);
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

  .lien {
    font-size: 0.85rem;
    text-align: center;
  }

  .appel {
    padding: 24px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 20px;
    flex-wrap: wrap;
  }

  .appel h2 {
    font-size: 1.15rem;
    margin-bottom: 4px;
  }

  .lien-discret {
    border: 0;
    background: none;
    color: var(--rouge);
    font: inherit;
    cursor: pointer;
    padding: 4px;
  }

  @media (min-width: 700px) {
    .editeur {
      grid-template-columns: minmax(0, 1fr) 280px;
    }

    .ligne {
      grid-template-columns: minmax(0, 1fr) 80px 100px 100px 28px;
      align-items: center;
      border: 0;
      background: none;
      padding: 0;
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

    .ligne__nombres {
      display: contents;
    }

    .ligne__champ span,
    .ligne__total-libelle {
      display: none;
    }

    .totaux {
      position: sticky;
      top: 16px;
    }
  }
</style>
