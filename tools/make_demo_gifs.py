"""Собирает GIF-демо для README из кадров самодельных SVG.

Кадр снимается Chrome в headless-режиме: SVG встраивается в пустую страницу,
анимация ставится на паузу и перематывается на нужную секунду через
SVGSVGElement.setCurrentTime(). Кадры склеиваются Pillow в зацикленный GIF.

Запуск: python tools/make_demo_gifs.py (из корня eight-projects).
Требуется Google Chrome или Microsoft Edge.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import tempfile

from PIL import Image

SRC_DIR = os.path.join("src", "main", "resources", "static", "svg")
OUT_DIR = os.path.join("docs", "demo")

WIDTH, HEIGHT = 480, 270
SCALE = 2
FRAMES = 18
WINDOW_SECONDS = 3.6
FRAME_MS = 200
PALETTE = 64

CHROME_CANDIDATES = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
]

DEMOS = [
    ("hero", "hero.svg"),
    ("p1", "p1.svg"),
    ("p2", "p2.svg"),
    ("p3", "p3.svg"),
    ("p4", "p4.svg"),
    ("p5", "p5.svg"),
    ("p6", "p6.svg"),
    ("p7", "p7.svg"),
    ("p8", "p8.svg"),
]

PAGE = """<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<style>
html, body {{ margin: 0; padding: 0; background: #0b1220; }}
body {{ width: {w}px; height: {h}px; overflow: hidden; }}
svg {{ display: block; width: {w}px; height: {h}px; }}
</style>
</head>
<body>
{svg}
<script>
const root = document.querySelector("svg");
root.pauseAnimations();
root.setCurrentTime({t});
document.title = "frame";
</script>
</body>
</html>
"""


def find_browser() -> str:
    for path in CHROME_CANDIDATES:
        if os.path.exists(path):
            return path
    found = shutil.which("chrome") or shutil.which("msedge")
    if found:
        return found
    raise SystemExit("Не найден Chrome или Edge для рендера SVG-кадров")


def read_svg(name: str) -> str:
    path = os.path.join(SRC_DIR, name)
    with open(path, "r", encoding="utf-8") as handle:
        return handle.read()


def shoot(browser: str, work: str, svg: str, seconds: float, output: str) -> None:
    page_path = os.path.join(work, "frame.html")
    page = PAGE.format(
        w=WIDTH * SCALE,
        h=HEIGHT * SCALE,
        svg=svg,
        t=f"{seconds:.3f}",
    )
    with open(page_path, "w", encoding="utf-8") as handle:
        handle.write(page)
    args = [
        browser,
        "--headless=new",
        "--disable-gpu",
        "--hide-scrollbars",
        "--allow-file-access-from-files",
        f"--window-size={WIDTH * SCALE},{HEIGHT * SCALE}",
        "--force-device-scale-factor=1",
        "--virtual-time-budget=1500",
        f"--screenshot={output}",
        "file:///" + page_path.replace("\\", "/"),
    ]
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def build(browser: str, key: str, name: str, work: str) -> str:
    svg = read_svg(name)
    frames = []
    for index in range(FRAMES):
        seconds = WINDOW_SECONDS * index / FRAMES
        raw = os.path.join(work, f"{key}-{index:02d}.png")
        shoot(browser, work, svg, seconds, raw)
        image = Image.open(raw).convert("RGB")
        small = image.resize((WIDTH, HEIGHT), Image.LANCZOS)
        frames.append(small.quantize(colors=PALETTE, method=Image.MEDIANCUT, dither=Image.NONE))
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, f"{key}.gif")
    frames[0].save(
        path,
        save_all=True,
        append_images=frames[1:],
        duration=FRAME_MS,
        loop=0,
        optimize=True,
    )
    return path


def main() -> None:
    browser = find_browser()
    work = tempfile.mkdtemp(prefix="giga-svg-")
    try:
        for key, name in DEMOS:
            path = build(browser, key, name, work)
            print(f"{path}  {os.path.getsize(path) // 1024} KB")
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    main()
