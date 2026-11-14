<script lang="ts">
  import { onMount } from 'svelte'
  import { supabase } from '../../lib/supabase'
  import {
    TARIFS_DEFAUT,
    pointsDepuisTexte,
    tarifsDepuisReglages,
    texteDepuisPoints,
    validerTarifs,
    type TarifsRegles,
  } from '../../lib/tarifs'
  import { afficherNumero, lienWhatsApp, numeroValide } from '../../lib/whatsapp'

  // Deux réglages, une seule fonction de lecture : la page des tarifs est
  // publique et lit `reglages_publics()`; ici on écrit les deux clés qu'elle
  // expose. Tout le reste vit dans le code, et le dit plus bas.
  let numero = $state('')
  let enregistre = $state('')
  let chargement = $state(true)
  let enCours = $state<string | null>(null)
  let erreur = $state<string | null>(null)
  let message = $state<string | null>(null)
  let formulaire = $state<HTMLFormElement>()

  const apercu = $derived(numeroValide(numero) ? lienWhatsApp(numero, 'essentiel') : '')

  // ---------------------------------------------------------------- les tarifs
  let tarifEssentiel = $state(TARIFS_DEFAUT.essentiel.prix)
  let quotaEssentiel = $state(TARIFS_DEFAUT.essentiel.quota ?? 50)
  let pointsEssentiel = $state(texteDepuisPoints(TARIFS_DEFAUT.essentiel.points))
  let tarifPro = $state(TARIFS_DEFAUT.pro.prix)
  let pointsPro = $state(texteDepuisPoints(TARIFS_DEFAUT.pro.points))
  let erreurTarifs = $state<string | null>(null)
  let messageTarifs = $state<string | null>(null)

  const bareme = $derived<TarifsRegles>({
    essentiel: {
      prix: Number(tarifEssentiel),
      quota: Number(quotaEssentiel),
      points: pointsDepuisTexte(pointsEssentiel),
    },
    pro: { prix: Number(tarifPro), quota: null, points: pointsDepuisTexte(pointsPro) },
  })

  // Le quota est appliqué par `quota_limite()` ; la liste des prestations est
  // du texte libre. Sans ce rappel, une carte peut annoncer « 50 documents »
  // en puces tout en accordant 60 — le genre d'écart qu'un client remarque.
  const quotaAbsentDesPoints = $derived(
    !pointsDepuisTexte(pointsEssentiel).some((point) =>
      point.includes(String(quotaEssentiel)),
    ),
  )

  onMount(() => {
    void charger()
  })

  function appliquerTarifs(tarifs: TarifsRegles) {
    tarifEssentiel = tarifs.essentiel.prix
    quotaEssentiel = tarifs.essentiel.quota ?? 50
    pointsEssentiel = texteDepuisPoints(tarifs.essentiel.points)
    tarifPro = tarifs.pro.prix
    pointsPro = texteDepuisPoints(tarifs.pro.points)
  }

  async function charger() {
    if (!supabase) return
    chargement = true
    erreur = null
    const { data, error } = await supabase.rpc('reglages_publics')
    chargement = false
    if (error) {
      erreur = error.message
      return
    }
    const reglages = (data ?? {}) as { whatsapp?: string; tarifs?: unknown }
    enregistre = reglages.whatsapp ?? ''
    numero = enregistre ? afficherNumero(enregistre) : ''
    appliquerTarifs(tarifsDepuisReglages(reglages.tarifs))
  }

  async function enregistrer(evenement: SubmitEvent) {
    evenement.preventDefault()
    if (!supabase) return
    erreur = null
    message = null

    if (numero.trim() !== '' && !numeroValide(numero)) {
      erreur =
        'Ce numéro ne ressemble pas à un numéro marocain (06 12 34 56 78) ni à un numéro international (212612345678).'
      return
    }

    enCours = 'whatsapp'
    const { data, error } = await supabase.rpc('admin_reglage_whatsapp', { p_numero: numero })
    enCours = null
    if (error) {
      erreur = error.message
      return
    }

    enregistre = (data as string | null) ?? ''
    numero = enregistre ? afficherNumero(enregistre) : ''
    message = enregistre
      ? `Numéro enregistré : ${afficherNumero(enregistre)}. Le bouton « Commander sur WhatsApp » apparaît sur la page des tarifs.`
      : 'Numéro retiré : le bouton WhatsApp disparaît du site, l’e-mail reste le seul contact.'
  }

  function retirer() {
    numero = ''
    formulaire?.requestSubmit()
  }

  async function enregistrerTarifs(evenement: SubmitEvent) {
    evenement.preventDefault()
    if (!supabase) return
    erreurTarifs = null
    messageTarifs = null

    const souci = validerTarifs(bareme)
    if (souci) {
      erreurTarifs = souci
      return
    }

    enCours = 'tarifs'
    const { data, error } = await supabase.rpc('admin_tarifs', { p_tarifs: bareme })
    enCours = null
    if (error) {
      erreurTarifs = error.message
      return
    }

    const propres = tarifsDepuisReglages(data)
    appliquerTarifs(propres)
    messageTarifs = `Barème enregistré : Essentiel ${propres.essentiel.prix} DH, Pro ${propres.pro.prix} DH. La page des tarifs et la limite de documents suivent immédiatement.`
  }
