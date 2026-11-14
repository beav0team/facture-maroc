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

# Le barème payant vit dans la console (migration 0016) : les blocs balisés du
# site sont réécrits depuis `reglages_publics()` avant d'être vérifiés. Si la
# base est injoignable, les valeurs déjà publiées sont conservées.
node scripts/tarifs-site.mjs

# Vérifie le site marketing avant de compiler : une page sans navigation, sans
# lien vers l'application ou avec un prix recopié fait échouer le déploiement.
node scripts/check-site.mjs

# `corepack enable` only when pnpm is missing (the Cloudflare build image).
# Run unconditionally, it replaces a working local pnpm with a shim that points
# at a file pnpm 12 does not ship (`bin/pnpm.cjs`) — the build then dies before
# installing anything.
if ! command -v pnpm >/dev/null 2>&1; then
  corepack enable
fi

pnpm install --frozen-lockfile
pnpm build
