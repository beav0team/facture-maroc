# FactureApp — application web

Version web de Facture Maroc : facturation en ligne pour les commerces et TPE
au Maroc. L'app Android reste hors ligne et n'est pas touchée par ce dossier.

**État : M1.** Comptes, boutiques et isolation des données. L'éditeur de
factures, les devis et les avoirs arrivent en M2.

| | |
|---|---|
| Connexion | e-mail / mot de passe, et Google |
| Boutique | créée par son propriétaire à la première connexion |
| Isolation | RLS Postgres, vérifiée par `supabase/tests/rls_isolation.sql` |
| Factures | à venir (M2) |

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

SQL Editor → coller le contenu de `supabase/migrations/0001_init.sql` → Run.
Le script est idempotent, on peut le relancer.

Il crée `shops` et `memberships`, la fonction `is_shop_member()` utilisée par
les politiques, et la fonction `create_shop_for_me()` qui insère la boutique et
son appartenance « owner » dans la même transaction. Aucune écriture directe
n'est autorisée sur les deux tables : tout passe par ces fonctions.

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
supprimer chez l'autre. Tout se passe dans une transaction terminée par
`rollback` : aucune ligne ne survit, et les données existantes ne sont ni lues
ni modifiées.

SQL Editor → coller le fichier → Run. Sortie attendue :

```
NOTICE:  RLS : isolation vérifiée entre deux boutiques.
DO
ROLLBACK
```

Le test échoue si une politique est retirée. Il peut donc servir de garde-fou
après toute modification du schéma.

## Déploiement

`docs/` est servi tel quel par Cloudflare Pages (build command vide, output
`docs`). `docs/_redirects` renvoie `/app/*` vers `/app/index.html` pour que le
routeur survive à un rechargement.

GitHub Pages, l'hébergeur actuel, ignore `_redirects` : seul `/app/` répond
tant que la bascule vers Cloudflare n'est pas faite. Le routeur n'a qu'une
route pour l'instant, donc rien ne casse.

## Structure

```
webapp/
├─ src/
│  ├─ App.svelte              choix de l'écran selon session et route
│  ├─ lib/chemin.ts           chemins /app, module pur testé par vitest
│  ├─ lib/router.svelte.ts    routeur minimal (pushState + clics)
│  ├─ lib/session.svelte.ts   auth, boutique courante, messages d'erreur
│  ├─ lib/supabase.ts         client, ou null si non configuré
│  └─ routes/                 Login, Accueil, Introuvable
├─ supabase/
│  ├─ migrations/0001_init.sql
│  └─ tests/rls_isolation.sql
└─ tests/chemin.test.ts
```

## Limites connues de M1

- Pas encore d'éditeur de factures ; l'accueil l'annonce comme « bientôt ».
- « Mot de passe oublié » envoie le lien de réinitialisation, mais l'écran qui
  permet de choisir un nouveau mot de passe n'existe pas encore.
- Le plan gratuit (5 factures par mois) est affiché, pas encore appliqué :
  il n'y a pas de factures à compter.
- Une seule boutique par utilisateur dans l'interface. Le schéma en autorise
  plusieurs, `chargerBoutique()` prend la plus ancienne.
