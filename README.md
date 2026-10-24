# Facture Maroc

**Facturation hors ligne pour les commerçants marocains.**
Factures, devis, bons de commande, tickets, avoirs, proformas, acomptes,
régularisations, factures simplifiées et factures à l'export — avec ICE, IF, RC,
patente, CNSS, TVA, montants en toutes lettres et mode auto-entrepreneur.

Version **0.3.0** (versionCode 4) · Android 8.0+ (minSdk 26) · Français.

<img src="play_store/screenshots/01-factures.png" width="260" alt="Écran Factures">

## Ce que fait l’application

Trois onglets, pas de détour :

- **Factures** — brouillons et documents émis, total facturé du mois, recherche,
  duplication, suppression, export XML.
- **Clients** — carnet d’adresses avec adresse, ICE, IF, RC, patente, CNSS et
  téléphone ; le client se remplit sur la facture en un tap.
- **Articles** — catalogue de prestations et de produits, **rangé en dossiers**
  (« Prestations », « Produits », ou tout autre dossier créé par le commerce).

Dans le constructeur de document :

- **Type de document** : facture, devis, bon de commande, ticket 80 mm, avoir,
  proforma, acompte, régularisation, facture simplifiée, facture à l'export.
  Chacun a sa propre séquence de numérotation (`F-`, `D-`, `BC-`, `T-`, `A-`,
  `PF-`, `AC-`, `REG-`, `FS-`, `E-`).
- Lignes prises dans le catalogue ou **tapées à la main**, quantité et prix
  modifiables, remise par ligne et remise générale.
- **TVA configurable** (20 % par défaut), **exonération par ligne** et **mode
  auto-entrepreneur** (TVA non applicable sur tous les documents du magasin).
