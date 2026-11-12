#!/usr/bin/env python3
"""Régénère le sitemap de la vitrine, écrit à la main nulle part ailleurs.

    python3 docs/build.py            # met à jour sitemap.xml
    python3 docs/build.py --check    # sort en erreur si le sitemap est périmé

Le site ne publie plus de numéro de version : la seule chose tenue à jour ici
est `sitemap.xml`, la liste des pages indexables et leur `lastmod`, pris sur le
dernier commit qui a touché le fichier. Le script ne dépend donc plus de
l'application Android.

Il ne touche à rien d'autre : ni au CSS, ni aux polices, ni au contenu.
"""
from __future__ import annotations

import datetime as dt
import pathlib
import subprocess
import sys

DOCS = pathlib.Path(__file__).resolve().parent
REPO = DOCS.parent

# Pages indexables, dans l'ordre du sitemap. `test.html` est en noindex et
# `404.html` ne doit jamais être listé : les deux sont volontairement absents.
PAGES = [
    ("index.html", "https://factureapp.ma/", "1.0", "weekly"),
    ("mentions-obligatoires-facture-maroc.html",
     "https://factureapp.ma/mentions-obligatoires-facture-maroc.html", "0.9", "monthly"),
    ("facture-modele-marocain-gratuit.html",
     "https://factureapp.ma/facture-modele-marocain-gratuit.html", "0.9", "monthly"),
    ("avis.html", "https://factureapp.ma/avis.html", "0.7", "monthly"),
    ("a-propos.html", "https://factureapp.ma/a-propos.html", "0.6", "monthly"),
    ("cgv.html", "https://factureapp.ma/cgv.html", "0.4", "yearly"),
    ("contact.html", "https://factureapp.ma/contact.html", "0.5", "yearly"),
    ("confidentialite.html", "https://factureapp.ma/confidentialite.html", "0.3", "yearly"),
]


def lastmod(name: str) -> str:
    """Date ISO du dernier commit qui a touché le fichier, sinon aujourd'hui."""
    try:
        out = subprocess.run(
            ["git", "log", "-1", "--format=%cI", "--", f"docs/{name}"],
            cwd=REPO, capture_output=True, text=True, check=True,
        ).stdout.strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        out = ""
    if not out:
        return dt.date.today().isoformat()
    return out[:10]


def sitemap() -> str:
    lines = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">',
    ]
    for name, loc, priority, freq in PAGES:
        lines += [
            "  <url>",
            f"    <loc>{loc}</loc>",
            f"    <lastmod>{lastmod(name)}</lastmod>",
            f"    <changefreq>{freq}</changefreq>",
            f"    <priority>{priority}</priority>",
            "  </url>",
        ]
    lines.append("</urlset>")
    return "\n".join(lines) + "\n"


def main() -> int:
    check = "--check" in sys.argv
    stale: list[str] = []

    out = sitemap()
    current = (DOCS / "sitemap.xml").read_text(encoding="utf-8") if (DOCS / "sitemap.xml").exists() else ""
    if out != current:
        if check:
            stale.append("sitemap.xml : à régénérer (lastmod ou liste des pages)")
        else:
            (DOCS / "sitemap.xml").write_text(out, encoding="utf-8")
            print("sitemap.xml régénéré")

    if check and stale:
        print("\n".join(stale), file=sys.stderr)
        return 1
    print("docs/ à jour" if not check else "docs/ à jour")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
