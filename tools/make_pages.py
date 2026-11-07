"""Генератор восьми страниц проектов и общей обвязки витрины.

Страницы статические: каждая содержит задачу, принятые решения, живой вызов API
и фрагмент кода из соответствующего пакета. Запуск: python tools/make_pages.py.
"""

from __future__ import annotations

import html
import os

OUT_DIR = os.path.join("src", "main", "resources", "static", "projects")


def attr(value: str) -> str:
    return html.escape(value, quote=True)


def code(value: str) -> str:
    return html.escape(value.strip("\n"), quote=False)


XML_SAMPLE = """<?xml version="1.0" encoding="UTF-8"?>
<invoice xmlns="urn:eight:invoice" version="1">
  <number>IN-2026/000142</number>
  <issuedAt>2026-11-06</issuedAt>
  <seller>
    <name>ООО «Восемь проектов»</name>
    <inn>7701234567</inn>
  </seller>
  <buyer>
    <name>АО «Заказчик»</name>
    <inn>7709876543</inn>
  </buyer>
  <items>
    <item line="1">
      <name>Разработка backend-компонента</name>
      <quantity>1</quantity>
      <price>180000.00</price>
    </item>
  </items>
  <total>180000.00</total>
</invoice>
"""

PROJECTS = [
    {
        "key": "p1",
        "num": 1,
        "title": "REST API по контракту",
        "accent": "#38bdf8",
        "lead": "CRUD по OpenAPI-контракту: наружу уходят только DTO, вход проверяется Jakarta Validation, "
                "а изменяющие запросы защищены ключом идемпотентности.",
        "tags": ["Spring MVC", "Jakarta Validation", "Idempotency-Key", "Пагинация", "Единый формат ошибок"],
        "decisions": [
            ("DTO отделены от сущности",
             "WorkItemRequest и WorkItemResponse описывают контракт, JPA-сущность WorkItem наружу не выходит."),
            ("Валидация на входе",
             "@Valid на теле запроса: длина заголовка, приоритет 1..5, обязательный исполнитель. "
             "Дополнительно сервис проверяет допустимость команд."),
            ("Идемпотентное создание",
             "Заголовок Idempotency-Key сохраняет результат операции в таблицу idempotency_record; "
             "уникальность ключа держит база, а не приложение."),
            ("Пагинация и фильтрация",
             "PageResponse отдаёт items, page, size и totalElements, фильтр по статусу применяется в запросе."),
            ("Ошибки как данные",
             "ApiError с кодом и деталями, GlobalExceptionHandler переводит исключения в согласованные HTTP-статусы."),
        ],
        "api": [
            ("GET", "/api/v1/work-items?page=0&size=3", None, None, None),
            ("POST", "/api/v1/work-items",
             '{"title":"Спринт 12","description":"Проверить контракт","priority":2,"assigneeId":1}',
             "Idempotency-Key=demo-1", "application/json"),
            ("PATCH", "/api/v1/work-items/1/status?target=IN_PROGRESS", None, None, None),
            ("POST", "/api/v1/work-items/1/notes", '{"author":"Анна Петрова","content":"Согласовано"}',
             None, "application/json"),
        ],
        "code": """
@PostMapping
public ResponseEntity<WorkItemResponse> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                               @Valid @RequestBody WorkItemRequest request) {
    WorkItemResponse created = this.idempotency.execute(idempotencyKey, CREATE_OPERATION,
            WorkItemResponse.class, () -> this.service.create(request));
    ResponseEntity<WorkItemResponse> response = ResponseEntity.status(HttpStatus.CREATED).body(created);
    return response;
}
""",
        "note": "Нажмите «Создать» дважды с одним и тем же ключом: второй вызов вернёт сохранённый ответ "
                "и не создаст дубликат. Смените ключ в атрибуте data-headers, чтобы получить новую запись.",
    },
    {
        "key": "p2",
        "num": 2,
        "title": "JPA без N+1",
        "accent": "#a78bfa",
        "lead": "Одна и та же выборка задач в четырёх вариантах: наивный обход связей, fetch join, "
                "EntityGraph и DTO-проекция. Число SQL-запросов измеряется статистикой Hibernate.",
        "tags": ["Hibernate", "Статистика запросов", "fetch join", "EntityGraph", "DTO-проекция", "@Version"],
        "decisions": [
            ("Проблема воспроизводится",
             "naive-вариант читает задачи и дёргает assignee и notes в цикле: число запросов растёт вместе с выборкой."),
            ("fetch join и EntityGraph",
             "Связи загружаются одним запросом; для страниц используется EntityGraph, чтобы не ломать пагинацию."),
            ("DTO-проекция",
             "Конструкторное выражение selects только нужные поля и считает notes через size(), без сущностей в контексте."),
            ("Оптимистичный контроль",
             "@Version на сущности: конкурентное обновление возвращает конфликт вместо тихой перезаписи."),
            ("Пессимистичная блокировка",
             "SELECT FOR UPDATE через @Lock(PESSIMISTIC_WRITE) с удержанием на заданное время."),
        ],
        "api": [
            ("GET", "/api/v2/jpa/naive?status=NEW", None, None, None),
            ("GET", "/api/v2/jpa/fetch-join?status=NEW", None, None, None),
            ("GET", "/api/v2/jpa/entity-graph?status=NEW", None, None, None),
            ("GET", "/api/v2/jpa/projection?status=NEW", None, None, None),
            ("POST", "/api/v2/jpa/optimistic?id=1", None, None, None),
            ("POST", "/api/v2/jpa/pessimistic?id=1&holdMillis=120", None, None, None),
        ],
        "code": """
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select w from WorkItem w where w.id = :id")
Optional<WorkItem> findByIdForUpdate(@Param("id") Long id);

@Query("select new ru.eight.p2jpa.WorkItemSummary(w.id, w.title, a.name, size(w.notes), w.status) "
        + "from WorkItem w join w.assignee a where w.status = :status order by w.id")
List<WorkItemSummary> summarizeByStatus(@Param("status") WorkItemStatus status);
""",
        "note": "Сравните поле queries в ответах naive и projection: один и тот же результат, "
                "но разное число обращений к базе.",
    },
    {
        "key": "p3",
        "num": 3,
        "title": "Границы транзакций",
        "accent": "#f472b6",
        "lead": "Перевод между счетами в одной транзакции, независимый аудит через REQUIRES_NEW и наглядная "
                "демонстрация того, почему self-invocation отключает прокси и аннотацию.",
        "tags": ["@Transactional", "REQUIRES_NEW", "AOP-прокси", "Откат", "readOnly"],
        "decisions": [
            ("Одна транзакция на перевод",
             "Списание и зачисление выполняются в REQUIRED-транзакции: при исключении откатываются оба изменения."),
            ("Аудит переживает откат",
             "AuditWriter помечен REQUIRES_NEW и вызывается как отдельный бин, поэтому запись аудита остаётся "
             "даже после отката бизнес-операции."),
            ("Self-invocation не работает",
             "Вызов this.writeInNewTransaction() идёт мимо прокси: аннотация игнорируется, и аудит откатывается "
             "вместе с транзакцией. Это показано отдельно."),
            ("Read-only для чтения",
             "@Transactional(readOnly = true) на запросах отключает лишний flush и подсказывает базе режим чтения."),
            ("Проверка инварианта в домене",
             "LedgerAccount.withdraw запрещает отрицательный баланс и бросает ApiException до записи в базу."),
        ],
        "api": [
            ("GET", "/api/v3/tx/accounts", None, None, None),
            ("POST", "/api/v3/tx/transfer",
             '{"sourceCode":"ACC-001","targetCode":"ACC-002","amount":150.00}', None, "application/json"),
            ("POST", "/api/v3/tx/transfer-with-audit",
             '{"sourceCode":"ACC-001","targetCode":"ACC-002","amount":999999.00}', None, "application/json"),
            ("POST", "/api/v3/tx/propagation-demo", None, None, None),
            ("GET", "/api/v3/tx/audit", None, None, None),
        ],
        "code": """
// LedgerService.demoSelfInvocation(): два вызова одного и того же кода
this.writeInNewTransaction("REQUIRES_NEW", "вызов через this — аннотация игнорируется");
this.auditWriter.writeInNewTransaction("REQUIRES_NEW", "вызов через бин — независимая транзакция");
""",
        "note": "Сначала вызовите transfer (транзакция фиксируется), затем transfer-with-audit с суммой больше баланса: "
                "балансы не меняются, а запись аудита остаётся.",
    },
    {
        "key": "p4",
        "num": 4,
        "title": "Многопоточность под контролем",
        "accent": "#fbbf24",
        "lead": "Параллельные вызовы, тайм-ауты, потерянные обновления, дедлок и перегрузка пула — "
                "каждый сценарий воспроизводится и измеряется, а не описывается словами.",
        "tags": ["ExecutorService", "CompletableFuture", "orTimeout", "Atomic-классы", "ReentrantLock", "AbortPolicy"],
        "decisions": [
            ("Пул с ограниченной очередью",
             "ThreadPoolExecutor на 8 потоков и очередь 64; AbortPolicy явно сообщает о перегрузке вместо "
             "бесконечного роста очереди."),
            ("Тайм-ауты вместо ожидания",
             "CompletableFuture.orTimeout ограничивает внешний вызов, TimeoutException возвращается клиенту как ошибка."),
            ("Гонка видна в цифрах",
             "Счётчик long под нагрузкой теряет обновления, AtomicLong — нет: разницу показывает тот же прогон."),
            ("Дедлок и его лечение",
             "Немаркированный захват двух ReentrantLock даёт дедлок, упорядоченный захват — нет."),
            ("Честные имена потоков",
             "NamedThreadFactory даёт предсказуемые имена eight-worker-N, по ним читается thread dump."),
        ],
        "api": [
            ("GET", "/api/v4/concurrency/parallel", None, None, None),
            ("GET", "/api/v4/concurrency/timeout", None, None, None),
            ("GET", "/api/v4/concurrency/race?threads=4&iterations=20000", None, None, None),
            ("GET", "/api/v4/concurrency/deadlock?ordered=false", None, None, None),
            ("GET", "/api/v4/concurrency/deadlock?ordered=true", None, None, None),
            ("GET", "/api/v4/concurrency/pool?tasks=100", None, None, None),
        ],
        "code": """
@Bean(name = "eightExecutor", destroyMethod = "shutdown")
public ThreadPoolExecutor eightExecutor(@Value("${eight.concurrency.pool-size:8}") int poolSize,
                                       @Value("${eight.concurrency.queue-capacity:64}") int queueCapacity) {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(
            poolSize, poolSize, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(queueCapacity),
            new NamedThreadFactory("eight-worker-"),
            new ThreadPoolExecutor.AbortPolicy());
    executor.allowCoreThreadTimeOut(true);
    executor.prestartAllCoreThreads();
    return executor;
}
""",
        "note": "Запрос deadlock выполняется около секунды: сервис ждёт с тайм-аутом и возвращает отчёт, "
                "а не подвешивает поток контейнера.",
    },
    {
        "key": "p5",
        "num": 5,
        "title": "Брокер, повторы и Outbox",
        "accent": "#34d399",
        "lead": "Семантика Kafka и RabbitMQ в одном прогоне: топик, партиции, группы потребителей, оффсеты, "
                "ограниченные повторы, DLQ и дедупликация, плюс Transactional Outbox для согласования записи и отправки.",
        "tags": ["Kafka-семантика", "ack/nack", "Retry", "DLQ", "Дедупликация", "Transactional Outbox"],
        "decisions": [
            ("Партиции и группы",
             "События раскладываются по партициям, каждый потребитель в группе получает свою долю, оффсеты "
             "сдвигаются только после обработки."),
            ("Ограниченные повторы",
             "Неудачная обработка повторяется до настроенного предела, затем сообщение уходит в DLQ, "
             "а не крутится бесконечно."),
            ("Дедупликация",
             "Повторная доставка с тем же идентификатором распознаётся и не применяется дважды — "
             "at-least-once превращается в эффект ровно одного применения."),
            ("Outbox вместо dual write",
             "Событие пишется в таблицу outbox_message в той же транзакции, что и бизнес-изменение, "
             "и публикуется после коммита отдельным публикатором."),
            ("Наблюдаемое состояние",
             "Эндпоинт state показывает оффсеты, размеры очередей и содержимое DLQ после прогона."),
        ],
        "api": [
            ("GET", "/api/v5/bus/scenario?poison=false&events=6", None, None, None),
            ("GET", "/api/v5/bus/scenario?poison=true&events=6", None, None, None),
            ("GET", "/api/v5/bus/state", None, None, None),
            ("POST", "/api/v5/bus/outbox?payload=invoice-paid", None, None, None),
        ],
        "code": """
// Transactional Outbox: событие сохраняется в той же транзакции,
// что и бизнес-изменение, а публикуется после коммита.
public void markSent() {
    this.status = Status.SENT;
    this.sentAt = Instant.now();
    this.attempts = this.attempts + 1;
}
""",
        "note": "Сравните два прогона: с poison=true сообщение после исчерпания повторов попадает в DLQ, "
                "с poison=false — обрабатывается и подтверждается.",
    },
    {
        "key": "p6",
        "num": 6,
        "title": "XML, XSD и защита от XXE",
        "accent": "#22d3ee",
        "lead": "Счёт по схеме с include общей библиотеки типов: сборка через JAXB, валидация через SchemaFactory "
                "и разбор, которому запрещены DTD и внешние сущности.",
        "tags": ["JAXB", "SchemaFactory", "include схем", "Пространства имён", "XXE-защита", "StAX"],
        "decisions": [
            ("Схема как контракт",
             "invoice.xsd включает common-types.xsd: типы InvoiceNumber, Inn, Money и структуры Party/InvoiceItem "
             "описаны один раз и переиспользуются."),
            ("JAXB с явным пространством имён",
             "Все поля модели помечены namespace = urn:eight:invoice, иначе при elementFormDefault=qualified "
             "элементы уезжают в пустое пространство и валидация падает."),
            ("Валидация с разбором ошибок",
             "ErrorHandler собирает предупреждения и ошибки с номерами строк, отчёт возвращается клиенту списком."),
            ("Закрытые внешние ресурсы",
             "SchemaFactory: ACCESS_EXTERNAL_DTD пуст, ACCESS_EXTERNAL_SCHEMA ограничен file,jar. "
             "XMLInputFactory: SUPPORT_DTD=false, IS_SUPPORTING_EXTERNAL_ENTITIES=false."),
            ("Сборка и разбор в одном месте",
             "build формирует документ и сразу валидирует его, parse возвращает разобранную модель с итогом."),
        ],
        "api": [
            ("GET", "/api/v6/xml/sample", None, None, None),
            ("POST", "/api/v6/xml/validate", XML_SAMPLE, None, "application/xml"),
            ("POST", "/api/v6/xml/parse", XML_SAMPLE, None, "application/xml"),
        ],
        "code": """
private static Schema loadSchema() {
    Schema schema = null;
    try {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file,jar");
        URL resource = XmlService.class.getResource(XSD_PATH);
        schema = factory.newSchema(resource);
    } catch (SAXException ex) {
        throw new IllegalStateException("Схема " + XSD_PATH + " не загружена", ex);
    }
    return schema;
}
""",
        "note": "Отправьте документ с DOCTYPE и внешней сущностью — parse вернёт XML_REJECTED: "
                "ни DTD, ни внешние сущности не загружаются.",
    },
    {
        "key": "p7",
        "num": 7,
        "title": "Подпись, ключи и сертификаты",
        "accent": "#c084fc",
        "lead": "Формирование и проверка подписи через JCA/JCE, паспорт сертификата из PKCS12-хранилища, "
                "проверка срока действия и цепочки, демонстрация подмены данных.",
        "tags": ["Signature", "PKCS12 KeyStore", "X.509", "Проверка цепочки", "SHA-256", "Готово к КриптоПро JCP"],
        "decisions": [
            ("Алгоритм вынесен в конфигурацию",
             "Signature.getInstance берёт имя алгоритма из настроек: подмена на ГОСТ-алгоритм КриптоПро "
             "не требует правки кода, только провайдера и имени."),
            ("Ключи из KeyStore",
             "PKCS12-хранилище загружается один раз, приватный ключ и сертификат достаются по alias."),
            ("Проверка подписи с причиной",
             "VerifyReport различает SIGNATURE_INVALID, CERT_EXPIRED, CERT_NOT_YET_VALID и SIGNATURE_MALFORMED — "
             "диагностика важнее булева ответа."),
            ("Подмена данных видна",
             "tamper-сценарий подписывает платёж, меняет сумму и показывает, что проверка падает."),
            ("Отпечаток сертификата",
             "SHA-256 от encoded-формы сертификата даёт стабильный идентификатор для логов и сверок."),
        ],
        "api": [
            ("GET", "/api/v7/crypto/certificate", None, None, None),
            ("GET", "/api/v7/crypto/chain", None, None, None),
            ("POST", "/api/v7/crypto/sign", '{"payload":"Платёж на 300 000 рублей"}', None, "application/json"),
            ("GET", "/api/v7/crypto/tamper?payload=Платёж на 300 000 рублей", None, None, None),
        ],
        "code": """
public SignResult sign(String payload) {
    byte[] data = payload.getBytes(StandardCharsets.UTF_8);
    String signatureBase64 = null;
    try {
        Signature signature = Signature.getInstance(this.signatureAlgorithm);
        signature.initSign(privateKey());
        signature.update(data);
        byte[] signed = signature.sign();
        signatureBase64 = Base64.getEncoder().encodeToString(signed);
    } catch (Exception ex) {
        throw new ApiException("SIGN_FAILED", "Не удалось сформировать подпись: " + ex.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
    return new SignResult(this.signatureAlgorithm, signatureBase64, data.length,
            this.cachedInfo.subject(), Instant.now());
}
""",
        "note": "Демо использует RSA-ключ из ресурсов, чтобы проект запускался без лицензии КриптоПро. "
                "Для ГОСТ достаточно подключить провайдера JCP/JCSP и указать его алгоритм в eight.crypto.signature-algorithm.",
    },
    {
        "key": "p8",
        "num": 8,
        "title": "WebSocket и машина состояний",
        "accent": "#fb7185",
        "lead": "Серверная машина состояний поверх WebSocket: приветственный кадр, валидация команд, "
                "запрет недопустимых переходов, лимит размера сообщения и освобождение ресурсов при разрыве.",
        "tags": ["Spring WebSocket", "Машина состояний", "Лимиты сообщений", "ConcurrentWebSocketSessionDecorator", "Очистка сессий"],
        "decisions": [
            ("Соединение с состоянием",
             "При открытии сервер присылает welcome с текущим состоянием и списком допустимых переходов."),
            ("Переходы проверяются на сервере",
             "Клиент присылает {\"command\":\"IN_PROGRESS\"}, сервер сверяет переход с WorkItemStateMachine "
             "и отвечает state либо error."),
            ("Ограничение размера",
             "max-text-message-bytes отклоняет слишком большое сообщение с кодом MESSAGE_TOO_LARGE, "
             "размер буфера задан на контейнере."),
            ("Потокобезопасная отправка",
             "Сессия оборачивается в ConcurrentWebSocketSessionDecorator с тайм-аутом отправки."),
            ("Ресурсы освобождаются",
             "afterConnectionClosed и handleTransportError удаляют сессию из карты: утечки сессий нет."),
        ],
        "api": [
            ("GET", "/api/v8/ws/schema", None, None, None),
        ],
        "ws": True,
        "code": """
@Override
protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    SessionState state = this.sessions.get(session.getId());
    if (state == null) {
        sendError(session, "NOT_REGISTERED", "Соединение не зарегистрировано сервером");
    } else {
        processMessage(state, message);
    }
}

@Override
public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    this.sessions.remove(session.getId());
    LOG.info("WebSocket закрыт: {} ({})", session.getId(), status.getCode());
}
""",
        "note": "Подключитесь, затем отправляйте команды: недопустимый переход вернёт INVALID_TRANSITION, "
                "и состояние останется прежним.",
    },
]