- Avoir, acompte et régularisation portent une **référence** ("Annule et
  remplace la facture N° …") ; l'export porte un **Incoterms** facultatif.
- **Montant total écrit en toutes lettres**, en français, avec l'ouverture
  adaptée au type de document.
- Quatre modèles PDF : **Classique, Moderne, Facture Maroc, Ticket**.
- Logo du commerce, coordonnées et mentions légales sur chaque document.

Les documents sont générés en PDF dans le cache de l’application, puis remis au
sélecteur Android. L’application n’a **aucun accès à vos données** : elle ne
demande ni Internet, ni stockage, ni contacts.

## Confidentialité

- Aucun compte, aucune publicité, aucun traceur, aucun serveur.
- Les données restent dans l’espace privé de l’application.
- Les PDF, XML et sauvegardes ne sortent que sur une action de l’utilisateur.
- La sauvegarde automatique d’Android, si elle est activée sur le téléphone,
  peut copier les données dans le compte Google de l’utilisateur ; c’est le
  système Android qui la gère, pas l’application.

Politique complète : [`docs/privacy.html`](docs/privacy.html) — publiée sur
GitHub Pages à l’adresse
`https://beav0team.github.io/facture-maroc/privacy.html`.

> **Sauvegarde et restauration** : Android peut sauvegarder l’application, mais
> l’application fournit aussi son propre fichier de sauvegarde (Paramètres →
> Sauvegarde). Ce fichier contient toutes les données, **y compris les clients**,
> et n’est **pas chiffré**. Gardez-le dans un endroit sûr.

## Construire

Prérequis : JDK 21 et le SDK Android (compileSdk 35). Le projet utilise le
wrapper Gradle fourni.

```bash
export JAVA_HOME="$HOME/.local/jdk"      # ou votre JDK 21
./gradlew testDebugUnitTest             # 60 tests JVM
./gradlew assembleDebug                 # app/build/outputs/apk/debug/app-debug.apk
./gradlew bundleRelease                 # app/build/outputs/bundle/release/app-release.aab
./gradlew assembleRelease               # app/build/outputs/apk/release/app-release.apk
```

### Signature

La release est signée avec le keystore déclaré dans `keystore.properties`.
Ce fichier est **ignoré par Git** ; `keystore.properties.example` montre les
clés attendues. Ne commitez jamais le keystore ni ses mots de passe.

Le même keystore que la version 0.1.0 est utilisé, pour que les mises à jour
s’installent par-dessus l’application existante. Une rotation de mot de passe
`keytool -storepasswd` conserve le certificat et n’empêche pas les mises à jour.

## Tests

**60 tests JVM**, sans Android instrumenté :

| Suite | Ce qu’elle protège |
|---|---|
| `AmountWordsTest` | Nombres en lettres, accords de « cent », centimes, type de document |
| `InvoiceDataTest` | Sous-total, remise, TVA, exonération, arrondis au centime |
| `InvoiceJsonTest` | Aller-retour JSON sans perdre un champ imprimé |
| `InvoiceValidationTest` | Champs obligatoires et blocage des documents incomplets |
| `InvoiceXmlTest` | Structure XML, échappement, totaux et numéro |
| `BackupTest` | Sauvegarde, restauration, compatibilité, plafond de taille, dossiers |
| `SavedItemTest` | Articles, dossiers, doublons, migration v1 → v2 |
| `InvoiceSummaryTest` | Le total du mois exclut devis, bons de commande et brouillons |

Les captures Play utilisent un jeu de démonstration (« Atelier Atlas »), jamais
les données d’un client réel.

## Données et formats

Tout est local, en JSON ou dans les préférences Android :

- `filesDir/invoices.json` — brouillons et documents émis.
- `filesDir/saved_items.json` — articles et dossiers (format v2).
- `filesDir/shop_logo.png` — logo copié depuis le sélecteur de photos.
- Préférences — commerce, clients, compteurs de numérotation, design.

**Sauvegarde** : un seul fichier JSON versionné (format 1) contenant commerce,
logo, clients, articles, dossiers, factures, compteurs et préférences. La
restauration valide le fichier puis remplace les données après confirmation.

**XML** : espace de noms `urn:facture-maroc:invoice:1.0`, écrit avec le numéro
définitif du document. Un export XML réussi consomme le numéro et marque le
document comme émis ; un échec remet le compteur en place.

## Structure

```
app/src/main/java/com/yassine/facturemaroc/
├── MainActivity.kt            navigation entre les trois onglets et le constructeur
├── data/                      modèles, stores JSON, sauvegarde, numérotation
├── invoice/                   rendu PDF (4 modèles), XML, validation
└── ui/                        Compose : accueil, constructeur, éditeurs, pickers
```

Architecture volontairement simple : une activité, Compose Material 3, des
`StateFlow` exposés par les stores, aucune injection de dépendances, aucune
base de données, aucun appel réseau.

## Limites connues

- Ce n’est **pas un logiciel de comptabilité certifié**. Les mentions
  obligatoires (ICE, IF, RC, patente, CNSS, TVA) doivent être validées par le
  comptable du commerce.
- Pas de suivi des paiements, de crédit client, de stock ni de rapports : choix
  de périmètre de cette version.
- Pas encore de synchronisation cloud ni de connexion Google. Une sauvegarde
  cloud facultative viendra plus tard, dans une version séparée, avec sa propre
  mise à jour de la politique de confidentialité.
- Application mono-appareil ; le transfert passe par le fichier de sauvegarde.
- Interface en français uniquement.

## Play Store

Les ressources sont prêtes dans `play_store/` :

- `icon-512.png`, `feature-graphic-1024x500.png` ;
- `screenshots/` — quatre captures 1080 × 2160 ;
- `listing-fr.md` — titre, descriptions, catégorie, Data Safety, classification.

Le compte Play Console n’est pas encore créé. Il faudra y renseigner l’adresse
e-mail de contact (obligatoire et vérifiée par Google) et publier l’AAB signé.

## Historique

- **0.4.0** — nouveaux types de document : avoir, proforma, acompte,
  régularisation, facture simplifiée, facture à l'export, chacun avec sa propre
  numérotation ; référence ("Annule et remplace la facture N° …") sur les avoirs,
  acomptes et régularisations ; Incoterms sur l'export ; montant en toutes
  lettres adapté au type de document ; mode auto-entrepreneur (TVA non
  applicable).
- **0.3.0** — refonte en trois onglets, constructeur unique, clients et articles
  enregistrés, dossiers, validation, export XML, sauvegarde/restauration locale,
  nouvelle icône et ressources Play.
- **0.1.0** — première version fonctionnelle : documents, PDF, montant en
  lettres, modèles, logo et mentions légales.
