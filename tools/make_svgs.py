"""Генератор анимированных SVG для витрины: восемь сценок и обложка.

Каждая сценка — самостоятельный SVG 480×270 с SMIL-анимацией: ничего не
подгружается извне, вся графика рисуется примитивами. Оформление — Windows 11
Fluent: светлая поверхность Mica, белые карточки с тонкой рамкой, Segoe UI,
акцент #0067c0. Запуск: python tools/make_svgs.py.
"""

from __future__ import annotations

import os

W, H = 480, 270
BG_TOP = "#f7f8fa"
BG_BOTTOM = "#e2e7ee"
CARD = "#ffffff"
CARD_ALT = "#eaeef4"
BORDER = "#d3d9e2"
LINE = "#dfe4eb"
TEXT = "#15181c"
MUTED = "#565c66"
OK = "#107c10"
BAD = "#c42b1c"
MONO = "'Cascadia Code', 'Cascadia Mono', Consolas, monospace"
SANS = "'Segoe UI Variable Text', 'Segoe UI', system-ui, sans-serif"

OUT_DIR = os.path.join("src", "main", "resources", "static", "svg")

SCENES = [
    ("p1", "#0067c0", "REST API", "POST /api/v1/work-items"),
    ("p2", "#8764b8", "JPA без N+1", "fetch join"),
    ("p3", "#b146c2", "транзакции", "REQUIRES_NEW"),
    ("p4", "#ca5010", "многопоточность", "orTimeout"),
    ("p5", "#107c10", "брокер", "topic → partitions"),
    ("p6", "#038387", "XML / XSD", "SchemaFactory"),
    ("p7", "#4f52b2", "подпись", "Signature"),
    ("p8", "#c42b1c", "WebSocket", "/ws/state"),
]


def head(key: str, accent: str, title: str, subtitle: str) -> str:
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" role="img" aria-label="{title}">
  <title>{title}</title>
  <defs>
    <linearGradient id="bg-{key}" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color="{BG_TOP}"/>
      <stop offset="100%" stop-color="{BG_BOTTOM}"/>
    </linearGradient>
    <linearGradient id="line-{key}" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0%" stop-color="{accent}" stop-opacity="0.2"/>
      <stop offset="50%" stop-color="{accent}" stop-opacity="0.9"/>
      <stop offset="100%" stop-color="{accent}" stop-opacity="0.2"/>
    </linearGradient>
    <filter id="sh-{key}" x="-14%" y="-18%" width="128%" height="160%">
      <feDropShadow dx="0" dy="2" stdDeviation="2.6" flood-color="#1a2129" flood-opacity="0.30"/>
    </filter>
  </defs>
  <rect width="{W}" height="{H}" fill="url(#bg-{key})"/>
  <circle cx="404" cy="52" r="92" fill="{accent}" fill-opacity="0.09"/>
  <text x="22" y="34" font-family="{SANS}" font-size="15" font-weight="600" fill="{TEXT}">{title}</text>
  <text x="22" y="53" font-family="{MONO}" font-size="11" fill="{MUTED}">{subtitle}</text>
  <rect x="22" y="63" width="{W - 44}" height="1" fill="{LINE}"/>
  <rect x="22" y="63" width="34" height="2" rx="1" fill="{accent}" fill-opacity="0.85"/>