TEMPLATE = """<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>%%TITLE%% — проект %%NUM%% из восьми</title>
    <meta name="description" content="%%LEAD%%">
    <link rel="stylesheet" href="/assets/styles.css">
</head>
<body style="--accent: %%ACCENT%%">
<div class="grid-bg" aria-hidden="true"></div>

<header class="topbar">
    <div class="wrap topbar__inner">
        <a class="brand" href="/">
            <span class="brand__mark" aria-hidden="true"></span>
            <span class="brand__name">Восемь проектов <span>/ Java 17 · Spring Boot</span></span>
        </a>
        <nav class="nav" aria-label="Основная навигация">
            <a href="/">Витрина</a>
            <a href="/index.html#projects">Проекты</a>
            <a href="/logic.html">Логика</a>
            <a href="/projects/%%KEY%%.html" aria-current="page">Проект %%NUM%%</a>
        </nav>
    </div>
</header>

<main class="wrap">
    <section class="project-hero">
        <div>
            <span class="eyebrow">Проект 0%%NUM%% из 08</span>
            <h1>%%TITLE%%</h1>
            <p class="lead">%%LEAD%%</p>
            <div class="tags">%%TAGS%%</div>
        </div>
        <div class="project-hero__media">
            <img src="/svg/%%KEY%%.svg" alt="Анимация проекта: %%TITLE%%" width="480" height="270">
        </div>
    </section>

    <section class="columns">
        <div class="panel reveal">
            <h3>Что реализовано</h3>
            <ul class="list">%%DECISIONS%%</ul>
        </div>
        <div class="panel reveal">
            <h3>Живые вызовы</h3>
            <p style="color:var(--muted);font-size:13.5px;margin:0 0 12px">
                Кнопки обращаются к запущенному приложению на этом же хосте и показывают реальный ответ.
            </p>
            <div class="api-list">%%API%%</div>
            %%WS_PANEL%%
            <div class="result" data-result hidden>
                <div class="result__status" data-result-status></div>
                <pre data-result-body></pre>
                <div class="result__status" style="margin-top:8px">
                    <span data-result-timer></span>
                </div>
            </div>
        </div>
    </section>

    <section class="section">
        <div class="columns" style="margin-top:0">
            <div class="panel reveal">
                <div style="display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:10px">
                    <h3 style="margin:0">Фрагмент кода</h3>
                    <button class="btn" type="button" data-copy="#code-%%KEY%%">Копировать</button>
                </div>
                <pre id="code-%%KEY%%"><code>%%CODE%%</code></pre>
            </div>
            <div class="panel reveal prose">
                <h3>Как читать результат</h3>
                <p>%%NOTE%%</p>
                <p>Полный список эндпоинтов проекта доступен на витрине:
                    <a href="/api/showcase/projects" style="color:var(--accent)">/api/showcase/projects</a>.</p>
                <p>Проект запускается профилем по умолчанию на H2 в режиме PostgreSQL; профиль
                    <code>postgres</code> переключает приложение на настоящий PostgreSQL из
                    <code>docker-compose.yml</code>.</p>
            </div>
        </div>
    </section>

    <nav class="pager" aria-label="Переход между проектами">
        %%PAGER%%
    </nav>
</main>

<footer class="footer">
    <div class="wrap footer__inner">
        <span>Проект 0%%NUM%% · %%TITLE%%</span>
        <span><a href="/">← все восемь проектов</a> · <a href="/logic.html">логика</a></span>
    </div>
</footer>

<script src="/assets/project.js" defer></script>
</body>
</html>
"""

