"""Генератор восьми анимированных GIF для витрины проектов.

Каждый файл — самостоятельная мини-анимация на тёмном фоне в цвете проекта.
Запуск: python tools/make_gifs.py (из корня eight-projects).
"""

from __future__ import annotations

import math
import os
from PIL import Image, ImageDraw, ImageFont

W, H = 480, 270
FRAMES = 24
BG = (11, 18, 32)

OUT_DIR = os.path.join("src", "main", "resources", "static", "gifs")

PROJECTS = [
    ("p1", (56, 189, 248), "REST API"),
    ("p2", (167, 139, 250), "JPA без N+1"),
    ("p3", (244, 114, 182), "транзакции"),
    ("p4", (251, 191, 36), "многопоточность"),
    ("p5", (52, 211, 153), "брокер"),
    ("p6", (34, 211, 238), "XML / XSD"),
    ("p7", (192, 132, 252), "подпись"),
    ("p8", (251, 113, 133), "WebSocket"),
]


def font(size: int) -> ImageFont.FreeTypeFont:
    for name in ("segoeui.ttf", "arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


TITLE_FONT = font(20)
SMALL_FONT = font(13)


def mix(color, ratio: float):
    """Осветлить цвет к белому на ratio (0..1)."""
    return tuple(int(c + (255 - c) * ratio) for c in color)


def dim(color, ratio: float):
    return tuple(int(c * (1 - ratio)) for c in color)


def frame_base(accent, index: int, title: str) -> Image.Image:
    img = Image.new("RGB", (W, H), BG)
    draw = ImageDraw.Draw(img)
    glow = dim(accent, 0.75)
    for radius in range(160, 0, -20):
        alpha = int(6 + 20 * math.sin((math.pi * ((index % FRAMES) / FRAMES)) + radius / 160))
        color = tuple(min(255, c + max(0, alpha) // 3) for c in glow)
        draw.ellipse(
            [W - radius - 90, -radius + 40, W + radius - 90, radius + 40],
            outline=color,
        )
    draw.rectangle([0, 0, W, H], outline=dim(accent, 0.55))
    draw.text((20, 16), title, font=TITLE_FONT, fill=mix(accent, 0.45))
    draw.line([(20, 46), (W - 20, 46)], fill=dim(accent, 0.6), width=1)
    return img


def dot(draw, x, y, r, color):
    draw.ellipse([x - r, y - r, x + r, y + r], fill=color)


def draw_p1(img, draw, accent, t):
    draw.text((20, 56), "POST /api/v1/work-items", font=SMALL_FONT, fill=dim(accent, 0.1))
    path_y = 120
    draw.line([(50, path_y), (W - 50, path_y)], fill=dim(accent, 0.5), width=2)
    for step in range(4):
        x = 50 + ((t / FRAMES + step / 4) % 1.0) * (W - 100)
        dot(draw, x, path_y, 6, mix(accent, 0.3 if step % 2 else 0.0))
    draw.rectangle([60, 150, 200, 240], outline=dim(accent, 0.4))
    draw.rectangle([270, 150, 420, 240], outline=dim(accent, 0.4))
    for i in range(4):
        active = (t // 3) % 4 == i
        color = mix(accent, 0.35) if active else dim(accent, 0.35)
        draw.line([(75, 168 + i * 18), (185 - (i * 12), 168 + i * 18)], fill=color, width=4)
        draw.line([(285, 168 + i * 18), (405 - (i * 8), 168 + i * 18)], fill=color, width=4)


def draw_p2(img, draw, accent, t):
    queries = max(2, 12 - int(t * 1.6))
    draw.text((20, 58), f"SQL-запросов на страницу: {queries}", font=SMALL_FONT, fill=mix(accent, 0.25))
    for row in range(4):
        y = 90 + row * 38
        shift = int(10 * math.sin(t / 3 + row))
        draw.rectangle([40 + shift, y, 40 + shift + 90, y + 26], outline=dim(accent, 0.3))
        draw.rectangle([170, y, 260, y + 26], outline=dim(accent, 0.15))
        draw.line([(130 + shift, y + 13), (170, y + 13)], fill=mix(accent, 0.1), width=2)
        if t % 6 > 2:
            draw.line([(150, y + 13), (162, y + 21)], fill=mix(accent, 0.5), width=2)
            draw.line([(162, y + 21), (176, y + 4)], fill=mix(accent, 0.5), width=2)


def draw_p3(img, draw, accent, t):
    draw.text((20, 58), "@Transactional: REQUIRED / REQUIRES_NEW", font=SMALL_FONT, fill=mix(accent, 0.2))
    progress = (t % FRAMES) / FRAMES
    bars = [(240, "commit", (52, 211, 153)), (330, "rollback", (248, 113, 113))]
    for y, label, color in bars:
        draw.rectangle([40, y, 440, y + 34], outline=dim(accent, 0.45))
        width = int(400 * (0.35 + 0.65 * abs(math.sin(math.pi * progress))))
        draw.rectangle([40, y, 40 + width, y + 34], fill=dim(color, 0.55))
        draw.text((52, y + 9), label, font=SMALL_FONT, fill=mix(color, 0.5))


def draw_p4(img, draw, accent, t):
    draw.text((20, 58), "ExecutorService · orTimeout · pool", font=SMALL_FONT, fill=mix(accent, 0.2))
    lanes = 4
    for lane in range(lanes):
        y = 100 + lane * 38
        draw.line([(40, y), (W - 40, y)], fill=dim(accent, 0.55), width=1)
    for lane in range(lanes):
        y = 100 + lane * 38
        x = 40 + ((t / FRAMES + lane / lanes) % 1.0) * (W - 80)
        timed_out = lane == 3 and t % FRAMES > 12
        color = (248, 113, 113) if timed_out else mix(accent, 0.25)
        dot(draw, x, y, 7, color)
        if timed_out:
            draw.line([(x - 10, y - 10), (x + 10, y + 10)], fill=color, width=2)
            draw.line([(x - 10, y + 10), (x + 10, y - 10)], fill=color, width=2)


def draw_p5(img, draw, accent, t):
    draw.text((20, 58), "producer → topic → partitions → consumer", font=SMALL_FONT, fill=mix(accent, 0.2))
    for part in range(3):
        y = 100 + part * 40
        draw.rectangle([150, y, 330, y + 24], outline=dim(accent, 0.45))
        for slot in range(5):
            x = 160 + slot * 34
            moving = int((t * 12 + part * 7) % 34)
            filled = slot % 2 == 0
            if filled:
                draw.rectangle([x + moving // 3, y + 5, x + moving // 3 + 14, y + 19], fill=mix(accent, 0.15))
    dead = t % FRAMES > 16
    draw.text((360, 130), "DLQ", font=SMALL_FONT, fill=(248, 113, 113) if dead else dim(accent, 0.4))
    if dead:
        dot(draw, 400, 138, 8, (248, 113, 113))


def draw_p6(img, draw, accent, t):
    draw.text((20, 58), "JAXB · SchemaFactory · XXE off", font=SMALL_FONT, fill=mix(accent, 0.2))
    nodes = [(90, 110), (180, 90), (180, 140), (280, 90), (280, 140), (380, 115)]
    for i, (x, y) in enumerate(nodes):
        if t >= i * 2:
            draw.rectangle([x - 30, y - 12, x + 30, y + 12], outline=mix(accent, 0.1))
    links = [(0, 1), (0, 2), (1, 3), (2, 4), (3, 5), (4, 5)]
    for a, b in links:
        if t >= max(a, b) * 2:
            draw.line([nodes[a], nodes[b]], fill=dim(accent, 0.35), width=2)
    if t % FRAMES > 18:
        draw.line([(330, 190), (350, 210)], fill=mix(accent, 0.4), width=3)
        draw.line([(350, 210), (390, 170)], fill=mix(accent, 0.4), width=3)
        draw.text((300, 220), "valid", font=SMALL_FONT, fill=mix(accent, 0.4))


def draw_p7(img, draw, accent, t):
    draw.text((20, 58), "X.509 · KeyStore · ГОСТ-подпись", font=SMALL_FONT, fill=mix(accent, 0.2))
    for i in range(3):
        x = 70 + i * 120
        y = 120 + int(6 * math.sin(t / 3 + i))
        draw.rectangle([x, y, x + 90, y + 60], outline=dim(accent, 0.35))
        draw.text((x + 10, y + 8), f"cert{i + 1}", font=SMALL_FONT, fill=dim(accent, 0.1))
        if i < 2:
            draw.line([(x + 90, y + 30), (x + 120, y + 30)], fill=mix(accent, 0.1), width=2)
    seal_y = 210 + int(4 * math.sin(t / 2))
    draw.ellipse([210, seal_y, 270, seal_y + 46], outline=mix(accent, 0.25), width=2)
    if t % FRAMES > 10:
        draw.line([(228, seal_y + 24), (245, seal_y + 36)], fill=mix(accent, 0.4), width=3)
        draw.line([(245, seal_y + 36), (262, seal_y + 12)], fill=mix(accent, 0.4), width=3)


def draw_p8(img, draw, accent, t):
    draw.text((20, 58), "WS /ws/state · машина состояний", font=SMALL_FONT, fill=mix(accent, 0.2))
    states = [("NEW", 80), ("IN_PROGRESS", 200), ("DONE", 360)]
    for name, x in states:
        draw.rectangle([x - 45, 110, x + 45, 150], outline=dim(accent, 0.4))
        draw.text((x - 34, 122), name, font=SMALL_FONT, fill=dim(accent, 0.1))
    active = (t // 8) % 3
    _, x = states[active]
    draw.rectangle([x - 45, 110, x + 45, 150], outline=mix(accent, 0.4), width=2)
    pulse = 6 + int(4 * math.sin(t / 2))
    dot(draw, x + 45, 130, pulse, mix(accent, 0.3))
    for frame in range(5):
        y = 190 + frame * 12
        offset = int(8 * math.sin(t / 3 + frame))
        draw.line([(60 + offset, y), (390 - offset, y)], fill=dim(accent, 0.5), width=2)


RENDERERS = [draw_p1, draw_p2, draw_p3, draw_p4, draw_p5, draw_p6, draw_p7, draw_p8]


def build(name: str, accent, title: str, renderer) -> str:
    frames = []
    for index in range(FRAMES):
        img = frame_base(accent, index, title)
        renderer(img, ImageDraw.Draw(img), accent, index)
        frames.append(img)
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, f"{name}.gif")
    frames[0].save(
        path,
        save_all=True,
        append_images=frames[1:],
        duration=70,
        loop=0,
        optimize=True,
    )
    return path


def main() -> None:
    for (name, accent, title), renderer in zip(PROJECTS, RENDERERS):
        path = build(name, accent, title, renderer)
        print(f"{path}  {os.path.getsize(path) // 1024} KB")


if __name__ == "__main__":
    main()
