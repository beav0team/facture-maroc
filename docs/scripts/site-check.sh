#!/usr/bin/env bash
# Contrôle du site avant publication.
#
#   docs/scripts/site-check.sh
#
# Vérifie que chaque page indexable a un titre unique, une description, un
# canonical et un seul <h1> ; que les images portent leurs dimensions ; que les
# liens locaux existent ; que le JSON-LD est du JSON valide ; et que le budget
# de polices n'est pas dépassé.
set -euo pipefail
cd "$(dirname "$0")/.."
python3 build.py --check
python3 scripts/site_check.py
