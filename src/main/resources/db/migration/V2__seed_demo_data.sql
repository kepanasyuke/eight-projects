-- Демонстрационные данные: задачи, заметки, счета для транзакций и таблица результатов игры.

insert into assignee (name, email, created_at) values
    ('Анна Петрова', 'anna@example.com', timestamp '2026-01-12 09:00:00'),
    ('Игорь Волков', 'igor@example.com', timestamp '2026-01-12 09:05:00'),
    ('Мария Соколова', 'maria@example.com', timestamp '2026-01-12 09:10:00');

insert into work_item (title, description, status, priority, assignee_id, version, created_at, updated_at) values
    ('Реализовать REST-контракт', 'OpenAPI, DTO, валидация, пагинация', 'IN_PROGRESS', 1, 1, 0,
     timestamp '2026-01-13 10:00:00', timestamp '2026-01-13 10:00:00'),
    ('Убрать N+1 в выдаче задач', 'fetch join и DTO-проекция', 'IN_PROGRESS', 2, 2, 0,
     timestamp '2026-01-13 10:15:00', timestamp '2026-01-13 10:15:00'),
    ('Настроить границы транзакций', 'REQUIRES_NEW для аудита', 'NEW', 2, 1, 0,
     timestamp '2026-01-13 10:30:00', timestamp '2026-01-13 10:30:00'),
    ('Ограничить пул и тайм-ауты', 'AbortPolicy и orTimeout', 'BLOCKED', 1, 3, 0,
     timestamp '2026-01-13 11:00:00', timestamp '2026-01-13 11:00:00'),
    ('Подключить outbox', 'согласование записи и отправки', 'NEW', 3, 2, 0,
     timestamp '2026-01-13 11:20:00', timestamp '2026-01-13 11:20:00'),
    ('Закрыть XXE в парсере', 'disable-doctype-decl', 'DONE', 1, 3, 0,
     timestamp '2026-01-14 09:00:00', timestamp '2026-01-15 18:00:00'),
    ('Проверить цепочку сертификатов', 'KeyStore и срок действия', 'NEW', 4, 1, 0,
     timestamp '2026-01-14 09:30:00', timestamp '2026-01-14 09:30:00'),
    ('Лимиты WebSocket-сообщений', 'размер буфера и прав доступа', 'CANCELLED', 5, null, 0,
     timestamp '2026-01-14 10:00:00', timestamp '2026-01-14 12:00:00');

insert into work_note (work_item_id, author, content, created_at) values
    (1, 'Анна Петрова', 'Контракт согласован с архитектором, ошибки отдаём в формате problem-details.', timestamp '2026-01-13 12:00:00'),
    (1, 'Игорь Волков', 'Ключ идемпотентности сохраняем в отдельной таблице перед изменением.', timestamp '2026-01-13 12:20:00'),
    (2, 'Игорь Волков', 'Число запросов сверяем по статистике Hibernate: было 1 + N, стало 2.', timestamp '2026-01-13 13:00:00'),
    (4, 'Мария Соколова', 'Ждём решение по размеру очереди, иначе снова упрёмся в перегрузку.', timestamp '2026-01-13 14:00:00');

insert into ledger_account (code, balance, version) values
    ('ACC-001', 1000.00, 0),
    ('ACC-002', 250.00, 0);

insert into game_score (player, level, score, seconds, created_at) values
    ('архитектор', 8, 9600, 214, timestamp '2026-01-20 20:00:00'),
    ('senior', 8, 9200, 236, timestamp '2026-01-20 20:10:00'),
    ('middle', 6, 7100, 188, timestamp '2026-01-20 20:20:00');
