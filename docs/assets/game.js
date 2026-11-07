(() => {
    const canvas = document.getElementById("game");
    const ctx = canvas.getContext("2d");

    const VIEW_W = 960;
    const VIEW_H = 520;
    const GROUND_Y = 470;
    const GROUND_H = 52;
    const STEP = 720 + 60;

    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = VIEW_W * dpr;
    canvas.height = VIEW_H * dpr;
    ctx.scale(dpr, dpr);

    const LEVELS = [
        { id: 1, key: "p1", title: "Контракт", project: "REST API по контракту", accent: "#2f7ae5", hint: "DTO, валидация, 201 Created", orbs: 6, enemySpeed: 1 },
        { id: 2, key: "p2", title: "N+1", project: "JPA и Hibernate", accent: "#7c5cf0", hint: "fetch join вместо ленивых связей", orbs: 7, enemySpeed: 1 },
        { id: 3, key: "p3", title: "Границы", project: "Транзакции", accent: "#e0578f", hint: "откат не должен уносить аудит", orbs: 7, enemySpeed: 2 },
        { id: 4, key: "p4", title: "Гонка", project: "Многопоточность", accent: "#e79a1e", hint: "Atomic спасает счётчик от потерь", orbs: 8, enemySpeed: 2 },
        { id: 5, key: "p5", title: "Очередь", project: "Брокер и Outbox", accent: "#22b573", hint: "не дай событиям уйти в DLQ", orbs: 8, enemySpeed: 3 },
        { id: 6, key: "p6", title: "Схема", project: "XML и XSD", accent: "#1aa6b8", hint: "DTD и внешние сущности закрыты", orbs: 9, enemySpeed: 3 },
        { id: 7, key: "p7", title: "Подпись", project: "КриптоПро и JCA", accent: "#9a5cf0", hint: "сертификат проверяем по цепочке", orbs: 9, enemySpeed: 4 },
        { id: 8, key: "p8", title: "Состояние", project: "WebSocket и машина состояний", accent: "#e2554f", hint: "до DONE только легальными переходами", orbs: 10, enemySpeed: 4 },
    ];

    const hudLevel = document.getElementById("hud-level");
    const hudOrbs = document.getElementById("hud-orbs");
    const hudScore = document.getElementById("hud-score");
    const hudTime = document.getElementById("hud-time");
    const levelSubtitle = document.getElementById("level-subtitle");
    const overlay = document.getElementById("overlay");
    const overlayTitle = document.getElementById("overlay-title");
    const overlayText = document.getElementById("overlay-text");
    const overlayEyebrow = document.getElementById("overlay-eyebrow");
    const overlayPrimary = document.getElementById("overlay-primary");
    const overlaySecondary = document.getElementById("overlay-secondary");
    const board = document.getElementById("board");
    const refreshBoard = document.getElementById("refresh-board");

    const STORAGE_KEY = "eight-projects-game";
    const LOCAL_BOARD_KEY = "eight-projects-leaderboard";

    const state = {
        levelIndex: 0,
        level: null,
        player: null,
        keys: { left: false, right: false, jump: false },
        camera: 0,
        time: 0,
        levelTime: 0,
        collected: 0,
        score: 0,
        running: false,
        paused: false,
        finished: false,
        particles: [],
        banner: 0,
        flash: 0,
        shake: 0,
        lastTs: 0,
        jumpHeld: false,
    };

    const store = {
        read() {
            try {
                return JSON.parse(window.localStorage.getItem(STORAGE_KEY) || "{}");
            } catch (error) {
                return {};
            }
        },
        write(patch) {
            try {
                window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...this.read(), ...patch }));
            } catch (error) {
                /* приватный режим — просто не сохраняем */
            }
        },
    };

    const mulberry32 = (seed) => {
        let a = seed >>> 0;
        return () => {
            a = (a + 0x6d2b79f5) >>> 0;
            let t = a;
            t = Math.imul(t ^ (t >>> 15), t | 1);
            t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
            return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
        };
    };

    const clamp = (value, min, max) => Math.min(Math.max(value, min), max);

    const buildLevel = (def) => {
        const rnd = mulberry32(def.id * 7919 + 41);
        const width = 1520 + def.id * 70;
        const solids = [];
        const platforms = [];
        const orbs = [];
        const enemies = [];

        let x = 0;
        while (x < width - 240) {
            const segment = 210 + Math.floor(rnd() * 130);
            solids.push({ x, y: GROUND_Y, w: segment, h: GROUND_H, kind: "ground" });
            x += segment;
            if (x < width - 300) {
                const gap = clamp(72 + rnd() * (24 + def.id * 4), 72, 112);
                x += gap;
            }
        }
        solids.push({ x, y: GROUND_Y, w: width - x, h: GROUND_H, kind: "ground" });

        const platformCount = 3 + Math.floor(def.id / 2);
        for (let index = 0; index < platformCount; index += 1) {
            const px = 240 + rnd() * (width - 520);
            const py = 326 + Math.floor(rnd() * 4) * 18;
            const pw = 96 + Math.floor(rnd() * 60);
            platforms.push({ x: px, y: py, w: pw, h: 16, kind: "platform" });
            solids.push({ x: px, y: py, w: pw, h: 16, kind: "platform" });
        }

        const topAt = (px) => {
            let best = null;
            solids.forEach((solid) => {
                if (px >= solid.x && px <= solid.x + solid.w) {
                    if (best === null || solid.y < best) {
                        best = solid.y;
                    }
                }
            });
            return best;
        };

        for (let index = 0; index < def.orbs; index += 1) {
            const px = 130 + (index + 0.5) * ((width - 300) / def.orbs);
            const top = topAt(px)
                ?? topAt(px - 40)
                ?? topAt(px + 40)
                ?? GROUND_Y;
            orbs.push({ x: px, y: top - 44, r: 9, taken: false, phase: rnd() * Math.PI * 2 });
        }

        const enemyCount = 1 + Math.floor(def.id / 2);
        for (let index = 0; index < enemyCount; index += 1) {
            const candidates = solids.filter((solid) => solid.kind === "ground" && solid.w > 170);
            const base = candidates[Math.floor(rnd() * candidates.length)] || solids[0];
            const minX = base.x + 24;
            const maxX = base.x + base.w - 54;
            enemies.push({
                x: minX + rnd() * Math.max(10, maxX - minX),
                y: GROUND_Y - 26,
                w: 26,
                h: 26,
                minX,
                maxX,
                dir: rnd() > 0.5 ? 1 : -1,
                speed: 0.45 + def.enemySpeed * 0.22,
                phase: rnd() * Math.PI * 2,
            });
        }

        return {
            def,
            width,
            solids,
            platforms,
            orbs,
            enemies,
            goal: { x: width - 128, y: GROUND_Y - 96, w: 62, h: 96 },
            start: { x: 54, y: GROUND_Y - 44 },
            required: Math.ceil(def.orbs * 0.6),
            stars: Array.from({ length: 70 }, () => ({
                x: rnd() * width,
                y: 60 + rnd() * 300,
                r: 0.6 + rnd() * 1.8,
                a: 0.15 + rnd() * 0.5,
                depth: 0.2 + rnd() * 0.6,
            })),
        };
    };

    const resetPlayer = (level) => ({
        x: level.start.x,
        y: level.start.y,
        w: 22,
        h: 30,
        vx: 0,
        vy: 0,
        onGround: false,
        coyote: 0,
        buffer: 0,
        face: 1,
        walk: 0,
        invuln: 0,
    });

    const startLevel = (index, keepScore = true) => {
        state.levelIndex = clamp(index, 0, LEVELS.length - 1);
        const def = LEVELS[state.levelIndex];
        state.level = buildLevel(def);
        state.player = resetPlayer(state.level);
        state.collected = 0;
        state.levelTime = 0;
        state.camera = 0;
        state.particles = [];
        state.banner = 2.2;
        state.flash = 0;
        state.shake = 0;
        state.running = true;
        state.paused = false;
        state.finished = false;
        if (!keepScore) {
            state.score = 0;
        }
        document.body.style.setProperty("--level-accent", def.accent);
        overlay.hidden = true;
        levelSubtitle.textContent = `Уровень ${def.id} · ${def.project}`;
        hudLevel.textContent = `${def.id} / 8`;
        updateHud();
    };

    const updateHud = () => {
        const level = state.level;
        if (!level) {
            return;
        }
        hudOrbs.textContent = `${state.collected} / ${level.def.orbs}`;
        hudScore.textContent = String(state.score);
        const minutes = Math.floor(state.levelTime / 60);
        const seconds = Math.floor(state.levelTime % 60);
        hudTime.textContent = `${minutes}:${String(seconds).padStart(2, "0")}`;
    };

    const overlap = (a, b) => a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y;

    const burst = (x, y, color, count = 12, power = 3.2) => {
        for (let index = 0; index < count; index += 1) {
            const angle = (Math.PI * 2 * index) / count + Math.random() * 0.4;
            const speed = power * (0.5 + Math.random());
            state.particles.push({
                x,
                y,
                vx: Math.cos(angle) * speed,
                vy: Math.sin(angle) * speed - 1,
                life: 1,
                color,
                size: 1.6 + Math.random() * 2.4,
            });
        }
    };

    const respawn = (penalty = 60) => {
        const level = state.level;
        state.player = resetPlayer(level);
        state.player.invuln = 90;
        state.score = Math.max(0, state.score - penalty);
        state.flash = 0.55;
        state.shake = 10;
        burst(level.start.x + 11, level.start.y, "#f87171", 16, 3);
        updateHud();
    };

    const collectOrb = (orb) => {
        orb.taken = true;
        state.collected += 1;
        state.score += 40;
        burst(orb.x, orb.y, "#7dd3fc", 10, 2.6);
        updateHud();
    };

    const hitEnemy = () => {
        burst(state.player.x + 11, state.player.y + 15, "#f87171", 18, 3.6);
        respawn(60);
    };

    const update = (dt) => {
        const level = state.level;
        const player = state.player;
        if (!level || !player || state.paused) {
            return;
        }
        state.levelTime += dt;
        const frames = dt * 60;
        const speed = 4.15;

        if (state.keys.left) {
            player.vx = -speed;
            player.face = -1;
        } else if (state.keys.right) {
            player.vx = speed;
            player.face = 1;
        } else {
            player.vx *= 0.7;
            if (Math.abs(player.vx) < 0.1) {
                player.vx = 0;
            }
        }

        player.coyote = player.onGround ? 8 : Math.max(0, player.coyote - frames);
        player.buffer = state.keys.jump ? 8 : Math.max(0, player.buffer - frames);
        if (player.buffer > 0 && player.coyote > 0) {
            player.vy = -12.6;
            player.onGround = false;
            player.coyote = 0;
            player.buffer = 0;
            burst(player.x + 11, player.y + player.h, "#93a4c0", 6, 1.6);
        }

        player.vy = Math.min(player.vy + 0.72 * frames, 16);

        player.x += player.vx * frames;
        player.x = clamp(player.x, 0, level.width - player.w);
        level.solids.forEach((solid) => {
            if (overlap(player, solid)) {
                if (player.vx > 0) {
                    player.x = solid.x - player.w;
                } else if (player.vx < 0) {
                    player.x = solid.x + solid.w;
                }
                player.vx = 0;
            }
        });

        player.y += player.vy * frames;
        player.onGround = false;
        level.solids.forEach((solid) => {
            if (overlap(player, solid)) {
                if (player.vy > 0) {
                    player.y = solid.y - player.h;
                    player.vy = 0;
                    player.onGround = true;
                } else if (player.vy < 0) {
                    player.y = solid.y + solid.h;
                    player.vy = 0;
                }
            }
        });

        player.walk += Math.abs(player.vx) * frames * 0.16;
        player.invuln = Math.max(0, player.invuln - frames / 60 * 60);
        if (player.y > VIEW_H + 160) {
            respawn(40);
        }

        level.orbs.forEach((orb) => {
            if (orb.taken) {
                return;
            }
            orb.phase += dt * 2.6;
            const dx = player.x + player.w / 2 - orb.x;
            const dy = player.y + player.h / 2 - orb.y;
            if (Math.hypot(dx, dy) < 24) {
                collectOrb(orb);
            }
        });

        level.enemies.forEach((enemy) => {
            enemy.phase += dt * 3;
            enemy.x += enemy.dir * enemy.speed * frames;
            if (enemy.x <= enemy.minX) {
                enemy.x = enemy.minX;
                enemy.dir = 1;
            }
            if (enemy.x >= enemy.maxX) {
                enemy.x = enemy.maxX;
                enemy.dir = -1;
            }
            if (player.invuln <= 0 && overlap(player, enemy)) {
                hitEnemy();
            }
        });

        state.particles.forEach((particle) => {
            particle.x += particle.vx * frames;
            particle.y += particle.vy * frames;
            particle.vy += 0.16 * frames;
            particle.life -= dt * 1.5;
        });
        state.particles = state.particles.filter((particle) => particle.life > 0);

        const targetCam = clamp(player.x + player.w / 2 - VIEW_W / 2, 0, level.width - VIEW_W);
        state.camera += (targetCam - state.camera) * Math.min(1, dt * 6);

        state.banner = Math.max(0, state.banner - dt);
        state.flash = Math.max(0, state.flash - dt * 1.6);
        state.shake = Math.max(0, state.shake - dt * 60);

        const ready = state.collected >= level.required;
        if (ready && overlap(player, level.goal)) {
            completeLevel();
        }

        updateHud();
    };

    const roundRect = (x, y, w, h, radius) => {
        const r = Math.min(radius, w / 2, h / 2);
        ctx.beginPath();
        ctx.moveTo(x + r, y);
        ctx.arcTo(x + w, y, x + w, y + h, r);
        ctx.arcTo(x + w, y + h, x, y + h, r);
        ctx.arcTo(x, y + h, x, y, r);
        ctx.arcTo(x, y, x + w, y, r);
        ctx.closePath();
    };

    const INK = "#16202c";
    const SKY_TOP = "#e6f0ff";
    const SKY_BOTTOM = "#fbfdff";

    const tint = (hex, amount) => {
        const r = parseInt(hex.slice(1, 3), 16);
        const g = parseInt(hex.slice(3, 5), 16);
        const b = parseInt(hex.slice(5, 7), 16);
        const lift = (value) => Math.round(value + (255 - value) * amount);
        return `rgb(${lift(r)}, ${lift(g)}, ${lift(b)})`;
    };

    const hardShadow = (x, y, w, h, radius) => {
        ctx.fillStyle = "rgba(22, 32, 44, 0.14)";
        roundRect(x + 4, y + 5, w, h, radius);
        ctx.fill();
    };

    const outline = (lineWidth = 3) => {
        ctx.strokeStyle = INK;
        ctx.lineWidth = lineWidth;
        ctx.lineJoin = "round";
        ctx.lineCap = "round";
        ctx.stroke();
    };

    const draw = () => {
        const level = state.level;
        const player = state.player;
        const accent = level ? level.def.accent : "#38bdf8";

        const gradient = ctx.createLinearGradient(0, 0, 0, VIEW_H);
        gradient.addColorStop(0, SKY_TOP);
        gradient.addColorStop(0.7, "#f3f8ff");
        gradient.addColorStop(1, SKY_BOTTOM);
        ctx.fillStyle = gradient;
        ctx.fillRect(0, 0, VIEW_W, VIEW_H);

        const wash = ctx.createRadialGradient(VIEW_W * 0.78, 40, 30, VIEW_W * 0.78, 40, 420);
        wash.addColorStop(0, tint(accent, 0.55));
        wash.addColorStop(1, "rgba(255, 255, 255, 0)");
        ctx.fillStyle = wash;
        ctx.fillRect(0, 0, VIEW_W, VIEW_H);

        if (!level) {
            return;
        }

        const shakeX = state.shake > 0 ? (Math.random() - 0.5) * state.shake : 0;
        const shakeY = state.shake > 0 ? (Math.random() - 0.5) * state.shake : 0;
        ctx.save();
        ctx.translate(shakeX, shakeY);

        level.stars.forEach((star) => {
            const sx = star.x - state.camera * star.depth;
            if (sx > -40 && sx < VIEW_W + 40) {
                ctx.globalAlpha = Math.min(0.55, star.a);
                ctx.fillStyle = tint(accent, 0.35);
                ctx.beginPath();
                ctx.arc(sx, star.y, star.r * 2.4, 0, Math.PI * 2);
                ctx.fill();
            }
        });
        ctx.globalAlpha = 1;

        ctx.save();
        ctx.translate(-state.camera, 0);

        level.solids.forEach((solid) => {
            if (solid.x + solid.w < state.camera - 40 || solid.x > state.camera + VIEW_W + 40) {
                return;
            }
            if (solid.kind === "ground") {
                hardShadow(solid.x, solid.y, solid.w, solid.h, 10);
                ctx.fillStyle = tint(accent, 0.72);
                roundRect(solid.x, solid.y, solid.w, solid.h, 10);
                ctx.fill();
                ctx.strokeStyle = INK;
                ctx.lineWidth = 3;
                ctx.lineJoin = "round";
                ctx.stroke();
                ctx.save();
                roundRect(solid.x, solid.y, solid.w, solid.h, 10);
                ctx.clip();
                ctx.fillStyle = accent;
                ctx.fillRect(solid.x, solid.y, solid.w, 12);
                ctx.strokeStyle = "rgba(22, 32, 44, 0.18)";
                ctx.lineWidth = 2;
                for (let gx = solid.x + 16; gx < solid.x + solid.w; gx += 28) {
                    ctx.beginPath();
                    ctx.moveTo(gx, solid.y + 12);
                    ctx.lineTo(gx - 10, solid.y + solid.h);
                    ctx.stroke();
                }
                ctx.restore();
                ctx.strokeStyle = INK;
                ctx.lineWidth = 3;
                roundRect(solid.x, solid.y, solid.w, solid.h, 10);
                ctx.stroke();
            } else {
                hardShadow(solid.x, solid.y, solid.w, solid.h, 8);
                ctx.fillStyle = tint(accent, 0.5);
                roundRect(solid.x, solid.y, solid.w, solid.h, 8);
                ctx.fill();
                ctx.fillStyle = "#ffffff";
                roundRect(solid.x + 5, solid.y + 4, solid.w - 10, 4, 2);
                ctx.fill();
                outline(3);
            }
        });

        const goal = level.goal;
        const open = state.collected >= level.required;
        const pulse = 1 + Math.sin(state.time * 4) * 0.05;
        ctx.save();
        ctx.translate(goal.x + goal.w / 2, goal.y + goal.h / 2);
        ctx.scale(pulse, pulse);
        ctx.fillStyle = open ? tint(accent, 0.55) : "rgba(22, 32, 44, 0.06)";
        ctx.beginPath();
        ctx.ellipse(0, 0, 74, 82, 0, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();

        ctx.save();
        ctx.translate(goal.x, goal.y);
        hardShadow(0, 0, goal.w, goal.h, 12);
        ctx.fillStyle = open ? tint(accent, 0.45) : "#e7edf6";
        roundRect(0, 0, goal.w, goal.h, 12);
        ctx.fill();
        outline(3);
        ctx.fillStyle = "#ffffff";
        roundRect(9, 12, goal.w - 18, goal.h - 24, 7);
        ctx.fill();
        outline(2);
        ctx.fillStyle = open ? accent : "rgba(22, 32, 44, 0.24)";
        ctx.beginPath();
        ctx.arc(goal.w / 2, goal.h / 2, open ? 12 : 8, 0, Math.PI * 2);
        ctx.fill();
        outline(2.5);
        ctx.fillStyle = open ? INK : "rgba(22, 32, 44, 0.55)";
        ctx.font = "700 10px 'Cascadia Code', monospace";
        ctx.textAlign = "center";
        ctx.fillText(open ? "DONE" : "LOCK", goal.w / 2, goal.h + 18);
        ctx.restore();

        level.orbs.forEach((orb) => {
            if (orb.taken) {
                return;
            }
            const bob = Math.sin(orb.phase) * 4;
            ctx.save();
            ctx.translate(orb.x, orb.y + bob);
            ctx.fillStyle = "rgba(22, 32, 44, 0.10)";
            ctx.beginPath();
            ctx.ellipse(2, 16, 11, 4, 0, 0, Math.PI * 2);
            ctx.fill();
            ctx.fillStyle = "#ffd23f";
            ctx.beginPath();
            ctx.arc(0, 0, 11, 0, Math.PI * 2);
            ctx.fill();
            outline(2.5);
            ctx.fillStyle = "#fff3bf";
            ctx.beginPath();
            ctx.arc(-3, -3.5, 3.4, 0, Math.PI * 2);
            ctx.fill();
            ctx.strokeStyle = "rgba(22, 32, 44, 0.5)";
            ctx.lineWidth = 2;
            ctx.beginPath();
            ctx.arc(0, 0, 5.4, orb.phase, orb.phase + Math.PI * 1.2);
            ctx.stroke();
            ctx.restore();
        });

        level.enemies.forEach((enemy) => {
            const hover = Math.sin(enemy.phase) * 3;
            ctx.save();
            ctx.translate(enemy.x, enemy.y + hover);
            ctx.fillStyle = "rgba(22, 32, 44, 0.10)";
            ctx.beginPath();
            ctx.ellipse(enemy.w / 2, enemy.h + 6, enemy.w / 2.2, 3.4, 0, 0, Math.PI * 2);
            ctx.fill();
            ctx.fillStyle = "#ff7a7a";
            roundRect(0, 2, enemy.w, enemy.h - 4, 9);
            ctx.fill();
            outline(2.5);
            ctx.strokeStyle = INK;
            ctx.lineWidth = 2.5;
            ctx.beginPath();
            ctx.moveTo(enemy.w / 2 - 5, 3);
            ctx.lineTo(enemy.w / 2 - 9, -7);
            ctx.moveTo(enemy.w / 2 + 5, 3);
            ctx.lineTo(enemy.w / 2 + 9, -7);
            ctx.stroke();
            ctx.fillStyle = INK;
            ctx.beginPath();
            ctx.arc(enemy.w / 2 - 9, -7, 2, 0, Math.PI * 2);
            ctx.arc(enemy.w / 2 + 9, -7, 2, 0, Math.PI * 2);
            ctx.fill();
            ctx.fillStyle = "#ffffff";
            [-6, 6].forEach((offset) => {
                ctx.beginPath();
                ctx.arc(enemy.w / 2 + offset, enemy.h / 2, 4, 0, Math.PI * 2);
                ctx.fill();
            });
            outline(2);
            ctx.fillStyle = INK;
            [-6, 6].forEach((offset) => {
                ctx.beginPath();
                ctx.arc(enemy.w / 2 + offset + enemy.dir * 1.4, enemy.h / 2, 1.7, 0, Math.PI * 2);
                ctx.fill();
            });
            ctx.restore();
        });

        if (player) {
            ctx.save();
            const blink = player.invuln > 0 && Math.floor(player.invuln / 6) % 2 === 0;
            ctx.globalAlpha = blink ? 0.45 : 1;
            const bob = player.onGround && Math.abs(player.vx) < 0.2 ? Math.sin(state.time * 3) * 1.2 : 0;
            ctx.translate(player.x, player.y + bob);
            ctx.fillStyle = "rgba(22, 32, 44, 0.12)";
            ctx.beginPath();
            ctx.ellipse(player.w / 2, player.h + 5, player.w / 1.7, 3.6, 0, 0, Math.PI * 2);
            ctx.fill();
            const legSwing = Math.sin(player.walk) * 3;
            ctx.fillStyle = "#ffffff";
            ctx.fillRect(2, player.h - 4 + Math.max(0, legSwing), 7, 6);
            ctx.fillRect(player.w - 9, player.h - 4 + Math.max(0, -legSwing), 7, 6);
            outline(2.5);
            ctx.fillStyle = accent;
            roundRect(0, 0, player.w, player.h - 3, 7);
            ctx.fill();
            outline(3);
            ctx.fillStyle = "#ffffff";
            roundRect(3.5, 6, player.w - 7, 10, 4);
            ctx.fill();
            outline(2);
            ctx.fillStyle = INK;
            const eyeShift = player.face > 0 ? 2 : 0;
            ctx.beginPath();
            ctx.arc(7 + eyeShift, 11, 1.9, 0, Math.PI * 2);
            ctx.fill();
            ctx.beginPath();
            ctx.arc(14 + eyeShift, 11, 1.9, 0, Math.PI * 2);
            ctx.fill();
            ctx.restore();
        }

        state.particles.forEach((particle) => {
            ctx.globalAlpha = Math.max(0, particle.life);
            ctx.fillStyle = particle.color;
            ctx.beginPath();
            ctx.arc(particle.x, particle.y, particle.size * particle.life, 0, Math.PI * 2);
            ctx.fill();
        });
        ctx.globalAlpha = 1;
        ctx.restore();

        hardShadow(18, 16, 320, 58, 12);
        ctx.fillStyle = "rgba(255, 255, 255, 0.94)";
        roundRect(18, 16, 320, 58, 12);
        ctx.fill();
        outline(3);
        ctx.fillStyle = accent;
        roundRect(28, 30, 6, 30, 3);
        ctx.fill();
        outline(2.5);
        ctx.fillStyle = INK;
        ctx.font = "700 14px Inter, 'Segoe UI', sans-serif";
        ctx.textAlign = "left";
        ctx.fillText(`Уровень ${level.def.id} · ${level.def.title}`, 44, 40);
        ctx.fillStyle = "rgba(22, 32, 44, 0.62)";
        ctx.font = "12px 'Cascadia Code', monospace";
        ctx.fillText(level.def.hint, 44, 60);

        const progress = Math.min(1, (state.camera + VIEW_W / 2) / level.width);
        ctx.fillStyle = "#ffffff";
        roundRect(VIEW_W - 238, 28, 214, 12, 6);
        ctx.fill();
        outline(2.5);
        if (progress > 0.01) {
            ctx.fillStyle = accent;
            roundRect(VIEW_W - 235, 31, 208 * progress, 6, 3);
            ctx.fill();
        }

        if (state.banner > 0) {
            const alpha = Math.min(1, state.banner);
            ctx.globalAlpha = alpha;
            hardShadow(VIEW_W / 2 - 230, 300, 460, 74, 16);
            ctx.fillStyle = "rgba(255, 255, 255, 0.96)";
            roundRect(VIEW_W / 2 - 230, 300, 460, 74, 16);
            ctx.fill();
            outline(3);
            ctx.fillStyle = tint(accent, 0.35);
            roundRect(VIEW_W / 2 - 214, 316, 6, 42, 3);
            ctx.fill();
            outline(2.5);
            ctx.fillStyle = INK;
            ctx.font = "700 22px Inter, 'Segoe UI', sans-serif";
            ctx.textAlign = "center";
            ctx.fillText(level.def.project, VIEW_W / 2, 334);
            ctx.fillStyle = "rgba(22, 32, 44, 0.62)";
            ctx.font = "13px 'Cascadia Code', monospace";
            ctx.fillText(`соберите ${level.required} из ${level.def.orbs} навыков`, VIEW_W / 2, 358);
            ctx.globalAlpha = 1;
        }

        if (state.paused) {
            ctx.fillStyle = "rgba(240, 245, 252, 0.82)";
            ctx.fillRect(0, 0, VIEW_W, VIEW_H);
            ctx.fillStyle = INK;
            ctx.font = "700 26px Inter, 'Segoe UI', sans-serif";
            ctx.textAlign = "center";
            ctx.fillText("Пауза", VIEW_W / 2, VIEW_H / 2);
            ctx.font = "13px 'Cascadia Code', monospace";
            ctx.fillStyle = "rgba(22, 32, 44, 0.62)";
            ctx.fillText("P — продолжить, R — начать уровень заново", VIEW_W / 2, VIEW_H / 2 + 28);
        }

        if (state.flash > 0) {
            ctx.fillStyle = `rgba(255, 122, 122, ${state.flash * 0.38})`;
            ctx.fillRect(0, 0, VIEW_W, VIEW_H);
        }

        ctx.restore();
    };

    const levelScore = () => {
        const base = 800 + state.level.def.orbs * 120;
        const timePenalty = Math.floor(state.levelTime * 4);
        return Math.max(200, base - timePenalty);
    };

    const completeLevel = () => {
        state.running = false;
        state.score += levelScore();
        updateHud();
        burst(state.level.goal.x + 31, state.level.goal.y + 48, "#34d399", 26, 4.5);

        const isLast = state.levelIndex === LEVELS.length - 1;
        if (!isLast) {
            const progress = store.read();
            store.write({ unlocked: Math.max(progress.unlocked || 1, state.levelIndex + 2), best: Math.max(progress.best || 0, state.score) });
            showOverlay({
                eyebrow: `уровень ${state.level.def.id} пройден`,
                title: `Дальше: ${LEVELS[state.levelIndex + 1].project}`,
                text: `Собрано ${state.collected} из ${state.level.def.orbs} навыков, очки: ${state.score}. `
                    + "Следующий уровень открыт — или повторите текущий, чтобы улучшить результат.",
                primary: "Следующий уровень",
                secondary: "Пройти заново",
                onPrimary: () => startLevel(state.levelIndex + 1),
                onSecondary: () => {
                    state.score -= levelScore();
                    startLevel(state.levelIndex);
                },
            });
        } else {
            finishGame();
        }
    };

    const finishGame = () => {
        state.finished = true;
        store.write({ unlocked: 8, best: Math.max(store.read().best || 0, state.score), lastScore: state.score });
        const form = document.createElement("div");
        form.className = "name-form";
        form.innerHTML = '<input type="text" maxlength="20" minlength="2" placeholder="ваше имя" '
            + 'aria-label="Имя игрока" autocomplete="off">'
            + '<button class="btn btn--primary" type="button">Сохранить результат</button>';
        const input = form.querySelector("input");
        const button = form.querySelector("button");
        input.value = store.read().player || "";
        button.addEventListener("click", async () => {
            const player = input.value.trim();
            if (player.length < 2) {
                input.focus();
                return;
            }
            button.disabled = true;
            button.textContent = "Сохраняю…";
            const target = await saveScore({
                player,
                level: LEVELS.length,
                score: state.score,
                seconds: Math.round(state.time),
            });
            store.write({ player });
            button.textContent = target === "server" ? "Сохранено" : "Сохранено локально";
            loadLeaderboard();
            window.setTimeout(() => {
                button.disabled = false;
                button.textContent = "Сохранить результат";
            }, 1600);
        });

        showOverlay({
            eyebrow: "все восемь уровней пройдены",
            title: `Финальный счёт: ${state.score}`,
            text: "Восемь проектов позади: контракт, N+1, границы транзакций, гонки, очередь, схема, подпись "
                + "и машина состояний. Оставьте имя — результат попадёт в таблицу лидеров.",
            primary: "Пройти заново",
            secondary: "Сохранить и вернуться",
            onPrimary: () => {
                state.score = 0;
                state.time = 0;
                startLevel(0, false);
            },
            onSecondary: () => {
                loadLeaderboard();
                document.getElementById("board").scrollIntoView({ behavior: "smooth", block: "center" });
            },
            extra: form,
        });
    };

    const showOverlay = ({ eyebrow, title, text, primary, secondary, onPrimary, onSecondary, extra }) => {
        overlayEyebrow.textContent = eyebrow;
        overlayTitle.textContent = title;
        overlayText.textContent = text;
        overlayPrimary.textContent = primary;
        overlaySecondary.textContent = secondary;
        overlaySecondary.style.display = onSecondary ? "" : "none";
        const previousExtra = overlay.querySelector(".name-form");
        if (previousExtra) {
            previousExtra.remove();
        }
        if (extra) {
            overlayText.after(extra);
        }
        overlayPrimary.onclick = () => {
            overlay.hidden = true;
            onPrimary();
        };
        overlaySecondary.onclick = () => {
            if (onSecondary) {
                onSecondary();
            }
        };
        overlay.hidden = false;
    };

    const loadLeaderboard = async () => {
        if (!board) {
            return;
        }
        const note = document.getElementById("board-note");
        const render = (scores, source) => {
            if (note) {
                note.hidden = source === "server";
                note.textContent = "офлайн-режим: результаты хранятся в этом браузере";
            }
            board.dataset.source = source;
            if (!Array.isArray(scores) || scores.length === 0) {
                board.innerHTML = '<li class="board__empty">Пока пусто — пройдите игру и оставьте результат.</li>';
                return;
            }
            board.innerHTML = scores.map((score, index) => {
                const level = LEVELS[clamp(score.level - 1, 0, LEVELS.length - 1)];
                return `<li class="${index === 0 ? "is-top" : ""}">
                    <span class="board__rank">${index + 1}.</span>
                    <span class="board__player">${escapeHtml(score.player)}</span>
                    <span class="board__meta board__meta--level">ур. ${score.level} · ${escapeHtml(level.title)}</span>
                    <span class="board__meta">${score.score} очк · ${formatTime(score.seconds)}</span>
                </li>`;
            }).join("");
        };
        try {
            if (window.EIGHT_OFFLINE) {
                throw new Error("offline build");
            }
            const response = await fetch("/api/game/leaderboard?limit=10");
            if (!response.ok) {
                throw new Error("HTTP " + response.status);
            }
            render(await response.json(), "server");
        } catch (error) {
            render(readLocalBoard(), "local");
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
            /* приватный режим — просто не сохраняем */
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
                body: JSON.stringify(entry),
            });
            if (!response.ok) {
                throw new Error("HTTP " + response.status);
            }
            return "server";
        } catch (error) {
            writeLocalScore({ ...entry, createdAt: new Date().toISOString() });
            return "local";
        }
    };

    const escapeHtml = (value) => String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");

    const formatTime = (seconds) => {
        const safe = Math.max(0, Math.round(seconds || 0));
        return `${Math.floor(safe / 60)}:${String(safe % 60).padStart(2, "0")}`;
    };

    const loop = (timestamp) => {
        const dt = state.lastTs ? Math.min(0.05, (timestamp - state.lastTs) / 1000) : 0;
        state.lastTs = timestamp;
        state.time += dt;
        if (state.running && !state.paused) {
            update(dt);
        }
        draw();
        window.requestAnimationFrame(loop);
    };

    const keyMap = {
        ArrowLeft: "left",
        KeyA: "left",
        ArrowRight: "right",
        KeyD: "right",
        Space: "jump",
        ArrowUp: "jump",
        KeyW: "jump",
    };

    window.addEventListener("keydown", (event) => {
        if (event.target instanceof HTMLInputElement) {
            return;
        }
        const action = keyMap[event.code];
        if (action) {
            event.preventDefault();
            state.keys[action] = true;
        }
        if (event.code === "KeyR" && state.level) {
            startLevel(state.levelIndex);
        }
        if (event.code === "KeyP" && state.running) {
            state.paused = !state.paused;
        }
        if (event.code === "Enter" && overlay.hidden === false) {
            overlayPrimary.click();
        }
    });

    window.addEventListener("keyup", (event) => {
        const action = keyMap[event.code];
        if (action) {
            state.keys[action] = false;
        }
    });

    document.querySelectorAll("[data-touch]").forEach((button) => {
        const action = button.dataset.touch;
        const press = (event) => {
            event.preventDefault();
            state.keys[action === "jump" ? "jump" : action] = true;
        };
        const release = (event) => {
            event.preventDefault();
            state.keys[action === "jump" ? "jump" : action] = false;
        };
        button.addEventListener("pointerdown", press);
        button.addEventListener("pointerup", release);
        button.addEventListener("pointerleave", release);
        button.addEventListener("pointercancel", release);
    });

    if (refreshBoard) {
        refreshBoard.addEventListener("click", loadLeaderboard);
    }

    const boot = () => {
        loadLeaderboard();
        const progress = store.read();
        const unlocked = clamp(progress.unlocked || 1, 1, LEVELS.length);
        overlayTitle.textContent = "Собери восемь проектов";
        overlayText.textContent = "Восемь уровней — восемь тем: REST, JPA, транзакции, многопоточность, брокер, "
            + "XML, подпись и WebSocket. Собирайте навыки, избегайте багов и доходите до портала DONE.";
        overlayPrimary.textContent = "Начать игру";
        overlaySecondary.textContent = unlocked > 1 ? `Продолжить с уровня ${unlocked}` : "С последнего уровня";
        overlaySecondary.style.display = unlocked > 1 ? "" : "none";
        overlayPrimary.onclick = () => {
            state.score = 0;
            state.time = 0;
            startLevel(0, false);
        };
        overlaySecondary.onclick = () => {
            state.score = 0;
            state.time = 0;
            startLevel(unlocked - 1, false);
        };
        state.level = buildLevel(LEVELS[0]);
        state.player = resetPlayer(state.level);
        document.body.style.setProperty("--level-accent", LEVELS[0].accent);
        hudLevel.textContent = "1 / 8";
        updateHud();
        window.requestAnimationFrame(loop);

        if (window.location.hash === "#play") {
            state.score = 0;
            state.time = 0;
            startLevel(clamp(unlocked - 1, 0, LEVELS.length - 1), false);
        }
    };

    boot();
})();
