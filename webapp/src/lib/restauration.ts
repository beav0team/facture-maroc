// Relire un export « Exporter mes données ».
//
// L’export est la copie que la boutique emporte. Il doit donc pouvoir rentrer :
// un fichier qu’on ne sait pas relire n’est pas une sauvegarde, c’est un
// reçu. Deux limites assumées, écrites dans l’écran :
//
//   - un document émis ne se réinsère pas — la RLS l’interdit, et un numéro
//     déjà imprimé n’appartient plus au logiciel. Les documents reviennent en
//     brouillons, avec leur ancien numéro en référence, exactement comme
//     l’import d’une sauvegarde Android ;
//   - les paiements ne reviennent pas : ils pendent à un document émis, et un
//     brouillon restauré n’est pas ce document.

import type { Resultat } from './documentsApi'
import {
  factureDeDocument,
  type DocumentRow,
  type LigneRow,
  type PaiementRow,
} from './documents'
import type { ExportDonnees } from './export'
import type { Facture } from './invoice'

/** Le même plafond que l’import Android : au-delà, le navigateur souffre. */
export const TAILLE_MAX_EXPORT = 32 * 1024 * 1024

export type DocumentRestaure = {
  /** Le numéro d’origine, gardé en référence sur le brouillon. */
  numero: string
  statut: string
  type: string
  facture: Facture
}

export type Restauration = {
  boutique: ExportDonnees['boutique']
  clients: Record<string, unknown>[]
  articles: Record<string, unknown>[]
  documents: DocumentRestaure[]
  compteurs: Record<string, unknown>
  /** Comptés pour le rapport : ils ne sont pas réinsérés. */
  paiementsNonRestaures: number
  exporteLe: string
}

function objet(valeur: unknown): Record<string, unknown> | null {
  return valeur && typeof valeur === 'object' && !Array.isArray(valeur)
    ? (valeur as Record<string, unknown>)
    : null
}

/**
 * Valide le fichier avant d’en toucher une ligne. Un export d’une autre
 * application, tronqué, ou d’une version future est refusé en entier plutôt
 * qu’importé à moitié.
 */
export function lireExport(texte: string): Resultat<ExportDonnees> {
  let brut: unknown
  try {
    brut = JSON.parse(texte)
  } catch {
    return { donnees: null, erreur: 'Ce fichier n’est pas du JSON.' }
  }

  const objetLigne = objet(brut)
  if (!objetLigne || objetLigne.application !== 'factureapp' || objetLigne.format !== 'export') {
    return {
      donnees: null,
      erreur: 'Ce fichier n’est pas un export FactureApp. Attendu : factureapp-….json.',
    }
  }

  const version = typeof objetLigne.version === 'number' ? objetLigne.version : 0
  if (version > 1) {
    return {
      donnees: null,
      erreur:
        'Cet export vient d’une version plus récente de FactureApp : mettez l’application à jour avant de le restaurer.',
    }
  }

  if (!objet(objetLigne.boutique)) {
    return { donnees: null, erreur: 'L’export ne contient pas de boutique.' }
  }

  for (const cle of ['clients', 'articles', 'documents', 'lignes', 'paiements'] as const) {
    if (!Array.isArray(objetLigne[cle])) {
      return { donnees: null, erreur: `L’export est incomplet : « ${cle} » manque.` }
    }
  }

  return { donnees: objetLigne as unknown as ExportDonnees, erreur: null }
}

/**
 * Reconstruit les documents de l’export en brouillons prêts à enregistrer.
 *
 * Les lignes et les paiements sont rangés par `document_id`, comme dans la
 * base : ils n’ont pas de `shop_id`. Un document sans ligne reste restaurable
 * (un brouillon vide est légal), un document dont l’identifiant manque est
 * ignoré.
 */
export function preparerRestauration(donnees: ExportDonnees): Resultat<Restauration> {
  const lignesParDocument = new Map<string, LigneRow[]>()
  for (const brute of donnees.lignes) {
    const ligne = objet(brute) as LigneRow | null
    const id = typeof ligne?.document_id === 'string' ? ligne.document_id : ''
    if (!ligne || id === '') continue
    const liste = lignesParDocument.get(id) ?? []
    liste.push(ligne)
    lignesParDocument.set(id, liste)
  }

  const paiementsParDocument = new Map<string, PaiementRow[]>()
  for (const brute of donnees.paiements) {
    const paiement = objet(brute) as PaiementRow | null
    const id = typeof paiement?.document_id === 'string' ? paiement.document_id : ''
    if (!paiement || id === '') continue
    const liste = paiementsParDocument.get(id) ?? []
    liste.push(paiement)
    paiementsParDocument.set(id, liste)
  }

  const documents: DocumentRestaure[] = []
  for (const brute of donnees.documents) {
    const ligne = objet(brute)
    const id = typeof ligne?.id === 'string' ? ligne.id : ''
    if (!ligne || id === '') continue
    const row = ligne as unknown as DocumentRow
    const facture = factureDeDocument(
      row,
      lignesParDocument.get(id) ?? [],
      paiementsParDocument.get(id) ?? [],
    )
    // Le brouillon restauré ne porte pas de numéro : il sera numéroté à sa
    // propre émission. L’ancien reste en référence pour retrouver le papier.
    facture.numero = ''
    if (row.numero?.trim()) {
      facture.reference = facture.reference?.trim()
        ? `${facture.reference} · Restauration ${row.numero}`
        : `Restauration ${row.numero}`
    }
    documents.push({
      numero: row.numero ?? '',
      statut: row.statut ?? 'brouillon',
      type: row.type ?? 'facture',
      facture,
    })
  }

  return {
    donnees: {
      boutique: donnees.boutique,
      clients: donnees.clients.map((client) => objet(client)).filter((c): c is Record<string, unknown> => c !== null),
      articles: donnees.articles.map((article) => objet(article)).filter((a): a is Record<string, unknown> => a !== null),
      documents,
      compteurs: objet(donnees.compteurs) ?? {},
      paiementsNonRestaures: donnees.paiements.length,
      exporteLe: typeof donnees.exporte_le === 'string' ? donnees.exporte_le : '',
    },
    erreur: null,
  }
}

/** La phrase que l’écran affiche avant de lancer la restauration. */
export function resumeRestauration(plan: Restauration): string {
  const morceaux = [
    `${plan.clients.length} client${plan.clients.length > 1 ? 's' : ''}`,
    `${plan.articles.length} article${plan.articles.length > 1 ? 's' : ''}`,
    `${plan.documents.length} document${plan.documents.length > 1 ? 's' : ''} en brouillon`,
  ]
  if (plan.paiementsNonRestaures > 0) {
    morceaux.push(
      `${plan.paiementsNonRestaures} paiement${plan.paiementsNonRestaures > 1 ? 's' : ''} non restauré${plan.paiementsNonRestaures > 1 ? 's' : ''}`,
    )
  }
  return morceaux.join(', ')
}
