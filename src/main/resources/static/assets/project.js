const resultHost = document.querySelector("[data-result]");
const statusHost = document.querySelector("[data-result-status]");
const bodyHost = document.querySelector("[data-result-body]");
const timerHost = document.querySelector("[data-result-timer]");

const formatJson = (value) => JSON.stringify(value, null, 2);

const showResult = (status, payload, elapsed) => {
    if (!resultHost) {
        return;
    }
    resultHost.hidden = false;
    const ok = status >= 200 && status < 300;
    statusHost.innerHTML = `<span class="badge ${ok ? "badge--ok" : "badge--err"}">${status || "network"}</span>`
        + `<span>${ok ? "успех" : "ошибка"}</span>`;
    timerHost.textContent = `${elapsed} мс`;
    bodyHost.textContent = typeof payload === "string" ? payload : formatJson(payload);
};

const callApi = async (button) => {
    const method = (button.dataset.method || "GET").toUpperCase();
    const url = button.dataset.url;
    const rawBody = button.dataset.body;
    const headers = { Accept: "application/json" };
    if (button.dataset.contentType) {
        headers["Content-Type"] = button.dataset.contentType;
    }
    if (rawBody) {
        headers["Content-Type"] = headers["Content-Type"] || "application/json";
    }
    (button.dataset.headers || "").split(";").filter(Boolean).forEach((pair) => {
        const [name, ...rest] = pair.split("=");
        headers[name] = rest.join("=");
    });

    const previous = button.textContent;
    button.disabled = true;
    button.textContent = "…";
    const started = performance.now();
    try {
        const response = await fetch(url, { method, headers, body: rawBody });
        const elapsed = Math.round(performance.now() - started);
        const text = await response.text();
        let payload = text;
        try {
            payload = text ? JSON.parse(text) : "(пустой ответ)";
        } catch (error) {
            payload = text;
        }
        showResult(response.status, payload, elapsed);
    } catch (error) {
        showResult(0, `Не удалось выполнить запрос: ${error.message}`, Math.round(performance.now() - started));
    } finally {
        button.disabled = false;
        button.textContent = previous;
    }
};

document.querySelectorAll("[data-call]").forEach((button) => {
    button.addEventListener("click", () => callApi(button));
});

const wsLog = document.querySelector("[data-ws-log]");
const wsButtons = document.querySelectorAll("[data-ws-command]");

const logLine = (text, kind = "") => {
    if (!wsLog) {
        return;
    }
    const line = document.createElement("div");
    line.className = `ws-line ${kind}`;
    line.textContent = text;
    wsLog.appendChild(line);
    wsLog.scrollTop = wsLog.scrollHeight;
};

let socket = null;

const openSocket = () => {
    if (!wsLog) {
        return null;
    }
    if (socket && socket.readyState <= WebSocket.OPEN) {
        return socket;
    }
    const scheme = window.location.protocol === "https:" ? "wss" : "ws";
    socket = new WebSocket(`${scheme}://${window.location.host}/ws/state`);
    socket.addEventListener("open", () => {
        logLine("соединение открыто", "ok");
        wsButtons.forEach((button) => { button.disabled = false; });
    });
    socket.addEventListener("message", (event) => {
        const frame = JSON.parse(event.data);
        const head = frame.type === "welcome"
            ? `welcome: состояние ${frame.state}, лимит ${frame.maxPayloadBytes} байт`
            : frame.type === "state"
                ? `state: ${frame.state} → допустимо ${frame.allowed.join(", ")}`
                : `error ${frame.code}: ${frame.message}`;
        logLine(head, frame.type === "error" ? "err" : "ok");
    });
    socket.addEventListener("close", (event) => {
        logLine(`соединение закрыто (${event.code})`, "err");
        wsButtons.forEach((button) => { button.disabled = true; });
    });
    socket.addEventListener("error", () => logLine("ошибка транспорта", "err"));
    logLine("подключаюсь к /ws/state…");
    return socket;
};

const connectButton = document.querySelector("[data-ws-connect]");
if (connectButton) {
    connectButton.addEventListener("click", openSocket);
}

wsButtons.forEach((button) => {
    button.disabled = true;
    button.addEventListener("click", () => {
        const active = openSocket();
        if (!active || active.readyState !== WebSocket.OPEN) {
            logLine("сначала откройте соединение", "err");
            return;
        }
        active.send(button.dataset.wsCommand);
    });
});

const copyButtons = document.querySelectorAll("[data-copy]");
copyButtons.forEach((button) => {
    button.addEventListener("click", async () => {
        const target = document.querySelector(button.dataset.copy);
        if (!target) {
            return;
        }
        try {
            await navigator.clipboard.writeText(target.textContent);
            const previous = button.textContent;
            button.textContent = "Скопировано";
            window.setTimeout(() => { button.textContent = previous; }, 1400);
        } catch (error) {
            button.textContent = "Не удалось";
        }
    });
});
