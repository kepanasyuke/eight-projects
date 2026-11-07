"""Сборка статической площадки для GitHub Pages.

Копирует «Логику» и общие ассеты из src/main/resources/static в docs/, переписывает абсолютные
пути на относительные. Лендинг docs/index.html и витрина docs/showcase.html пишутся вручную
и не трогаются.
Запуск: python tools/build_pages.py
"""

from __future__ import annotations

import os
import shutil

STATIC = os.path.join("src", "main", "resources", "static")
DOCS = "docs"
ASSETS = ["styles.css", "icons.js", "logic.css", "logic.js", "app.css", "app.js", "demo-data.js"]

PATH_REPLACEMENTS = [
    ('<a href="/"><svg class="icon"><use href="#i-terminal"></use></svg>Консоль</a>',
     '<a href="console.html"><svg class="icon"><use href="#i-terminal"></use></svg>Консоль</a>'),
    ('href="/assets/', 'href="assets/'),
    ('src="/assets/', 'src="assets/'),
    ('href="/showcase.html#projects"', 'href="showcase.html"'),
    ('href="/showcase.html"', 'href="showcase.html"'),
    ('href="/logic.html"', 'href="logic.html"'),
    ('href="/index.html#projects"', 'href="index.html#projects"'),
    ('href="/"', 'href="index.html"'),
    ('href="/projects/', 'href="showcase.html"'),
    ('<a class="brand" href="index.html">', '<a class="brand" href="index.html">'),
]

CONSOLE_REPLACEMENTS = [
    ('<a href="index.html" aria-current="page">', '<a href="console.html" aria-current="page">'),
    ('<span class="window__title">восемь проектов · localhost:8080</span>',
     '<span class="window__title">восемь проектов · записанные ответы API</span>'),
    ('<script src="assets/app.js" defer></script>',
     '<script src="assets/demo-data.js" defer></script>\n<script src="assets/app.js" defer></script>'),
    ('<span class="brand__name">Восемь проектов <span>/ консоль</span></span>',
     '<span class="brand__name">Восемь проектов <span>/ консоль без backend</span></span>'),
]


def read(path: str) -> str:
    with open(path, "r", encoding="utf-8") as handle:
        return handle.read()


def write(path: str, text: str) -> None:
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)


def relativize(markup: str) -> str:
    result = markup
    for source, target in PATH_REPLACEMENTS:
        result = result.replace(source, target)
    return result


def build_logic() -> None:
    markup = relativize(read(os.path.join(STATIC, "logic.html")))
    write(os.path.join(DOCS, "logic.html"), markup)


def build_console() -> None:
    markup = relativize(read(os.path.join(STATIC, "index.html")))
    for source, target in CONSOLE_REPLACEMENTS:
        markup = markup.replace(source, target, 1)
    write(os.path.join(DOCS, "console.html"), markup)


def build_assets() -> None:
    target = os.path.join(DOCS, "assets")
    os.makedirs(target, exist_ok=True)
    for name in ASSETS:
        shutil.copyfile(os.path.join(STATIC, "assets", name), os.path.join(target, name))


def main() -> None:
    os.makedirs(DOCS, exist_ok=True)
    build_assets()
    build_logic()
    build_console()
    write(os.path.join(DOCS, ".nojekyll"), "")
    print("docs/ ready: logic.html, console.html, assets/, .nojekyll")


if __name__ == "__main__":
    main()
