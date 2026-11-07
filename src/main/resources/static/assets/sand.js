(() => {
    const canvas = document.getElementById("sand");
    const ctx = canvas.getContext("2d");

    const GW = 192;
    const GH = 104;
    const CELL = 5;
    const VIEW_W = GW * CELL;
    const VIEW_H = GH * CELL;

    const EMPTY = 0;
    const SAND = 1;
    const WATER = 2;
    const WALL = 3;
    const MEMBRANE = 4;
    const SLIT = 5;
    const SINK = 6;

    const GATE1_Y = 30;
    const GATE2_Y = 48;
    const SINK_Y = 66;
    const FLOOR_Y = 74;
    const CLOSED_Y = 78;
    const SPOUT_X0 = 40;
    const SPOUT_X1 = 150;
    const WINDOW_X0 = 60;
    const WINDOW_X1 = 120;
    const CHUTE_X0 = 100;
    const CHUTE_X1 = 120;
    const SLIT_ZONE_X1 = 97;
    const SLIT_X0 = 61;
    const SINK_X0 = 50;
    const SINK_X1 = 135;
    const SLIT_COUNT = 6;
    const SLIT_STEP = 6;
    const SLIT_HOLD = 18;
    const MATERIAL_LIMIT = 320;
    const TARGET_DELIVERED = 60;
    const LOCAL_BOARD_KEY = "eight-projects-leaderboard";
    const LOCAL_PLAYER_KEY = "eight-sand-player";

    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = VIEW_W * dpr;
    canvas.height = VIEW_H * dpr;
    ctx.scale(dpr, dpr);

    const field = document.createElement("canvas");
    field.width = GW;
    field.height = GH;
    const fieldCtx = field.getContext("2d");
    const image = fieldCtx.createImageData(GW, GH);

    const grid = new Uint8Array(GW * GH);
    const owner = new Uint8Array(GW * GH);
    const hold = new Int16Array(GW * GH);

    const COLORS = {
        [SAND]: [139, 212, 255],
        [WATER]: [74, 108, 240],
        [WALL]: [42, 74, 116],
        [MEMBRANE]: [122, 43, 57],
        [SLIT]: [107, 90, 31],
        [SINK]: [29, 122, 85]
    };

    const hudDelivered = document.getElementById("hud-delivered");
    const hudRejected = document.getElementById("hud-rejected");
    const hudQueries = document.getElementById("hud-queries");
    const hudMaterial = document.getElementById("hud-material");
    const hudTime = document.getElementById("hud-time");
    const overlay = document.getElementById("sand-overlay");
    const overlayEyebrow = document.getElementById("sand-overlay-title").previousElementSibling;
    const overlayTitle = document.getElementById("sand-overlay-title");
    const overlayText = document.getElementById("sand-overlay-text");
    const overlayPrimary = document.getElementById("sand-overlay-primary");
    const overlaySecondary = document.getElementById("sand-overlay-secondary");
    const nameForm = document.getElementById("sand-name-form");
    const playerInput = document.getElementById("sand-player");
    const tapButton = document.getElementById("sand-tap");
    const resetButton = document.getElementById("sand-reset");
    const brushInput = document.getElementById("sand-brush");
    const hint = document.getElementById("sand-hint");
    const board = document.getElementById("sand-board");
    const boardNote = document.getElementById("sand-board-note");
    const boardRefresh = document.getElementById("sand-board-refresh");
    const swatches = Array.from(document.querySelectorAll(".sand-swatch"));

    const state = {
        material: MATERIAL_LIMIT,
        tool: "wall",
        brush: 2,
        tapOpen: false,
        running: false,
        startedAt: 0,
        seconds: 0,
        delivered: 0,
        rejected: 0,
        queries: 0,
        tick: 0,
        painting: false,
        sent: false
    };

    const cellIndex = (x, y) => y * GW + x;
    const isEmpty = (kind) => kind === EMPTY;
    const isGrain = (kind) => kind === SAND || kind === WATER;

    const fillRect = (x0, y0, width, height, kind) => {
        for (let y = y0; y < y0 + height; y++) {
            for (let x = x0; x < x0 + width; x++) {
                if (x < 0 || y < 0 || x >= GW || y >= GH) {
                    continue;
                }
                const i = cellIndex(x, y);
                grid[i] = kind;
                owner[i] = 0;
                hold[i] = 0;
            }
        }
    };

    const buildMap = () => {
        grid.fill(EMPTY);
        owner.fill(0);
        hold.fill(0);
        fillRect(0, 0, GW, 3, WALL);
        fillRect(SPOUT_X0, 0, SPOUT_X1 - SPOUT_X0 + 1, 3, EMPTY);
        fillRect(0, 0, 3, FLOOR_Y, WALL);
        fillRect(GW - 3, 0, 3, FLOOR_Y, WALL);
        fillRect(0, FLOOR_Y, GW, 4, WALL);
        fillRect(3, GATE1_Y, GW - 6, 2, MEMBRANE);
        fillRect(WINDOW_X0, GATE1_Y, WINDOW_X1 - WINDOW_X0 + 1, 2, EMPTY);
        fillRect(3, GATE2_Y, SLIT_ZONE_X1 - 2, 2, WALL);
        fillRect(CHUTE_X1 + 1, GATE2_Y, GW - 3 - (CHUTE_X1 + 1), 2, WALL);
        for (let slit = 0; slit < SLIT_COUNT; slit++) {
            fillRect(SLIT_X0 + slit * SLIT_STEP, GATE2_Y, 1, 2, SLIT);
        }
        fillRect(SINK_X0, SINK_Y, SINK_X1 - SINK_X0 + 1, 3, SINK);
    };

    const swapCells = (a, b) => {
        const kind = grid[a];
        grid[a] = grid[b];
        grid[b] = kind;
        const owned = owner[a];
        owner[a] = owner[b];
        owner[b] = owned;
        const wait = hold[a];
        hold[a] = hold[b];
        hold[b] = wait;
    };

    const clearCell = (i) => {
        grid[i] = EMPTY;
        owner[i] = 0;
        hold[i] = 0;
    };

    const canTake = (i) => isEmpty(grid[i]) || grid[i] === WATER;

    const moveSand = (x, y, i) => {
        if (hold[i] > 0) {
            hold[i] -= 1;
            return;
        }
        const down = i + GW;
        const below = grid[down];
        if (below === MEMBRANE) {
            state.rejected += 1;
            clearCell(i);
            return;
        }
        if (below === SINK) {
            state.delivered += 1;
            clearCell(i);
            return;
        }
        if (below === SLIT && y + 1 === GATE2_Y) {
            state.queries += 1;
            hold[i] = SLIT_HOLD;
            return;
        }
        if (canTake(down)) {
            swapCells(i, down);
            return;
        }
        const first = Math.random() < 0.5 ? -1 : 1;
        const sides = [first, -first];
        for (let s = 0; s < sides.length; s++) {
            const target = i + sides[s] + GW;
            if (x + sides[s] > 0 && x + sides[s] < GW - 1 && canTake(target)) {
                swapCells(i, target);
                return;
            }
        }
    };

    const moveWater = (x, y, i) => {
        const down = i + GW;
        if (isEmpty(down)) {
            swapCells(i, down);
            return;
        }
        const first = (state.tick + x) % 2 === 0 ? -1 : 1;
        const order = [first, -first];
        for (let s = 0; s < order.length; s++) {
            const diagonal = i + order[s] + GW;
            if (x + order[s] > 0 && x + order[s] < GW - 1 && isEmpty(diagonal)) {
                swapCells(i, diagonal);
                return;
            }
        }
        for (let s = 0; s < order.length; s++) {
            const side = i + order[s];
            if (x + order[s] > 0 && x + order[s] < GW - 1 && isEmpty(side)) {
                swapCells(i, side);
                return;
            }
        }
    };

    const step = () => {
        for (let y = GH - 2; y >= 1; y--) {
            const forward = (y + state.tick) % 2 === 0;
            for (let n = 0; n < GW - 2; n++) {
                const x = forward ? n + 1 : GW - 2 - n;
                const i = cellIndex(x, y);
                if (grid[i] === SAND) {
                    moveSand(x, y, i);
                } else if (grid[i] === WATER) {
                    moveWater(x, y, i);
                }
            }
        }
    };

    const spawn = () => {
        if (!state.tapOpen || Math.random() > 0.42) {
            return;
        }
        const x = SPOUT_X0 + Math.floor(Math.random() * (SPOUT_X1 - SPOUT_X0 + 1));
        const i = cellIndex(x, 3);
        if (isEmpty(grid[i])) {
            grid[i] = SAND;
            owner[i] = 0;
        }
    };

    const paint = (gx, gy, hardErase) => {
        const erase = hardErase || state.tool === "erase";
        const kind = state.tool === "water" ? WATER : WALL;
        const radius = state.brush;
        for (let dy = -radius; dy <= radius; dy++) {
            for (let dx = -radius; dx <= radius; dx++) {
                const x = gx + dx;
                const y = gy + dy;
                const inside = x > 0 && y > 0 && x < GW - 1 && y < FLOOR_Y;
                if (!inside) {
                    continue;
                }
                const i = cellIndex(x, y);
                if (erase) {
                    if (owner[i] === 1) {
                        state.material = Math.min(MATERIAL_LIMIT, state.material + 1);
                        clearCell(i);
                    }
                    continue;
                }
                if (!isEmpty(grid[i]) || state.material <= 0) {
                    continue;
                }
                grid[i] = kind;
                owner[i] = 1;
                state.material -= 1;
                hold[i] = 0;
            }
        }
    };

    const pointToCell = (event) => {
        const rect = canvas.getBoundingClientRect();
        const gx = Math.floor(((event.clientX - rect.left) / rect.width) * GW);
        const gy = Math.floor(((event.clientY - rect.top) / rect.height) * GH);
        return { x: gx, y: gy };
    };

    const handlePaint = (event) => {
        const point = pointToCell(event);
        const erase = event.button === 2 || (event.buttons & 2) === 2;
        paint(point.x, point.y, erase);
        updateHud();
    };

    canvas.addEventListener("pointerdown", (event) => {
        if (canvas.setPointerCapture) {
            try {
                canvas.setPointerCapture(event.pointerId);
            } catch (error) {
                /* синтетическое событие без активного указателя */
            }
        }
        state.painting = true;
        handlePaint(event);
    });
    canvas.addEventListener("pointermove", (event) => {
        if (state.painting) {
            handlePaint(event);
        }
    });
    canvas.addEventListener("pointerup", () => {
        state.painting = false;
    });
    canvas.addEventListener("pointercancel", () => {
        state.painting = false;
    });
    canvas.addEventListener("contextmenu", (event) => {
        event.preventDefault();
    });

    const render = () => {
        const data = image.data;
        for (let y = 0; y < GH; y++) {
            for (let x = 0; x < GW; x++) {
                const i = cellIndex(x, y);
                const kind = grid[i];
                const offset = i * 4;
                if (kind === EMPTY) {
                    data[offset] = 8;
                    data[offset + 1] = 13;
                    data[offset + 2] = 24;
                    data[offset + 3] = 255;
                    continue;
                }
                const color = COLORS[kind];
                const noise = isGrain(kind) ? ((x * 37 + y * 17) % 5) * 3 : 0;
                data[offset] = Math.min(255, color[0] + noise);
                data[offset + 1] = Math.min(255, color[1] + noise);
                data[offset + 2] = Math.min(255, color[2] + noise);
                data[offset + 3] = 255;
            }
        }
        fieldCtx.putImageData(image, 0, 0);
        ctx.imageSmoothingEnabled = false;
        ctx.drawImage(field, 0, 0, VIEW_W, VIEW_H);
        drawLabels();
    };

    const drawLabels = () => {
        ctx.textBaseline = "alphabetic";
        ctx.font = "600 10px Consolas, monospace";
        ctx.fillStyle = "rgba(147, 164, 192, 0.75)";
        ctx.fillText("КРАН", SPOUT_X0 * CELL, 3 * CELL - 12);
        ctx.fillText("СТВОР 1 · КОНТРАКТ — окно принимает, мембрана отбивает 400", 14, GATE1_Y * CELL - 9);
        ctx.fillText("СТВОР 2 · JPA — слева узкие щели (по запросу на запись), справа широкий жёлоб", 14, GATE2_Y * CELL - 9);
        ctx.fillStyle = "rgba(52, 211, 153, 0.85)";
        ctx.fillText("ПРИЁМНИК " + state.delivered, SINK_X0 * CELL, SINK_Y * CELL - 9);

        const closedTop = CLOSED_Y * CELL;
        ctx.fillStyle = "rgba(9, 15, 27, 0.96)";
        ctx.fillRect(0, closedTop, VIEW_W, VIEW_H - closedTop);
        ctx.strokeStyle = "rgba(120, 150, 190, 0.18)";
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(0, closedTop + 0.5);
        ctx.lineTo(VIEW_W, closedTop + 0.5);
        ctx.stroke();
        ctx.fillStyle = "rgba(147, 164, 192, 0.7)";
        ctx.font = "600 11px Consolas, monospace";
        ctx.fillText("СТВОРЫ 3–8 ЗАКРЫТЫ", 14, closedTop + 30);
        ctx.font = "500 10px Consolas, monospace";
        ctx.fillText("транзакции · потоки · брокер · XML · подпись · WebSocket — в прототипе открыты два створа",
            14, closedTop + 50);
    };

    const formatTime = (totalSeconds) => {
        const minutes = Math.floor(totalSeconds / 60);
        const seconds = totalSeconds % 60;
        return minutes + ":" + String(seconds).padStart(2, "0");
    };

    const score = () => Math.max(0, state.delivered * 10 - state.rejected * 2 - state.queries);

    const updateHud = () => {
        hudDelivered.textContent = String(state.delivered);
        hudRejected.textContent = String(state.rejected);
        hudQueries.textContent = String(state.queries);
        hudMaterial.textContent = String(state.material);
        hudTime.textContent = formatTime(state.seconds);
        tapButton.textContent = state.tapOpen ? "кран: открыт" : "кран: закрыт";
        tapButton.classList.toggle("btn--primary", state.tapOpen);
        tapButton.classList.toggle("btn--ghost", !state.tapOpen);
    };

    const setMaterial = (name) => {
        state.tool = name;
        swatches.forEach((button) => {
            button.classList.toggle("is-active", button.dataset.material === name);
        });
    };

    const setOverlay = (config) => {
        overlayEyebrow.textContent = config.eyebrow;
        overlayTitle.textContent = config.title;
        overlayText.textContent = config.text;
        overlayPrimary.textContent = config.primary;
        overlayPrimary.onclick = config.onPrimary;
        overlaySecondary.textContent = config.secondary || "";
        overlaySecondary.hidden = !config.secondary;
        overlaySecondary.onclick = config.onSecondary || null;
        nameForm.hidden = !config.showName;
        overlay.hidden = false;
    };

    const reset = () => {
        buildMap();
        state.material = MATERIAL_LIMIT;
        state.tapOpen = false;
        state.running = false;
        state.startedAt = 0;
        state.seconds = 0;
        state.delivered = 0;
        state.rejected = 0;
        state.queries = 0;
        state.tick = 0;
        state.sent = false;
        hint.textContent = "Стены стоят материала; ластик возвращает его обратно.";
        setOverlay({
            eyebrow: "песочница · одна карта · два створа",
            title: "Проведите поток",
            text: "Сверху сыплются записи. Створ контракта принимает их только в окно — всё, что упадёт на мембрану, "
                + "отбивается как 400. Под ним развилка JPA: узкие щели считают отдельный запрос на каждую запись, "
                + "широкий жёлоб обходится одним. Стройте стены, чтобы поток шёл по короткому пути.",
            primary: "Открыть кран",
            onPrimary: start,
            secondary: "Заново",
            onSecondary: reset
        });
        updateHud();
    };

    const start = () => {
        overlay.hidden = true;
        state.running = true;
        state.tapOpen = true;
        state.startedAt = Date.now();
        updateHud();
    };

    const finish = () => {
        state.running = false;
        state.tapOpen = false;
        state.seconds = Math.max(1, Math.round((Date.now() - state.startedAt) / 1000));
        updateHud();
        setOverlay({
            eyebrow: "прототип · створы 1–2",
            title: "Поток дошёл до приёмника",
            text: "Доставлено " + state.delivered + " записей, отклонено " + state.rejected
                + ", лишних запросов " + state.queries + ". Очки: " + score() + ". Оставьте результат в таблице.",
            primary: "Заново",
            onPrimary: reset,
            showName: true
        });
        playerInput.value = readPlayer();
    };

    const readPlayer = () => {
        try {
            return window.localStorage.getItem(LOCAL_PLAYER_KEY) || "";
        } catch (error) {
            return "";
        }
    };

    const readLocalBoard = () => {
        try {
            const parsed = JSON.parse(window.localStorage.getItem(LOCAL_BOARD_KEY) || "[]");
            return Array.isArray(parsed) ? parsed : [];
        } catch (error) {
            return [];
        }
    };

    const writeLocalScore = (entry) => {
        try {
            const scores = [entry, ...readLocalBoard()]
                .sort((left, right) => right.score - left.score)
                .slice(0, 20);
            window.localStorage.setItem(LOCAL_BOARD_KEY, JSON.stringify(scores));
        } catch (error) {
            /* приватный режим — не сохраняем */
        }
    };

    const escapeHtml = (value) => String(value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");

    const renderBoard = (scores, source) => {
        boardNote.hidden = source === "server";
        boardNote.textContent = "офлайн-режим: результаты хранятся в этом браузере";
        if (!Array.isArray(scores) || scores.length === 0) {
            board.innerHTML = '<li class="board__empty">Пока пусто — доведите поток и оставьте результат.</li>';
            return;
        }
        board.innerHTML = scores.map((entry, index) => `
            <li class="${index === 0 ? "is-top" : ""}">
                <span class="board__rank">${index + 1}.</span>
                <span class="board__player">${escapeHtml(entry.player)}</span>
                <span class="board__meta board__meta--level" style="color:#34d399">поток · ${entry.level} створа</span>
                <span class="board__meta">${entry.score} очк · ${formatTime(entry.seconds)}</span>
            </li>`).join("");
    };

    const loadLeaderboard = async () => {
        try {
            if (window.EIGHT_OFFLINE) {
                throw new Error("offline build");
            }
            const response = await fetch("/api/game/leaderboard?limit=10");
            if (!response.ok) {
                throw new Error("HTTP " + response.status);
            }
            renderBoard(await response.json(), "server");
        } catch (error) {
            renderBoard(readLocalBoard(), "local");
        }
    };

    const saveScore = async (entry) => {
        try {
            if (window.EIGHT_OFFLINE) {
                throw new Error("offline build");
            }
            const response = await fetch("/api/game/scores", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(entry)
            });
            if (!response.ok) {
                throw new Error("HTTP " + response.status);
            }
            return "server";
        } catch (error) {
            writeLocalScore(entry);
            return "local";
        }
    };

    nameForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        const player = playerInput.value.trim();
        if (player.length < 2) {
            hint.textContent = "Имя от 2 до 20 символов.";
            playerInput.focus();
            return;
        }
        if (state.sent) {
            return;
        }
        state.sent = true;
        try {
            window.localStorage.setItem(LOCAL_PLAYER_KEY, player);
        } catch (error) {
            /* приватный режим — просто не запоминаем имя */
        }
        const entry = { player: player, level: 2, score: score(), seconds: state.seconds };
        const source = await saveScore(entry);
        nameForm.hidden = true;
        overlayText.textContent = source === "server"
            ? "Результат записан: " + entry.score + " очков за " + formatTime(entry.seconds) + "."
            : "Сервер недоступен — результат сохранён в браузере.";
        hint.textContent = "Стены стоят материала; ластик возвращает его обратно.";
        await loadLeaderboard();
    });

    swatches.forEach((button) => {
        button.addEventListener("click", () => {
            setMaterial(button.dataset.material);
        });
    });

    tapButton.addEventListener("click", () => {
        state.tapOpen = !state.tapOpen;
        if (state.tapOpen && !state.running) {
            start();
        }
        updateHud();
    });

    resetButton.addEventListener("click", reset);
    boardRefresh.addEventListener("click", loadLeaderboard);

    brushInput.addEventListener("input", () => {
        state.brush = Number(brushInput.value);
    });

    window.addEventListener("keydown", (event) => {
        if (event.target instanceof HTMLInputElement) {
            return;
        }
        if (event.key === "1") {
            setMaterial("wall");
        } else if (event.key === "2") {
            setMaterial("water");
        } else if (event.key === "3") {
            setMaterial("erase");
        } else if (event.code === "Space") {
            event.preventDefault();
            tapButton.click();
        }
    });

    const loop = () => {
        if (state.running) {
            step();
            spawn();
            state.tick += 1;
            if (state.startedAt) {
                state.seconds = Math.floor((Date.now() - state.startedAt) / 1000);
            }
            updateHud();
            if (state.delivered >= TARGET_DELIVERED) {
                finish();
            }
        }
        render();
        window.requestAnimationFrame(loop);
    };

    setMaterial("wall");
    reset();
    loadLeaderboard();
    window.requestAnimationFrame(loop);
})();
