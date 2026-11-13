#!/usr/bin/env python3
"""Régénère le sitemap de la vitrine et estampille la feuille de style.

    python3 docs/build.py            # met à jour sitemap.xml et les ?v=
    python3 docs/build.py --check    # sort en erreur si l'un des deux est périmé

Le site ne publie plus de numéro de version : `sitemap.xml` est tenu à jour ici
(lastmod pris sur le dernier commit qui a touché la page), et le `?v=` de
`assets/css/site.css` est recalculé sur son contenu.

Le `?v=` n'est pas décoratif : `docs/_headers` sert `/assets/*` en
`max-age=31536000, immutable`, donc sans empreinte une correction de CSS
n'atteindrait jamais un visiteur déjà venu. Les polices, les images du site et
le bundle de l'app (haché par Vite) ne sont pas touchés.
"""
from __future__ import annotations

import datetime as dt
import hashlib
import pathlib
import re
import subprocess
import sys

DOCS = pathlib.Path(__file__).resolve().parent
REPO = DOCS.parent
CSS = DOCS / "assets" / "css" / "site.css"
LIEN_CSS = re.compile(r'href="((?:/)?assets/css/site\.css)(?:\?v=[0-9a-f]+)?"')


# Pages indexables, dans l'ordre du sitemap. `test.html` est en noindex et
# `404.html` ne doit jamais être listé : les deux sont volontairement absents.
# Les URL publiées n'ont pas d'extension : Cloudflare Pages redirige
# `/x.html` vers `/x` en 308. Sitemap et canonicals désignent la version finale,
# sinon chaque page indexée passe par une redirection.
PAGES = [
    ("index.html", "https://factureapp.ma/", "1.0", "weekly"),
    ("mentions-obligatoires-facture-maroc.html",
     "https://factureapp.ma/mentions-obligatoires-facture-maroc", "0.9", "monthly"),
    ("facture-modele-marocain-gratuit.html",
     "https://factureapp.ma/facture-modele-marocain-gratuit", "0.9", "monthly"),
    ("facture-auto-entrepreneur-maroc.html",
     "https://factureapp.ma/facture-auto-entrepreneur-maroc", "0.8", "monthly"),
    ("logiciel-facturation-gratuit-maroc.html",
     "https://factureapp.ma/logiciel-facturation-gratuit-maroc", "0.8", "monthly"),
    ("facturation-electronique-maroc.html",
     "https://factureapp.ma/facturation-electronique-maroc", "0.7", "monthly"),
    ("factureapp-ou-hisab.html",
     "https://factureapp.ma/factureapp-ou-hisab", "0.7", "monthly"),
    ("avis.html", "https://factureapp.ma/avis", "0.7", "monthly"),
    ("a-propos.html", "https://factureapp.ma/a-propos", "0.6", "monthly"),
    ("cgv.html", "https://factureapp.ma/cgv", "0.4", "yearly"),
    ("contact.html", "https://factureapp.ma/contact", "0.5", "yearly"),
    ("confidentialite.html", "https://factureapp.ma/confidentialite", "0.3", "yearly"),
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


def empreinte() -> str:
    """Empreinte courte du CSS, la seule chose qui change son URL publique."""
    return hashlib.sha256(CSS.read_bytes()).hexdigest()[:10]


def pages_perimees(empreinte_css: str) -> list[pathlib.Path]:
    """Pages dont le `?v=` ne correspond plus au contenu du CSS."""
    marque = f"?v={empreinte_css}"
    perimees: list[pathlib.Path] = []
    for page in sorted(DOCS.glob("*.html")):
        avant = page.read_text(encoding="utf-8")
        if LIEN_CSS.sub(lambda m: f'href="{m.group(1)}{marque}"', avant) != avant:
            perimees.append(page)
    return perimees


def estampiller(pages: list[pathlib.Path], empreinte_css: str) -> None:
    marque = f"?v={empreinte_css}"
    for page in pages:
        page.write_text(
            LIEN_CSS.sub(lambda m: f'href="{m.group(1)}{marque}"', page.read_text(encoding="utf-8")),
            encoding="utf-8",
        )


def main() -> int:
    check = "--check" in sys.argv
    stale: list[str] = []

    empreinte_css = empreinte()
    perimees = pages_perimees(empreinte_css)
    if perimees:
        if check:
            stale.append(
                "assets/css/site.css : ?v= périmé dans " + ", ".join(p.name for p in perimees)
            )
        else:
            estampiller(perimees, empreinte_css)
            print(f"site.css?v={empreinte_css} écrit dans {len(perimees)} pages")

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
