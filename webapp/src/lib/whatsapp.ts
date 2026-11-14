// Le numéro WhatsApp se règle depuis la console (migration 0015), il ne se code
// pas ici : `plateforme_reglages.whatsapp` le porte, `/app/tarifs` le lit par
// `reglages_publics()`. Ce fichier ne fait que le normaliser et le transformer
// en lien — sans Svelte ni Supabase, donc testable.
//
// Un numéro vide n'est pas une erreur : c'est l'état normal avant que Yassine
// l'ait posé. Le bouton disparaît alors et l'e-mail reprend la main.

import { OFFRES, type ClePlan } from './offres'

/** Le numéro tel que `wa.me` l'attend : chiffres seuls, sans `+`. */
export function normaliserNumero(brut: string | null | undefined): string {
  const texte = (brut ?? '').trim()
  // Même règle qu'en SQL : un texte n'est pas un numéro, et ne doit pas
  // ressembler à une demande d'effacement.
  if (!/^[0-9+()\s./-]*$/.test(texte)) return ''

  const chiffres = texte.replace(/\D/g, '')
  if (chiffres === '') return ''

  // Un numéro local marocain (06…, 07…, 05…) vaut 212 + les neuf chiffres.
  const complet = chiffres.length === 10 && chiffres.startsWith('0') ? `212${chiffres.slice(1)}` : chiffres

  if (complet.length < 8 || complet.length > 15) return ''
  if (complet.startsWith('0')) return ''
  return complet
}

export function numeroValide(brut: string | null | undefined): boolean {
  return normaliserNumero(brut) !== ''
}

/** « 212612345678 » → « 06 12 34 56 78 » ; le reste en `+…`. */
export function afficherNumero(brut: string | null | undefined): string {
  const numero = normaliserNumero(brut)
  if (numero === '') return ''
  if (numero.startsWith('212') && numero.length === 12) {
    const local = `0${numero.slice(3)}`
    return local.replace(/(\d{2})(?=\d)/g, '$1 ')
  }
  return `+${numero}`
}

/**
 * Le message qui part dans la conversation. Le nom et le prix viennent de
 * `offres.ts` : changer un tarif change le message, sans seconde copie.
 */
export function messageWhatsApp(cle: ClePlan): string {
  const offre = OFFRES.find((o) => o.cle === cle)
  const nom = offre?.nom ?? 'FactureApp'
  const prix = offre ? `${offre.prix} ${offre.periode}` : ''
  const montant = prix ? ` (${prix})` : ''
  return (
    `Bonjour, je veux prendre le plan ${nom}${montant} de FactureApp.\n\n` +
    'Nom de ma boutique : '
  )
}

/** Le lien `wa.me`, ou `''` si aucun numéro valide n'est réglé. */
export function lienWhatsApp(numero: string | null | undefined, cle: ClePlan): string {
  const propre = normaliserNumero(numero)
  if (propre === '') return ''
  return `https://wa.me/${propre}?text=${encodeURIComponent(messageWhatsApp(cle))}`
}
