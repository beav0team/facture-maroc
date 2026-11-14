// Les trois formules : la structure, les libellés et les valeurs par défaut.
//
// Depuis la 0016, le prix annuel, le quota et les prestations des deux paliers
// payants se règlent dans la console (`/app/admin/reglages`) et vivent en base ;
// ce fichier fournit les défauts quand rien n'est réglé, la clé et le nom de
// chaque palier, et les puces du plan gratuit, qui ne se vend pas. Changer un
// prix ici change donc le défaut, pas le barème en vigueur.
//
// Trois paliers, un seul axe : les documents facturés par mois. Le plan
// gratuit reste à cinq — c'est le plancher du marché marocain, s'y aligner est
// la condition d'entrée, pas un argument. Essentiel (240 DH/an) couvre le
// commerce qui facture chaque jour sans atteindre l'illimité ; Pro (480 DH/an)
// restait, au moment de l'écrire, l'illimité le moins cher publié au Maroc.
//
// Aucune promesse que le produit ne tient pas : pas de « 3 utilisateurs »
// (aucune invitation n'existe), pas de « plusieurs boutiques » (rien ne bride
// la création), pas de quotas de stockage (les archives sont illimitées).

export const CONTACT_ESSENTIEL =
  'mailto:contact@factureapp.ma?subject=Passage%20au%20plan%20Essentiel%20FactureApp'

export const CONTACT_PRO =
  'mailto:contact@factureapp.ma?subject=Passage%20au%20plan%20Pro%20FactureApp'

export type ClePlan = 'gratuit' | 'essentiel' | 'pro'

export interface Offre {
  /** La clé écrite en base dans `subscriptions.plan`. */
  cle: ClePlan
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
    cle: 'gratuit',
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
    cle: 'essentiel',
    nom: 'Essentiel',
    prix: '240 DH',
    periode: 'par an',
    texte: 'Pour un commerce qui facture chaque jour, sans se ruiner.',
    points: [
      '50 documents facturés par mois',
      'Devis, avoirs, clients et articles illimités',
      'PDF archivé, sans filigrane',
      'Un an, sans reconduction automatique',
      'Support par e-mail',
    ],
    action: 'Demander Essentiel',
    lien: CONTACT_ESSENTIEL,
    principal: true,
  },
  {
    cle: 'pro',
    nom: 'Pro',
    prix: '480 DH',
    periode: 'par an',
    texte: 'Pour une boutique qui ne compte plus ses factures.',
    points: [
      'Documents facturés illimités',
      'Devis, avoirs, clients et articles illimités',
      'PDF archivé, sans filigrane',
      'Un an, sans reconduction automatique',
      'Support par e-mail et WhatsApp',
    ],
    action: 'Passer au Pro',
    lien: CONTACT_PRO,
    principal: false,
  },
]

/** Le libellé d'un palier, pour les écrans qui n'affichent qu'un mot. */
export function nomOffre(cle: ClePlan): string {
  return OFFRES.find((offre) => offre.cle === cle)?.nom ?? 'Gratuit'
}
