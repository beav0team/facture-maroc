#!/usr/bin/env python3
"""Régénère ce qui ne doit pas être écrit à la main dans docs/.

    python3 docs/build.py            # met à jour sitemap.xml et les versions
    python3 docs/build.py --check    # sort en erreur si quelque chose est périmé

Ce que le script tient à jour :
  * `Version x.y.z` dans les pages HTML, lue depuis app/build.gradle.kts ;
  * `softwareVersion` dans le JSON-LD ;
  * `sitemap.xml` : la liste des pages indexables et leur `lastmod`, pris sur
    le dernier commit qui a touché le fichier.

Il ne touche à rien d'autre : ni au CSS, ni aux polices, ni au contenu.
"""
from __future__ import annotations

import datetime as dt
import pathlib
import re
import subprocess
import sys

DOCS = pathlib.Path(__file__).resolve().parent
REPO = DOCS.parent
GRADLE = REPO / "app" / "build.gradle.kts"

# Pages indexables, dans l'ordre du sitemap. `test.html` est en noindex et
# `404.html` ne doit jamais être listé : les deux sont volontairement absents.
PAGES = [
    ("index.html", "https://factureapp.ma/", "1.0", "weekly"),
    ("mentions-obligatoires-facture-maroc.html",
     "https://factureapp.ma/mentions-obligatoires-facture-maroc.html", "0.9", "monthly"),
    ("a-propos.html", "https://factureapp.ma/a-propos.html", "0.6", "monthly"),
    ("contact.html", "https://factureapp.ma/contact.html", "0.5", "yearly"),
    ("privacy.html", "https://factureapp.ma/privacy.html", "0.3", "yearly"),
]


def version() -> str:
    text = GRADLE.read_text(encoding="utf-8")
    match = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    if not match:
        sys.exit(f"versionName introuvable dans {GRADLE}")
    return match.group(1)


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
    ver = version()
    stale: list[str] = []

    for path in sorted(DOCS.glob("*.html")):
        text = path.read_text(encoding="utf-8")
        new = re.sub(r"Version \d+\.\d+\.\d+(?:-[0-9A-Za-z.]+)?",
                     f"Version {ver}", text)
        new = re.sub(r'"softwareVersion":\s*"[^"]+"',
                     f'"softwareVersion": "{ver}"', new)
        if new != text:
            if check:
                stale.append(f"{path.name} : version périmée (attendu {ver})")
            else:
                path.write_text(new, encoding="utf-8")
                print(f"version  {path.name} -> {ver}")

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
