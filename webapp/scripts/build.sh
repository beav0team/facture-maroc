#!/usr/bin/env bash
# Build de la webapp sur Cloudflare Pages.
#
# Le dossier de sortie reste `docs`, à la racine du dépôt : le site marketing
# et l'application sont servis ensemble, et `docs/_redirects` fait survivre le
# routeur de l'app à un rechargement.
#
# Utilisé comme commande de build : `bash webapp/scripts/build.sh`.

set -euo pipefail

cd "$(dirname "$0")/.."

# Vérifie le site marketing avant de compiler : une page sans navigation, sans
# lien vers l'application ou avec un prix recopié fait échouer le déploiement.
node scripts/check-site.mjs

corepack enable
pnpm install --frozen-lockfile
pnpm build
