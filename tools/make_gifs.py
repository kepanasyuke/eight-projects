"""Пересобирает GIF-витрину приложения в стиле Windows 11.

Кадры рендерятся из тех же SVG, что и витрина (tools/make_svgs.py), поэтому
картинки в приложении и в README совпадают. Запуск: python tools/make_gifs.py
(из корня eight-projects, нужен Chrome или Edge).
"""

from __future__ import annotations

import os
import shutil
import tempfile

import make_demo_gifs

OUT_DIR = os.path.join("src", "main", "resources", "static", "gifs")


def main() -> None:
    browser = make_demo_gifs.find_browser()
    work = tempfile.mkdtemp(prefix="giga-gif-")
    try:
        for key, name in make_demo_gifs.DEMOS:
            path = make_demo_gifs.build(browser, key, name, work, OUT_DIR)
            print(f"{path}  {os.path.getsize(path) // 1024} KB")
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    main()
