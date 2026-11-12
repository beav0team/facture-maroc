// Les deux formules, à un seul endroit.
//
// Le site marketing n'affiche aucun montant : la seule page qui imprime un
// prix est /app/tarifs, et elle lit ce fichier. Changer le prix du Pro ici le
// change partout, sans copie à retrouver dans le HTML.
//
// Le tarif n'est pas encore arbitré (le paiement en ligne n'est pas ouvert) :
// 990 DH est le chiffre affiché aujourd'hui, pas une décision figée.

export const CONTACT_PRO =
  'mailto:contact@factureapp.ma?subject=Passage%20au%20plan%20Pro%20FactureApp'

export interface Offre {
  nom: string
  prix: string
  periode: string
  texte: string
  points: string[]
  action: string
  lien: string
  principal: boolean
}

export const OFFRES: Offre[] = [
  {
    nom: 'Gratuit',
    prix: '0 DH',
    periode: 'pour toujours',
    texte: 'Pour essayer et facturer quelques clients.',
    points: [
      '5 documents facturés par mois (factures, tickets, acomptes)',
      'Clients, articles et devis illimités',
      'PDF archivé, sans filigrane',
      'Accès depuis téléphone et ordinateur',
    ],
    action: 'Commencer',
    lien: '/app/',
    principal: false,
  },
  {
    nom: 'Pro',
    prix: '990 DH',
    periode: 'par an',
    texte: 'Pour un commerce qui facture tous les jours.',
    points: [
      'Documents facturés illimités',
      'Un an, sans reconduction automatique',
      'Support par e-mail',
    ],
    action: 'Passer au Pro',
    lien: CONTACT_PRO,
    principal: true,
  },
]
