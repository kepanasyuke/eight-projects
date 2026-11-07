/* Логика: четыре мини-игры про код и порядок работ. */
(() => {
    "use strict";

    const STORE = "eight-logic-v1";
    const STICKERS = ["Первый шаг", "Счётчик", "Логик", "Стратег", "Архитектор", "Мастер потока"];
    const COLORS = ["#2f7ae5", "#7c5cf0", "#e0578f", "#e79a1e", "#22b573", "#1aa6b8"];
    const XP_PER_SOLVE = 10;
    const XP_PER_LEVEL = 50;

    const el = (tag, cls, text) => {
        const node = document.createElement(tag);
        if (cls) {
            node.className = cls;
        }
        if (text !== undefined && text !== null) {
            node.textContent = text;
        }
        return node;
    };

    const pick = (list) => list[Math.floor(Math.random() * list.length)];

    const state = loadState();
    let mode = "steps";
    let round = null;
    let streak = state.streak;
    let audio = null;

    function loadState() {
        const fallback = { xp: 0, stars: 0, streak: 0, solved: 0, stickers: [], muted: false };
        try {
            const raw = window.localStorage.getItem(STORE);
            return raw ? Object.assign(fallback, JSON.parse(raw)) : fallback;
        } catch (err) {
            return fallback;
        }
    }

    function saveState() {
        try {
            window.localStorage.setItem(STORE, JSON.stringify(state));
        } catch (err) {
            return;
        }
    }

    function levelOf(xp) {
        return 1 + Math.floor(xp / XP_PER_LEVEL);
    }

    function blip(ok) {
        if (state.muted) {
            return;
        }
        try {
            const Ctx = window.AudioContext || window.webkitAudioContext;
            audio = audio || new Ctx();
            const osc = audio.createOscillator();
            const gain = audio.createGain();
            osc.type = ok ? "triangle" : "sawtooth";
            osc.frequency.value = ok ? 587 : 196;
            gain.gain.value = 0.05;
            osc.connect(gain);
            gain.connect(audio.destination);
            osc.start();
            osc.frequency.exponentialRampToValueAtTime(ok ? 988 : 130, audio.currentTime + 0.12);
            gain.gain.exponentialRampToValueAtTime(0.0001, audio.currentTime + 0.24);
            osc.stop(audio.currentTime + 0.26);
        } catch (err) {
            state.muted = true;
        }
    }

    function confetti() {
        const board = document.getElementById("board");
        for (let i = 0; i < 18; i += 1) {
            const bit = el("span", "lg-confetti");
            bit.style.left = `${6 + Math.random() * 86}%`;
            bit.style.background = COLORS[i % COLORS.length];
            bit.style.animationDelay = `${Math.random() * 0.2}s`;
            board.appendChild(bit);
            window.setTimeout(() => bit.remove(), 1500);
        }
    }

    function updateHud() {
        const level = levelOf(state.xp);
        const inLevel = state.xp % XP_PER_LEVEL;
        document.getElementById("hud-stars").textContent = String(state.stars);
        document.getElementById("hud-level").textContent = String(level);
        document.getElementById("hud-streak").textContent = String(streak);
        document.getElementById("hud-stickers").textContent = `${state.stickers.length} / ${STICKERS.length}`;
        document.getElementById("level-text").textContent = `${inLevel} / ${XP_PER_LEVEL}`;
        document.getElementById("level-fill").style.width = `${Math.round((inLevel / XP_PER_LEVEL) * 100)}%`;
        drawStickers();
    }

    function drawStickers() {
        const host = document.getElementById("stickers");
        host.replaceChildren();
        STICKERS.forEach((name, index) => {
            const unlocked = state.stickers.includes(name);
            const badge = el("span", `lg-sticker${unlocked ? " is-on" : ""}`);
            badge.title = unlocked ? `Наклейка: ${name}` : "Ещё не собрана";
            badge.style.setProperty("--i", String(index));
            const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
            svg.setAttribute("viewBox", "0 0 24 24");
            const path = document.createElementNS("http://www.w3.org/2000/svg", "path");
            path.setAttribute("d", "M12 2.4l2.9 6.1 6.6.9-4.8 4.7 1.2 6.6L12 17.5l-5.9 3.2 1.2-6.6L2.5 9.4l6.6-.9z");
            svg.appendChild(path);
            badge.appendChild(svg);
            host.appendChild(badge);
        });
    }

    function feedback(kind, text) {
        const node = document.getElementById("feedback");
        node.className = `lg-feedback${kind ? ` is-${kind}` : ""}`;
        node.replaceChildren();
        if (text) {
            node.appendChild(document.createTextNode(text));
        }
    }

    function solvedRound() {
        streak += 1;
        state.streak = streak;
        state.solved += 1;
        state.stars += streak >= 3 ? 2 : 1;
        state.xp += XP_PER_SOLVE + (streak >= 3 ? 5 : 0);
        const target = Math.floor(state.solved / 4);
        const unlocked = STICKERS.slice(0, target).filter((name) => !state.stickers.includes(name));
        unlocked.forEach((name) => state.stickers.push(name));
        saveState();
        updateHud();
        const board = document.getElementById("board");
        board.classList.add("is-done");
        document.getElementById("check").disabled = true;
        document.getElementById("next").hidden = false;
        confetti();
        blip(true);
        feedback("ok", unlocked.length
            ? `Верно! Новая наклейка: «${unlocked[0]}».`
            : `Верно! +${XP_PER_SOLVE} опыта, звёзд: ${state.stars}.`);
    }

    function failedRound(text) {
        streak = 0;
        state.streak = 0;
        saveState();
        updateHud();
        blip(false);
        feedback("bad", text);
    }

    function setMode(next) {
        mode = next;
        ensureRound(true);
        drawRound();
    }

    function ensureRound(force) {
        if (force || !round || round.mode !== mode) {
            round = buildRound(mode);
            document.getElementById("task-eyebrow").textContent = `задача ${round.index}`;
            document.getElementById("task-title").textContent = round.title;
            document.getElementById("task-text").textContent = round.text;
            document.getElementById("mode-subtitle").textContent = round.subtitle;
        }
    }

    function drawRound() {
        const board = document.getElementById("board");
        board.classList.remove("is-done");
        board.replaceChildren();
        document.getElementById("check").disabled = false;
        document.getElementById("check").hidden = Boolean(round.auto);
        document.getElementById("next").hidden = true;
        feedback("", round.tip || "");
        if (round.mode === "steps") {
            drawSteps(board);
        }
        if (round.mode === "gates") {
            drawGates(board);
        }
        if (round.mode === "bug") {
            drawBug(board);
        }
        if (round.mode === "plan") {
            drawPlan(board);
        }
    }

    const STEP_OPS = [
        { id: "+1", label: "+1", fn: (v) => v + 1 },
        { id: "+2", label: "+2", fn: (v) => v + 2 },
        { id: "x2", label: "×2", fn: (v) => v * 2 },
        { id: "-1", label: "−1", fn: (v) => Math.max(0, v - 1) },
        { id: "x3", label: "×3", fn: (v) => v * 3 }
    ];

    const GATE_OPS = [
        { id: "and", label: "И", fn: (a, b) => a === 1 && b === 1 },
        { id: "or", label: "ИЛИ", fn: (a, b) => a === 1 || b === 1 },
        { id: "xor", label: "искл.ИЛИ", fn: (a, b) => (a === 1) !== (b === 1) }
    ];

    const BUGS = [
        {
            title: "Сумма заказа",
            lines: [
                "int sum = 0;",
                "for (int i = 0; i <= items.size(); i++) {",
                "    sum += items.get(i).getPrice();",
                "}"
            ],
            options: [
                "На последнем шаге индекс равен size() — выход за границы",
                "getPrice() возвращает слишком маленькое число",
                "Нужно объявить sum как long",
                "Цикл нельзя начинать с нуля"
            ],
            answer: 0,
            why: "Условие i <= size() даёт лишний шаг: последний get(i) обращается к несуществующему элементу."
        },
        {
            title: "Проверка статуса",
            lines: [
                "String status = user.getStatus();",
                "if (status == \"ACTIVE\") {",
                "    grantAccess(user);",
                "}"
            ],
            options: [
                "Оператор == сравнивает ссылки, а не текст",
                "Код не компилируется: String нельзя сравнивать",
                "grantAccess объявлен ниже",
                "Нужно было взять char"
            ],
            answer: 0,
            why: "Строки сравнивают через equals: == совпадёт только для одного и того же литерала в пуле."
        },
        {
            title: "Средний чек",
            lines: [
                "int sum = 1570;",
                "int count = 12;",
                "double avg = sum / count;",
                "return avg;"
            ],
            options: [
                "Деление целых теряет дробную часть",
                "Нужно было объявить sum как double",
                "count может быть нулём — код не запустится",
                "Возврат double из метода int недопустим"
            ],
            answer: 0,
            why: "Сначала выполняется целочисленное деление, и только потом результат приводится к double: 130.0 вместо 130.83."
        },
        {
            title: "Тихий перехват",
            lines: [
                "try {",
                "    repository.save(order);",
                "} catch (Exception e) {",
                "    return;",
                "}"
            ],
            options: [
                "Исключение пропадает молча — ошибку никто не увидит",
                "catch обязан содержать throw по стандарту",
                "Exception нельзя ловить в сервисах",
                "Нужно продублировать try в репозитории"
            ],
            answer: 0,
            why: "Пустой обработчик глотает ошибку: заказ не сохранён, а вызывающий код считает, что всё хорошо."
        },
        {
            title: "Транзакция не работает",
            lines: [
                "@Service",
                "public class OrderService {",
                "    public void place() { this.save(); }",
                "    @Transactional private void save() { ... }",
                "}"
            ],
            options: [
                "Вызов идёт мимо прокси: транзакции не будет",
                "Нужно добавить @Service на второй класс",
                "Транзакция требует явного commit()",
                "Аннотацию ставят только на интерфейс"
            ],
            answer: 0,
            why: "Spring перехватывает вызовы через прокси: self-invocation и private-метод обходят перехват."
        },
        {
            title: "Деньги на хранении",
            lines: [
                "BigDecimal price =",
                "    new BigDecimal(0.1);",
                "BigDecimal total = price.add(price);"
            ],
            options: [
                "Конструктор с double приносит двоичную погрешность",
                "BigDecimal не умеет складывать два значения",
                "Для денег подходит только float",
                "Нужно вызвать stripTrailingZeros"
            ],
            answer: 0,
            why: "0.1 в double хранится приближённо: используйте new BigDecimal(\"0.1\") или BigDecimal.valueOf(0.1)."
        },
        {
            title: "Ключ потерялся",
            lines: [
                "class Key { String id;",
                "    // hashCode и equals от id",
                "}",
                "Map<Key, String> map = new HashMap<>();",
                "map.put(key, \"value\"); key.id = \"new\";"
            ],
            options: [
                "После смены поля изменился hashCode — ключ из корзины не найти",
                "HashMap запрещает неизменяемые ключи",
                "put сработал только со второго раза",
                "Нужно было взять TreeMap"
            ],
            answer: 0,
            why: "Ключ хеш-таблицы должен быть неизменяемым: новый hashCode кладёт запись в другую корзину."
        },
        {
            title: "Склейка строк",
            lines: [
                "String report = \"\";",
                "for (Order order : orders) {",
                "    report += order.getId() + \";\";",
                "}"
            ],
            options: [
                "Конкатенация в цикле копирует строку каждый раз",
                "Строки в Java нельзя изменить вообще",
                "Нужно заменить String на char[]",
                "Идентификаторы заказов повторяются"
            ],
            answer: 0,
            why: "String неизменяема, поэтому каждая итерация создаёт новую строку — O(n²) по объёму копирования."
        }
    ];

    function buildRound(name) {
        if (name === "steps") {
            return buildSteps();
        }
        if (name === "gates") {
            return buildGates();
        }
        if (name === "bug") {
            return buildBug();
        }
        return buildPlan();
    }

    function buildSteps() {
        const input = 2 + Math.floor(Math.random() * 7);
        let ops = [];
        let target = input;
        for (let guard = 0; guard < 40; guard += 1) {
            ops = [pick(STEP_OPS), pick(STEP_OPS), pick(STEP_OPS)];
            target = ops.reduce((value, op) => op.fn(value), input);
            if (target !== input && target <= 90) {
                break;
            }
        }
        return {
            mode: "steps",
            index: 1 + Math.floor(state.solved % 9),
            title: "Сборочный цех",
            subtitle: "Соберите программу из блоков — ровно три шага.",
            text: `Из числа ${input} нужно получить ${target}. Соберите цепочку из трёх блоков.`,
            tip: "Клик по блоку добавляет шаг, клик по слоту убирает его.",
            input,
            target,
            chosen: []
        };
    }

    function drawSteps(board) {
        const chain = el("div", "lg-chain");
        chain.appendChild(el("span", "lg-chip", String(round.input)));
        round.chosen.forEach((op, index) => {
            const slot = el("button", "lg-slot is-filled", op.label);
            slot.type = "button";
            slot.title = "Убрать шаг";
            slot.addEventListener("click", () => {
                round.chosen.splice(index, 1);
                drawRound();
            });
            chain.appendChild(el("span", "lg-arrow", "→"));
            chain.appendChild(slot);
        });
        for (let i = round.chosen.length; i < 3; i += 1) {
            chain.appendChild(el("span", "lg-arrow", "→"));
            const slot = el("button", "lg-slot", "шаг");
            slot.type = "button";
            slot.addEventListener("click", () => {
                if (round.chosen.length) {
                    round.chosen.pop();
                    drawRound();
                }
            });
            chain.appendChild(slot);
        }
        const result = stepValue();
        chain.appendChild(el("span", "lg-arrow", "="));
        chain.appendChild(el("span", "lg-chip lg-chip--goal", String(round.target)));
        board.appendChild(chain);

        const note = el("div", `lg-result${result === round.target ? " is-hit" : ""}`);
        note.append("сейчас: ");
        note.appendChild(el("b", null, String(result)));
        note.append(` · цель ${round.target}`);
        board.appendChild(note);

        const palette = el("div", "lg-palette");
        STEP_OPS.forEach((op) => {
            const button = el("button", null, op.label);
            button.type = "button";
            button.disabled = round.chosen.length >= 3;
            button.addEventListener("click", () => {
                if (round.chosen.length < 3) {
                    round.chosen.push(op);
                    drawRound();
                }
            });
            palette.appendChild(button);
        });
        board.appendChild(el("p", "lg-sub", "блоки"));
        board.appendChild(palette);
    }

    function stepValue() {
        return round.chosen.reduce((value, op) => op.fn(value), round.input);
    }

    function buildGates() {
        const rows = [[0, 0], [0, 1], [1, 0], [1, 1]];
        let first = pick(GATE_OPS);
        let second = pick(GATE_OPS);
        let table = gateTable(first, second, rows);
        for (let guard = 0; guard < 30 && table.every((row) => row[2] === table[0][2]); guard += 1) {
            first = pick(GATE_OPS);
            second = pick(GATE_OPS);
            table = gateTable(first, second, rows);
        }
        return {
            mode: "gates",
            index: 2,
            title: "Щиток",
            subtitle: "Подберите два вентиля, чтобы выход совпал с таблицей.",
            text: "Схема считает так: out = (A · B) ⊕ операции. Настройте оба вентиля.",
            tip: "Сверяйте столбец «нужно» со столбцом «сейчас».",
            hidden: [first, second],
            table,
            picked: [null, null]
        };
    }

    function gateTable(left, right, rows) {
        return rows.map(([a, b]) => {
            const mix = (a === 1) !== (b === 1) ? 1 : 0;
            const value = right.fn(left.fn(a, b) ? 1 : 0, mix) ? 1 : 0;
            return [a, b, value];
        });
    }

    function gateValue(left, right) {
        return gateTable(left, right, [[0, 0], [0, 1], [1, 0], [1, 1]]).map((row) => row[2]);
    }

    function drawGates(board) {
        const wire = el("div", "lg-chain");
        wire.appendChild(el("span", "lg-chip", "A"));
        wire.appendChild(el("span", "lg-op", round.picked[0] ? round.picked[0].label : "?"));
        wire.appendChild(el("span", "lg-chip", "B"));
        wire.appendChild(el("span", "lg-arrow", "→"));
        wire.appendChild(el("span", "lg-op", round.picked[1] ? round.picked[1].label : "?"));
        wire.appendChild(el("span", "lg-arrow", "→"));
        wire.appendChild(el("span", "lg-chip lg-chip--goal", "OUT"));
        board.appendChild(wire);

        [0, 1].forEach((slot) => {
            board.appendChild(el("p", "lg-sub", slot === 0 ? "первый вентиль" : "второй вентиль"));
            const row = el("div", "lg-ops");
            GATE_OPS.forEach((op) => {
                const button = el("button", `lg-op${round.picked[slot] === op ? " is-on" : ""}`, op.label);
                button.type = "button";
                button.setAttribute("aria-pressed", round.picked[slot] === op ? "true" : "false");
                button.addEventListener("click", () => {
                    round.picked[slot] = op;
                    drawRound();
                });
                row.appendChild(button);
            });
            board.appendChild(row);
        });

        const current = round.picked[0] && round.picked[1]
            ? gateValue(round.picked[0], round.picked[1])
            : null;
        const table = el("table", "lg-table");
        const head = el("tr");
        ["A", "B", "нужно", "сейчас"].forEach((label) => head.appendChild(el("th", null, label)));
        table.appendChild(head);
        round.table.forEach((row, index) => {
            const line = el("tr");
            line.appendChild(el("td", null, String(row[0])));
            line.appendChild(el("td", null, String(row[1])));
            line.appendChild(el("td", null, String(row[2])));
            if (!current) {
                line.appendChild(el("td", null, "—"));
            } else {
                const hit = current[index] === row[2];
                line.appendChild(el("td", hit ? "is-ok" : "is-bad", hit ? "✓" : "✗"));
            }
            table.appendChild(line);
        });
        board.appendChild(table);
    }

    function buildBug() {
        const puzzle = pick(BUGS);
        return {
            mode: "bug",
            index: 3,
            title: puzzle.title,
            subtitle: "Прочитайте код и найдите настоящую причину поломки.",
            text: "Выберите вариант, который объясняет ошибку в этом коде.",
            tip: "Один вариант описывает механику, остальные — мимо.",
            puzzle,
            choice: null
        };
    }

    function drawBug(board) {
        const code = el("pre", "lg-code");
        round.puzzle.lines.forEach((line, index) => {
            const number = el("span", "lg-ln", String(index + 1));
            code.appendChild(number);
            code.appendChild(document.createTextNode(`${line}\n`));
        });
        board.appendChild(code);

        const options = el("div", "lg-options");
        round.puzzle.options.forEach((option, index) => {
            const button = el("button", `lg-option${round.choice === index ? " is-on" : ""}`, option);
            button.type = "button";
            button.addEventListener("click", () => {
                round.choice = index;
                drawRound();
            });
            options.appendChild(button);
        });
        board.appendChild(options);
    }

    function buildPlan() {
        const names = ["Схема данных", "REST API", "Сервис заказов", "Тесты", "Документация"];
        const durations = [2, 3, 3, 4, 1];
        const deps = [[], [0], [0], [1, 2], [3]];
        const order = names.map((_, index) => index).sort(() => Math.random() - 0.5);
        const tasks = order.map((source, index) => ({
            id: index,
            name: names[source],
            hours: durations[source],
            deps: deps[source].map((dep) => order.indexOf(dep))
        }));
        return {
            mode: "plan",
            index: 4,
            title: "Планировщик",
            subtitle: "Расставьте работы по порядку с учётом зависимостей.",
            text: "Задача открывается, когда готовы все её зависимости. Соберите очередь.",
            tip: "Клик по карточке ставит работу в конец очереди.",
            auto: true,
            tasks,
            queue: []
        };
    }

    function drawPlan(board) {
        const cards = el("div", "lg-tasks");
        round.tasks.forEach((task) => {
            const taken = round.queue.includes(task.id);
            const card = el("button", `lg-card${taken ? " is-taken" : ""}`);
            card.type = "button";
            card.disabled = taken;
            card.appendChild(el("b", null, task.name));
            const waits = task.deps.map((dep) => round.tasks[dep].name);
            card.appendChild(el("span", null, `${task.hours} ч · ${waits.length ? `ждёт: ${waits.join(", ")}` : "можно начинать"}`));
            card.addEventListener("click", () => tryPlan(task, card));
            cards.appendChild(card);
        });
        board.appendChild(cards);

        const queue = el("div", "lg-queue");
        for (let i = 0; i < round.tasks.length; i += 1) {
            const id = round.queue[i];
            const slot = el("span", `lg-queue__slot${id === undefined ? "" : " is-filled"}`);
            slot.textContent = id === undefined ? String(i + 1) : round.tasks[id].name;
            queue.appendChild(slot);
        }
        board.appendChild(queue);
    }

    function tryPlan(task, card) {
        const ready = task.deps.every((dep) => round.queue.includes(dep));
        if (!ready) {
            const waits = task.deps.filter((dep) => !round.queue.includes(dep));
            const names = waits.map((dep) => round.tasks[dep].name).join(", ");
            card.classList.remove("is-wait");
            void card.offsetWidth;
            card.classList.add("is-wait");
            feedback("bad", `Ещё рано: «${task.name}» ждёт ${names}.`);
            blip(false);
            return;
        }
        round.queue.push(task.id);
        drawRound();
        if (round.queue.length === round.tasks.length) {
            const hours = round.tasks.reduce((sum, item) => sum + item.hours, 0);
            solvedRound();
            feedback("ok", `Очередь верная: все ${round.tasks.length} работ пройдут за ${hours} ч.`);
        }
    }

    function checkGates() {
        const [left, right] = round.picked;
        if (!left || !right) {
            feedback("bad", "Выберите оба вентиля.");
            blip(false);
            return;
        }
        const hits = gateValue(left, right).filter((value, index) => value === round.table[index][2]);
        if (hits.length === round.table.length) {
            solvedRound();
            return;
        }
        failedRound(`Совпало строк: ${hits.length} из ${round.table.length}. Попробуйте другую пару.`);
    }

    function checkBoard() {
        if (round.mode === "steps") {
            if (round.chosen.length < 3) {
                feedback("bad", "Нужно ровно три блока.");
                blip(false);
                return;
            }
            if (stepValue() === round.target) {
                solvedRound();
                return;
            }
            failedRound(`Получилось ${stepValue()}, а нужно ${round.target}.`);
            return;
        }
        if (round.mode === "gates") {
            checkGates();
            return;
        }
        if (round.mode === "bug") {
            if (round.choice === null) {
                feedback("bad", "Выберите один из вариантов.");
                blip(false);
                return;
            }
            const puzzle = round.puzzle;
            const options = document.querySelectorAll(".lg-option");
            options.forEach((node, index) => {
                if (index === puzzle.answer) {
                    node.classList.add("is-right");
                }
            });
            if (round.choice === puzzle.answer) {
                solvedRound();
                feedback("ok", `Верно. ${puzzle.why}`);
                return;
            }
            failedRound(`Мимо: ${puzzle.why}`);
        }
    }

    function nextRound() {
        ensureRound(true);
        drawRound();
    }

    function resetRound() {
        if (round.mode === "steps") {
            round.chosen = [];
        }
        if (round.mode === "gates") {
            round.picked = [null, null];
        }
        if (round.mode === "bug") {
            round.choice = null;
        }
        if (round.mode === "plan") {
            round.queue = [];
        }
        drawRound();
    }

    function bind() {
        document.querySelectorAll(".lg-tab").forEach((tab) => {
            tab.addEventListener("click", () => {
                document.querySelectorAll(".lg-tab").forEach((other) => {
                    other.classList.remove("is-active");
                    other.setAttribute("aria-pressed", "false");
                });
                tab.classList.add("is-active");
                tab.setAttribute("aria-pressed", "true");
                setMode(tab.dataset.mode);
            });
        });
        document.getElementById("check").addEventListener("click", checkBoard);
        document.getElementById("next").addEventListener("click", nextRound);
        document.getElementById("reset").addEventListener("click", resetRound);
        document.getElementById("sound").addEventListener("click", (event) => {
            state.muted = !state.muted;
            saveState();
            event.currentTarget.setAttribute("aria-pressed", state.muted ? "false" : "true");
            if (!state.muted) {
                blip(true);
            }
        });
    }

    function init() {
        document.getElementById("sound").setAttribute("aria-pressed", state.muted ? "false" : "true");
        bind();
        updateHud();
        setMode("steps");
    }

    init();
})();
