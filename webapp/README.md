# FactureApp — application web

Version web de Facture Maroc : facturation en ligne pour les commerces et TPE
au Maroc. L'app Android reste hors ligne et n'est pas touchée par ce dossier.

**État : M3 à M7 faits, M8 partiel.** Comptes, boutiques, moteur de
facturation, catalogues, éditeur de documents, PDF archivé, paiements,
relevés, générateur public, tarifs, admin et import Android.

| | |
|---|---|
| Connexion | e-mail / mot de passe, et Google ; réinitialisation par e-mail vers un écran « Nouveau mot de passe » |
| Boutique | créée par son propriétaire à la première connexion |
| Isolation | RLS Postgres, vérifiée par `supabase/tests/` |
| Moteur | arithmétique, dates et numérotation portés depuis Kotlin ; fixtures partagées |
| Clients | clients et fournisseurs, avec ICE, IF, RC, Patente, CNSS |
| Articles | produits et services, prix, unité, dossier, stock et seuil |
| Réglages | identifiants légaux, coordonnées bancaires, TVA, logo |
| Documents | facture, devis, avoir ; TVA par ligne, remises, acompte, arrondi |
| Émission | numéro et snapshot boutique pris en SQL, document immuable |
| PDF | deux modèles, filigrane gratuit, archivé avec SHA-256 |
| Paiements | encaissements multiples, reste dû, retard, relevé client |
| Public | `/app/gratuit` sans compte, `/app/tarifs`, `/app/admin` |
| Import | sauvegarde JSON de l'app Android (clients, articles, compteurs) |

L'application est servie sous `https://factureapp.ma/app/`, à côté du site
marketing qui reste à la racine de `docs/`.

## Développement

Node 22 et pnpm.

```bash
cd webapp
pnpm install
cp .env.example .env      # puis renseigner les deux variables
pnpm dev                  # http://localhost:5174/app/
```

Vérifications :

```bash
pnpm check   # svelte-check : types et erreurs Svelte
pnpm test    # vitest
pnpm build   # écrit dans ../docs/app/
```

`pnpm build` vide `docs/app/` et le réécrit : le dossier est commité, il n'y a
pas d'étape de build côté hébergeur.

## Supabase

### 1. Créer le projet

