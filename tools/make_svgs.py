"""Генератор анимированных SVG для витрины: восемь сценок и обложка.

Каждая сценка — самостоятельный SVG 480×270 с SMIL-анимацией: ничего не
подгружается извне, вся графика рисуется примитивами. Запуск: python tools/make_svgs.py.
"""

from __future__ import annotations

import os

W, H = 480, 270
BG = "#0b1220"
GRID = "rgba(120, 160, 210, 0.10)"
TEXT = "#e6eefc"
MUTED = "#93a4c0"
MONO = "'Cascadia Code', 'JetBrains Mono', Consolas, monospace"
SANS = "Inter, 'Segoe UI', sans-serif"

OUT_DIR = os.path.join("src", "main", "resources", "static", "svg")

SCENES = [
    ("p1", "#38bdf8", "REST API", "POST /api/v1/work-items"),
    ("p2", "#a78bfa", "JPA без N+1", "fetch join"),
    ("p3", "#f472b6", "транзакции", "REQUIRES_NEW"),
    ("p4", "#fbbf24", "многопоточность", "orTimeout"),
    ("p5", "#34d399", "брокер", "topic → partitions"),
    ("p6", "#22d3ee", "XML / XSD", "SchemaFactory"),
    ("p7", "#c084fc", "подпись", "Signature"),
    ("p8", "#fb7185", "WebSocket", "/ws/state"),
]


def head(key: str, accent: str, title: str, subtitle: str) -> str:
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" role="img" aria-label="{title}">
  <title>{title}</title>
  <defs>
    <pattern id="grid-{key}" width="24" height="24" patternUnits="userSpaceOnUse">
      <path d="M24 0H0V24" fill="none" stroke="{GRID}" stroke-width="1"/>
    </pattern>
    <radialGradient id="glow-{key}" cx="50%" cy="50%" r="50%">
      <stop offset="0%" stop-color="{accent}" stop-opacity="0.55"/>
      <stop offset="100%" stop-color="{accent}" stop-opacity="0"/>
    </radialGradient>
    <linearGradient id="line-{key}" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0%" stop-color="{accent}" stop-opacity="0.15"/>
      <stop offset="50%" stop-color="{accent}" stop-opacity="0.9"/>
      <stop offset="100%" stop-color="{accent}" stop-opacity="0.15"/>
    </linearGradient>
    <filter id="blur-{key}" x="-40%" y="-40%" width="180%" height="180%">
      <feGaussianBlur stdDeviation="5"/>
    </filter>
  </defs>
  <rect width="{W}" height="{H}" fill="{BG}"/>
  <rect width="{W}" height="{H}" fill="url(#grid-{key})"/>
  <circle cx="392" cy="64" r="86" fill="url(#glow-{key})">
    <animate attributeName="r" values="78;92;78" dur="6s" repeatCount="indefinite"/>
  </circle>
  <rect x="0.5" y="0.5" width="{W - 1}" height="{H - 1}" fill="none" stroke="{accent}" stroke-opacity="0.35"/>
  <text x="22" y="34" font-family="{SANS}" font-size="16" font-weight="700" fill="{TEXT}">{title}</text>
  <text x="22" y="54" font-family="{MONO}" font-size="11" fill="{MUTED}">{subtitle}</text>
  <line x1="22" y1="64" x2="{W - 22}" y2="64" stroke="{accent}" stroke-opacity="0.3"/>
