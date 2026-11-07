# Восемь проектов

Восемь backend-проектов на Java 17 и Spring Boot 3.3 плюс витрина с анимациями и платформер из восьми
уровней. Каждый блок закрывает тему из вакансии Middle Java Developer: контракт REST, устранение N+1,
границы транзакций, многопоточность, брокер сообщений с outbox и DLQ, XML/XSD, подпись и сертификаты,
WebSocket с машиной состояний.

## Быстрый старт

```bash
./mvnw spring-boot:run      # H2 в режиме PostgreSQL, миграции Flyway применяются на старте
# или
./mvnw package && java -jar target/eight-projects.jar
```

Приложение поднимается на `http://localhost:8080`:

| Адрес | Что там |
| --- | --- |
| `/` | витрина: восемь карточек с GIF и живыми данными |
| `/projects/p1.html` … `/projects/p8.html` | страница проекта: задача, решения, вызов API, фрагмент кода |
| `/game.html` | платформер из восьми уровней и таблица лидеров |
| `/actuator/health` | состояние приложения |
| `/api/showcase/projects` | JSON витрины (единый источник правды для страниц) |

### PostgreSQL вместо H2

```bash
docker compose up -d          # postgres:16-alpine, база eight
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

Профиль `postgres` меняет только datasource: схема и миграции те же, что и для H2.

## Что внутри

| № | Проект | Ключевые решения | Эндпоинты |
| --- | --- | --- | --- |
| 1 | REST API по контракту | DTO отделены от сущности, `@Valid`, пагинация, идемпотентность по `Idempotency-Key`, единый `ApiError` | `/api/v1/work-items` |
| 2 | JPA без N+1 | naive / fetch join / EntityGraph / DTO-проекция, `@Version`, `SELECT FOR UPDATE`, статистика Hibernate | `/api/v2/jpa/*` |
| 3 | Границы транзакций | `REQUIRED`, `REQUIRES_NEW` в отдельном бине, откат перевода, демонстрация self-invocation | `/api/v3/tx/*` |
| 4 | Многопоточность | пул 8/64 с `AbortPolicy`, `orTimeout`, гонка `long` против `AtomicLong`, дедлок и его лечение | `/api/v4/concurrency/*` |
| 5 | Брокер и Outbox | партиции, группы, оффсеты, ограниченные повторы, DLQ, дедупликация, Transactional Outbox | `/api/v5/bus/*` |
| 6 | XML и XSD | JAXB с явным namespace, `include` схем, валидация `SchemaFactory`, запрет DTD и внешних сущностей | `/api/v6/xml/*` |
| 7 | Подпись и сертификаты | `Signature` через JCA, PKCS12 KeyStore, X.509, проверка срока и цепочки, демонстрация подмены | `/api/v7/crypto/*` |
| 8 | WebSocket и состояния | `TextWebSocketHandler`, лимит размера сообщения, валидация переходов, освобождение сессий | `ws://localhost:8080/ws/state`, `/api/v8/ws/schema` |

Игровой модуль: `/api/game/levels`, `POST /api/game/scores`, `GET /api/game/leaderboard`,
`GET /api/game/stats`.

## Структура

```
src/main/java/ru/multitool/eight
├── common/      ApiError, ApiException, GlobalExceptionHandler, PageResponse
├── domain/      WorkItem, Assignee, WorkNote, WorkItemStateMachine, репозитории
├── p1rest/      REST-контракт, идемпотентность
├── p2jpa/       варианты загрузки связей и отчёты о запросах
├── p3tx/        счета, переводы, аудит в отдельной транзакции
├── p4conc/      пул, CompletableFuture, гонки, дедлок
├── p5bus/       in-memory брокер с семантикой Kafka и outbox
├── p6xml/       JAXB-модель, валидация XSD, защита парсера
├── p7crypto/    подпись, проверка, паспорт сертификата
├── p8ws/        WebSocket и машина состояний
├── game/        уровни, результаты, лидерборд
└── showcase/    метаданные витрины

src/main/resources
├── db/migration/    Flyway: схема, демо-данные, правка ограничений
├── xsd/             invoice.xsd + common-types.xsd
├── crypto/          демо-хранилище PKCS12
└── static/          витрина, страницы проектов, игра, ассеты, GIF
```

Страницы проектов и GIF генерируются скриптами: `python tools/make_gifs.py`, `python tools/make_pages.py`.

## Тесты

```bash
./mvnw test
```

43 теста: машина состояний, сервис XML (валидация, XXE, сборка), обработчик WebSocket на моке сессии
и сквозной `ApiIntegrationTest` на MockMvc — контракт REST, идемпотентность, недопустимые переходы,
переживающий откат аудит, DLQ, outbox, подпись и лидерборд.

## Ограничения и допущения

- **КриптоПро.** Демо работает на стандартном JCA-провайдере с RSA-ключом из ресурсов, чтобы проект
  запускался без лицензии. Для ГОСТ достаточно подключить провайдера JCP/JCSP и указать его алгоритм
  в `eight.crypto.signature-algorithm` — код сервиса менять не нужно.
- **Брокер.** Реализована in-memory модель с семантикой Kafka (партиции, группы, оффсеты, retry, DLQ,
  дедупликация), а не клиент настоящего кластера: цель — воспроизводимая демонстрация поведения.
- **База.** По умолчанию H2 в режиме PostgreSQL; для реального PostgreSQL есть профиль `postgres`
  и `docker-compose.yml`.
