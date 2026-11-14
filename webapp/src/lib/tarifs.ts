// Les tarifs payants sont réglés depuis la console (migration 0016) et lus par
// `/app/tarifs`. `offres.ts` garde les valeurs par défaut, la structure des
// cartes et les clés de plan ; ce fichier fusionne les valeurs réglées dessus.
//
// Deux règles tenues ici :
//   · un réglage partiel ne casse rien — champ absent ou invalide, on retombe
//     sur le défaut, jamais sur `NaN` ni sur une chaîne vide affichée ;
//   · le nombre imprimé et le nombre appliqué sont le même. `quota_limite()`
//     lit la même clé côté SQL ; `TARIFS_DEFAUT` n'est qu'un filet.

import { OFFRES, type ClePlan, type Offre } from './offres'

export interface TarifPlan {
  /** Prix annuel en dirhams, entier. */
  prix: number
  /** Documents facturés par mois ; `null` = illimité (Pro seulement). */
  quota: number | null
  /** Les puces affichées sur la carte, dans l'ordre. */
  points: string[]
}

export interface TarifsRegles {
  essentiel: TarifPlan
  pro: TarifPlan
}

const pointsOffre = (cle: ClePlan): string[] =>
  OFFRES.find((offre) => offre.cle === cle)?.points ?? []

const prixOffre = (cle: ClePlan): number =>
  Number.parseInt(OFFRES.find((offre) => offre.cle === cle)?.prix ?? '0', 10)

/** Le barème publié tant que la console n'a rien réglé. */
export const TARIFS_DEFAUT: TarifsRegles = {
  // 50 est la limite appliquée par `quota_limite()` en 0014 ; elle reste le
  // défaut si la clé `tarifs` est absente ou illisible.
  essentiel: { prix: prixOffre('essentiel'), quota: 50, points: pointsOffre('essentiel') },
  pro: { prix: prixOffre('pro'), quota: null, points: pointsOffre('pro') },
}

const entier = (valeur: unknown, min: number, max: number): number | null => {
  const nombre = typeof valeur === 'string' ? Number(valeur) : valeur
  if (typeof nombre !== 'number' || !Number.isFinite(nombre)) return null
  if (!Number.isInteger(nombre) || nombre < min || nombre > max) return null
  return nombre
}

const listePoints = (valeur: unknown, defaut: string[]): string[] => {
  if (!Array.isArray(valeur)) return defaut
  const propre = valeur
    .filter((p): p is string => typeof p === 'string')
    .map((p) => p.trim())
    .filter((p) => p.length >= 3 && p.length <= 140)
  return propre.length ? propre.slice(0, 8) : defaut
}

/**
 * Lit `reglages_publics().tarifs` (ou n'importe quel objet venu du réseau) et
 * rend un barème complet. Un champ douteux retombe sur le défaut : une page de
 * tarifs ne doit jamais afficher « NaN DH ».
 */
export function tarifsDepuisReglages(brut: unknown): TarifsRegles {
  const objet = (brut ?? {}) as Record<string, unknown>
  const plan = (cle: keyof TarifsRegles, defaut: TarifPlan): TarifPlan => {
    const source = objet[cle]
    const valeurs = (source && typeof source === 'object' ? source : {}) as Record<string, unknown>
    const quotaLisible =
      cle === 'pro' ? null : (entier(valeurs.quota, 6, 100_000) ?? defaut.quota)
    return {
      prix: entier(valeurs.prix, 50, 50_000) ?? defaut.prix,
      quota: quotaLisible,
      points: listePoints(valeurs.points, defaut.points),
    }
  }
  return {
    essentiel: plan('essentiel', TARIFS_DEFAUT.essentiel),
    pro: plan('pro', TARIFS_DEFAUT.pro),
  }
}

/** Les trois cartes de `/app/tarifs`, prix et puces remplacés par le barème. */
export function offresDepuis(tarifs: TarifsRegles): Offre[] {
  return OFFRES.map((offre) => {
    if (offre.cle === 'gratuit') return offre
    const tarif = tarifs[offre.cle]
    return { ...offre, prix: `${tarif.prix} DH`, points: tarif.points }
  })
}

/** Le texte d'une zone de saisie (une puce par ligne) vers une liste. */
export function pointsDepuisTexte(texte: string): string[] {
  return texte
    .split('\n')
    .map((ligne) => ligne.trim())
    .filter((ligne) => ligne !== '')
}

export function texteDepuisPoints(points: string[]): string {
  return points.join('\n')
}

/**
 * Les mêmes bornes que `admin_tarifs()` en SQL, pour que la console refuse un
 * barème invalide avant l'aller-retour. Le SQL reste la dernière ligne.
 */
export function validerTarifs(tarifs: TarifsRegles): string | null {
  for (const cle of ['essentiel', 'pro'] as const) {
    const tarif = tarifs[cle]
    const nom = cle === 'essentiel' ? 'Essentiel' : 'Pro'
    if (!Number.isInteger(tarif.prix) || tarif.prix < 50 || tarif.prix > 50_000) {
      return `${nom} : le prix doit être un nombre entier entre 50 et 50 000 DH.`
    }
    if (cle === 'essentiel') {
      if (tarif.quota === null || !Number.isInteger(tarif.quota) || tarif.quota < 6 || tarif.quota > 100_000) {
        return 'Essentiel : le nombre de documents doit être un entier entre 6 et 100 000.'
      }
    } else if (tarif.quota !== null) {
      return 'Pro reste illimité : son quota ne se règle pas.'
    }
    if (tarif.points.length < 1 || tarif.points.length > 8) {
      return `${nom} : entre 1 et 8 prestations.`
    }
    if (tarif.points.some((point) => point.trim().length < 3 || point.trim().length > 140)) {
      return `${nom} : chaque prestation fait entre 3 et 140 caractères.`
    }
  }
  return null
}
