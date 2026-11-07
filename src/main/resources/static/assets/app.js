(function () {
    'use strict';

    const STATUS_LABELS = {
        NEW: 'новая',
        IN_PROGRESS: 'в работе',
        BLOCKED: 'заблокирована',
        DONE: 'готова',
        CANCELLED: 'отменена'
    };

    const TASKS = {
        status: '',
        page: 0,
        size: 10,
        items: [],
        totalItems: 0,
        totalPages: 0,
        selectedId: null
    };

    const STATE = {
        transitions: {},
        states: [],
        assignees: [],
        signature: null,
        socket: null
    };

    /* ---------- транспорт ---------- */

    class ApiError extends Error {
        constructor(message, code, status) {
            super(message);
            this.name = 'ApiError';
            this.code = code;
            this.status = status;
        }
    }

    async function call(method, url, options = {}) {
        const headers = Object.assign({}, options.headers);
        const init = { method, headers };
        if (options.json !== undefined) {
            headers['Content-Type'] = 'application/json';
            init.body = JSON.stringify(options.json);
        }
        if (options.text !== undefined) {
            headers['Content-Type'] = 'text/plain;charset=UTF-8';
            init.body = options.text;
        }
        const response = await fetch(url, init);
        const contentType = response.headers.get('content-type') || '';
        let payload = null;
        if (response.status !== 204) {
            payload = contentType.includes('json') ? await response.json() : await response.text();
        }
        if (!response.ok) {
            const message = payload && payload.message ? payload.message : 'HTTP ' + response.status;
            throw new ApiError(message, payload && payload.code, response.status);
        }
        return payload;
    }

    function act(handler) {
        return async function (...args) {
            try {
                await handler.apply(null, args);
            } catch (error) {
                toast(error.message || 'Запрос не выполнен', 'err');
            }
        };
    }

    /* ---------- DOM ---------- */

    const $ = function (selector, root) {
        return (root || document).querySelector(selector);
    };

    const $$ = function (selector, root) {
        return Array.from((root || document).querySelectorAll(selector));
    };

    function el(tag, props, ...children) {
        const node = document.createElement(tag);
        Object.entries(props || {}).forEach(function (entry) {
            const key = entry[0];
            const value = entry[1];
            if (key === 'class') {
                node.className = value;
            } else if (key === 'text') {
                node.textContent = value === null || value === undefined ? '' : String(value);
            } else if (key === 'dataset') {
                Object.assign(node.dataset, value);
            } else if (key.startsWith('on') && typeof value === 'function') {
                node.addEventListener(key.slice(2).toLowerCase(), value);
            } else if (value !== null && value !== undefined && value !== false) {
                node.setAttribute(key, value === true ? '' : String(value));
            }
        });
        children.flat(3).forEach(function (child) {
            if (child === null || child === undefined || child === false) {
                return;
            }
            node.append(child instanceof Node ? child : document.createTextNode(String(child)));
        });
        return node;
    }

    function clear(node) {
        node.replaceChildren();
        return node;
    }

    function toast(message, kind) {
        const box = $('#toast');
        const item = el('div', { class: 'toast__item toast__item--' + (kind || 'ok'), text: message });
        box.append(item);
        window.setTimeout(function () {
            item.remove();
        }, 4500);
    }

    /* ---------- форматирование ---------- */

    const dateFormat = new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short', timeStyle: 'short' });
    const timeFormat = new Intl.DateTimeFormat('ru-RU', { timeStyle: 'medium' });

    function fmtDate(value) {
        return value ? dateFormat.format(new Date(value)) : '—';
    }

    function fmtNumber(value, digits) {
        const number = Number(value || 0);
        return number.toLocaleString('ru-RU', {
            minimumFractionDigits: digits || 0,
            maximumFractionDigits: digits || 0
        });
    }

    function fmtBool(value) {
        return value ? 'да' : 'нет';
    }

    function statusPill(status) {
        return el('span', { class: 'status status--' + status, text: STATUS_LABELS[status] || status });
    }

    function metrics(box, items) {
        clear(box);
        items.forEach(function (item) {
            box.append(el('div', { class: 'metric' },
                el('b', { text: item.value }),
                el('span', { text: item.label })));
        });
    }

    function pairs(box, items) {
        clear(box);
        items.forEach(function (item) {
            if (item.value === null || item.value === undefined) {
                return;
            }
            box.append(el('dt', { text: item.label }), el('dd', { text: String(item.value) }));
        });
    }

    function jsonBlock(value) {
        return el('pre', { text: JSON.stringify(value, null, 2) });
    }

    /* ---------- вкладки ---------- */

    const loaded = new Set();

    const loaders = {
        jpa: initJpa,
        tx: initTx,
        conc: initConc,
        bus: initBus,
        xml: initXml,
        crypto: initCrypto,
        ws: initWs,
        game: initGame
    };

    async function activate(name) {
        $$('.rail__item').forEach(function (button) {
            button.setAttribute('aria-selected', String(button.dataset.tab === name));
        });
        $$('.pane').forEach(function (pane) {
            pane.hidden = pane.dataset.pane !== name;
        });
        if (window.location.hash !== '#' + name) {
            window.history.replaceState(null, '', '#' + name);
        }
        if (!loaded.has(name) && loaders[name]) {
            loaded.add(name);
            try {
                await loaders[name]();
            } catch (error) {
                toast(error.message || 'Не удалось загрузить раздел', 'err');
            }
        }
    }

    /* ---------- задачи ---------- */

    function renderTasks() {
        const rows = clear($('#taskRows'));
        if (!TASKS.items.length) {
            rows.append(el('tr', {}, el('td', { colspan: 6, class: 'empty', text: 'Задач с таким фильтром нет' })));
        }
        TASKS.items.forEach(function (item) {
            const row = el('tr', {
                    class: item.id === TASKS.selectedId ? 'is-active' : '',
                    onclick: act(function () { return selectTask(item.id); })
                },
                el('td', { class: 'mono', text: item.id }),
                el('td', {}, el('span', { class: 'cell-title', title: item.title, text: item.title })),
                el('td', {}, statusPill(item.status)),
                el('td', { class: 'mono', text: item.priority }),
                el('td', { text: item.assigneeName || '—' }),
                el('td', { class: 'mono', text: item.notesCount }));
            rows.append(row);
        });
        const from = TASKS.totalItems === 0 ? 0 : TASKS.page * TASKS.size + 1;
        const to = Math.min(TASKS.page * TASKS.size + TASKS.items.length, TASKS.totalItems);
        $('#taskSummary').textContent = 'записей: ' + TASKS.totalItems;
        $('#taskPageInfo').textContent = 'страница ' + (TASKS.page + 1) + ' из ' + Math.max(TASKS.totalPages, 1)
            + ' · показано ' + from + '–' + to;
        $('#taskPrev').disabled = TASKS.page <= 0;
        $('#taskNext').disabled = TASKS.page + 1 >= TASKS.totalPages;
    }

    async function loadTasks() {
        const params = new URLSearchParams({ page: String(TASKS.page), size: String(TASKS.size) });
        if (TASKS.status) {
            params.set('status', TASKS.status);
        }
        const page = await call('GET', '/api/v1/work-items?' + params.toString());
        TASKS.items = page.items || [];
        TASKS.page = page.page;
        TASKS.totalItems = page.totalItems;
        TASKS.totalPages = page.totalPages;
        if (!TASKS.items.some(function (item) { return item.id === TASKS.selectedId; })) {
            TASKS.selectedId = null;
            renderEmptyDetail();
        }
        renderTasks();
    }

    function renderEmptyDetail() {
        clear($('#taskDetail')).append(
            el('p', { class: 'drawer__empty', text: 'Выберите задачу в таблице, чтобы открыть карточку: описание, заметки, смена статуса.' }));
    }

    function assigneeSelect(selectedId) {
        return el('select', { class: 'select', id: 'detailAssignee' },
            STATE.assignees.map(function (assignee) {
                return el('option', {
                    value: assignee.id,
                    selected: assignee.id === selectedId,
                    text: assignee.name
                });
            }));
    }

    function renderDetail(item, notes) {
        const allowed = STATE.transitions[item.status] || [];
        const box = clear($('#taskDetail'));

        box.append(
            el('h3', { class: 'detail-title', text: item.title }),
            el('div', { class: 'detail-meta' },
                statusPill(item.status),
                el('span', { text: 'приоритет ' + item.priority }),
                el('span', { text: item.assigneeName || 'без исполнителя' }),
                el('span', { class: 'mono', text: 'v' + item.version })),
            el('p', { class: 'detail-text', text: item.description || 'Описание не заполнено' }),
            el('div', { class: 'detail-meta' },
                el('span', { text: 'создана ' + fmtDate(item.createdAt) }),
                el('span', { text: 'изменена ' + fmtDate(item.updatedAt) })));

        const transitions = el('div', { class: 'actions' });
        if (allowed.length) {
            allowed.forEach(function (target) {
                transitions.append(el('button', {
                    class: 'btn btn--ghost btn--small',
                    type: 'button',
                    text: '→ ' + STATUS_LABELS[target],
                    onclick: act(function () { return changeStatus(item.id, target); })
                }));
            });
        } else {
            transitions.append(el('span', { class: 'drawer__empty', text: 'Дальше переходов нет' }));
        }
        box.append(el('h4', { text: 'Сменить статус' }), transitions);

        const form = el('form', { class: 'form-grid' },
            el('label', { class: 'field field--wide' }, 'Заголовок',
                el('input', { class: 'input', name: 'title', value: item.title, required: true, minlength: 3, maxlength: 120 })),
            el('label', { class: 'field field--wide' }, 'Описание',
                el('input', { class: 'input', name: 'description', value: item.description || '', maxlength: 2000 })),
            el('label', { class: 'field' }, 'Приоритет',
                el('select', { class: 'select', name: 'priority' },
                    [1, 2, 3, 4, 5].map(function (value) {
                        return el('option', { value: value, selected: value === item.priority, text: value });
                    }))),
            el('label', { class: 'field' }, 'Исполнитель', assigneeSelect(item.assigneeId)),
            el('div', { class: 'actions' },
                el('button', { class: 'btn btn--primary btn--small', type: 'submit', text: 'Сохранить' }),
                el('button', {
                    class: 'btn btn--danger btn--small',
                    type: 'button',
                    text: 'Удалить',
                    onclick: act(function () { return removeTask(item.id); })
                })));
        form.addEventListener('submit', act(async function (event) {
            event.preventDefault();
            const data = new FormData(form);
            await updateTask(item.id, {
                title: data.get('title'),
                description: data.get('description'),
                priority: Number(data.get('priority')),
                assigneeId: Number(data.get('assigneeId'))
            });
        }));
        box.append(el('details', {}, el('summary', { text: 'Редактировать' }), form));

        const list = el('ul', { class: 'note-list' },
            notes.length
                ? notes.map(function (note) {
                    return el('li', {},
                        el('small', { text: note.author + ' · ' + fmtDate(note.createdAt) }),
                        note.content);
                })
                : el('li', { class: 'drawer__empty', text: 'Заметок пока нет' }));
        box.append(el('h4', { text: 'Заметки' }), list);

        const noteForm = el('form', { class: 'form-grid' },
            el('label', { class: 'field field--wide' }, 'Заметка',
                el('input', { class: 'input', name: 'content', required: true, maxlength: 1000, placeholder: 'Что сделано или что мешает' })),
            el('div', { class: 'actions' },
                el('button', { class: 'btn btn--ghost btn--small', type: 'submit', text: 'Добавить' })));
        noteForm.addEventListener('submit', act(async function (event) {
            event.preventDefault();
            const data = new FormData(noteForm);
            await call('POST', '/api/v1/work-items/' + item.id + '/notes', {
                json: { author: 'Консоль', content: data.get('content') }
            });
            noteForm.reset();
            toast('Заметка добавлена');
            await refreshTask(item.id);
            await loadTasks();
        }));
        box.append(noteForm);
    }

    async function refreshTask(id) {
        const [item, notes] = await Promise.all([
            call('GET', '/api/v1/work-items/' + id),
            call('GET', '/api/v1/work-items/' + id + '/notes')
        ]);
        renderDetail(item, notes);
    }

    async function selectTask(id) {
        TASKS.selectedId = id;
        renderTasks();
        await refreshTask(id);
    }

    async function changeStatus(id, target) {
        await call('PATCH', '/api/v1/work-items/' + id + '/status?target=' + encodeURIComponent(target));
        toast('Статус: ' + (STATUS_LABELS[target] || target));
        await loadTasks();
        await refreshTask(id);
    }

    async function updateTask(id, body) {
        await call('PUT', '/api/v1/work-items/' + id, { json: body });
        toast('Задача сохранена');
        await loadTasks();
        await refreshTask(id);
    }

    async function removeTask(id) {
        if (!window.confirm('Удалить задачу #' + id + '?')) {
            return;
        }
        await call('DELETE', '/api/v1/work-items/' + id);
        toast('Задача удалена');
        TASKS.selectedId = null;
        renderEmptyDetail();
        await loadTasks();
    }

    async function createTask(event) {
        event.preventDefault();
        const body = {
            title: $('#taskTitle').value,
            description: $('#taskDescription').value,
            priority: Number($('#taskPriority').value),
            assigneeId: Number($('#taskAssignee').value)
        };
        const created = await call('POST', '/api/v1/work-items', {
            json: body,
            headers: { 'Idempotency-Key': window.crypto.randomUUID() }
        });
        toast('Создана задача #' + created.id);
        $('#taskForm').hidden = true;
        event.target.reset();
        $('#taskPriority').value = '3';
        TASKS.page = 0;
        await loadTasks();
        await selectTask(created.id);
    }

    async function loadAssignees() {
        STATE.assignees = await call('GET', '/api/v1/assignees');
        const select = clear($('#taskAssignee'));
        STATE.assignees.forEach(function (assignee) {
            select.append(el('option', { value: assignee.id, text: assignee.name + ' · ' + assignee.email }));
        });
    }

    function initTasks() {
        $('#taskForm').addEventListener('submit', act(createTask));
        $('#taskCreateToggle').addEventListener('click', function () {
            const form = $('#taskForm');
            form.hidden = !form.hidden;
            if (!form.hidden) {
                $('#taskTitle').focus();
            }
        });
        $('#taskCancel').addEventListener('click', function () {
            $('#taskForm').hidden = true;
        });
        $('#taskStatus').addEventListener('change', act(async function (event) {
            TASKS.status = event.target.value;
            TASKS.page = 0;
            await loadTasks();
        }));
        $('#taskSize').addEventListener('change', act(async function (event) {
            TASKS.size = Number(event.target.value);
            TASKS.page = 0;
            await loadTasks();
        }));
        $('#taskReload').addEventListener('click', act(async function () {
            await loadTasks();
            if (TASKS.selectedId) {
                await refreshTask(TASKS.selectedId);
            }
        }));
        $('#taskPrev').addEventListener('click', act(async function () {
            TASKS.page = Math.max(0, TASKS.page - 1);
            await loadTasks();
        }));
        $('#taskNext').addEventListener('click', act(async function () {
            TASKS.page = TASKS.page + 1;
            await loadTasks();
        }));
    }

    /* ---------- JPA ---------- */

    function initJpa() {
        $$('[data-jpa-mode]').forEach(function (button) {
            button.addEventListener('click', act(function () { return runJpa(button.dataset.jpaMode); }));
        });
        $('#jpaOptimistic').addEventListener('click', act(async function () {
            const report = await call('POST', '/api/v2/jpa/optimistic?id=' + $('#jpaId').value);
            metrics($('#jpaMetrics'), [
                { label: 'задача', value: report.id },
                { label: 'конфликт', value: fmtBool(report.conflictDetected) },
                { label: 'версия до', value: report.versionBefore },
                { label: 'версия после', value: report.versionAfter }
            ]);
            clear($('#jpaResult')).append(el('p', { text: report.message }));
        }));
        $('#jpaPessimistic').addEventListener('click', act(async function () {
            const report = await call('POST', '/api/v2/jpa/pessimistic?id=' + $('#jpaId').value + '&holdMillis=300');
            metrics($('#jpaMetrics'), [
                { label: 'задача', value: report.id },
                { label: 'блокировка', value: fmtBool(report.locked) },
                { label: 'держали, мс', value: report.heldMillis }
            ]);
            clear($('#jpaResult')).append(el('p', { text: report.message }));
        }));
        return runJpa('naive');
    }

    async function runJpa(mode) {
        const status = $('#jpaStatus').value;
        const report = await call('GET', '/api/v2/jpa/' + mode + '?status=' + encodeURIComponent(status));
        metrics($('#jpaMetrics'), [
            { label: 'SQL-запросов', value: report.statements },
            { label: 'строк', value: report.rows },
            { label: 'мс', value: report.durationMs },
            { label: 'режим', value: report.mode }
        ]);
        const note = report.statements <= 2
            ? 'Связи загружены одним-двумя запросами: N+1 нет.'
            : 'Классический N+1: отдельный запрос на каждую связь.';
        clear($('#jpaResult')).append(el('p', { text: note }));
        clear($('#jpaRows')).append(...(report.sample || []).map(function (row) {
            return el('tr', {},
                el('td', { class: 'mono', text: row.id }),
                el('td', { text: row.title }),
                el('td', { text: row.assigneeName || '—' }),
                el('td', { class: 'mono', text: row.notesCount }));
        }));
    }

    /* ---------- транзакции ---------- */

    function initTx() {
        $('#txReload').addEventListener('click', act(loadTx));
        $('#txTransfer').addEventListener('click', act(async function () {
            const result = await call('POST', '/api/v3/tx/transfer', {
                json: {
                    sourceCode: $('#txSource').value,
                    targetCode: $('#txTarget').value,
                    amount: Number($('#txAmount').value)
                }
            });
            metrics($('#txAccounts'), []);
            clear($('#txResult')).append(
                el('p', { text: 'Проводка #' + result.entryId + ' на ' + fmtNumber(result.amount, 2) }),
                el('dl', { class: 'kv' },
                    el('dt', { text: result.sourceCode }),
                    el('dd', { text: fmtNumber(result.sourceBalance, 2) }),
                    el('dt', { text: result.targetCode }),
                    el('dd', { text: fmtNumber(result.targetBalance, 2) })));
            await loadTx();
        }));
        $('#txTransferAudit').addEventListener('click', act(async function () {
            const report = await call('POST', '/api/v3/tx/transfer-with-audit', {
                json: {
                    sourceCode: $('#txSource').value,
                    targetCode: $('#txTarget').value,
                    amount: Number($('#txAmount').value)
                }
            });
            clear($('#txResult')).append(
                el('p', { text: report.message }),
                el('dl', { class: 'kv' },
                    el('dt', { text: 'перевод откатин' }),
                    el('dd', { text: fmtBool(report.rolledBack) }),
                    el('dt', { text: 'записей аудита' }),
                    el('dd', { text: report.auditRecordsWritten })));
            await loadTx();
        }));
        $('#txPropagation').addEventListener('click', act(async function () {
            const report = await call('POST', '/api/v3/tx/propagation-demo');
            clear($('#txResult')).append(
                el('p', { text: report.explanation }),
                el('dl', { class: 'kv' },
                    el('dt', { text: 'прямой вызов упал' }),
                    el('dd', { text: fmtBool(report.directCallFailed) }),
                    el('dt', { text: 'аудита после прямого' }),
                    el('dd', { text: report.auditRecordsAfterDirectCall }),
                    el('dt', { text: 'вызов через бин упал' }),
                    el('dd', { text: fmtBool(report.beanCallFailed) }),
                    el('dt', { text: 'аудита после бина' }),
                    el('dd', { text: report.auditRecordsAfterBeanCall })));
        }));
        return loadTx();
    }

    async function loadTx() {
        const [accounts, entries, audit] = await Promise.all([
            call('GET', '/api/v3/tx/accounts'),
            call('GET', '/api/v3/tx/entries'),
            call('GET', '/api/v3/tx/audit')
        ]);
        const box = clear($('#txAccounts'));
        accounts.forEach(function (account) {
            box.append(el('div', { class: 'metric' },
                el('b', { text: fmtNumber(account.balance, 2) }),
                el('span', { text: account.code })));
        });
        const source = $('#txSource');
        const target = $('#txTarget');
        const sourceValue = source.value;
        const targetValue = target.value;
        source.replaceChildren();
        target.replaceChildren();
        accounts.forEach(function (account) {
            source.append(el('option', { value: account.code, text: account.code + ' · ' + fmtNumber(account.balance, 2) }));
            target.append(el('option', { value: account.code, text: account.code + ' · ' + fmtNumber(account.balance, 2) }));
        });
        if (sourceValue) {
            source.value = sourceValue;
        }
        if (targetValue) {
            target.value = targetValue;
        } else if (accounts.length > 1) {
            target.value = accounts[1].code;
        }
        clear($('#txEntriesRows')).append(...entries.slice().reverse().slice(0, 10).map(function (entry) {
            return el('tr', {},
                el('td', { class: 'mono', text: entry.id }),
                el('td', { class: 'mono', text: entry.sourceAccount }),
                el('td', { class: 'mono', text: entry.targetAccount }),
                el('td', { class: 'mono', text: fmtNumber(entry.amount, 2) }),
                el('td', { text: fmtDate(entry.createdAt) }));
        }));
        clear($('#txAuditRows')).append(...audit.slice().reverse().slice(0, 8).map(function (record) {
            return el('li', {},
                el('small', { text: record.kind + ' · ' + fmtDate(record.createdAt) }),
                record.message);
        }));
    }

    /* ---------- многопоточность ---------- */

    function initConc() {
        $$('[data-conc]').forEach(function (button) {
            button.addEventListener('click', act(function () { return runConc(button.dataset.conc); }));
        });
    }

    async function runConc(kind) {
        const threads = $('#concThreads').value;
        const iterations = $('#concIterations').value;
        const tasks = $('#concTasks').value;
        const ordered = $('#concOrdered').value;
        const urls = {
            parallel: '/api/v4/concurrency/parallel',
            timeout: '/api/v4/concurrency/timeout',
            race: '/api/v4/concurrency/race?threads=' + threads + '&iterations=' + iterations,
            deadlock: '/api/v4/concurrency/deadlock?ordered=' + ordered,
            pool: '/api/v4/concurrency/pool?tasks=' + tasks
        };
        const report = await call('GET', urls[kind]);
        const box = $('#concMetrics');
        const result = clear($('#concResult'));

        if (kind === 'parallel') {
            metrics(box, [
                { label: 'параллельно, мс', value: report.totalMs },
                { label: 'последовательно, мс', value: report.sequentialMs },
                { label: 'вызовов', value: report.calls.length }
            ]);
            result.append(el('p', { text: report.comment }), el('ul', { class: 'note-list' },
                report.calls.map(function (item) {
                    return el('li', {}, el('small', { text: item.name + ' · ' + item.durationMs + ' мс' }), item.status);
                })));
            return;
        }
        if (kind === 'timeout') {
            metrics(box, [
                { label: 'работа, мс', value: report.workMillis },
                { label: 'тайм-аут, мс', value: report.timeoutMs },
                { label: 'фактически, мс', value: report.elapsedMs },
                { label: 'итог', value: report.status }
            ]);
            result.append(el('p', { text: report.message }));
            return;
        }
        if (kind === 'race') {
            metrics(box, [
                { label: 'ожидалось', value: fmtNumber(report.expected) },
                { label: 'long', value: fmtNumber(report.unsafeValue) },
                { label: 'AtomicLong', value: fmtNumber(report.safeValue) },
                { label: 'потеряно', value: fmtNumber(report.lostUpdates) }
            ]);
            result.append(el('p', { text: report.comment }));
            return;
        }
        if (kind === 'deadlock') {
            metrics(box, [
                { label: 'порядок', value: report.ordered ? 'упорядоченный' : 'встречный' },
                { label: 'завершено', value: report.completed },
                { label: 'по тайм-ауту', value: report.timedOut }
            ]);
            result.append(el('p', { text: report.comment }));
            return;
        }
        metrics(box, [
            { label: 'принято', value: report.submitted },
            { label: 'стартовало', value: report.started },
            { label: 'завершилось', value: report.finished },
            { label: 'отклонено', value: report.rejected },
            { label: 'пул', value: report.poolSize + '/' + report.maxPoolSize },
            { label: 'в очереди', value: report.queueRemaining }
        ]);
        result.append(el('p', { text: report.comment }));
    }

    /* ---------- брокер ---------- */

    function initBus() {
        $('#busScenario').addEventListener('click', act(async function () {
            const report = await call('GET', '/api/v5/bus/scenario?poison=' + $('#busPoison').value);
            metrics($('#busMetrics'), [
                { label: 'доставлено', value: report.delivered },
                { label: 'подтверждено', value: report.acknowledged },
                { label: 'повторов', value: report.retried },
                { label: 'в DLQ', value: report.deadLettered },
                { label: 'дедуплицировано', value: report.deduplicated },
                { label: 'размер DLQ', value: report.deadLetterQueueSize }
            ]);
            clear($('#busResult')).append(el('p', {
                text: 'Топик ' + report.topic + ', группа ' + report.group + ', попыток до DLQ: ' + report.maxAttempts
            }));
            renderEvents(report.events || []);
            renderPartitions(report.partitionSizes || {});
        }));
        $('#busState').addEventListener('click', act(async function () {
            const snapshot = await call('GET', '/api/v5/bus/state');
            metrics($('#busMetrics'), [
                { label: 'топик', value: snapshot.topic },
                { label: 'в outbox', value: snapshot.pendingOutbox },
                { label: 'в DLQ', value: (snapshot.deadLetters || []).length }
            ]);
            clear($('#busResult')).append(el('p', {
                text: 'Позиции групп: ' + JSON.stringify(snapshot.consumerPositions || {})
            }));
            renderPartitions(snapshot.partitionSizes || {});
            renderEvents((snapshot.deadLetters || []).map(function (item) {
                return 'DLQ: ' + JSON.stringify(item);
            }));
        }));
        $('#busOutbox').addEventListener('click', act(async function () {
            const payload = $('#busPayload').value || 'console-' + Date.now();
            const view = await call('POST', '/api/v5/bus/outbox?payload=' + encodeURIComponent(payload));
            clear($('#busResult')).append(
                el('p', { text: view.comment || 'Записано в outbox' }),
                el('dl', { class: 'kv' },
                    el('dt', { text: 'id' }),
                    el('dd', { text: view.id }),
                    el('dt', { text: 'статус' }),
                    el('dd', { text: view.status }),
                    el('dt', { text: 'после коммита' }),
                    el('dd', { text: fmtBool(view.publishedAfterCommit) })));
        }));
    }

    function renderPartitions(sizes) {
        const rows = clear($('#busPartitions'));
        const keys = Object.keys(sizes);
        if (!keys.length) {
            rows.append(el('tr', {}, el('td', { colspan: 2, class: 'empty', text: 'Партиции пусты' })));
        }
        keys.forEach(function (key) {
            rows.append(el('tr', {},
                el('td', { class: 'mono', text: key }),
                el('td', { class: 'mono', text: sizes[key] })));
        });
    }

    function renderEvents(events) {
        const log = clear($('#busEvents'));
        if (!events.length) {
            log.append(el('div', { class: 'log__line log__line--muted', text: 'событий нет' }));
        }
        events.forEach(function (event) {
            log.append(el('div', { class: 'log__line', text: event }));
        });
    }

    /* ---------- XML ---------- */

    function initXml() {
        $('#xmlSample').addEventListener('click', act(async function () {
            const sample = await call('GET', '/api/v6/xml/sample');
            $('#xmlText').value = sample.xml;
            clear($('#xmlResult')).append(el('p', { text: 'Образец загружен, длина ' + sample.xml.length + ' символов' }));
        }));
        $('#xmlValidate').addEventListener('click', act(async function () {
            const report = await call('POST', '/api/v6/xml/validate', { text: $('#xmlText').value });
            const box = clear($('#xmlResult'));
            box.append(el('p', { text: report.valid ? 'Документ валиден по XSD' : 'Документ не прошёл валидацию' }));
            if ((report.errors || []).length) {
                box.append(errorsList(report.errors));
            }
            box.append(el('p', { class: 'drawer__empty', text: report.comment }));
        }));
        $('#xmlParse').addEventListener('click', act(async function () {
            const report = await call('POST', '/api/v6/xml/parse', { text: $('#xmlText').value });
            const box = clear($('#xmlResult'));
            box.append(el('p', { text: report.message }));
            if ((report.errors || []).length) {
                box.append(errorsList(report.errors));
            }
            if (report.invoice) {
                const invoice = report.invoice;
                box.append(el('dl', { class: 'kv' },
                    el('dt', { text: 'номер' }),
                    el('dd', { text: invoice.number }),
                    el('dt', { text: 'дата' }),
                    el('dd', { text: invoice.issuedAt }),
                    el('dt', { text: 'продавец' }),
                    el('dd', { text: invoice.sellerName + ' · ' + invoice.sellerInn }),
                    el('dt', { text: 'покупатель' }),
                    el('dd', { text: invoice.buyerName + ' · ' + invoice.buyerInn }),
                    el('dt', { text: 'сумма' }),
                    el('dd', { text: fmtNumber(invoice.total, 2) })));
            }
        }));
        $('#xmlXxe').addEventListener('click', function () {
            const body = $('#xmlText').value.replace(/^\s*<\?xml[^>]*\?>\s*/i, '');
            $('#xmlText').value = '<?xml version="1.0" encoding="UTF-8"?>\n'
                + '<!DOCTYPE invoice SYSTEM "http://example.invalid/evil.dtd">\n' + body;
            toast('Подставлен DOCTYPE — парсер должен его отвергнуть', 'err');
        });
        $('#xmlBuildForm').addEventListener('submit', act(async function (event) {
            event.preventDefault();
            const data = new FormData(event.target);
            const draft = {
                number: data.get('number'),
                issuedAt: data.get('issuedAt'),
                sellerName: data.get('sellerName'),
                sellerInn: data.get('sellerInn'),
                buyerName: data.get('buyerName'),
                buyerInn: data.get('buyerInn'),
                items: [{
                    name: data.get('itemName'),
                    quantity: Number(data.get('quantity')),
                    price: Number(data.get('price'))
                }]
            };
            const built = await call('POST', '/api/v6/xml/build', { json: draft });
            $('#xmlText').value = built.xml;
            clear($('#xmlResult')).append(
                el('p', { text: built.valid ? 'Счёт собран и валиден' : 'Счёт собран, но не прошёл проверку' }),
                jsonBlock(built));
        }));
        return $('#xmlSample').click();
    }

    function errorsList(errors) {
        return el('ul', { class: 'note-list' },
            errors.map(function (error) {
                return el('li', { text: error });
            }));
    }

    /* ---------- подпись ---------- */

    function initCrypto() {
        $('#cryptoSign').addEventListener('click', act(async function () {
            const result = await call('POST', '/api/v7/crypto/sign', { json: { payload: $('#cryptoPayload').value } });
            STATE.signature = result.signatureBase64;
            clear($('#cryptoResult')).append(
                el('p', { text: 'Подписано алгоритмом ' + result.algorithm }),
                el('dl', { class: 'kv' },
                    el('dt', { text: 'байт' }),
                    el('dd', { text: result.payloadBytes }),
                    el('dt', { text: 'сертификат' }),
                    el('dd', { text: result.certificateSubject }),
                    el('dt', { text: 'подпись' }),
                    el('dd', { class: 'mono', text: result.signatureBase64.slice(0, 64) + '…' })));
        }));
        $('#cryptoVerify').addEventListener('click', act(async function () {
            if (!STATE.signature) {
                toast('Сначала подпишите нагрузку', 'err');
                return;
            }
            const report = await call('POST', '/api/v7/crypto/verify', {
                json: { payload: $('#cryptoPayload').value, signatureBase64: STATE.signature }
            });
            clear($('#cryptoResult')).append(
                el('p', { text: report.valid ? 'Подпись верна' : 'Подпись неверна: ' + report.reason }),
                el('dl', { class: 'kv' },
                    el('dt', { text: 'субъект' }),
                    el('dd', { text: report.subject }),
                    el('dt', { text: 'дней до истечения' }),
                    el('dd', { text: report.daysUntilExpiry }),
                    el('dt', { text: 'алгоритм' }),
                    el('dd', { text: report.algorithm })));
        }));
        $('#cryptoTamper').addEventListener('click', act(async function () {
            const report = await call('GET', '/api/v7/crypto/tamper?payload='
                + encodeURIComponent($('#cryptoPayload').value));
            clear($('#cryptoResult')).append(
                el('p', { text: report.comment }),
                el('dl', { class: 'kv' },
                    el('dt', { text: 'исходная подпись' }),
                    el('dd', { text: fmtBool(report.originalValid) }),
                    el('dt', { text: 'после подмены' }),
                    el('dd', { text: fmtBool(report.tamperedValid) }),
                    el('dt', { text: 'причина' }),
                    el('dd', { text: report.tamperedReason })));
        }));
        $('#cryptoCert').addEventListener('click', act(async function () {
            const [certificate, chain] = await Promise.all([
                call('GET', '/api/v7/crypto/certificate'),
                call('GET', '/api/v7/crypto/chain')
            ]);
            pairs($('#cryptoCertBox'), [
                { label: 'субъект', value: certificate.subject },
                { label: 'издатель', value: certificate.issuer },
                { label: 'серийный', value: certificate.serialNumber },
                { label: 'подпись', value: certificate.signatureAlgorithm },
                { label: 'ключ', value: certificate.keyAlgorithm },
                { label: 'действует до', value: fmtDate(certificate.notAfter) },
                { label: 'дней осталось', value: certificate.daysUntilExpiry },
                { label: 'длина цепочки', value: certificate.chainLength },
                { label: 'SHA-256', value: certificate.sha256Fingerprint },
                { label: 'цепочка', value: chain.join(' → ') }
            ]);
        }));
    }

    /* ---------- WebSocket ---------- */

    function initWs() {
        $('#wsConnect').addEventListener('click', act(connectSocket));
        $('#wsDisconnect').addEventListener('click', function () {
            if (STATE.socket) {
                STATE.socket.close();
            }
        });
        $('#wsNote').addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
            }
        });
        wsLog('Подключитесь к серверу, чтобы пройти машину состояний', 'muted');
    }

    function wsLog(line, kind) {
        const log = $('#wsLog');
        log.append(el('div', { class: 'log__line log__line--' + (kind || 'plain'), text: timeFormat.format(new Date()) + '  ' + line }));
        log.scrollTop = log.scrollHeight;
    }

    async function connectSocket() {
        if (STATE.socket && STATE.socket.readyState === WebSocket.OPEN) {
            toast('Соединение уже открыто');
            return;
        }
        const schema = STATE.schema || await call('GET', '/api/v8/ws/schema');
        const scheme = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
        const socket = new WebSocket(scheme + window.location.host + schema.endpoint);
        STATE.socket = socket;
        wsLog('соединение с ' + schema.endpoint, 'muted');
        socket.addEventListener('open', function () {
            wsLog('соединение открыто', 'ok');
        });
        socket.addEventListener('message', function (event) {
            handleFrame(JSON.parse(event.data));
        });
        socket.addEventListener('close', function (event) {
            wsLog('закрыто, код ' + event.code, 'muted');
            clear($('#wsCommands')).append(el('span', { class: 'drawer__empty', text: 'соединение закрыто' }));
        });
        socket.addEventListener('error', function () {
            wsLog('ошибка транспорта', 'err');
        });
    }

    function handleFrame(frame) {
        if (frame.type === 'welcome') {
            wsLog('welcome · сессия ' + frame.sessionId + ' · лимит ' + frame.maxPayloadBytes + ' Б', 'ok');
            renderCommands(frame.state, frame.allowed);
            return;
        }
        if (frame.type === 'state') {
            wsLog('state → ' + frame.state, 'ok');
            renderCommands(frame.state, frame.allowed, frame.history);
            return;
        }
        if (frame.type === 'error') {
            wsLog('error · ' + frame.code + ' · ' + frame.message, 'err');
        }
    }

    function renderCommands(current, allowed, history) {
        const box = clear($('#wsCommands'));
        (allowed || []).forEach(function (target) {
            box.append(el('button', {
                class: 'btn btn--ghost btn--small',
                type: 'button',
                text: '→ ' + (STATUS_LABELS[target] || target),
                onclick: act(function () {
                    sendCommand(target);
                })
            }));
        });
        if (!allowed || !allowed.length) {
            box.append(el('span', { class: 'drawer__empty', text: 'переходов нет' }));
        }
        const result = clear($('#wsStateBox'));
        result.append(el('dl', { class: 'kv' },
            el('dt', { text: 'текущее состояние' }),
            el('dd', {}, statusPill(current)),
            el('dt', { text: 'допустимо' }),
            el('dd', { text: (allowed || []).join(', ') || '—' }),
            el('dt', { text: 'история' }),
            el('dd', { text: (history || []).join(' → ') || '—' })));
    }

    function sendCommand(command) {
        if (!STATE.socket || STATE.socket.readyState !== WebSocket.OPEN) {
            toast('Соединение не открыто', 'err');
            return;
        }
        const frame = { command: command };
        const note = $('#wsNote').value.trim();
        if (note) {
            frame.note = note;
        }
        STATE.socket.send(JSON.stringify(frame));
        wsLog('→ ' + JSON.stringify(frame), 'muted');
    }

    /* ---------- игра ---------- */

    function initGame() {
        $('#gameReload').addEventListener('click', act(loadGame));
        return loadGame();
    }

    async function loadGame() {
        const [stats, leaders] = await Promise.all([
            call('GET', '/api/game/stats'),
            call('GET', '/api/game/leaderboard?limit=10')
        ]);
        const perLevel = Object.entries(stats.runsPerLevel || {})
            .map(function (entry) { return entry[0] + ': ' + entry[1]; })
            .join(' · ');
        metrics($('#gameMetrics'), [
            { label: 'забегов', value: stats.totalRuns },
            { label: 'уровней', value: stats.levels },
            { label: 'по уровням', value: perLevel || '—' }
        ]);
        clear($('#gameRows')).append(...leaders.map(function (row) {
            return el('tr', {},
                el('td', { text: row.player }),
                el('td', { class: 'mono', text: row.level }),
                el('td', { class: 'mono', text: fmtNumber(row.score) }),
                el('td', { class: 'mono', text: row.seconds + ' с' }));
        }));
    }

    /* ---------- запуск ---------- */

    async function checkHealth() {
        const badge = $('#healthBadge');
        try {
            const health = await call('GET', '/actuator/health');
            badge.classList.toggle('is-down', health.status !== 'UP');
            badge.replaceChildren(document.createTextNode('сервер: '), el('b', { text: health.status }));
        } catch (error) {
            badge.classList.add('is-down');
            badge.replaceChildren(document.createTextNode('сервер: '), el('b', { text: 'недоступен' }));
        }
    }

    async function boot() {
        initTasks();
        $$('.rail__item').forEach(function (button) {
            button.addEventListener('click', act(function () { return activate(button.dataset.tab); }));
        });
        window.addEventListener('unhandledrejection', function (event) {
            toast(event.reason && event.reason.message ? event.reason.message : 'Ошибка', 'err');
        });
        await checkHealth();
        try {
            const schema = await call('GET', '/api/v8/ws/schema');
            STATE.schema = schema;
            STATE.transitions = schema.transitions || {};
            STATE.states = schema.states || [];
        } catch (error) {
            STATE.transitions = {};
        }
        await loadAssignees();
        await loadTasks();
        const initial = window.location.hash.replace('#', '');
        if (initial && initial !== 'tasks' && loaders[initial]) {
            await activate(initial);
        }
    }

    document.addEventListener('DOMContentLoaded', act(boot));
})();
