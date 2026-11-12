// Validation des identifiants d'entreprise marocains.
//
// L'ICE compte 15 chiffres : 9 pour l'entreprise, 4 pour l'établissement
// (0000 pour le siège) et 2 chiffres de contrôle. Ces deux derniers sont
// choisis de sorte que les 15 chiffres, lus comme un nombre et réduits par
// la méthode du modulo 97 (comme un IBAN), donnent 0. Un chiffre mal
// recopié ou deux chiffres inversés font que le reste n'est plus nul.
//
// Une clé valide garantit que le numéro est bien formé, pas qu'il appartient
// à une entreprise en activité. Pour cela, consulter la fiche sur
// maroc.welipro.com ou télécharger l'attestation sur ice.gov.ma.

/**
 * Décompose un ICE en ses trois parties.
 *
 * Retourne null quand la chaîne ne fait pas exactement 15 chiffres.
 */
export function decomposerIce(ice: string): {
  entreprise: string
  etablissement: string
  cleControle: string
} | null {
  const propre = ice.replace(/[\s-]/g, '')
  if (!/^\d{15}$/.test(propre)) return null
  return {
    entreprise: propre.slice(0, 9),
    etablissement: propre.slice(9, 13),
    cleControle: propre.slice(13, 15),
  }
}

/**
 * Modulo 97 progressif d'une chaîne de chiffres (algorithme IBAN).
 *
 * Chaque chiffre est consommé de gauche à droite en maintenant le reste.
 * Évite de convertir un grand nombre en entier (dépasserait safe integer).
 */
function modulo97(chiffres: string): number {
  let reste = 0
  for (const c of chiffres) {
    reste = (reste * 10 + Number(c)) % 97
  }
  return reste
}

/**
 * Vérifie la clé de contrôle d'un ICE par la méthode du modulo 97.
 *
 * Retourne true quand les 15 chiffres réduits modulo 97 donnent 0.
 * Retourne null quand la chaîne ne fait pas 15 chiffres.
 */
export function validerIceCleControle(ice: string): boolean | null {
  const propre = ice.replace(/[\s-]/g, '')
  if (!/^\d{15}$/.test(propre)) return null
  return modulo97(propre) === 0
}

/**
 * Lien vers l'outil de vérification Welipro (annuaire indépendant, 2,3 M
 * d'entreprises). Permet de confirmer qu'un ICE bien formé existe vraiment
 * et d'en lire la dénomination exacte.
 */
export function lienWeliproIce(ice: string): string {
  return `https://maroc.welipro.com/outils/verifier-ice?ice=${ice.replace(/[\s-]/g, '')}`
}
