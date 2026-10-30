# Mise en ligne sur Google Play — marche à suivre

Compte développeur vérifié le 30 octobre 2026. Tout le texte à copier est dans
`listing-fr.md`, les images dans ce dossier. Ce document ne décrit que les clics
et les réponses aux formulaires.

## 1. Ce qui part chez Google

| Élément | Valeur |
|---|---|
| Paquet | `com.yassine.facturemaroc` |
| Version | `0.7.3` (`versionCode` 11) |
| Cible | API 36, minimum API 26 |
| Permissions | `CAMERA` seule, facultative |
| Fichier | `app/build/outputs/bundle/release/app-release.aab` |
| Taille | 15 210 219 octets |
| SHA-256 | `93caaf6599a73d57d27c30a40b3095bb62b1ba9e4996fdd63673d7de6893475b` |
| Signature | clé de `~/.android/tyre-inventory.jks` (alias `inventory`) |

L'AAB se reconstruit avec `./gradlew :app:bundleRelease`. **Ne jamais téléverser
un APK** : Play n'accepte que les App Bundles pour une nouvelle application.

## 2. Créer l'application

1. Play Console → **Créer une application**.
2. Nom : `Facture Maroc`. Langue par défaut : **français (fr-FR)**.
3. Type : **Application**. Gratuite : **Oui** (irréversible — un changement
   ultérieur vers payant est impossible).
4. Déclarations : application non destinée aux enfants, respect des lois
   américaines sur l'export.

## 3. Fiche du Play Store

- **Description courte** et **description complète** : copier depuis
  `listing-fr.md` (bloc de citation, sections « Description complète »).
- **Icône** : `icon-512.png`. **Bannière** : `feature-graphic-1024x500.png`.
- **Captures d'écran téléphone** : les quatre de `screenshots/`
  (`01-factures`, `02-builder`, `03-clients`, `04-articles`). Play exige au
  moins deux captures 16:9 ou 9:16 ; les nôtres sont en 1080 × 2160.
- **Catégorie** : Business. **Coordonnées** : e-mail de contact obligatoire
  (public et vérifié par Google) — à saisir, il ne peut pas être deviné.
- **Politique de confidentialité** :
  `https://beav0team.github.io/facture-maroc/privacy.html` (en ligne, vérifiée).

## 4. Contenu de l'application (obligatoire avant toute publication)

| Section | Réponse |
|---|---|
| Confidentialité | l'URL ci-dessus |
| Sécurité des données | **aucune collecte, aucun partage** (voir `listing-fr.md`) |
| Publicités | non |
| Classification du contenu | questionnaire IARC → outil professionnel, tout public |
| Public cible | 18 ans et plus (outil professionnel) |
| Accès à l'application | aucune connexion : tout est ouvert au premier lancement |
| Fonctionnalités financières | non — l'app rédige des documents, elle ne paie ni ne crédite |
| Application gouvernementale | non |
| Actualités | non |
| Suivi (tracking) | non |

La réponse « aucune donnée collectée » n'est exacte que depuis la 0.7.2 : ML Kit
ajoutait `INTERNET` par son propre manifeste pour téléverser ses journaux. C'est
retiré, et `CAMERA` est la seule permission de l'AAB.

La 0.7.3 ne change aucune de ces réponses : c'est un audit du code avant la mise
en ligne (prix TTC relus au bon taux, brouillon relu à l'ouverture, historique
illisible mis de côté, colonne de quantité du ticket mesurée). Aucun accès
réseau n'a été ajouté, et le fichier est celui qui porte le SHA-256 ci-dessus —
le vérifier avant de le téléverser évite d'envoyer par erreur l'AAB de la 0.7.2.

## 5. Signature

Play App Signing est obligatoire. **Utiliser la clé existante** comme clé de
signature de l'application : les commerçants qui ont installé l'APK depuis
GitHub doivent pouvoir passer par Play sans désinstaller. Play demande le
certificat public de la clé (`SHA-256`
`5A:9B:E2:A8:9E:09:D3:B1:A5:B5:0C:91:9D:0E:73:B7:08:67:8E:A4:E1:48:77:97:A0:6A:89:AD:D1:41:E6:6C`).

La clé a séjourné dans un dépôt public avant d'en être purgée. Elle ne peut plus
servir à publier sur Play une fois qu'un **nouvel upload key** est enregistré
(Play Console → Configuration → Intégrité de l'application → Gérer la clé
d'envoi). À faire juste après le premier téléversement réussi.

## 6. Pistes de test, puis production

- **Test interne** : disponible immédiatement, quelques minutes d'attente.
  C'est la bonne piste pour installer l'AAB et vérifier qu'il s'ouvre.
- **Test fermé** : obligatoire avant la production **si le compte est un compte
  personnel créé après le 13 novembre 2023** — 12 testeurs opt-in pendant
  14 jours consécutifs, puis demande d'accès à la production. Les comptes
  « organisation » (numéro D-U-N-S) en sont dispensés. Si l'entrée
  **Production** est grisée dans le tableau de bord, c'est cette règle.
- **Production** : téléverser l'AAB, décrire la version, envoyer en revue.
  Comptez de quelques heures à quelques jours ; une première application est
  relue plus lentement.
