"""Сборка статической площадки для GitHub Pages.

Копирует игру и её ассеты из src/main/resources/static в docs/, переписывает
абсолютные пути на относительные и включает офлайн-режим лидерборда, потому что
на Pages нет backend. Лендинг docs/index.html пишется вручную и не трогается.
Запуск: python tools/build_pages.py
"""

from __future__ import annotations

import os
import shutil

STATIC = os.path.join("src", "main", "resources", "static")
DOCS = "docs"
ASSETS = ["styles.css", "icons.js", "game.css", "game.js", "logic.css", "logic.js", "sand.css", "sand.js"]

OFFLINE_FLAG = '<script>window.EIGHT_OFFLINE = true;</script>\n'

PATH_REPLACEMENTS = [
    ('<a href="/"><svg class="icon"><use href="#i-terminal"></use></svg>Консоль</a>',
     '<a href="console.html"><svg class="icon"><use href="#i-terminal"></use></svg>Консоль</a>'),
    ('href="/assets/', 'href="assets/'),
    ('src="/assets/', 'src="assets/'),
    ('href="/showcase.html#projects"', 'href="showcase.html"'),
    ('href="/showcase.html"', 'href="showcase.html"'),
    ('href="/game.html"', 'href="game.html"'),
    ('href="/logic.html"', 'href="logic.html"'),
    ('href="/sand.html"', 'href="sand.html"'),
    ('href="/index.html#projects"', 'href="index.html#projects"'),
    ('href="/"', 'href="index.html"'),
    ('href="/projects/', 'href="showcase.html"'),
    ('<title>', '<title>'),
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


def build_game() -> None:
    markup = relativize(read(os.path.join(STATIC, "game.html")))
    markup = markup.replace('<script src="assets/game.js" defer></script>',
                            OFFLINE_FLAG + '<script src="assets/game.js" defer></script>')
    write(os.path.join(DOCS, "game.html"), markup)


def build_sand() -> None:
    markup = relativize(read(os.path.join(STATIC, "sand.html")))
    markup = markup.replace('<script src="assets/sand.js" defer></script>',
                            OFFLINE_FLAG + '<script src="assets/sand.js" defer></script>')
    write(os.path.join(DOCS, "sand.html"), markup)


def build_logic() -> None:
    markup = relativize(read(os.path.join(STATIC, "logic.html")))
    write(os.path.join(DOCS, "logic.html"), markup)


def build_assets() -> None:
    target = os.path.join(DOCS, "assets")
    os.makedirs(target, exist_ok=True)
    for name in ASSETS:
        shutil.copyfile(os.path.join(STATIC, "assets", name), os.path.join(target, name))


def main() -> None:
    os.makedirs(DOCS, exist_ok=True)
    build_assets()
    build_game()
    build_sand()
    build_logic()
    write(os.path.join(DOCS, ".nojekyll"), "")
    print("docs/ собран: game.html, sand.html, assets/, .nojekyll")


if __name__ == "__main__":
    main()
