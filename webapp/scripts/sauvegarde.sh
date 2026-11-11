#!/usr/bin/env bash
# Sauvegarde de la base FactureApp.
#
# Usage :
#   SUPABASE_DB_URL='postgresql://...' ./scripts/sauvegarde.sh
#
# `SUPABASE_DB_URL` vient de Supabase → Project Settings → Database →
# Connection string (mode « Session », jamais la clé anon). Le script écrit un
# dump compressé, le relit avant de le garder, puis supprime les dumps de plus
# de 30 jours. Il ne sauvegarde pas les PDF : ils vivent dans le bucket privé
# `archives` et se récupèrent avec `supabase storage cp`.
#
# Restauration : voir docs/status.md, section « Sauvegarde et restauration ».

set -euo pipefail

: "${SUPABASE_DB_URL:?Définir SUPABASE_DB_URL (chaîne de connexion Supabase)}"

DESTINATION="${FACTUREAPP_BACKUP_DIR:-$HOME/backups/factureapp}"
JOURS="${FACTUREAPP_BACKUP_JOURS:-30}"
HORODATAGE="$(date +%Y-%m-%d_%H%M)"
FICHIER="$DESTINATION/factureapp-$HORODATAGE.dump"

mkdir -p "$DESTINATION"

echo "→ dump vers $FICHIER"
pg_dump --no-owner --no-privileges --format=custom --file="$FICHIER" "$SUPABASE_DB_URL"

# Un dump non relu n'est pas une sauvegarde.
echo "→ vérification"
pg_restore --list "$FICHIER" > /dev/null

TAILLE="$(du -h "$FICHIER" | cut -f1)"
echo "✓ sauvegarde vérifiée ($TAILLE)"

echo "→ purge des dumps de plus de $JOURS jours"
find "$DESTINATION" -name 'factureapp-*.dump' -type f -mtime "+$JOURS" -print -delete

cat <<'EOF'

À faire une fois par mois, pour de vrai : restaurer le dernier dump dans une
base vide et compter les documents. Une sauvegarde jamais restaurée est une
hypothèse, pas une sauvegarde.
EOF
