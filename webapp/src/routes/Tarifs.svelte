<script lang="ts">
  import { onMount } from 'svelte'
  import { auth } from '../lib/session.svelte'
  import { supabase } from '../lib/supabase'
  import { CONTACT_ESSENTIEL } from '../lib/offres'
  import { offresDepuis, tarifsDepuisReglages } from '../lib/tarifs'
  import { lienWhatsApp, numeroValide } from '../lib/whatsapp'

  const contact = CONTACT_ESSENTIEL

  // Numéro et barème se règlent dans la console (0015, 0016), pas ici : cette
  // page est publique, ne dépend d'aucune session, et doit rester juste quand
  // ils changent. Sans réglage, `offres.ts` fait foi.
  let whatsapp = $state('')
  let tarifs = $state(tarifsDepuisReglages({}))
  const offres = $derived(offresDepuis(tarifs))

  onMount(() => {
    void chargerReglages()
  })

  async function chargerReglages() {
    if (!supabase) return
    const { data, error } = await supabase.rpc('reglages_publics')
    if (error) return
    const reglages = (data ?? {}) as { whatsapp?: string; tarifs?: unknown }
    const brut = reglages.whatsapp ?? ''
    if (numeroValide(brut)) whatsapp = brut
    tarifs = tarifsDepuisReglages(reglages.tarifs)
  }

</script>

<div class="page">
  <header class="barre-publique">
    <a class="marque" href="/app/">Facture<span>App</span></a>
    <nav aria-label="Navigation">
      <a href="/app/gratuit">Générateur gratuit</a>
      <a href="/app/tarifs" aria-current="page">Tarifs</a>
      <a class="btn" href="/app/">Se connecter</a>
    </nav>
  </header>

  <main class="contenu">
    <section class="entete">
      <p class="surtitre">Tarifs</p>
      <h1>Un prix simple, en dirhams</h1>
      <p class="texte">
        Pas de commission sur vos factures, pas d’engagement, pas de
        reconduction automatique. Le plan gratuit permet de travailler tout de
        suite ; Essentiel et Pro augmentent le nombre de documents facturés par
        mois (factures, tickets, factures d’acompte, régularisations, exports —
        les devis et les avoirs ne sont jamais comptés).
      </p>
    </section>

    <div class="offres">
      {#each offres as offre}
        {@const wa = offre.cle === 'gratuit' ? '' : lienWhatsApp(whatsapp, offre.cle)}
        <article class="carte offre" class:offre--principale={offre.principal}>
          <div class="offre__entete">
            <h2>{offre.nom}</h2>
            {#if offre.principal}<span class="etiquette">Recommandé</span>{/if}
          </div>
          <p class="offre__prix">
            <strong>{offre.prix}</strong>
            <span>{offre.periode}</span>
          </p>
          <p class="texte">{offre.texte}</p>
          <ul>
            {#each offre.points as point}
              <li>{point}</li>
            {/each}
          </ul>
          <div class="offre__actions">
            {#if wa}
              <a class="btn btn--whatsapp" href={wa} target="_blank" rel="noopener noreferrer">
                Commander sur WhatsApp
              </a>
              <a class="offre__mail" href={offre.lien}>ou écrire à contact@factureapp.ma</a>
            {:else}
              <a class="btn" class:btn--discret={!offre.principal} href={offre.lien}>{offre.action}</a>
            {/if}
          </div>
        </article>
      {/each}
    </div>

    <section class="carte precision">
      <h2>Comment se passe le paiement</h2>
      <p class="texte">
        Essentiel et Pro sont activés après réception du paiement, par virement
        ou espèces en boutique. {#if whatsapp}Le bouton WhatsApp ci-dessus ouvre
        la conversation avec le message déjà écrit ; l’e-mail reste possible à
        <a href={contact}>contact@factureapp.ma</a>.{:else}Écrivez à
        <a href={contact}>contact@factureapp.ma</a>.{/if}{' '}
        Dans les deux cas, indiquez le nom de votre boutique : l’activation se
        fait sous 24 heures ouvrées, pour un an, et ne se renouvelle pas toute
        seule. À l’échéance, le compte repasse au plan gratuit sans rien perdre :
        les documents restent lisibles et téléchargeables.
      </p>
      <p class="texte">
        <strong>Rétractation :</strong> vous pouvez renoncer à Essentiel ou au
        Pro dans les 7 jours suivant l’activation, à condition qu’aucune
        facture n’ait été émise pendant cette période. Le remboursement est
        alors intégral, par le même moyen que le paiement, sous 14 jours.
      </p>
      {#if auth.user}
        <p class="texte">Connecté{ auth.user.email ? ` en tant que ${auth.user.email}` : '' }.</p>
      {:else}
        <p class="texte">Créez d’abord un compte gratuit, puis demandez le passage à Essentiel ou au Pro.</p>
      {/if}
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

  /* Svelte scope les sélecteurs : `nav a` (0,1,2) battrait le .btn global
     (0,1,0) et peindrait « Se connecter » en ardoise sur le bleu. */
  nav a.btn {
    color: var(--blanc);
  }

  /* Sous 560 px la marque passe seule sur sa ligne ; on étire la nav pour
     que « Se connecter » reste collé au bord droit au lieu de flotter. */
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
    width: min(900px, 100%);
    margin: 0 auto;
    padding: clamp(24px, 5vw, 48px) clamp(16px, 4vw, 24px);
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  .entete {
    display: flex;
    flex-direction: column;
    gap: 8px;
    max-width: 640px;
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

  .offres {
    display: grid;
    gap: 16px;
    grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  }

  .offre {
    padding: 24px;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .offre--principale {
    border-color: var(--bleu);
    box-shadow: 0 12px 32px rgb(30 63 210 / 12%);
  }

  .offre__entete {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  .offre h2 {
    font-size: 1.2rem;
  }

  .offre__prix {
    display: flex;
    align-items: baseline;
    gap: 8px;
  }

  .offre__prix strong {
    font-family: var(--titre);
    font-size: 2rem;
  }

  .offre__prix span {
    color: var(--ardoise);
  }

  .offre ul {
    margin: 0;
    padding: 0;
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: 8px;
    color: var(--ardoise);
    font-size: 0.92rem;
  }

  .offre li::before {
    content: '✓';
    color: var(--vert);
    font-weight: 700;
    margin-right: 8px;
  }

  .offre .btn {
    margin-top: auto;
  }

  .offre__actions {
    margin-top: auto;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .offre__mail {
    color: var(--ardoise);
    font-size: 0.85rem;
    text-align: center;
    text-decoration: none;
  }

  .offre__mail:hover {
    text-decoration: underline;
  }

  /* Le vert de WhatsApp, choisi pour être reconnu, pas pour décorer : c'est le
     seul bouton du site où la couleur dit la destination. */
  .offre__actions .btn--whatsapp {
    background: #1faa54;
    color: var(--blanc);
    text-align: center;
  }

  .offre__actions .btn--whatsapp:hover {
    background: #188c44;
  }

  .precision {
    padding: 24px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .precision h2 {
    font-size: 1.1rem;
  }
</style>
