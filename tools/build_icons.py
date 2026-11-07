"""РЎР±РѕСЂРєР° СЃРїСЂР°Р№С‚Р° РёРєРѕРЅРѕРє РёР· РѕС„РёС†РёР°Р»СЊРЅРѕРіРѕ РЅР°Р±РѕСЂР° Microsoft Fluent System Icons (MIT).

РЎРєР°С‡РёРІР°РµС‚ 24-РїРёРєСЃРµР»СЊРЅС‹Рµ regular-РёРєРѕРЅРєРё Рё РїРµСЂРµРїРёСЃС‹РІР°РµС‚ assets/icons.js,
СЃРѕС…СЂР°РЅСЏСЏ РїСЂРµР¶РЅРёРµ РёРґРµРЅС‚РёС„РёРєР°С‚РѕСЂС‹ (i-grid, i-terminal, ...).
"""

from __future__ import annotations

import io
import os
import re
import urllib.error
import urllib.request

BASE = "https://raw.githubusercontent.com/microsoft/fluentui-system-icons/main/assets"
TARGET = os.path.join("src", "main", "resources", "static", "assets", "icons.js")

MAP = {
    "grid": ["Grid"],
    "terminal": ["Code"],
    "window": ["Window"],
    "api": ["Server"],
    "folder": ["Folder"],
    "gamepad": ["Games"],
    "sand": ["Filter"],
    "arrow-right": ["Next"],
    "arrow-left": ["Previous"],
    "external": ["Open"],
    "mail": ["Mail"],
    "database": ["Database"],
    "transfer": ["Send"],
    "threads": ["Branch"],
    "queue": ["Stack"],
    "code": ["Braces"],
    "shield": ["Shield"],
    "plug": ["Globe"],
    "refresh": ["Sparkle"],
    "plus": ["Add"],
    "search": ["Search"],
    "check": ["Checkmark"],
    "chevron-right": ["Next"],
    "copy": ["Copy"],
    "clock": ["Clock"],
    "list": ["List"],
    "play": ["Play"],
    "save": ["Save"],
    "settings": ["Settings"],
    "close": ["Dismiss"],
    "minimize": ["Subtract"],
    "maximize": ["Square"],
}
PATH_RE = re.compile(r'<path[^>]*\sd="([^"]+)"')
SHAPE_RE = re.compile(r"<(rect|circle|ellipse|polygon)\b")


def fetch(name: str) -> str | None:
    url = f"{BASE}/{name}/SVG/ic_fluent_{snake(name)}_24_regular.svg"
    request = urllib.request.Request(url, headers={"User-Agent": "MultiTool/1.0"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.read().decode("utf-8")
    except urllib.error.HTTPError:
        return None


def snake(name: str) -> str:
    step = re.sub(r"(?<!^)(?=[A-Z])", "_", name)
    return step.lower()


def geometry(svg: str) -> str:
    if SHAPE_RE.search(svg):
        raise SystemExit("РёРєРѕРЅРєР° СЃРѕРґРµСЂР¶РёС‚ РЅРµ С‚РѕР»СЊРєРѕ path: " + svg[:120])
    parts = []
    for data in PATH_RE.findall(svg):
        parts.append("<path d='" + data + "'/>")
    if not parts:
        raise SystemExit("РІ РёРєРѕРЅРєРµ РЅРµС‚ path: " + svg[:120])
    return "".join(parts)


def build_icons() -> dict[str, str]:
    icons = {}
    for key, candidates in MAP.items():
        for name in candidates:
            svg = fetch(name)
            if svg is not None:
                icons[key] = geometry(svg)
                print("ok   " + key + " <- " + name)
                break
        else:
            raise SystemExit("РЅРµ РЅР°Р№РґРµРЅР° РёРєРѕРЅРєР° РґР»СЏ " + key)
    return icons


def write(icons: dict[str, str]) -> None:
    lines = [
        "/* РРєРѕРЅРєРё: Microsoft Fluent System Icons, MIT License (github.com/microsoft/fluentui-system-icons). */",
        "(function () {",
        '    "use strict";',
        "",
        "    var icons = {",
    ]
    items = list(icons.items())
    for index, (key, markup) in enumerate(items):
        tail = "," if index < len(items) - 1 else ""
        lines.append('        "' + key + '": "' + markup + '"' + tail)
    lines += [
        "    };",
        "",
        "    function build() {",
        '        if (document.getElementById("mt-icon-sprite")) {',
        "            return;",
        "        }",
        '        var markup = [\'<svg id="mt-icon-sprite" aria-hidden="true" focusable="false" '
        'style="position:absolute;width:0;height:0;overflow:hidden">\'];',
        "        Object.keys(icons).forEach(function (name) {",
        '            markup.push(\'<symbol id="i-\' + name + \'" viewBox="0 0 24 24">\' + icons[name] + "</symbol>");',
        "        });",
        '        markup.push("</svg>");',
        '        var holder = document.createElement("div");',
        '        holder.innerHTML = markup.join("");',
        "        document.body.insertBefore(holder.firstChild, document.body.firstChild);",
        "    }",
        "",
        '    if (document.readyState === "loading") {',
        '        document.addEventListener("DOMContentLoaded", build);',
        "    } else {",
        "        build();",
        "    }",
        "})();",
        "",
    ]
    with io.open(TARGET, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines))


write(build_icons())
print("written " + TARGET.replace(os.sep, "/"))

