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
    const BAND_Y = 80;

    const SLIT_HOLD = 18;
    const MATERIAL_LIMIT = 600;
    const LOCAL_BOARD_KEY = "eight-projects-leaderboard";
    const LOCAL_PLAYER_KEY = "eight-sand-player";

    const LEVELS = [
        {
            name: "Контракт",
            project: "REST",
            rule: "окно узкое, остальное — мембрана 400",
            window: [76, 112],
            spouts: [{ x0: 40, x1: 150, rate: 0.42 }],
            sink: [60, 140],
            target: 40
        },
        {
            name: "JPA",
            project: "JPA",
            rule: "слева щели по запросу, справа жёлоб",
            window: [60, 120],
            gate2: { slits: [61, 6, 6], chute: [100, 120] },
            spouts: [{ x0: 40, x1: 150, rate: 0.42 }],
            sink: [50, 135],
            target: 40
        },
        {
            name: "Транзакции",
            project: "TX",
            rule: "партия из пяти: одно 400 откатывает всю пятёрку",
            window: [70, 118],
            batch: 5,
            spouts: [{ x0: 40, x1: 150, rate: 0.42 }],
            sink: [60, 140],
            target: 40
        },
        {
            name: "Потоки",
            project: "CONC",
            rule: "три крана и пул на 120 записей: переполнение теряет записи",
            window: [60, 160],
            fieldLimit: 120,
            spouts: [{ x0: 30, x1: 70, rate: 0.3 }, { x0: 80, x1: 120, rate: 0.3 }, { x0: 130, x1: 170, rate: 0.3 }],
            sink: [55, 165],
            target: 45
        },
        {
            name: "Брокер",
            project: "BUS",
            rule: "отбитые не исчезают — падают в карман DLQ справа",
            window: [60, 110],
            dlq: [152, 184],
            spouts: [{ x0: 40, x1: 150, rate: 0.42 }],
            sink: [50, 140],
            target: 35
        },
        {
            name: "XML и XSD",
            project: "XML",
            rule: "сито по форме: три щели по одной клетке",
            sieve: [70, 88, 106],
            spouts: [{ x0: 40, x1: 150, rate: 0.42 }],
            sink: [55, 145],
            target: 25
        },
        {
            name: "Подпись",
            project: "CRYPTO",
            rule: "без подписи водой запись не принимается",
            window: [60, 120],
            signature: true,
            spouts: [{ x0: 40, x1: 150, rate: 0.42 }],
            sink: [50, 140],
            target: 25
        },
        {
            name: "WebSocket",
            project: "WS",
            rule: "окно едет по карте — ловите такт",
            window: [52, 96],
            moving: 60,
            spouts: [{ x0: 20, x1: 170, rate: 0.42 }],
            sink: [40, 160],
            target: 35
        }
    ];

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
    const signed = new Uint8Array(GW * GH);

    const COLORS = {
        [SAND]: [139, 212, 255],
        [WATER]: [74, 108, 240],
        [WALL]: [42, 74, 116],
        [MEMBRANE]: [122, 43, 57],
        [SLIT]: [107, 90, 31],
        [SINK]: [29, 122, 85]
    };

    const hudLevel = document.getElementById("hud-level");
    const hudDelivered = document.getElementById("hud-delivered");
    const hudRejected = document.getElementById("hud-rejected");
    const hudQueries = document.getElementById("hud-queries");
    const hudMaterial = document.getElementById("hud-material");
    const hudMaterialBar = document.getElementById("hud-material-bar");
    const hudTime = document.getElementById("hud-time");
    const spendBadge = document.getElementById("sand-spend");
    const overlay = document.getElementById("sand-overlay");
    const overlayEyebrow = document.getElementById("sand-overlay-title").previousElementSibling;
    const overlayTitle = document.getElementById("sand-overlay-title");
    const overlayText = document.getElementById("sand-overlay-text");
    const overlayPrimary = document.getElementById("sand-overlay-primary");
    const overlaySecondary = document.getElementById("sand-overlay-secondary");
    const levelsBox = document.getElementById("sand-levels");
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
        level: 0,
        tapOpen: false,
        running: false,
        startedAt: 0,
        seconds: 0,
        delivered: 0,
        rejected: 0,
        queries: 0,
        lost: 0,
        rollbacks: 0,
        batchProgress: 0,
        tick: 0,
        painting: false,
        sent: false
    };

    const level = () => LEVELS[state.level];
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
                signed[i] = 0;
            }
        }
    };

    const windowSpan = () => {
        const config = level();
        if (!config.window) {
            return [0, -1];
        }
        const width = config.window[1] - config.window[0];
        if (!config.moving) {
            return config.window;
        }
        const offset = Math.round(Math.sin(state.tick / 90) * config.moving);
        const start = Math.max(6, Math.min(GW - 10 - width, config.window[0] + offset));
        return [start, start + width];
    };

    const setGateCell = (i, kind) => {
        if (grid[i] === SAND || grid[i] === WATER) {
            return;
        }
        grid[i] = kind;
        owner[i] = 0;
        hold[i] = 0;
        signed[i] = 0;
    };

    const drawGateRow = () => {
        const config = level();
        const span = windowSpan();
        const holes = config.sieve || null;
        for (let row = 0; row < 2; row++) {
            for (let x = 3; x < GW - 3; x++) {
                const i = cellIndex(x, GATE1_Y + row);
                let kind = MEMBRANE;
                if (holes) {
                    const inHole = holes.indexOf(x) >= 0 || holes.indexOf(x - 1) >= 0;
                    kind = inHole ? EMPTY : MEMBRANE;
                } else if (x >= span[0] && x <= span[1]) {
                    kind = EMPTY;
                }
                setGateCell(i, kind);
            }
        }
    };

    const buildLevel = () => {
        const config = level();
        grid.fill(EMPTY);
        owner.fill(0);
        hold.fill(0);
        signed.fill(0);
        fillRect(0, 0, GW, 3, WALL);
        config.spouts.forEach((spout) => fillRect(spout.x0, 0, spout.x1 - spout.x0 + 1, 3, EMPTY));
        fillRect(0, 0, 3, FLOOR_Y, WALL);
        fillRect(GW - 3, 0, 3, FLOOR_Y, WALL);
        fillRect(0, FLOOR_Y, GW, 4, WALL);
        drawGateRow();
        if (config.gate2) {
            const [slitStart, slitStep, slitCount] = config.gate2.slits;
            const [chuteX0, chuteX1] = config.gate2.chute;
            fillRect(3, GATE2_Y, chuteX0 - 4, 2, WALL);
            fillRect(chuteX1 + 1, GATE2_Y, GW - 3 - (chuteX1 + 1), 2, WALL);
            for (let slit = 0; slit < slitCount; slit++) {
                fillRect(slitStart + slit * slitStep, GATE2_Y, 1, 2, SLIT);
            }
        }
        fillRect(config.sink[0], SINK_Y, config.sink[1] - config.sink[0] + 1, 3, SINK);
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
        const mark = signed[a];
        signed[a] = signed[b];
        signed[b] = mark;
    };

    const clearCell = (i) => {
        grid[i] = EMPTY;
        owner[i] = 0;
        hold[i] = 0;
        signed[i] = 0;
    };

    const canTake = (i) => isEmpty(grid[i]) || grid[i] === WATER;

    const parkInDlq = () => {
        const pocket = level().dlq;
        if (!pocket) {
            return;
        }
        const x = pocket[0] + Math.floor(Math.random() * (pocket[1] - pocket[0]));
        const i = cellIndex(x, FLOOR_Y - 1);
        if (isEmpty(grid[i])) {
            grid[i] = SAND;
            owner[i] = 0;
            signed[i] = 0;
        }
    };

    const onReject = () => {
        state.rejected += 1;
        if (level().batch) {
            if (state.batchProgress > 0) {
                state.rollbacks += 1;
            }
            state.batchProgress = 0;
        }
    };

    const onDeliver = () => {
        state.delivered += 1;
        if (level().batch) {
            state.batchProgress = (state.batchProgress + 1) % level().batch;
        }
    };

    const moveSand = (x, y, i) => {
        if (hold[i] > 0) {
            hold[i] -= 1;
            return;
        }
        const down = i + GW;
        const below = grid[down];
        if (below === MEMBRANE) {
            onReject();
            parkInDlq();
            clearCell(i);
            return;
        }
        if (below === SINK) {
            if (level().signature && signed[i] === 0) {
                onReject();
            } else {
                onDeliver();
            }
            clearCell(i);
            return;
        }
        if (below === SLIT && y + 1 === GATE2_Y) {
            state.queries += 1;
            hold[i] = SLIT_HOLD;
            return;
        }
        if (canTake(down)) {
            const wet = grid[down] === WATER;
            swapCells(i, down);
            if (wet) {
                signed[down] = 1;
            }
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

    const grainCount = () => {
        let total = 0;
        for (let i = 0; i < grid.length; i++) {
            if (grid[i] === SAND) {
                total += 1;
            }
        }
        return total;
    };

    const step = () => {
        if (level().moving) {
            drawGateRow();
        }
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
        if (!state.tapOpen) {
            return;
        }
        const config = level();
        if (config.fieldLimit && grainCount() > config.fieldLimit) {
            state.lost += 1;
            return;
        }
        config.spouts.forEach((spout) => {
            if (Math.random() > spout.rate) {
                return;
            }
            const x = spout.x0 + Math.floor(Math.random() * (spout.x1 - spout.x0 + 1));
            const i = cellIndex(x, 3);
            if (isEmpty(grid[i])) {
                grid[i] = SAND;
                owner[i] = 0;
                signed[i] = 0;
            }
        });
    };

    const paint = (gx, gy, hardErase) => {
        const erase = hardErase || state.tool === "erase";
        const kind = state.tool === "water" ? WATER : WALL;
        const radius = state.brush;
        let spent = 0;
        let returned = 0;
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
                        returned += 1;
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
                spent += 1;
            }
        }
        return { spent: spent, returned: returned };
    };

    const pointToCell = (event) => {
        const rect = canvas.getBoundingClientRect();
        const gx = Math.floor(((event.clientX - rect.left) / rect.width) * GW);
        const gy = Math.floor(((event.clientY - rect.top) / rect.height) * GH);
        return { x: gx, y: gy };
    };

    const showSpend = (event, spent, returned) => {
        const delta = spent > 0 ? spent : returned;
        if (delta <= 0) {
            return;
        }
        const host = canvas.parentElement.getBoundingClientRect();
        spendBadge.textContent = (spent > 0 ? "−" : "+") + delta;
        spendBadge.classList.toggle("spend--gain", spent === 0);
        spendBadge.style.left = (event.clientX - host.left) + "px";
        spendBadge.style.top = (event.clientY - host.top) + "px";
        spendBadge.hidden = false;
        spendBadge.style.animation = "none";
        spendBadge.offsetWidth;
        spendBadge.style.animation = "";
        if (state.material <= 0) {
            hint.textContent = "Материал кончился: ластик забирает свои стены и возвращает клетки.";
        }
    };

    const handlePaint = (event) => {
        const point = pointToCell(event);
        const erase = event.button === 2 || (event.buttons & 2) === 2;
        const result = paint(point.x, point.y, erase);
        if (result.spent > 0 || result.returned > 0) {
            showSpend(event, result.spent, result.returned);
        }
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
        const config = level();
        ctx.textBaseline = "alphabetic";
        ctx.font = "600 10px Consolas, monospace";
        ctx.fillStyle = "rgba(147, 164, 192, 0.78)";
        ctx.fillText("КРАН", config.spouts[0].x0 * CELL, 3 * CELL - 12);
        ctx.fillStyle = "rgba(147, 164, 192, 0.9)";
        ctx.fillText("СТВОР " + (state.level + 1) + " · " + config.name.toUpperCase() + " — " + config.rule,
            14, GATE1_Y * CELL - 9);
        if (config.gate2) {
            ctx.fillStyle = "rgba(147, 164, 192, 0.78)";
            ctx.fillText("РАЗВИЛКА · слева щели (запрос на запись), справа жёлоб", 14, GATE2_Y * CELL - 9);
        }
        ctx.fillStyle = "rgba(52, 211, 153, 0.9)";
        ctx.fillText("ПРИЁМНИК " + state.delivered + " / " + config.target,
            config.sink[0] * CELL, SINK_Y * CELL - 9);

        const bandTop = BAND_Y * CELL;
        ctx.fillStyle = "rgba(9, 15, 27, 0.94)";
        ctx.fillRect(0, bandTop, VIEW_W, VIEW_H - bandTop);
        ctx.strokeStyle = "rgba(120, 150, 190, 0.18)";
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(0, bandTop + 0.5);
        ctx.lineTo(VIEW_W, bandTop + 0.5);
        ctx.stroke();
        ctx.font = "600 11px Consolas, monospace";
        ctx.fillStyle = "rgba(230, 238, 252, 0.82)";
        ctx.fillText("СТВОР " + (state.level + 1) + " ИЗ 8 · " + config.project, 14, bandTop + 24);
        ctx.font = "500 10px Consolas, monospace";
        ctx.fillStyle = "rgba(147, 164, 192, 0.72)";
        ctx.fillText(config.rule, 14, bandTop + 42);
        const extra = [];
        if (config.batch) {
            extra.push("партия " + state.batchProgress + "/" + config.batch + " · откатов " + state.rollbacks);
        }
        if (config.fieldLimit) {
            extra.push("в поле " + grainCount() + " / " + config.fieldLimit + " · потеряно " + state.lost);
        }
        if (config.dlq) {
            extra.push("DLQ справа");
        }
        if (config.signature) {
            extra.push("подпись: проведите запись через воду");
        }
        if (extra.length > 0) {
            ctx.fillText(extra.join(" · "), 14, bandTop + 58);
        }
    };

    const formatTime = (totalSeconds) => {
        const minutes = Math.floor(totalSeconds / 60);
        const seconds = totalSeconds % 60;
        return minutes + ":" + String(seconds).padStart(2, "0");
    };

    const score = () => Math.max(0, state.delivered * 10 - state.rejected * 2 - state.queries
        - state.rollbacks * 25 - state.lost * 5);

    const updateHud = () => {
        hudLevel.textContent = (state.level + 1) + " / 8";
        hudDelivered.textContent = String(state.delivered);
        hudRejected.textContent = String(state.rejected);
        hudQueries.textContent = String(state.queries);
        hudMaterial.textContent = String(state.material);
        hudTime.textContent = formatTime(state.seconds);
        hudMaterialBar.style.width = ((state.material / MATERIAL_LIMIT) * 100).toFixed(1) + "%";
        hudMaterialBar.classList.toggle("is-empty", state.material <= 0);
        tapButton.textContent = state.tapOpen ? "кран: открыт" : "кран: закрыт";
        tapButton.classList.toggle("btn--primary", state.tapOpen);
        tapButton.classList.toggle("btn--ghost", !state.tapOpen);
        Array.from(levelsBox.children).forEach((chip, index) => {
            chip.classList.toggle("is-active", index === state.level);
        });
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
        levelsBox.hidden = !config.showLevels;
        nameForm.hidden = !config.showName;
        overlay.hidden = false;
    };

    const reset = () => {
        const config = level();
        buildLevel();
        state.material = MATERIAL_LIMIT;
        state.tapOpen = false;
        state.running = false;
        state.startedAt = 0;
        state.seconds = 0;
        state.delivered = 0;
        state.rejected = 0;
        state.queries = 0;
        state.lost = 0;
        state.rollbacks = 0;
        state.batchProgress = 0;
        state.tick = 0;
        state.sent = false;
        hint.textContent = "Стены и вода стоят материала (600 клеток на забег); ластик возвращает клетки за свои постройки.";
        setOverlay({
            eyebrow: "песочница · восемь створов",
            title: "Створ " + (state.level + 1) + " · " + config.name,
            text: "Правило: " + config.rule + ". Песчинка — запись, стена — валидация. "
                + "Доведите " + config.target + " записей до приёмника.",
            primary: "Открыть кран",
            onPrimary: start,
            secondary: "Заново",
            onSecondary: reset,
            showLevels: true
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
        const config = level();
        state.running = false;
        state.tapOpen = false;
        state.seconds = Math.max(1, Math.round((Date.now() - state.startedAt) / 1000));
        updateHud();
        setOverlay({
            eyebrow: "створ " + (state.level + 1) + " из 8 · " + config.project,
            title: "Створ пройден",
            text: "Доставлено " + state.delivered + " записей, отклонено " + state.rejected
                + ", лишних запросов " + state.queries + ". Очки: " + score() + ". Оставьте результат в таблице.",
            primary: "Заново",
            onPrimary: reset,
            showName: true
        });
        playerInput.value = readPlayer();
    };

    const selectLevel = (index) => {
        state.level = index;
        reset();
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
            board.innerHTML = '<li class="board__empty">Пока пусто — пройдите створ и оставьте результат.</li>';
            return;
        }
        board.innerHTML = scores.map((entry, index) => `
            <li class="${index === 0 ? "is-top" : ""}">
                <span class="board__rank">${index + 1}.</span>
                <span class="board__player">${escapeHtml(entry.player)}</span>
                <span class="board__meta board__meta--level" style="color:#34d399">створ ${entry.level}</span>
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
        const entry = { player: player, level: state.level + 1, score: score(), seconds: state.seconds };
        const source = await saveScore(entry);
        nameForm.hidden = true;
        overlayText.textContent = source === "server"
            ? "Результат записан: " + entry.score + " очков за " + formatTime(entry.seconds) + "."
            : "Сервер недоступен — результат сохранён в браузере.";
        hint.textContent = "Стены и вода стоят материала (600 клеток на забег); ластик возвращает клетки за свои постройки.";
        await loadLeaderboard();
    });

    LEVELS.forEach((config, index) => {
        const chip = document.createElement("button");
        chip.type = "button";
        chip.className = "level-chip";
        chip.dataset.level = String(index);
        chip.innerHTML = '<b>' + (index + 1) + '</b><span>' + config.name + '</span>';
        chip.addEventListener("click", () => selectLevel(index));
        levelsBox.appendChild(chip);
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
            if (state.delivered >= level().target) {
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