"""


def tail() -> str:
    return "</svg>\n"


def card(x: int, y: int, w: int, h: int, accent: str | None = None, opacity: float = 0.75,
         fill: str = CARD, rx: int = 8, shadow: bool = True) -> str:
    stroke = accent if accent else BORDER
    shadow_attr = ' filter="url(#sh-{})"'.format(_KEY) if shadow else ""
    return (
        f'  <rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" '
        f'stroke="{stroke}" stroke-opacity="{opacity if accent else 1}"{shadow_attr}/>\n'
    )


def label(x: int, y: int, text: str, color: str = MUTED, size: int = 10,
          family: str | None = None, weight: str = "400") -> str:
    return (
        f'  <text x="{x}" y="{y}" font-family="{family or MONO}" font-size="{size}" '
        f'font-weight="{weight}" fill="{color}">{text}</text>\n'
    )


def dot(cx: int, cy: int, r: int, color: str, dur: str, values: str, opacity: str = "1") -> str:
    return (
        f'  <circle cx="{cx}" cy="{cy}" r="{r}" fill="{color}" opacity="{opacity}">\n'
        f'    <animate attributeName="r" values="{values}" dur="{dur}" repeatCount="indefinite"/>\n'
        f"  </circle>\n"
    )


_KEY = "p1"


def scene_p1(accent: str) -> str:
    body = card(36, 112, 96, 62, accent, 0.55)
    body += card(348, 112, 96, 62, accent, 0.55)
    body += label(50, 132, "клиент", TEXT, 11, SANS, "600")
    body += label(50, 150, "fetch", MUTED, 9)
    body += label(362, 132, "сервер", TEXT, 11, SANS, "600")
    body += label(362, 150, "controller", MUTED, 9)
    body += f'  <line x1="140" y1="143" x2="340" y2="143" stroke="url(#line-p1)" stroke-width="2"/>\n'
    for delay in ("0s", "1s", "2s"):
        body += (
            f'  <circle r="5" fill="{accent}">\n'
            f'    <animateMotion dur="3s" begin="{delay}" repeatCount="indefinite" '
            f'path="M140,143 L340,143"/>\n'
            f'  </circle>\n'
        )
        body += (
            f'  <circle r="5" fill="{accent}" opacity="0.45">\n'
            f'    <animateMotion dur="3s" begin="{delay}" repeatCount="indefinite" '
            f'path="M340,166 L140,166"/>\n'
            f'  </circle>\n'
        )
    body += card(196, 166, 128, 30, OK, 0.5, fill="#f3faf3")
    body += label(208, 185, "201 Created", OK, 11)
    body += (
        f'  <rect x="196" y="166" width="128" height="30" rx="8" fill="none" stroke="{accent}">\n'
        f'    <animate attributeName="stroke-opacity" values="0.15;0.75;0.15" dur="2s" repeatCount="indefinite"/>\n'
        f"  </rect>\n"
    )
    for row in range(3):
        body += label(52, 202 + row * 14, "{ id, title, status }", MUTED, 9)
    body += label(316, 100, "Idempotency-Key", accent, 9)
    return body


def scene_p2(accent: str) -> str:
    body = ""
    for row in range(4):
        y = 88 + row * 32
        body += card(40, y, 84, 24, accent, 0.35, shadow=False)
        body += label(52, y + 16, f"row {row + 1}", TEXT, 9)
        body += f'  <line x1="124" y1="{y + 12}" x2="176" y2="{y + 12}" stroke="url(#line-p2)" stroke-width="2">\n'
        body += '    <animate attributeName="stroke-opacity" values="0.2;0.9;0.2" ' \
                f'dur="1.6s" begin="{row * 0.15}s" repeatCount="indefinite"/>\n  </line>\n'
        body += card(176, y, 112, 24, None, shadow=False, fill=CARD_ALT, rx=6)
        body += label(186, y + 16, "assignee", MUTED, 9)
        body += card(304, y, 120, 24, None, shadow=False, fill=CARD_ALT, rx=6)
        body += label(314, y + 16, "notes", MUTED, 9)
    body += f'  <rect x="40" y="228" width="384" height="1" fill="{LINE}"/>\n'
    body += label(40, 250, "запросов:", MUTED, 10)
    body += (
        f'  <text x="106" y="252" font-family="{MONO}" font-size="14" font-weight="600" fill="{accent}">\n'
        f'    <animate attributeName="opacity" values="1;0.4;1" dur="2.4s" repeatCount="indefinite"/>\n'
        f"    1 + N → 2\n"
        f"  </text>\n"
    )
    body += label(330, 250, "fetch join", OK, 10)
    return body


def scene_p3(accent: str) -> str:
    body = card(40, 88, 400, 44, accent, 0.35)
    body += label(52, 106, "transfer: REQUIRED", TEXT, 10)
    body += f'  <rect x="52" y="112" width="376" height="12" rx="6" fill="{CARD_ALT}"/>\n'
    body += (
        f'  <rect x="52" y="112" height="12" rx="6" fill="{accent}" fill-opacity="0.75">\n'
        f'    <animate attributeName="width" values="0;376;376;0" keyTimes="0;0.55;0.7;1" '
        f'dur="5s" repeatCount="indefinite"/>\n'
        f"  </rect>\n"
    )
    body += card(40, 146, 400, 44, None)
    body += label(52, 164, "audit: REQUIRES_NEW", TEXT, 10)
    body += f'  <rect x="52" y="170" width="376" height="12" rx="6" fill="{CARD_ALT}"/>\n'
    body += (
        f'  <rect x="52" y="170" height="12" rx="6" fill="{OK}" fill-opacity="0.75">\n'
        f'    <animate attributeName="width" values="0;120;120" keyTimes="0;0.35;1" '
        f'dur="5s" repeatCount="indefinite"/>\n'
        f"  </rect>\n"
    )
    body += (
        f'  <text x="186" y="188" font-family="{MONO}" font-size="10" fill="{OK}">\n'
        f'    <animate attributeName="opacity" values="0;0;1;1" keyTimes="0;0.4;0.5;1" '
        f'dur="5s" repeatCount="indefinite"/>\n'
        f"    аудит пережил откат\n"
        f"  </text>\n"
    )
    body += f'  <rect x="40" y="204" width="400" height="34" rx="8" fill="#fdf3f2" stroke="{BAD}" stroke-opacity="0.35"/>\n'
    body += (
        f'  <text x="52" y="226" font-family="{MONO}" font-size="10" fill="{BAD}">\n'
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
        body += f'  <line x1="40" y1="{y}" x2="440" y2="{y}" stroke="{BORDER}" stroke-width="2"/>\n'
        body += (
            f'  <circle cy="{y}" r="7" fill="{accent}" fill-opacity="{0.9 - lane * 0.13:.2f}">\n'
            f'    <animate attributeName="cx" values="40;440" dur="{2.6 + lane * 0.35:.2f}s" '
            f'repeatCount="indefinite"/>\n'
            f"  </circle>\n"
        )
        body += label(46, y - 10, f"eight-worker-{lane + 1}", MUTED, 8)
    body += f'  <g>\n'
    body += (
        f'    <circle cx="404" cy="212" r="12" fill="#fdf3f2" stroke="{BAD}" stroke-width="2">\n'
        f'      <animate attributeName="opacity" values="0.25;0.4;1;0.4" dur="3s" repeatCount="indefinite"/>\n'
        f"    </circle>\n"
    )
    body += (
        f'    <path d="M399 207 L409 217 M409 207 L399 217" stroke="{BAD}" stroke-width="2" '
        f'stroke-linecap="round">\n'
        f'      <animate attributeName="opacity" values="0.25;0.4;1;0.4" dur="3s" repeatCount="indefinite"/>\n'
        f"    </path>\n  </g>\n"
    )
    body += card(40, 212, 236, 30, accent, 0.4)
    body += label(54, 231, "orTimeout(1200 ms)", TEXT, 10)
    return body


def scene_p5(accent: str) -> str:
    body = card(36, 92, 92, 108, accent, 0.45)
    body += label(50, 112, "producer", TEXT, 10, SANS, "600")
    for index in range(3):
        body += (
            f'  <rect x="52" y="{122 + index * 24}" width="18" height="14" rx="3" fill="{accent}" fill-opacity="0.7">\n'
            f'    <animate attributeName="x" values="52;160;160" keyTimes="0;0.45;1" '
            f'dur="{2.4 + index * 0.4:.1f}s" begin="{index * 0.3}s" repeatCount="indefinite"/>\n'
            f'    <animate attributeName="opacity" values="1;1;0" keyTimes="0;0.45;0.55" '
            f'dur="{2.4 + index * 0.4:.1f}s" begin="{index * 0.3}s" repeatCount="indefinite"/>\n'
            f"  </rect>\n"
        )
    for part in range(3):
        y = 92 + part * 38
        body += card(160, y, 168, 30, None, shadow=False, fill=CARD_ALT, rx=6)
        body += label(172, y + 19, f"partition {part}", TEXT, 9)
        body += f'  <rect x="172" y="{y + 22}" width="144" height="3" rx="1.5" fill="{accent}" fill-opacity="0.42"/>\n'
        body += (
            f'  <circle cx="316" cy="{y + 15}" r="4" fill="{accent}">\n'
            f'    <animate attributeName="opacity" values="0.2;1;0.2" dur="{1.6 + part * 0.5:.1f}s" '
            f'repeatCount="indefinite"/>\n'
            f"  </circle>\n"
        )
    body += card(348, 92, 96, 72, accent, 0.4)
    body += label(362, 112, "consumer", TEXT, 10, SANS, "600")
    body += label(362, 130, "group A", MUTED, 9)
    body += label(362, 152, "offset", accent, 9)
    body += f'  <rect x="160" y="212" width="168" height="32" rx="8" fill="#fdf3f2" stroke="{BAD}" stroke-opacity="0.4"/>\n'
    body += (
        f'  <text x="174" y="233" font-family="{MONO}" font-size="10" fill="{BAD}">\n'
        f'    <animate attributeName="opacity" values="0.35;1;0.35" dur="2.8s" repeatCount="indefinite"/>\n'
        f"    DLQ\n"
        f"  </text>\n"
    )
    body += (
        f'  <path d="M360 168 C360 196 300 196 300 210" fill="none" stroke="{BAD}" '
        f'stroke-opacity="0.5" stroke-dasharray="4 4" stroke-width="2">\n'
        f'    <animate attributeName="stroke-dashoffset" values="0;-16" dur="1s" repeatCount="indefinite"/>\n'
        f"  </path>\n"
    )
    return body


def scene_p6(accent: str) -> str:
    body = ""
    nodes = [(70, 120), (170, 96), (170, 148), (282, 96), (282, 148), (390, 122)]
    links = [(0, 1), (0, 2), (1, 3), (2, 4), (3, 5), (4, 5)]
    for start, end in links:
        x1, y1 = nodes[start]
        x2, y2 = nodes[end]
        body += (
            f'  <line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{accent}" stroke-opacity="0.55" '
            f'stroke-width="2"/>\n'
        )
    for index, (x, y) in enumerate(nodes):
        body += (
            f'  <rect x="{x - 34}" y="{y - 14}" width="68" height="28" rx="6" fill="{CARD}" '
            f'stroke="{accent}" stroke-opacity="0.5">\n'
            f'    <animate attributeName="stroke-opacity" values="0.15;0.85;0.15" dur="3s" '
            f'begin="{index * 0.25}s" repeatCount="indefinite"/>\n'
            f"  </rect>\n"
        )
    body += label(40, 123, "invoice", TEXT, 9) + label(148, 100, "party", TEXT, 9)
    body += label(148, 152, "items", TEXT, 9) + label(258, 100, "name/inn", TEXT, 9)
    body += label(256, 152, "price", TEXT, 9) + label(366, 126, "total", TEXT, 9)
    body += (
        f'  <path d="M206 208 L226 228 L262 188" fill="none" stroke="{OK}" stroke-width="4" '
        f'stroke-linecap="round" stroke-linejoin="round" stroke-dasharray="90 90">\n'
        f'    <animate attributeName="stroke-dashoffset" values="90;0;0;90" keyTimes="0;0.45;0.8;1" '
        f'dur="4s" repeatCount="indefinite"/>\n'
        f"  </path>\n"
    )
    body += label(272, 218, "schema valid", OK, 10)
    body += f'  <g>\n'
    body += f'    <rect x="352" y="196" width="92" height="26" rx="6" fill="#fdf3f2" stroke="{BAD}" stroke-opacity="0.4"/>\n'
    body += f'    <text x="362" y="214" font-family="{MONO}" font-size="10" fill="{BAD}">DTD off</text>\n'
    body += (
        f'    <line x1="352" y1="200" x2="444" y2="218" stroke="{BAD}" stroke-width="2">\n'
        f'      <animate attributeName="opacity" values="0.15;1;0.15" dur="2.2s" repeatCount="indefinite"/>\n'
        f"    </line>\n  </g>\n"
    )
    return body


def scene_p7(accent: str) -> str:
    body = ""
    for index in range(3):
        x = 52 + index * 116
        y = 104 + index * 4
        body += card(x, y, 96, 62, accent, 0.45)
        body += label(x + 12, y + 22, f"cert {index + 1}", TEXT, 10, SANS, "600")
        body += label(x + 12, y + 42, "X.509", MUTED, 9)
        body += (
            f'  <rect x="{x}" y="{y}" width="96" height="62" rx="8" fill="none" stroke="{accent}">\n'
            f'    <animate attributeName="stroke-opacity" values="0.1;0.7;0.1" dur="3.6s" '
            f'begin="{index * 0.4}s" repeatCount="indefinite"/>\n'
            f"  </rect>\n"
        )
        if index < 2:
            body += (
                f'  <line x1="{x + 96}" y1="{y + 31}" x2="{x + 116}" y2="{y + 41}" stroke="{accent}" '
                f'stroke-opacity="0.45" stroke-width="2"/>\n'
            )
    body += (
        f'  <circle cx="240" cy="220" r="26" fill="{CARD}" stroke="{accent}" stroke-width="2" '
        f'stroke-dasharray="6 6">\n'
        f'    <animateTransform attributeName="transform" type="rotate" values="0 240 220;360 240 220" '
        f'dur="9s" repeatCount="indefinite"/>\n'
        f"  </circle>\n"
    )
    body += (
        f'  <path d="M228 220 L236 228 L254 208" fill="none" stroke="{OK}" stroke-width="3" '
        f'stroke-linecap="round" stroke-linejoin="round">\n'
        f'    <animate attributeName="opacity" values="0;1;1" keyTimes="0;0.3;1" dur="3s" '
        f'repeatCount="indefinite"/>\n'
        f"  </path>\n"
    )
    body += label(52, 234, "подпись проверена", MUTED, 10)
    return body


def scene_p8(accent: str) -> str:
    body = ""
    states = [("NEW", 96), ("IN_PROGRESS", 240), ("DONE", 396)]
    for name, x in states:
        body += card(x - 56, 100, 112, 40, accent, 0.55)
        body += label(x - 46, 125, name, TEXT, 9)
    body += f'  <line x1="152" y1="120" x2="184" y2="120" stroke="url(#line-p8)" stroke-width="2"/>\n'
    body += f'  <line x1="296" y1="120" x2="340" y2="120" stroke="url(#line-p8)" stroke-width="2"/>\n'
    for index, (start, end) in enumerate(((156, 180), (300, 336))):
        body += (
            f'  <circle r="5" fill="{accent}">\n'
            f'    <animateMotion dur="3s" begin="{index * 1.5}s" repeatCount="indefinite" '
            f'path="M{start},120 L{end},120"/>\n'
            f'  </circle>\n'
        )
    body += (
        f'  <rect x="184" y="100" width="112" height="40" rx="8" fill="none" stroke="{accent}" '
        f'stroke-width="2">\n'
        f'    <animate attributeName="stroke-opacity" values="0.15;1;0.15" dur="3s" repeatCount="indefinite"/>\n'
        f"  </rect>\n"
    )
    for row in range(3):
        y = 168 + row * 24
        body += f'  <rect x="72" y="{y}" width="{336 - row * 40}" height="12" rx="6" fill="{CARD_ALT}"/>\n'
        body += (
            f'  <rect x="72" y="{y}" width="{80 + row * 30}" height="12" rx="6" fill="{accent}" '
            f'fill-opacity="0.6">\n'
            f'    <animate attributeName="width" values="{80 + row * 30};{200 + row * 40};'
            f'{80 + row * 30}" dur="{2.4 + row * 0.4:.1f}s" repeatCount="indefinite"/>\n'
            f"  </rect>\n"
        )
    body += label(72, 250, "валидация команд и лимит размера сообщения", MUTED, 9)
    return body


def scene_hero(accent: str) -> str:
    body = ""
    body += (
        f'  <circle cx="240" cy="152" r="94" fill="none" stroke="{accent}" stroke-opacity="0.3" '
        f'stroke-dasharray="3 9">\n'
        f'    <animateTransform attributeName="transform" type="rotate" values="0 240 152;-360 240 152" '
        f'dur="26s" repeatCount="indefinite"/>\n'
        f"  </circle>\n"
    )
    body += (
        f'  <circle cx="240" cy="152" r="66" fill="{CARD}" stroke="{accent}" stroke-opacity="0.55" '
        f'filter="url(#sh-hero)">\n'
        f'    <animate attributeName="r" values="64;70;64" dur="7s" repeatCount="indefinite"/>\n'
        f"  </circle>\n"
    )
    for index in range(8):
        angle = index * 45
        body += (
            f'  <circle cx="240" cy="86" r="5" fill="{accent}">\n'
            f'    <animateTransform attributeName="transform" type="rotate" '
            f'values="{angle} 240 152;{angle + 360} 240 152" dur="{18 + index}s" repeatCount="indefinite"/>\n'
            f'    <animate attributeName="opacity" values="0.3;1;0.3" dur="{2 + index * 0.25:.2f}s" '
            f'repeatCount="indefinite"/>\n'
            f"  </circle>\n"
        )
    body += (
        f'  <text x="240" y="170" text-anchor="middle" font-family="{MONO}" font-size="52" '
        f'font-weight="700" fill="{TEXT}">8</text>\n'
    )
    body += (
        f'  <text x="240" y="194" text-anchor="middle" font-family="{MONO}" font-size="10" '
        f'fill="{MUTED}">проектов</text>\n'
    )
    for index, (x, y) in enumerate(((78, 92), (402, 92), (78, 212), (402, 212))):
        body += (
            f'  <rect x="{x - 36}" y="{y - 11}" width="72" height="22" rx="11" fill="{CARD}" '
            f'stroke="{BORDER}"/>\n'
        )
        body += (
            f'  <text x="{x}" y="{y + 4}" text-anchor="middle" font-family="{MONO}" font-size="8" '
            f'fill="{MUTED}">{["REST", "JPA", "KAFKA", "WS"][index]}</text>\n'
        )
    return body


RENDERERS = [scene_p1, scene_p2, scene_p3, scene_p4, scene_p5, scene_p6, scene_p7, scene_p8]


def build_scenes() -> None:
    global _KEY
    os.makedirs(OUT_DIR, exist_ok=True)
    for (key, accent, title, subtitle), renderer in zip(SCENES, RENDERERS):
        _KEY = key
        svg = head(key, accent, title, subtitle) + renderer(accent) + tail()
        path = os.path.join(OUT_DIR, f"{key}.svg")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(svg)
        print(f"{path}  {len(svg) // 1024} KB")


def build_hero() -> None:
    global _KEY
    _KEY = "hero"
    os.makedirs(OUT_DIR, exist_ok=True)
    svg = head("hero", "#0067c0", "Восемь проектов", "живая витрина") + scene_hero("#0067c0") + tail()
    path = os.path.join(OUT_DIR, "hero.svg")
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(svg)
    print(f"{path}  {len(svg) // 1024} KB")


def main() -> None:
    print("scenes")
    build_scenes()
    print("hero")
    build_hero()


if __name__ == "__main__":
    main()