WS_PANEL = """
<div style="margin-top:18px;border-top:1px solid var(--line);padding-top:14px">
    <h3 style="margin-bottom:10px">WebSocket вживую</h3>
    <div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:10px">
        <button class="btn" type="button" data-ws-connect>Подключиться к /ws/state</button>
        <button class="btn" type="button" data-ws-command='{"command":"IN_PROGRESS"}'>→ IN_PROGRESS</button>
        <button class="btn" type="button" data-ws-command='{"command":"BLOCKED"}'>→ BLOCKED</button>
        <button class="btn" type="button" data-ws-command='{"command":"DONE"}'>→ DONE</button>
        <button class="btn" type="button" data-ws-command='{"command":"CANCELLED"}'>→ CANCELLED</button>
        <button class="btn" type="button" data-ws-command='{"command":"NEW"}'>→ NEW (недопустимо)</button>
    </div>
    <div class="ws-log" data-ws-log aria-live="polite"></div>
</div>
"""


def render_api(rows) -> str:
    chunks = []
    for method, url, body, headers, content_type in rows:
        css = method.lower()
        call = [f'data-call data-method="{method}" data-url="{attr(url)}"']
        if body:
            call.append(f'data-body="{attr(body)}"')
        if headers:
            call.append(f'data-headers="{attr(headers)}"')
        if content_type:
            call.append(f'data-content-type="{attr(content_type)}"')
        chunks.append(
            '            <div class="api-row">\n'
            f'                <span class="method method--{css}">{method}</span>\n'
            f'                <code>{html.escape(url)}</code>\n'
            f'                <button class="btn" type="button" {" ".join(call)}>Вызвать</button>\n'
            "            </div>"
        )
    return "\n".join(chunks)


