/* Демо-данные консоли: ответы записаны с работающего приложения 07.11.2026. */
window.EIGHT_DEMO = {
  capturedAt: "2026-11-07",
  routes: {
    "GET /actuator/health": {
      "status": "UP"
    },
    "GET /api/game/leaderboard": [
      {
        "createdAt": "2026-01-20T17:00:00Z",
        "id": 1,
        "level": 8,
        "player": "архитектор",
        "score": 9600,
        "seconds": 214
      },
      {
        "createdAt": "2026-01-20T17:10:00Z",
        "id": 2,
        "level": 8,
        "player": "senior",
        "score": 9200,
        "seconds": 236
      },
      {
        "createdAt": "2026-01-20T17:20:00Z",
        "id": 3,
        "level": 6,
        "player": "middle",
        "score": 7100,
        "seconds": 188
      }
    ],
    "GET /api/game/stats": {
      "levels": 8,
      "runsPerLevel": {
        "N+1": 0,
        "Гонка": 0,
        "Границы": 0,
        "Контракт": 0,
        "Очередь": 0,
        "Подпись": 0,
        "Состояние": 2,
        "Схема": 1
      },
      "totalRuns": 3
    },
    "GET /api/v1/assignees": [
      {
        "email": "anna@example.com",
        "id": 1,
        "name": "Анна Петрова"
      },
      {
        "email": "igor@example.com",
        "id": 2,
        "name": "Игорь Волков"
      },
      {
        "email": "maria@example.com",
        "id": 3,
        "name": "Мария Соколова"
      }
    ],
    "GET /api/v1/work-items": {
      "hasNext": false,
      "items": [
        {
          "createdAt": "2026-01-14T07:00:00Z",
          "description": "размер буфера и прав доступа",
          "id": 8,
          "notesCount": 0,
          "priority": 5,
          "status": "CANCELLED",
          "title": "Лимиты WebSocket-сообщений",
          "updatedAt": "2026-01-14T09:00:00Z",
          "version": 0
        },
        {
          "assigneeId": 1,
          "assigneeName": "Анна Петрова",
          "createdAt": "2026-01-14T06:30:00Z",
          "description": "KeyStore и срок действия",
          "id": 7,
          "notesCount": 0,
          "priority": 4,
          "status": "NEW",
          "title": "Проверить цепочку сертификатов",
          "updatedAt": "2026-01-14T06:30:00Z",
          "version": 0
        },
        {
          "assigneeId": 2,
          "assigneeName": "Игорь Волков",
          "createdAt": "2026-01-13T08:20:00Z",
          "description": "согласование записи и отправки",
          "id": 5,
          "notesCount": 0,
          "priority": 3,
          "status": "NEW",
          "title": "Подключить outbox",
          "updatedAt": "2026-01-13T08:20:00Z",
          "version": 0
        },
        {
          "assigneeId": 2,
          "assigneeName": "Игорь Волков",
          "createdAt": "2026-01-13T07:15:00Z",
          "description": "fetch join и DTO-проекция",
          "id": 2,
          "notesCount": 1,
          "priority": 2,
          "status": "IN_PROGRESS",
          "title": "Убрать N+1 в выдаче задач",
          "updatedAt": "2026-01-13T07:15:00Z",
          "version": 0
        },
        {
          "assigneeId": 1,
          "assigneeName": "Анна Петрова",
          "createdAt": "2026-01-13T07:30:00Z",
          "description": "REQUIRES_NEW для аудита",
          "id": 3,
          "notesCount": 0,
          "priority": 2,
          "status": "NEW",
          "title": "Настроить границы транзакций",
          "updatedAt": "2026-01-13T07:30:00Z",
          "version": 0
        },
        {
          "assigneeId": 1,
          "assigneeName": "Анна Петрова",
          "createdAt": "2026-01-13T07:00:00Z",
          "description": "OpenAPI, DTO, валидация, пагинация",
          "id": 1,
          "notesCount": 2,
          "priority": 1,
          "status": "IN_PROGRESS",
          "title": "Реализовать REST-контракт",
          "updatedAt": "2026-01-13T07:00:00Z",
          "version": 0
        },
        {
          "assigneeId": 3,
          "assigneeName": "Мария Соколова",
          "createdAt": "2026-01-13T08:00:00Z",
          "description": "AbortPolicy и orTimeout",
          "id": 4,
          "notesCount": 1,
          "priority": 1,
          "status": "BLOCKED",
          "title": "Ограничить пул и тайм-ауты",
          "updatedAt": "2026-01-13T08:00:00Z",
          "version": 0
        },
        {
          "assigneeId": 3,
          "assigneeName": "Мария Соколова",
          "createdAt": "2026-01-14T06:00:00Z",
          "description": "disable-doctype-decl",
          "id": 6,
          "notesCount": 0,
          "priority": 1,
          "status": "DONE",
          "title": "Закрыть XXE в парсере",
          "updatedAt": "2026-01-15T15:00:00Z",
          "version": 0
        }
      ],
      "page": 0,
      "size": 10,
      "totalItems": 8,
      "totalPages": 1
    },
    "GET /api/v2/jpa/entity-graph": {
      "comment": "@EntityGraph подгружает исполнителя; заметки всё ещё ленивые — остаточные запросы",
      "durationMs": 28,
      "mode": "entity-graph",
      "rows": 3,
      "sample": [
        {
          "assigneeName": "Анна Петрова",
          "id": 3,
          "notesCount": 0,
          "title": "Настроить границы транзакций"
        },
        {
          "assigneeName": "Игорь Волков",
          "id": 5,
          "notesCount": 0,
          "title": "Подключить outbox"
        },
        {
          "assigneeName": "Анна Петрова",
          "id": 7,
          "notesCount": 0,
          "title": "Проверить цепочку сертификатов"
        }
      ],
      "statements": 4
    },
    "GET /api/v2/jpa/fetch-join": {
      "comment": "Один запрос с join fetch по исполнителю и заметкам",
      "durationMs": 28,
      "mode": "fetch-join",
      "rows": 3,
      "sample": [
        {
          "assigneeName": "Анна Петрова",
          "id": 3,
          "notesCount": 0,
          "title": "Настроить границы транзакций"
        },
        {
          "assigneeName": "Игорь Волков",
          "id": 5,
          "notesCount": 0,
          "title": "Подключить outbox"
        },
        {
          "assigneeName": "Анна Петрова",
          "id": 7,
          "notesCount": 0,
          "title": "Проверить цепочку сертификатов"
        }
      ],
      "statements": 1
    },
    "GET /api/v2/jpa/naive": {
      "comment": "Ленивые связи: 1 запрос на страницу и по 1–2 на каждую строку",
      "durationMs": 10,
      "mode": "naive",
      "rows": 3,
      "sample": [
        {
          "assigneeName": "Анна Петрова",
          "id": 3,
          "notesCount": 0,
          "title": "Настроить границы транзакций"
        },
        {
          "assigneeName": "Игорь Волков",
          "id": 5,
          "notesCount": 0,
          "title": "Подключить outbox"
        },
        {
          "assigneeName": "Анна Петрова",
          "id": 7,
          "notesCount": 0,
          "title": "Проверить цепочку сертификатов"
        }
      ],
      "statements": 6
    },
    "GET /api/v2/jpa/projection": {
      "comment": "DTO-проекция: нужные поля одним запросом, сущности не поднимаются",
      "durationMs": 2,
      "mode": "projection",
      "rows": 3,
      "sample": [
        {
          "assigneeName": "Анна Петрова",
          "id": 3,
          "notesCount": 0,
          "title": "Настроить границы транзакций"
        },
        {
          "assigneeName": "Игорь Волков",
          "id": 5,
          "notesCount": 0,
          "title": "Подключить outbox"
        },
        {
          "assigneeName": "Анна Петрова",
          "id": 7,
          "notesCount": 0,
          "title": "Проверить цепочку сертификатов"
        }
      ],
      "statements": 1
    },
    "GET /api/v3/tx/accounts": [
      {
        "balance": 850.0,
        "code": "ACC-001",
        "id": 1,
        "version": 1
      },
      {
        "balance": 400.0,
        "code": "ACC-002",
        "id": 2,
        "version": 1
      }
    ],
    "GET /api/v3/tx/audit": [
      {
        "createdAt": "2026-11-07T19:24:34.340736Z",
        "id": 3,
        "kind": "REQUIRES_NEW",
        "message": "аудит зафиксирован до бизнес-операции ACC-001 -> ACC-002"
      },
      {
        "createdAt": "2026-11-07T19:24:12.335879Z",
        "id": 2,
        "kind": "REQUIRES_NEW",
        "message": "аудит зафиксирован до бизнес-операции null -> null"
      },
      {
        "createdAt": "2026-11-07T19:23:57.292396Z",
        "id": 1,
        "kind": "REQUIRES_NEW",
        "message": "аудит зафиксирован до бизнес-операции null -> null"
      }
    ],
    "GET /api/v3/tx/entries": [
      {
        "amount": 150.0,
        "createdAt": "2026-11-07T19:24:34.284143Z",
        "id": 1,
        "operation": "TRANSFER",
        "sourceAccount": "ACC-001",
        "targetAccount": "ACC-002"
      }
    ],
    "GET /api/v4/concurrency/deadlock": {
      "comment": "Оба потока берут блокировки в одном порядке — взаимной блокировки нет",
      "completed": 2,
      "ordered": true,
      "timedOut": 0
    },
    "GET /api/v4/concurrency/parallel": {
      "calls": [
        {
          "durationMs": 157,
          "name": "invoice-api",
          "status": "OK"
        },
        {
          "durationMs": 405,
          "name": "crypto-api",
          "status": "OK"
        },
        {
          "durationMs": 250,
          "name": "warehouse-api",
          "status": "OK"
        }
      ],
      "comment": "Параллельный вызов трёх сервисов быстрее последовательного примерно во столько раз, сколько было одновременных вызовов",
      "sequentialMs": 812,
      "totalMs": 813
    },
    "GET /api/v4/concurrency/pool": {
      "comment": "AbortPolicy явно сообщает о перегрузке вместо бесконечного роста очереди",
      "finished": 12,
      "maxPoolSize": 8,
      "poolSize": 8,
      "queueRemaining": 64,
      "rejected": 0,
      "started": 12,
      "submitted": 12
    },
    "GET /api/v4/concurrency/race": {
      "comment": "Гонки не случилось — на этой машине операции не пересеклись, повторите запуск",
      "expected": 4000,
      "iterations": 1000,
      "lostUpdates": 0,
      "safeValue": 4000,
      "threads": 4,
      "unsafeValue": 4000
    },
    "GET /api/v4/concurrency/timeout": {
      "elapsedMs": 1214,
      "message": "Задача не уложилась в 1200 мс и была отменена",
      "status": "TIMEOUT",
      "timeoutMs": 1200,
      "workMillis": 1900
    },
    "GET /api/v5/bus/scenario": {
      "acknowledged": 6,
      "deadLetterQueueSize": 0,
      "deadLettered": 0,
      "deduplicated": 1,
      "delivered": 7,
      "events": [
        "сообщение msg-108910473863100 подтверждено после 1 попытки",
        "сообщение msg-108910473944400 подтверждено после 1 попытки",
        "сообщение msg-108910473918000 подтверждено после 1 попытки",
        "сообщение msg-108910473952800 подтверждено после 1 попытки",
        "сообщение msg-108910473934300 подтверждено после 1 попытки",
        "сообщение msg-108910473963600 подтверждено после 1 попытки",
        "дубликат msg-108910473963600 пропущен: уже обработан"
      ],
      "group": "order-processor",
      "maxAttempts": 3,
      "partitionSizes": {
        "partition-0": 4,
        "partition-1": 4,
        "partition-2": 6
      },
      "retried": 0,
      "topic": "orders"
    },
    "GET /api/v5/bus/state": {
      "consumerPositions": {
        "order-processor": 14
      },
      "deadLetters": [],
      "outboxMessages": [],
      "partitionSizes": {
        "partition-0": 4,
        "partition-1": 4,
        "partition-2": 6
      },
      "pendingOutbox": 0,
      "topic": "orders"
    },
    "GET /api/v6/xml/sample": {
      "valid": true,
      "xml": "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<invoice version=\"1\" xmlns=\"urn:eight:invoice\">\n    <number>IN-2026/000142</number>\n    <issuedAt>2026-11-06</issuedAt>\n    <seller>\n        <name>ООО «Восемь проектов»</name>\n        <inn>7701234567</inn>\n    </seller>\n    <buyer>\n        <name>АО «Заказчик»</name>\n        <inn>7709876543</inn>\n    </buyer>\n    <items>\n        <item line=\"1\">\n            <name>Разработка backend-компонента</name>\n            <quantity>1</quantity>\n            <price>180000.00</price>\n        </item>\n        <item line=\"2\">\n            <name>Интеграционные тесты</name>\n            <quantity>3</quantity>\n            <price>24000.00</price>\n        </item>\n    </items>\n    <total>252000.00</total>\n</invoice>\n"
    },
    "GET /api/v7/crypto/certificate": {
      "chainLength": 1,
      "daysUntilExpiry": 3649,
      "issuer": "CN=Eight Demo,O=MultiTool,C=RU",
      "keyAlgorithm": "RSA",
      "notAfter": "2036-11-03T19:40:35Z",
      "notBefore": "2026-11-06T19:40:35Z",
      "serialNumber": "bf02cb85040bb895",
      "sha256Fingerprint": "A1:92:B3:03:DD:4B:8C:D2:1D:A6:19:4C:55:64:29:66:D9:D7:82:BA:E5:90:44:3B:D9:7D:77:C0:BA:AF:E7:F6",
      "signatureAlgorithm": "SHA256withRSA",
      "subject": "CN=Eight Demo,O=MultiTool,C=RU"
    },
    "GET /api/v7/crypto/chain": [
      "CN=Eight Demo,O=MultiTool,C=RU / SHA256withRSA"
    ],
    "GET /api/v8/ws/schema": {
      "activeSessions": 0,
      "endpoint": "ws://localhost:8080/ws/state",
      "states": [
        "NEW",
        "IN_PROGRESS",
        "BLOCKED",
        "DONE",
        "CANCELLED"
      ],
      "transitions": {
        "BLOCKED": [
          "IN_PROGRESS",
          "CANCELLED"
        ],
        "CANCELLED": [],
        "DONE": [],
        "IN_PROGRESS": [
          "BLOCKED",
          "DONE",
          "CANCELLED"
        ],
        "NEW": [
          "IN_PROGRESS",
          "CANCELLED"
        ]
      }
    },
    "POST /api/v3/tx/propagation-demo": {
      "auditRecordsAfterBeanCall": 1,
      "auditRecordsAfterDirectCall": 0,
      "beanCallFailed": true,
      "directCallFailed": true,
      "explanation": "Вызов this.метод() идёт мимо прокси: запись аудита попала в общую транзакцию и откатилась. Вызов через отдельный бин с REQUIRES_NEW переживает откат."
    },
    "POST /api/v3/tx/transfer": {
      "amount": 10,
      "entryId": 3,
      "sourceBalance": 840.0,
      "sourceCode": "ACC-001",
      "targetBalance": 410.0,
      "targetCode": "ACC-002"
    },
    "POST /api/v3/tx/transfer-with-audit": {
      "auditRecordsWritten": 1,
      "message": "Демонстрационный сбой после движения денег",
      "rolledBack": true,
      "state": [
        {
          "balance": 840.0,
          "code": "ACC-001",
          "id": 1,
          "version": 2
        },
        {
          "balance": 410.0,
          "code": "ACC-002",
          "id": 2,
          "version": 2
        }
      ]
    },
    "POST /api/v6/xml/parse": {
      "errors": [],
      "invoice": {
        "buyerInn": "7709876543",
        "buyerName": "АО «Заказчик»",
        "issuedAt": "2026-11-06",
        "items": [
          {
            "line": 1,
            "name": "Разработка backend-компонента",
            "price": 180000.0,
            "quantity": 1
          },
          {
            "line": 2,
            "name": "Интеграционные тесты",
            "price": 24000.0,
            "quantity": 3
          }
        ],
        "number": "IN-2026/000142",
        "sellerInn": "7701234567",
        "sellerName": "ООО «Восемь проектов»",
        "total": 252000.0,
        "version": 1
      },
      "message": "Документ разобран",
      "valid": true
    },
    "POST /api/v6/xml/validate": {
      "comment": "Валидация по XSD с ограничением внешних схем и DTD",
      "errors": [],
      "length": 755,
      "valid": true
    },
    "POST /api/v7/crypto/sign": {
      "algorithm": "SHA256withRSA",
      "certificateSubject": "CN=Eight Demo,O=MultiTool,C=RU",
      "payloadBytes": 25,
      "signatureBase64": "ucLVlM2haxRDr6dapKD249fnSHy7NZ9+0YnmqjiIWU9aze+hM2lYxjZdJoZCf+1hwRUS/sXyUrbjg8ie9Z67VvlKPSdC78vC+H9buad84jwkfnvOtFIqVmlSmE66on+JlXG37Elsu4OB80UbtRbzdn3QRkIK6GuyItCiqOTxdLLKnkwx6xIWy4N0ZHe18G6KB6I6rMSRvSg6nEesU9qwkmuAuadrz/djw46aEWUQ7jwcfb8rCvwS7XOnQROs5akUJmC2vWD7yax54mw5yDDG93IWEg52MDPqG0/CFFrHDtg1bm2dKzkv4VVAPMdU++a1hcxKt0TI3gDEgJt0kJZiyw==",
      "signedAt": "2026-11-07T19:25:51.473008700Z"
    }
  }
};