</script>

<section class="carte bloc" aria-labelledby="titre-whatsapp">
  <header class="bloc__entete">
    <div>
      <h2 id="titre-whatsapp">Achat par WhatsApp</h2>
      <p class="note">
        Le numéro derrière le bouton « Commander sur WhatsApp » de la page des tarifs, et
        le contact affiché pour l’achat d’un plan. Il vit en base, pas dans le code : le
        changer ne demande aucun déploiement.
      </p>
    </div>
    <span class="etiquette" class:etiquette--pro={enregistre !== ''} class:etiquette--alerte={enregistre === ''}>
      {enregistre ? 'Réglé' : 'Vide'}
    </span>
  </header>

  {#if erreur}<p class="alerte" role="alert">{erreur}</p>{/if}
  {#if message}<p class="alerte alerte--info" role="status">{message}</p>{/if}

  {#if chargement}
    <p class="note">Chargement…</p>
  {:else}
    <form class="formulaire" onsubmit={enregistrer} bind:this={formulaire}>
      <div class="formulaire__grille">
        <div class="champ">
          <label for="r-whatsapp">Numéro WhatsApp</label>
          <input
            id="r-whatsapp"
            type="tel"
            inputmode="tel"
            autocomplete="off"
            placeholder="06 12 34 56 78"
            bind:value={numero}
          />
          <p class="note">
            Numéro marocain ou format international. Les espaces, points et tirets sont
            retirés ; un numéro en 06, 07 ou 05 devient 212… tout seul.
          </p>
        </div>
      </div>

      <div class="formulaire__actions">
        <button class="btn" type="submit" disabled={enCours !== null}>
          {enCours === 'whatsapp' ? 'Enregistrement…' : 'Enregistrer'}
        </button>
        {#if enregistre}
          <button class="btn btn--discret" type="button" onclick={retirer} disabled={enCours !== null}>
            Retirer le numéro
          </button>
        {/if}
      </div>
    </form>

    {#if apercu}
      <p class="note">
        Ce que le client envoie en ouvrant la conversation :
        <em>Bonjour, je veux prendre le plan Essentiel ({tarifEssentiel} DH par an) de FactureApp.</em>
        — le prix y est écrit par la page des tarifs, pas par ce réglage.
      </p>
      <p>
        <a class="lien-discret" href={apercu} target="_blank" rel="noopener noreferrer">
          Tester le lien WhatsApp
        </a>
      </p>
    {:else}
      <p class="note">
        Sans numéro, la page des tarifs garde sa demande par e-mail : rien ne casse, le
        bouton n’apparaît simplement pas.
      </p>
    {/if}
  {/if}
</section>

<section class="carte bloc" aria-labelledby="titre-tarifs">
  <header class="bloc__entete">
    <div>
      <h2 id="titre-tarifs">Tarifs payants</h2>
      <p class="note">
        Le prix annuel, le nombre de documents inclus et les prestations affichées sur la
        page des tarifs. Enregistrer ici change l’affichage et la limite appliquée à
        l’émission : un palier vendu pour N documents les accorde vraiment.
      </p>
    </div>
  </header>

  {#if erreurTarifs}<p class="alerte" role="alert">{erreurTarifs}</p>{/if}
  {#if messageTarifs}<p class="alerte alerte--info" role="status">{messageTarifs}</p>{/if}

  {#if chargement}
    <p class="note">Chargement…</p>
  {:else}
    <form class="formulaire" onsubmit={enregistrerTarifs}>
      <div class="formulaire__grille">
        <div class="champ">
          <label for="t-essentiel-prix">Essentiel — prix annuel (DH)</label>
          <input id="t-essentiel-prix" type="number" min="50" max="50000" step="10" bind:value={tarifEssentiel} />
        </div>
        <div class="champ">
          <label for="t-essentiel-quota">Essentiel — documents par mois</label>
          <input id="t-essentiel-quota" type="number" min="6" max="100000" step="1" bind:value={quotaEssentiel} />
          <p class="note">Le plan gratuit en inclut 5 ; en dessous, il n’y aurait plus de palier à vendre.</p>
        </div>
        <div class="champ champ--large">
          <label for="t-essentiel-points">Essentiel — prestations (une par ligne)</label>
          <textarea id="t-essentiel-points" rows="5" bind:value={pointsEssentiel}></textarea>
          {#if quotaAbsentDesPoints}
            <p class="note">
              Le quota est {quotaEssentiel} documents mais aucune prestation ne cite ce nombre :
              la carte affichera la liste telle quelle, le quota n’apparaîtra qu’à la ligne
              « documents par mois ».
            </p>
          {/if}
        </div>

        <div class="champ">
          <label for="t-pro-prix">Pro — prix annuel (DH)</label>
          <input id="t-pro-prix" type="number" min="50" max="50000" step="10" bind:value={tarifPro} />
        </div>
        <div class="champ">
          <label for="t-pro-quota">Pro — documents par mois</label>
          <input id="t-pro-quota" type="text" value="Illimité" disabled />
          <p class="note">Un Pro limité n’aurait plus de raison d’exister : le quota ne se règle pas.</p>
        </div>
        <div class="champ champ--large">
          <label for="t-pro-points">Pro — prestations (une par ligne)</label>
          <textarea id="t-pro-points" rows="5" bind:value={pointsPro}></textarea>
        </div>
      </div>

      <div class="formulaire__actions">
        <button class="btn" type="submit" disabled={enCours !== null}>
          {enCours === 'tarifs' ? 'Enregistrement…' : 'Enregistrer les tarifs'}
        </button>
      </div>
    </form>

    <p class="note">
      Le site public (accueil et conditions de vente) est régénéré à partir de ce barème
      au prochain déploiement ; la page des tarifs et l’application changent tout de suite.
    </p>
  {/if}
</section>

<section class="carte bloc" aria-labelledby="titre-suite">
  <header class="bloc__entete">
    <div>
      <h2 id="titre-suite">Ce qui ne se règle pas ici</h2>
      <p class="note">Pour éviter de chercher ce qui n’existe pas encore.</p>
    </div>
  </header>
  <ul class="note">
    <li>Le plan gratuit : 0 DH et 5 documents, le plancher du marché marocain.</li>
    <li>Les clés de plan (<code>gratuit</code>, <code>essentiel</code>, <code>pro</code>) : elles sont écrites en base et dans les politiques.</li>
    <li>Le plan d’une boutique : fiche de la boutique → Abonnement.</li>
    <li>Le paiement par carte : pas ouvert, et il demanderait un compte marchand.</li>
  </ul>
</section>
