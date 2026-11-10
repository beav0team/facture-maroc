#!/usr/bin/env python3
"""Contrôles de base sur le site publié (docs/).

Lancé par `docs/scripts/site-check.sh`. Sort en erreur au premier problème :
c'est ce qui empêche une page de partir sans canonical, sans description, ou
avec une image sans dimensions.
"""
from __future__ import annotations

import json
import pathlib
import re
import sys
from urllib.parse import urlparse

DOCS = pathlib.Path(__file__).resolve().parent.parent
NOINDEX = {"test.html", "404.html"}          # exemptés de canonical/description
FONT_BUDGET = 120 * 1024                     # octets, polices au premier chargement

errors: list[str] = []


def err(page: str, message: str) -> None:
    errors.append(f"{page}: {message}")


def pages() -> list[pathlib.Path]:
    return sorted(p for p in DOCS.glob("*.html"))


def check_jsonld(page: pathlib.Path, text: str) -> None:
    for block in re.findall(r'<script type="application/ld\+json">(.*?)</script>',
                            text, re.S):
        try:
            json.loads(block)
        except json.JSONDecodeError as exc:
            err(page.name, f"JSON-LD invalide ({exc})")


def check_local_links(page: pathlib.Path, text: str) -> None:
    for attr, value in re.findall(r'(href|src)="([^"]+)"', text):
        if value.startswith(("http://", "https://", "mailto:", "#", "data:")):
            continue
        target = value.split("#", 1)[0].split("?", 1)[0]
        if target and not (DOCS / target).exists():
            err(page.name, f"{attr} cassé : {value}")


def main() -> int:
    titles: dict[str, str] = {}
    sitemap = (DOCS / "sitemap.xml").read_text(encoding="utf-8")

    for page in pages():
        text = page.read_text(encoding="utf-8")

        title = re.search(r"<title>(.*?)</title>", text, re.S)
        if not title:
            err(page.name, "pas de <title>")
        else:
            clean = title.group(1).strip()
            if clean in titles:
                err(page.name, f"titre identique à {titles[clean]}")
            titles[clean] = page.name

        h1 = len(re.findall(r"<h1[\s>]", text))
        if h1 != 1:
            err(page.name, f"{h1} <h1> (attendu 1)")

        if page.name not in NOINDEX:
            if not re.search(r'<meta name="description" content="[^"]+"', text):
                err(page.name, "pas de meta description")
            if not re.search(r'<link rel="canonical" href="https://factureapp\.ma/', text):
                err(page.name, "pas de canonical")
            loc = "https://factureapp.ma/" + (
                "" if page.name == "index.html" else page.name)
            if loc not in sitemap:
                err(page.name, "absent du sitemap")

        for img in re.findall(r"<img[^>]*>", text):
            for attr in ("width=", "height=", "alt="):
                if attr not in img:
                    err(page.name, f"<img> sans {attr.rstrip('=')} : {img[:70]}")

        check_jsonld(page, text)
        check_local_links(page, text)

    fonts = DOCS / "assets" / "fonts"
    total = sum(f.stat().st_size for f in fonts.glob("*.woff2"))
    if total > FONT_BUDGET:
        errors.append(f"assets/fonts : {total // 1024} Ko > {FONT_BUDGET // 1024} Ko")

    for stale in ("a79fdb52", "e2291e84", "01d28544", "08949f72", "0d1f0b8d",
                  "-520.webp", "latin-ext"):
        for path in DOCS.rglob("*.html"):
            if stale in path.read_text(encoding="utf-8"):
                errors.append(f"{path.name} référence encore {stale}")

    if errors:
        print("\n".join(f"  ✗ {e}" for e in errors), file=sys.stderr)
        return 1

    print(f"  ✓ {len(pages())} pages, {total // 1024} Ko de polices, tous les liens locaux résolus")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
