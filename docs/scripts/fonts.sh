#!/usr/bin/env bash
# Régénère les polices auto-hébergées de docs/assets/fonts.
#
#   docs/scripts/fonts.sh                 # utilise le cache ~/.cache/factureapp-fonts
#   docs/scripts/fonts.sh /chemin/vers/src
#
# Pourquoi : les fichiers servis par Google Fonts embarquent tous les glyphes
# latins (75 Ko pour Bricolage, 45 Ko pour Plex Sans). Le site n'utilise que le
# français, et Bricolage traîne un axe optique (opsz) que le CSS ne demande pas.
# On garde donc 210 glyphes, on fige opsz à 48 (la taille des titres) et on
# limite chaque axe à ce que le CSS déclare :
#
#   bricolage   75 Ko -> 35 Ko   (opsz 48, wght 400–800)
#   plex-sans   45 Ko -> 31 Ko   (wght 400–600)
#   plex-mono   15 Ko -> 12 Ko   (400 et 500 seulement, le 600 n'est pas utilisé)
#
# Les sources non sous-ensemblées ne sont pas dans le dépôt : récupérer les
# fichiers « latin » de l'API Google Fonts (Bricolage Grotesque, IBM Plex Sans,
# IBM Plex Mono, variable), ou les sortir de l'historique git :
#   git show <commit>:docs/assets/fonts/bricolage-a79fdb52-latin.woff2 > src/…
set -euo pipefail
cd "$(dirname "$0")/.."
SRC="${1:-$HOME/.cache/factureapp-fonts}"
OUT="assets/fonts"
CHARSET="scripts/fonts-charset.txt"

[ -d "$SRC" ] || { echo "sources introuvables : $SRC" >&2; exit 1; }
[ -f "$SRC/bricolage-a79fdb52-latin.woff2" ] || {
  echo "attendu dans $SRC : bricolage-a79fdb52-latin.woff2, plex-sans-e2291e84-latin.woff2," >&2
  echo "plex-mono-01d28544-latin.woff2, plex-mono-08949f72-latin.woff2 (voir l'en-tête)" >&2
  exit 1
}

uvx --from fonttools --with brotli python - "$SRC" "$OUT" "$CHARSET" <<'PY'
import os, subprocess, sys
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

src, out_dir, charset = sys.argv[1], sys.argv[2], sys.argv[3]
jobs = [
    ("bricolage-a79fdb52-latin.woff2", "bricolage-latin.woff2", {"opsz": 48, "wght": (400, 800)}),
    ("plex-sans-e2291e84-latin.woff2", "plex-sans-latin.woff2", {"wght": (400, 600)}),
    ("plex-mono-01d28544-latin.woff2", "plex-mono-400-latin.woff2", None),
    ("plex-mono-08949f72-latin.woff2", "plex-mono-500-latin.woff2", None),
]
for source, target, axes in jobs:
    font = TTFont(os.path.join(src, source))
    if axes:
        instancer.instantiateVariableFont(font, axes, inplace=True)
    tmp = "/tmp/factureapp-font.ttf"
    font.save(tmp)
    subprocess.run(
        ["uvx", "--from", "fonttools", "--with", "brotli", "fonttools", "subset", tmp,
         "--text-file=" + charset, "--flavor=woff2", "--layout-features=*",
         "--output-file=" + os.path.join(out_dir, target)],
        check=True, capture_output=True,
    )
    os.remove(tmp)
    print(f"{target:30} {os.path.getsize(os.path.join(out_dir, target)) // 1024:3} Ko")

# Plages réellement couvertes par les quatre fichiers : à recopier dans
# l'unicode-range de site.css si le jeu de glyphes change.
cmaps = []
for _, target, _ in jobs:
    font = TTFont(os.path.join(out_dir, target))
    covered = set()
    for table in font["cmap"].tables:
        covered |= set(table.cmap.keys())
    cmaps.append(covered)

codes = sorted(set.intersection(*cmaps))
ranges, start, prev = [], codes[0], codes[0]
for code in codes[1:]:
    if code != prev + 1:
        ranges.append((start, prev))
        start = code
    prev = code
ranges.append((start, prev))
fmt = lambda a, b: f"U+{a:04X}" if a == b else f"U+{a:04X}-{b:04X}"
print("\nunicode-range à jour (" + str(len(codes)) + " glyphes) :")
print("    " + ",\n    ".join(fmt(a, b) for a, b in ranges) + ";")
PY