supabase.com → New project. Noter la région (Europe de l'Ouest pour le Maroc)
et garder le mot de passe de la base.

### 2. Créer le schéma

SQL Editor → coller, dans cet ordre, `0001_init.sql`, `0002_catalogue.sql`,
`0003_documents.sql`, `0004_import.sql` puis `0005_admin.sql` → Run. Les cinq
scripts sont idempotents : on peut les relancer.

`0005_admin.sql` ajoute la console d'administration : lecture des boutiques
avec leur propriétaire, des comptes avec leur e-mail, des chiffres de la
plateforme, et les écritures correspondantes (abonnement, nomination et
retrait d'un administrateur). Tout est `security definer` et réservé aux
administrateurs : un onglet n'a jamais besoin du service_role.

Le premier crée `shops` et `memberships`, la fonction `is_shop_member()`
utilisée par les politiques, et la fonction `create_shop_for_me()` qui insère la
boutique et son appartenance « owner » dans la même transaction. Aucune
écriture directe n'est autorisée sur les deux tables : tout passe par ces
fonctions.

Le second ajoute `clients` et `articles` (chacun filtré par `shop_id` via la
RLS), les colonnes de réglages manquantes sur `shops`, et le bucket public
`logos` avec ses politiques : lecture libre, écriture réservée aux membres de la
boutique dont l'identifiant ouvre le chemin du fichier.

Le troisième ajoute les documents, leurs lignes, les paiements, les archives,
les abonnements et la numérotation. Aucun client n'écrit directement dans
`documents` : tout passe par `enregistrer_brouillon()`, `emettre_document()`
(numéro, snapshot boutique, quota), `creer_avoir()` et `apercu_numero()`. Le
bucket privé `archives` reçoit le PDF émis sous `<shop_id>/<document_id>.pdf` (le premier segment est la boutique, ce que vérifie la politique Storage).

Le quatrième ajoute `importer_compteurs()`, la seule écriture de numérotation
ouverte à une boutique, pour reprendre les compteurs d'une sauvegarde Android
sans jamais les faire reculer.

### 2 bis. Nommer un administrateur

Le panneau `/app/admin` (activation manuelle du plan Pro) est réservé aux
utilisateurs présents dans `platform_admins`. Après la première connexion,
récupérer l'identifiant dans Authentication → Users, puis dans SQL Editor :

```sql
insert into public.platform_admins (user_id)
values ('<uuid-de-l-utilisateur>')
on conflict do nothing;
```

La fonction `est_admin_plateforme()` lit cette table ; aucun membre de
boutique ne peut s'y ajouter lui-même (aucun privilège d'écriture).

### 3. Activer Google

1. Google Cloud Console → APIs & Services → Credentials → Create credentials
   → OAuth client ID → **Web application**.
   - Authorized redirect URI :
     `https://<référence-du-projet>.supabase.co/auth/v1/callback`
2. Supabase → Authentication → Providers → **Google** : activer, coller le
   Client ID et le Client Secret.
3. Supabase → Authentication → URL Configuration :
   - Site URL : `https://factureapp.ma/app/`
   - Redirect URLs : `https://factureapp.ma/app/` et
     `http://localhost:5174/app/`

L'app envoie `redirectTo = <origine>/app/`. Une URL absente de la liste
blanche est ignorée par Supabase, qui retombe sur la Site URL.

### 4. Renseigner les variables

Project Settings → API :

```
VITE_SUPABASE_URL=https://<référence>.supabase.co
VITE_SUPABASE_ANON_KEY=<clé anon public>
```

La clé « anon » est publique par conception : c'est la RLS qui protège les
données. Ne jamais mettre la clé `service_role` dans ce fichier — elle
contourne la RLS et serait visible dans le bundle.

Sans `.env`, l'app démarre quand même et affiche « Supabase n'est pas
configuré » : pratique pour travailler sur l'interface.

### 5. Vérifier l'isolation

`supabase/tests/rls_isolation.sql` crée deux utilisateurs jetables, fait créer
une boutique à chacun, et vérifie qu'aucun ne peut lire, modifier, insérer ou
supprimer chez l'autre. `rls_catalogue.sql` fait le même contrôle sur les
clients et les articles, y compris une insertion avec un `shop_id` forgé.
`rls_documents.sql` couvre la numérotation (l'aperçu ne consomme pas), le quota
de 5, l'immuabilité d'un document émis, l'avoir, les paiements et l'import des
compteurs. `rls_admin.sql` vérifie qu'un compte ordinaire ne lit ni les
boutiques ni les comptes et ne peut pas écrire un abonnement, qu'une date de
fin passée est refusée, et que le dernier administrateur ne peut pas se
retirer. Tout se passe dans une transaction terminée par `rollback` : aucune
ligne ne survit.

SQL Editor → coller un fichier → Run. Sortie attendue :

```
NOTICE:  RLS : isolation vérifiée entre deux boutiques.
NOTICE:  RLS catalogue : isolation clients/articles vérifiée.
NOTICE:  Documents : numérotation, quota, émission et avoir vérifiés.
DO
ROLLBACK
```

Les tests échouent si une politique est retirée. Ils servent de garde-fou après
toute modification du schéma.

## Déploiement

Le site est servi par **Cloudflare Pages** sur `https://factureapp.ma` : le
marketing à la racine de `docs/`, l'application sous `/app/`.

Réglages du projet Pages (`factureapp`) :

| Champ | Valeur |
|---|---|
| Branche de production | `site-m3` (puis `master` après fusion) |
| Commande de build | `bash webapp/scripts/build.sh` |
| Dossier de sortie | `docs` |
| Variables | `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`, `NODE_VERSION=22` |

Pièges vérifiés en production le 2026-11-11 :

- **`_redirects` ne doit jamais contenir `/app/* /app/ 200`.** Pages applique
  les règles même quand un fichier existe : le catch-all réécrivait aussi
  `/app/assets/index-*.js` vers la coquille HTML, donc l'app recevait du HTML
  à la place de son JavaScript. `docs/_redirects` liste les routes une par une.
- **La cible d'une réécriture 200 est `/app/`, pas `/app/index.html`** : Pages
  normalise `/index.html` vers `/` avec un 308, et cette normalisation
  s'applique aussi à la cible.
- **`VITE_*` est figé au build.** Sans les variables dans Pages, le bundle
  affiche « Supabase n'est pas configuré » même si le reste du site marche.
- **`pnpm install --frozen-lockfile` exige le lockfile à jour** : le pin
  `packageManager` ajoute un document au `pnpm-lock.yaml`. Lancer
  `pnpm install` une fois après avoir changé ce champ.

Le 2026-11-11, les enregistrements A de GitHub Pages ont été remplacés par le
CNAME `factureapp.pages.dev` via Cloudflare ; le domaine répond sur Pages et
les liens profonds (`/app/documents/<uuid>`) renvoient 200.

GitHub Pages ignore `_redirects` : ne pas y revenir pour l'app.

## Structure

```
webapp/
├─ src/
│  ├─ App.svelte              choix de l'écran selon session et route
│  ├─ lib/archive.ts          upload du PDF émis et re-téléchargement
│  ├─ lib/catalogue.ts        filtres et validation clients/articles
│  ├─ lib/chemin.ts           chemins /app, module pur testé par vitest
│  ├─ lib/dates.ts            dates strictes, retards, jours restants
│  ├─ lib/document.ts         types de document, TVA, moyens de paiement
│  ├─ lib/documents.ts        lignes de base lues par le moteur, statuts
│  ├─ lib/documentsApi.ts     appels Supabase des documents
│  ├─ lib/importAndroid.ts    lecture du backup JSON Android
│  ├─ lib/invoice.ts          arithmétique portée depuis InvoiceData.kt
│  ├─ lib/money.ts            arrondi et écriture des montants
│  ├─ lib/numbering.ts        numéros et compteurs par type
│  ├─ lib/pdf.ts              rendu pdf-lib déterministe (deux modèles)
│  ├─ lib/prix.ts             prix HT/TTC, porté de PriceBasis.kt
│  ├─ lib/router.svelte.ts    routeur minimal (pushState + clics)
│  ├─ lib/session.svelte.ts   auth, boutique courante, messages d'erreur
│  ├─ lib/storage.ts          bucket des logos
│  ├─ lib/supabase.ts         client, ou null si non configuré
│  ├─ lib/ui/                 Barre, SansBoutique
│  └─ routes/                 Login, Accueil, Documents, éditeur, Clients,
│                             Articles, Paramètres, Relevé, Générateur,
│                             Tarifs, Admin, Import
├─ supabase/
│  ├─ migrations/0001_init.sql … 0005_admin.sql
│  ├─ tests/rls_isolation.sql
│  ├─ tests/rls_catalogue.sql
│  ├─ tests/rls_documents.sql
│  └─ tests/rls_admin.sql
└─ tests/                     money, dates, fixtures, catalogue, écrans,
                              documents, prix, pdf, import, admin
```

## Fixtures partagées

`fixtures/factures.json` et `fixtures/numerotation.json` vivent à la racine du
dépôt, hors de `webapp/`. Les blocs `attendu` sont produits par le moteur
Kotlin, jamais écrits à la main :

```bash
./gradlew :app:testDebugUnitTest --tests 'com.yassine.facturemaroc.data.FixtureTest'
```

Le premier passage écrit les valeurs et échoue ; le second vérifie. Le test
`webapp/tests/fixtures.test.ts` rejoue les mêmes fichiers : une divergence entre
le site et l'application Android casse la suite.

## Limites connues

- Le paiement en ligne n'existe pas : le plan Pro est activé manuellement dans
  `/app/admin` après virement ou espèces. C'est assumé pour le lancement.
- Les paliers de prix et les attributs libres d'un article sont stockés et
  conservés, mais pas encore modifiables dans l'interface web.
- Une seule boutique par utilisateur dans l'interface. Le schéma en autorise
  plusieurs, `chargerBoutique()` prend la plus ancienne.
- L'import Android ramène les documents en brouillons : un numéro déjà imprimé
  n'est jamais réémis par la webapp.
- Les écritures supposent une connexion : la v1 est « en ligne ». La file
  hors ligne (outbox) du plan n'est pas encore branchée ; l'app Android reste le
  produit de terrain pour les coupures.
- Sauvegarde/restauration et monitoring restent à faire (M8) : voir
  `scripts/sauvegarde.sh` pour le dump Postgres.
