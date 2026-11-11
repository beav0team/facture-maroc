<script lang="ts">
  import { onMount } from 'svelte'
  import { auth } from '../lib/session.svelte'
  import { supabase } from '../lib/supabase'
  import Barre from '../lib/ui/Barre.svelte'

  type Abonnement = {
    plan: 'gratuit' | 'pro'
    paid_until: string | null
    source: string
    note: string
  }

  type BoutiqueAdmin = {
    id: string
    nom: string
    created_at: string
    subscriptions: Abonnement | Abonnement[] | null
  }

  let admin = $state(false)
  let verifie = $state(false)
  let boutiques = $state<BoutiqueAdmin[]>([])
  let chargement = $state(true)
  let erreur = $state<string | null>(null)
  let message = $state<string | null>(null)
  let enCours = $state<string | null>(null)

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

  async function charger() {
    if (!supabase) return
    chargement = true
    erreur = null
    const { data, error } = await supabase
      .from('shops')
      .select('id, nom, created_at, subscriptions(plan, paid_until, source, note)')
      .order('nom', { ascending: true })
    chargement = false
    if (error) {
      erreur = error.message
      return
    }
    boutiques = (data as BoutiqueAdmin[]) ?? []
  }

  function abonnement(boutique: BoutiqueAdmin): Abonnement | null {
    if (Array.isArray(boutique.subscriptions)) return boutique.subscriptions[0] ?? null
    return boutique.subscriptions ?? null
  }

  function estPro(boutique: BoutiqueAdmin): boolean {
    const abo = abonnement(boutique)
    if (!abo || abo.plan !== 'pro') return false
    if (!abo.paid_until) return true
    return abo.paid_until >= new Date().toISOString().slice(0, 10)
  }

  async function ecrire(boutique: BoutiqueAdmin, abo: Omit<Abonnement, 'note'> & { note?: string }) {
    if (!supabase) return
    enCours = boutique.id
    erreur = null
    message = null
    const { error } = await supabase.from('subscriptions').upsert(
      {
        shop_id: boutique.id,
        plan: abo.plan,
        paid_until: abo.paid_until,
        source: abo.source,
        note: abo.note ?? '',
      },
      { onConflict: 'shop_id' },
    )
    enCours = null
    if (error) {
      erreur = error.message
      return
    }
    message = `Abonnement de « ${boutique.nom} » mis à jour.`
    await charger()
  }

  function activer(boutique: BoutiqueAdmin) {
    const fin = new Date()
    fin.setFullYear(fin.getFullYear() + 1)
    void ecrire(boutique, {
      plan: 'pro',
      paid_until: fin.toISOString().slice(0, 10),
      source: 'manuel',
      note: 'Activation manuelle depuis l’administration',
    })
  }

  function desactiver(boutique: BoutiqueAdmin) {
    if (!confirm(`Repasser « ${boutique.nom} » au plan gratuit ?`)) return
    void ecrire(boutique, { plan: 'gratuit', paid_until: null, source: 'manuel', note: 'Retour au plan gratuit' })
  }

  const nombrePro = $derived(boutiques.filter((boutique) => estPro(boutique)).length)
</script>

<div class="page">
  <Barre />

  <main class="contenu">
    {#if !verifie || chargement}
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
          <p class="note">{boutiques.length} boutique{boutiques.length > 1 ? 's' : ''} · {nombrePro} en Pro</p>
        </div>
        <button class="btn btn--discret" type="button" onclick={() => void charger()}>Recharger</button>
      </section>

      {#if message}<p class="alerte alerte--info" role="status">{message}</p>{/if}
      {#if erreur}<p class="alerte" role="alert">{erreur}</p>{/if}

      <ul class="liste">
        {#each boutiques as boutique (boutique.id)}
          <li class="carte boutique">
            <div class="boutique__texte">
              <h2>{boutique.nom}</h2>
              <p class="note">
                {#if estPro(boutique)}
                  Pro{#if abonnement(boutique)?.paid_until} jusqu’au {abonnement(boutique)?.paid_until}{/if}
                {:else}
                  Plan gratuit
                {/if}
                {#if abonnement(boutique)?.note} · {abonnement(boutique)?.note}{/if}
              </p>
              <p class="note">Créée le {boutique.created_at.slice(0, 10)}</p>
            </div>
            <div class="boutique__actions">
              {#if estPro(boutique)}
                <button class="lien-discret lien-discret--danger" type="button" disabled={enCours === boutique.id} onclick={() => desactiver(boutique)}>
                  Repasser en gratuit
                </button>
              {:else}
                <button class="btn" type="button" disabled={enCours === boutique.id} onclick={() => activer(boutique)}>
                  {enCours === boutique.id ? 'Activation…' : 'Activer Pro 1 an'}
                </button>
              {/if}
            </div>
          </li>
        {/each}
      </ul>
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

  .bloc {
    padding: 24px;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .liste {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .boutique {
    padding: 16px 18px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
  }

  .boutique__texte h2 {
    font-size: 1.05rem;
  }

  .note {
    color: var(--ardoise);
    font-size: 0.88rem;
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
</style>
