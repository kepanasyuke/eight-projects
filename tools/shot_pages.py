"""Временный помощник: собирает страницы для съёмки игр с шимом requestAnimationFrame."""

from __future__ import annotations

import io
import os

STATIC = os.path.join("src", "main", "resources", "static")

SHIM = """<script>
(function () {
    var limit = performance.now() + 4200;
    window.requestAnimationFrame = function (cb) {
        if (performance.now() > limit) { return 0; }
        return setTimeout(function () { cb(performance.now()); }, 16);
    };
})();
</script>"""

GAME_DRIVER = """
""" + SHIM + """
<script src="/assets/game.js"></script>
<script>
window.addEventListener("load", function () {
    setTimeout(function () { var b = document.getElementById("overlay-primary"); if (b) b.click(); }, 200);
    setTimeout(function () {
        window.dispatchEvent(new KeyboardEvent("keydown", { code: "ArrowRight", key: "ArrowRight", bubbles: true }));
    }, 400);
    var jump = function () {
        window.dispatchEvent(new KeyboardEvent("keydown", { code: "Space", key: " ", bubbles: true }));
        setTimeout(function () {
            window.dispatchEvent(new KeyboardEvent("keyup", { code: "Space", key: " ", bubbles: true }));
        }, 120);
    };
    for (var i = 0; i < 4; i++) { setTimeout(jump, 900 + i * 800); }
});
</script>
"""

SAND_DRIVER = """
""" + SHIM + """
<script src="/assets/sand.js"></script>
<script>
window.addEventListener("load", function () {
    var canvas = document.getElementById("sand");
    var send = function (type, rx, ry) {
        var r = canvas.getBoundingClientRect();
        canvas.dispatchEvent(new PointerEvent(type, {
            bubbles: true, pointerId: 1, pointerType: "mouse", isPrimary: true,
            clientX: r.left + r.width * rx, clientY: r.top + r.height * ry
        }));
    };
    setTimeout(function () { var b = document.getElementById("sand-overlay-primary"); if (b) b.click(); }, 200);
    setTimeout(function () { send("pointerdown", 0.5, 0.13); }, 400);
    var step = 0;
    var timer = setInterval(function () {
        step += 1;
        if (step > 26) { clearInterval(timer); send("pointerup", 0.5, 0.9); return; }
        send("pointermove", 0.5 + Math.sin(step / 5) * 0.008, 0.13 + step * 0.026);
    }, 55);
});
</script>
"""

JOBS = [
    ("game", GAME_DRIVER, "/assets/game.js"),
    ("sand", SAND_DRIVER, "/assets/sand.js"),
]


def main() -> None:
    for name, driver, script in JOBS:
        source = os.path.join(STATIC, name + ".html")
        with io.open(source, encoding="utf-8") as handle:
            markup = handle.read()
        tag = '<script src="' + script + '" defer></script>'
        if tag not in markup:
            raise SystemExit("нет скрипта " + script + " в " + name + ".html")
        target = os.path.join(STATIC, "__shot-" + name + ".html")
        with io.open(target, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(markup.replace(tag, driver.strip()))
        print("wrote " + target.replace(os.sep, "/"))


if __name__ == "__main__":
    main()
