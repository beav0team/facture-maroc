#!/usr/bin/env bash
# Régénère la police auto-hébergée de docs/assets/fonts.
#
#   docs/scripts/fonts.sh                 # utilise le cache ~/.cache/factureapp-fonts
#   docs/scripts/fonts.sh /chemin/vers/src
#
# Le site n'utilise plus qu'une famille : Roboto Flex, la police variable de
# Material Design. Deux raisons de la sous-ensembler plutôt que de servir le
# fichier de Google Fonts :
#
#   * le fichier d'origine porte 13 axes et tous les alphabets latins ; le site
#     n'a besoin que de `wght` et `opsz`, et du français ;
#   * le budget de docs/scripts/site_check.py refuse plus de 120 Ko de polices.
#
# Roboto Flex complet  1,8 Mo  ->  45 Ko (wght 400–800, opsz 14–48, 336 glyphes)
#
# Les icônes ne sont pas une police : les Material Symbols utilisés par les
# pages sont des SVG en ligne (voir la sprite en haut de chaque page), pour ne
# rien charger quand une page n'a pas d'icône.
#
# Sources attendues dans $SRC (non versionnées) :
#   RobotoFlex-var.ttf                 https://github.com/google/fonts/raw/main/ofl/robotoflex/RobotoFlex%5BGRAD,XOPQ,XTRA,YOPQ,YTAS,YTDE,YTFI,YTLC,YTUC,opsz,slnt,wdth,wght%5D.ttf
set -euo pipefail
cd "$(dirname "$0")/.."
SRC="${1:-$HOME/.cache/factureapp-fonts}"
OUT="assets/fonts"
CHARSET="scripts/fonts-charset.txt"

[ -d "$SRC" ] || { echo "sources introuvables : $SRC" >&2; exit 1; }
[ -f "$SRC/RobotoFlex-var.ttf" ] || {
  echo "attendu dans $SRC : RobotoFlex-var.ttf (voir l'en-tête de ce script)" >&2
  exit 1
}

uvx --from fonttools --with brotli python - "$SRC" "$OUT" "$CHARSET" <<'PY'
import os, sys
from fontTools.ttLib import TTFont
from fontTools.subset import Options, Subsetter
from fontTools.varLib import instancer

src, out_dir, charset_path = sys.argv[1], sys.argv[2], sys.argv[3]

# Le fichier de jeu de glyphes ne contient que l'ASCII et la ponctuation
# française ; les lettres accentuées et les espaces insécables sont ajoutées ici
# pour que le sous-ensemble reste lisible en français.
EXTRA = (
    "\u00a0\u202f\u2011"
    "ÀÂÄÇÉÈÊËÎÏÔÖÙÛÜŸŒ"
    "àâäçéèêëîïôöùûüÿœ"
    "«»°²³µ·×÷–—’‘“”…€"
)
text = "".join(dict.fromkeys(open(charset_path, encoding="utf-8").read() + EXTRA))

font = TTFont(os.path.join(src, "RobotoFlex-var.ttf"))
# On ne garde que le poids et la taille optique ; les onze autres axes sont
# figés à leur valeur par défaut, sinon le fichier porte des tables inutiles.
instancer.instantiateVariableFont(
    font,
    {
        "wght": (400, 800),
        "opsz": (14, 48),
        "GRAD": 0, "wdth": 100, "slnt": 0, "XOPQ": 96, "YOPQ": 79,
        "XTRA": 468, "YTUC": 712, "YTLC": 514, "YTAS": 750, "YTDE": -203,
        "YTFI": 738,
    },
    inplace=True,
)
# fontTools 4.5x indexe gvar pour chaque glyphe conservé : quelques glyphes
# n'ont aucune donnée de variation et font échouer la découpe. Une entrée vide
# dit exactement ce qu'ils sont — un glyphe qui ne varie pas.
if "gvar" in font:
    gvar = font["gvar"]
    for name in font.getGlyphOrder():
        if name not in gvar.variations:
            gvar.variations[name] = []

options = Options()
options.layout_features = [
    "kern", "liga", "clig", "ccmp", "locl", "calt", "case", "tnum", "pnum",
]
subsetter = Subsetter(options=options)
subsetter.populate(text=text)
subsetter.subset(font)

target = os.path.join(out_dir, "robotoflex-latin.woff2")
font.flavor = "woff2"
font.save(target)
print(f"{'robotoflex-latin.woff2':30} {os.path.getsize(target) // 1024:3} Ko "
      f"({len(text)} caractères demandés)")

# Plages réellement couvertes : à recopier dans l'unicode-range de tokens.css.
covered = set()
for table in TTFont(target)["cmap"].tables:
    covered |= set(table.cmap.keys())
codes = sorted(covered)
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