def render_pager(index: int) -> str:
    parts = []
    if index > 0:
        prev = PROJECTS[index - 1]
        parts.append(f'<a href="/projects/{prev["key"]}.html"><small>← проект 0{prev["num"]}</small>'
                     f'<span>{prev["title"]}</span></a>')
    else:
        parts.append('<a href="/"><small>← назад</small><span>Витрина проектов</span></a>')
    if index < len(PROJECTS) - 1:
        nxt = PROJECTS[index + 1]
        parts.append(f'<a href="/projects/{nxt["key"]}.html" style="margin-left:auto;text-align:right">'
                     f'<small>проект 0{nxt["num"]} →</small><span>{nxt["title"]}</span></a>')
    else:
        parts.append('<a href="/logic.html" style="margin-left:auto;text-align:right">'
                     '<small>дальше →</small><span>Логика: четыре мини-игры</span></a>')
    return "\n        ".join(parts)


def build(project, index: int) -> str:
    decisions = "\n".join(
        f'                <li><b>{html.escape(title)}</b> — {html.escape(text)}</li>'
        for title, text in project["decisions"]
    )
    tags = "".join(f'<span class="tag">{html.escape(tag)}</span>' for tag in project["tags"])
    page = TEMPLATE
    replacements = {
        "%%TITLE%%": html.escape(project["title"]),
        "%%LEAD%%": html.escape(project["lead"]),
        "%%ACCENT%%": project["accent"],
        "%%NUM%%": str(project["num"]),
        "%%KEY%%": project["key"],
        "%%TAGS%%": tags,
        "%%DECISIONS%%": decisions,
        "%%API%%": render_api(project["api"]),
        "%%WS_PANEL%%": WS_PANEL if project.get("ws") else "",
        "%%CODE%%": code(project["code"]),
        "%%NOTE%%": html.escape(project["note"]),
        "%%PAGER%%": render_pager(index),
    }
    for token, value in replacements.items():
        page = page.replace(token, value)
    return page


def main() -> None:
    os.makedirs(OUT_DIR, exist_ok=True)
    for index, project in enumerate(PROJECTS):
        path = os.path.join(OUT_DIR, f'{project["key"]}.html')
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(build(project, index))
        print(f"{path}  {os.path.getsize(path) // 1024} KB")


if __name__ == "__main__":
    main()