"""


def tail() -> str:
    return "</svg>\n"


def box(x: int, y: int, w: int, h: int, accent: str, opacity: float = 0.45) -> str:
    return (
        f'  <rect x="{x}" y="{y}" width="{w}" height="{h}" rx="8" fill="rgba(18, 27, 43, 0.85)" '
        f'stroke="{accent}" stroke-opacity="{opacity}"/>\n'
    )


def label(x: int, y: int, text: str, color: str = MUTED, size: int = 10) -> str:
    return f'  <text x="{x}" y="{y}" font-family="{MONO}" font-size="{size}" fill="{color}">{text}</text>\n'


def dot(cx: int, cy: int, r: int, color: str, dur: str, values: str, opacity: str = "1") -> str:
    return (
        f'  <circle cx="{cx}" cy="{cy}" r="{r}" fill="{color}" opacity="{opacity}">\n'
        f'    <animate attributeName="r" values="{values}" dur="{dur}" repeatCount="indefinite"/>\n'
        f"  </circle>\n"
    )


def scene_p1(accent: str) -> str:
    body = box(36, 112, 96, 62, accent) + box(348, 112, 96, 62, accent)
    body += label(50, 136, "клиент") + label(362, 136, "сервер")
    body += f'  <line x1="140" y1="143" x2="340" y2="143" stroke="url(#line-p1)" stroke-width="2"/>\n'
    for index, delay in enumerate(("0s", "1s", "2s")):
        body += (
            f'  <circle r="6" fill="{accent}">\n'
            f'    <animateMotion dur="3s" begin="{delay}" repeatCount="indefinite" '
            f'path="M140,143 L340,143"/>\n'
            f'  </circle>\n'
        )
        body += (
            f'  <circle r="6" fill="{accent}" opacity="0.5">\n'
            f'    <animateMotion dur="3s" begin="{delay}" repeatCount="indefinite" '
            f'path="M340,166 L140,166"/>\n'
            f'  </circle>\n'
        )
    body += box(196, 166, 128, 30, accent, 0.3)
    body += label(206, 186, "201 Created", "#a7f3d0", 12)
    body += (
        f'  <rect x="196" y="166" width="128" height="30" rx="8" fill="none" stroke="{accent}">\n'
        f'    <animate attributeName="stroke-opacity" values="0.2;0.9;0.2" dur="2s" repeatCount="indefinite"/>\n'
        f"  </rect>\n"
    )
    for row in range(4):
        body += label(60, 196 + row * 14, "{ id, title, status }", MUTED, 9)
    body += label(252, 100, "Idempotency-Key", accent, 9)
    return body


def scene_p2(accent: str) -> str:
    body = ""
    for row in range(4):
        y = 92 + row * 34
        body += box(40, y, 84, 24, accent, 0.35)
        body += label(52, y + 16, f"row {row + 1}", MUTED, 9)
        body += f'  <line x1="124" y1="{y + 12}" x2="176" y2="{y + 12}" stroke="url(#line-p2)" stroke-width="2">\n'
        body += '    <animate attributeName="stroke-opacity" values="0.2;0.9;0.2" ' \
                f'dur="1.6s" begin="{row * 0.15}s" repeatCount="indefinite"/>\n  </line>\n'
        body += box(176, y, 112, 24, accent, 0.25)
        body += label(186, y + 16, "assignee", MUTED, 9)
        body += box(304, y, 120, 24, accent, 0.2)
        body += label(314, y + 16, "notes", MUTED, 9)
    body += f'  <rect x="40" y="232" width="384" height="1" fill="{accent}" fill-opacity="0.3"/>\n'
    body += label(40, 252, "запросов:", MUTED, 10)
    body += (
        f'  <text x="104" y="254" font-family="{MONO}" font-size="14" fill="{accent}">\n'
        f'    <animate attributeName="opacity" values="1;0.35;1" dur="2.4s" repeatCount="indefinite"/>\n'
        f"    1 + N → 2\n"
        f"  </text>\n"
    )
    body += label(336, 252, "fetch join", "#a7f3d0", 11)
    return body


def scene_p3(accent: str) -> str:
    body = box(40, 92, 400, 44, accent, 0.3)
    body += label(50, 108, "transfer: REQUIRED", MUTED, 10)
    body += (
        f'  <rect x="42" y="112" height="22" rx="4" fill="{accent}" fill-opacity="0.55">\n'
        f'    <animate attributeName="width" values="0;394;394;0" keyTimes="0;0.55;0.7;1" '
        f'dur="5s" repeatCount="indefinite"/>\n'
        f"  </rect>\n"
    )
    body += box(40, 154, 400, 44, accent, 0.3)
    body += label(50, 170, "audit: REQUIRES_NEW", MUTED, 10)
    body += (
        f'  <rect x="42" y="174" height="22" rx="4" fill="#34d399" fill-opacity="0.6">\n'
        f'    <animate attributeName="width" values="0;120;120" keyTimes="0;0.35;1" '
        f'dur="5s" repeatCount="indefinite"/>\n'
        f"  </rect>\n"
    )
    body += (
        f'  <text x="176" y="192" font-family="{MONO}" font-size="11" fill="#a7f3d0">\n'
        f'    <animate attributeName="opacity" values="0;0;1;1" keyTimes="0;0.4;0.5;1" '
        f'dur="5s" repeatCount="indefinite"/>\n'
        f"    аудит пережил откат\n"
        f"  </text>\n"
    )
    body += box(40, 212, 400, 32, "#f87171", 0.35)
    body += (
        f'  <text x="50" y="233" font-family="{MONO}" font-size="11" fill="#fca5a5">\n'
        f'    <animate attributeName="opacity" values="0;0;1;0" keyTimes="0;0.55;0.7;1" '
        f'dur="5s" repeatCount="indefinite"/>\n'
        f"    rollback: деньги вернулись\n"
        f"  </text>\n"
    )
    return body


def scene_p4(accent: str) -> str:
    body = ""
    for lane in range(4):
        y = 96 + lane * 38
        body += f'  <line x1="40" y1="{y}" x2="440" y2="{y}" stroke="{accent}" stroke-opacity="0.22"/>\n'
        body += (
            f'  <circle r="7" fill="{accent}" opacity="{0.95 - lane * 0.12:.2f}">\n'
            f'    <animate attributeName="cx" values="40;440" dur="{2.6 + lane * 0.35:.2f}s" '
            f'repeatCount="indefinite"/>\n'
            f"  </circle>\n"
        )
        body += label(46, y - 8, f"eight-worker-{lane + 1}", MUTED, 8)
    body += f'  <g opacity="0.9">\n'
    body += (
        f'    <circle cx="404" cy="212" r="12" fill="none" stroke="#f87171" stroke-width="2">\n'
        f'      <animate attributeName="opacity" values="0;0.2;1;0.2" dur="3s" repeatCount="indefinite"/>\n'
        f"    </circle>\n"
    )
    body += (
        f'    <path d="M397 205 L411 219 M411 205 L397 219" stroke="#f87171" stroke-width="2" '
        f'stroke-linecap="round">\n'
        f'      <animate attributeName="opacity" values="0;0.2;1;0.2" dur="3s" repeatCount="indefinite"/>\n'
        f"    </path>\n  </g>\n"
    )
    body += box(40, 222, 250, 28, accent, 0.3)
    body += label(52, 241, "orTimeout(1200 ms)", MUTED, 10)
    return body


def scene_p5(accent: str) -> str:
    body = box(36, 96, 92, 108, accent, 0.35)
    body += label(48, 116, "producer", MUTED, 9)
    for index in range(3):
        body += (
            f'  <rect x="52" y="{126 + index * 24}" width="16" height="14" rx="3" fill="{accent}" fill-opacity="0.75">\n'
            f'    <animate attributeName="x" values="52;160;160" keyTimes="0;0.45;1" '
            f'dur="{2.4 + index * 0.4:.1f}s" begin="{index * 0.3}s" repeatCount="indefinite"/>\n'
            f'    <animate attributeName="opacity" values="1;1;0" keyTimes="0;0.45;0.55" '
            f'dur="{2.4 + index * 0.4:.1f}s" begin="{index * 0.3}s" repeatCount="indefinite"/>\n'
            f"  </rect>\n"
        )
    for part in range(3):
        y = 96 + part * 38
        body += box(160, y, 168, 30, accent, 0.35)
        body += label(170, y + 20, f"partition {part}", MUTED, 9)
        body += (
            f'  <circle cx="316" cy="{y + 15}" r="4" fill="{accent}">\n'
            f'    <animate attributeName="opacity" values="0.2;1;0.2" dur="{1.6 + part * 0.5:.1f}s" '
            f'repeatCount="indefinite"/>\n'
            f"  </circle>\n"
        )
    body += box(348, 96, 96, 68, accent, 0.3)
    body += label(360, 116, "consumer", MUTED, 9)
    body += label(360, 134, "group A", MUTED, 9)
    body += (
        f'  <text x="360" y="154" font-family="{MONO}" font-size="10" fill="{accent}">offset</text>\n'
    )
    body += box(160, 214, 168, 34, "#f87171", 0.45)
    body += (
        f'  <text x="172" y="236" font-family="{MONO}" font-size="11" fill="#fca5a5">\n'
        f'    <animate attributeName="opacity" values="0.25;1;0.25" dur="2.8s" repeatCount="indefinite"/>\n'
        f"    DLQ\n"
        f"  </text>\n"
    )
    body += (
        f'  <path d="M360 170 C360 200 300 200 300 212" fill="none" stroke="#f87171" '
        f'stroke-opacity="0.6" stroke-dasharray="4 4" stroke-width="2">\n'
        f'    <animate attributeName="stroke-dashoffset" values="0;-16" dur="1s" repeatCount="indefinite"/>\n'
        f"  </path>\n"
    )
    return body


def scene_p6(accent: str) -> str:
    body = ""
    nodes = [(70, 120), (170, 96), (170, 148), (282, 96), (282, 148), (390, 122)]
    links = [(0, 1), (0, 2), (1, 3), (2, 4), (3, 5), (4, 5)]
    for index, (x, y) in enumerate(nodes):
        body += (
            f'  <rect x="{x - 34}" y="{y - 14}" width="68" height="28" rx="6" '
            f'fill="rgba(18, 27, 43, 0.9)" stroke="{accent}" stroke-opacity="0.5">\n'
            f'    <animate attributeName="stroke-opacity" values="0.2;0.85;0.2" dur="3s" '
            f'begin="{index * 0.25}s" repeatCount="indefinite"/>\n'
            f"  </rect>\n"
        )
    for start, end in links:
        x1, y1 = nodes[start]
        x2, y2 = nodes[end]
        body += (
            f'  <line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{accent}" stroke-opacity="0.28" '
            f'stroke-width="2"/>\n'
        )
    body += label(38, 122, "invoice", MUTED, 9) + label(148, 100, "party", MUTED, 9)
    body += label(148, 152, "items", MUTED, 9) + label(258, 100, "name/inn", MUTED, 9)
    body += label(258, 152, "price", MUTED, 9) + label(366, 126, "total", MUTED, 9)
    body += (
        f'  <path d="M212 216 L232 236 L268 196" fill="none" stroke="#34d399" stroke-width="4" '
        f'stroke-linecap="round" stroke-linejoin="round" stroke-dasharray="90 90">\n'
        f'    <animate attributeName="stroke-dashoffset" values="90;0;0;90" keyTimes="0;0.45;0.8;1" '
        f'dur="4s" repeatCount="indefinite"/>\n'
        f"  </path>\n"
    )
    body += label(286, 222, "schema valid", "#a7f3d0", 11)
    body += (
        f'  <g>\n'
        f'    <rect x="352" y="196" width="84" height="26" rx="6" fill="none" stroke="#f87171" '
        f'stroke-opacity="0.7"/>\n'
        f'    <text x="362" y="214" font-family="{MONO}" font-size="10" fill="#fca5a5">DTD off</text>\n'
        f'    <line x1="352" y1="200" x2="436" y2="218" stroke="#f87171" stroke-width="2">\n'
        f'      <animate attributeName="opacity" values="0.1;1;0.1" dur="2.2s" repeatCount="indefinite"/>\n'
        f"    </line>\n  </g>\n"
    )
    return body


def scene_p7(accent: str) -> str:
    body = ""
    for index in range(3):
        x = 52 + index * 116
        y = 108 + index * 4
        body += box(x, y, 96, 62, accent, 0.4)
        body += label(x + 12, y + 20, f"cert {index + 1}", MUTED, 9)
        body += label(x + 12, y + 40, "X.509", MUTED, 9)
        body += (
            f'  <rect x="{x}" y="{y}" width="96" height="62" rx="8" fill="none" stroke="{accent}">\n'
            f'    <animate attributeName="stroke-opacity" values="0.15;0.7;0.15" dur="3.6s" '
            f'begin="{index * 0.4}s" repeatCount="indefinite"/>\n'
            f"  </rect>\n"
        )
        if index < 2:
            body += (
                f'  <line x1="{x + 96}" y1="{y + 31}" x2="{x + 116}" y2="{y + 41}" stroke="{accent}" '
                f'stroke-opacity="0.5" stroke-width="2"/>\n'
            )
    body += (
        f'  <circle cx="240" cy="224" r="26" fill="none" stroke="{accent}" stroke-width="2" '
        f'stroke-dasharray="6 6">\n'
        f'    <animateTransform attributeName="transform" type="rotate" values="0 240 224;360 240 224" '
        f'dur="9s" repeatCount="indefinite"/>\n'
        f"  </circle>\n"
    )
    body += (
        f'  <path d="M228 224 L236 232 L254 212" fill="none" stroke="#a7f3d0" stroke-width="3" '
        f'stroke-linecap="round" stroke-linejoin="round">\n'
        f'    <animate attributeName="opacity" values="0;1;1" keyTimes="0;0.3;1" dur="3s" '
        f'repeatCount="indefinite"/>\n'
        f"  </path>\n"
    )
    body += label(52, 236, "подпись проверена", MUTED, 10)
    return body


def scene_p8(accent: str) -> str:
    body = ""
    states = [("NEW", 96), ("IN_PROGRESS", 240), ("DONE", 396)]
    for name, x in states:
        body += box(x - 56, 104, 112, 40, accent, 0.4)
        body += label(x - 44, 129, name, TEXT, 10)
    body += f'  <line x1="152" y1="124" x2="184" y2="124" stroke="url(#line-p8)" stroke-width="2"/>\n'
    body += f'  <line x1="296" y1="124" x2="340" y2="124" stroke="url(#line-p8)" stroke-width="2"/>\n'
    for index, (start, end) in enumerate(((96, 184), (240, 340))):
        body += (
            f'  <circle r="5" fill="{accent}">\n'
            f'    <animateMotion dur="3s" begin="{index * 1.5}s" repeatCount="indefinite" '
            f'path="M{start},124 L{end},124"/>\n'
            f"  </circle>\n"
        )
    body += (
        f'  <rect x="240" y="104" width="112" height="40" rx="8" fill="none" stroke="{accent}" '
        f'stroke-width="2">\n'
        f'    <animate attributeName="stroke-opacity" values="0.2;1;0.2" dur="3s" repeatCount="indefinite"/>\n'
        f"  </rect>\n"
    )
    for row in range(3):
        y = 172 + row * 24
        body += (
            f'  <rect x="72" y="{y}" width="{336 - row * 40}" height="12" rx="6" fill="{accent}" '
            f'fill-opacity="0.18"/>\n'
        )
        body += (
            f'  <rect x="72" y="{y}" width="{80 + row * 30}" height="12" rx="6" fill="{accent}" '
            f'fill-opacity="0.7">\n'
            f'    <animate attributeName="width" values="{80 + row * 30};{200 + row * 40};'
            f'{80 + row * 30}" dur="{2.4 + row * 0.4:.1f}s" repeatCount="indefinite"/>\n'
            f"  </rect>\n"
        )
    body += label(72, 252, "валидация команд и лимит размера сообщения", MUTED, 9)
    return body


def scene_hero(accent: str) -> str:
    body = ""
    body += (
        f'  <circle cx="240" cy="150" r="96" fill="none" stroke="{accent}" stroke-opacity="0.35" '
        f'stroke-dasharray="3 9">\n'
        f'    <animateTransform attributeName="transform" type="rotate" values="0 240 150;-360 240 150" '
        f'dur="26s" repeatCount="indefinite"/>\n'
        f"  </circle>\n"
    )
    body += (
        f'  <circle cx="240" cy="150" r="68" fill="none" stroke="{accent}" stroke-opacity="0.5">\n'
        f'    <animate attributeName="r" values="66;72;66" dur="7s" repeatCount="indefinite"/>\n'
        f"  </circle>\n"
    )
    for index in range(8):
        angle = index * 45
        body += (
            f'  <circle cx="240" cy="82" r="5" fill="{accent}">\n'
            f'    <animateTransform attributeName="transform" type="rotate" '
            f'values="{angle} 240 150;{angle + 360} 240 150" dur="{18 + index}s" repeatCount="indefinite"/>\n'
            f'    <animate attributeName="opacity" values="0.35;1;0.35" dur="{2 + index * 0.25:.2f}s" '
            f'repeatCount="indefinite"/>\n'
            f"  </circle>\n"
        )
    body += (
        f'  <text x="240" y="168" text-anchor="middle" font-family="{MONO}" font-size="54" '
        f'font-weight="700" fill="{TEXT}">8</text>\n'
    )
    body += (
        f'  <text x="240" y="196" text-anchor="middle" font-family="{MONO}" font-size="11" '
        f'fill="{MUTED}">Java 17 · Spring Boot</text>\n'
    )
    for index, (x, y) in enumerate(((78, 96), (402, 96), (78, 206), (402, 206))):
        body += (
            f'  <rect x="{x - 34}" y="{y - 10}" width="68" height="20" rx="10" fill="none" '
            f'stroke="{accent}" stroke-opacity="0.4"/>\n'
        )
        body += label(x - 26, y + 4, ["REST", "JPA", "KAFKA", "WS"][index], MUTED, 8)
    return body


RENDERERS = [scene_p1, scene_p2, scene_p3, scene_p4, scene_p5, scene_p6, scene_p7, scene_p8]


def build_scenes() -> None:
    os.makedirs(OUT_DIR, exist_ok=True)
    for (key, accent, title, subtitle), renderer in zip(SCENES, RENDERERS):
        svg = head(key, accent, title, subtitle) + renderer(accent) + tail()
        path = os.path.join(OUT_DIR, f"{key}.svg")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(svg)
        print(f"{path}  {len(svg) // 1024} KB")


def build_hero() -> None:
    os.makedirs(OUT_DIR, exist_ok=True)
    svg = head("hero", "#38bdf8", "Восемь проектов", "живая витрина") + scene_hero("#38bdf8") + tail()
    path = os.path.join(OUT_DIR, "hero.svg")
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(svg)
    print(f"{path}  {len(svg) // 1024} KB")


def main() -> None:
    build_scenes()
    build_hero()


if __name__ == "__main__":
    main()
