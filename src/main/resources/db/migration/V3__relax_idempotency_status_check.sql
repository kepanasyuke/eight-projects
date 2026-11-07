-- Ключ идемпотентности резервируется со статусом 0 («операция выполняется»),
-- поэтому ограничение из V1 не должно отсекать ноль. Остальные коды — реальные HTTP-статусы.

alter table idempotency_record drop constraint ck_idempotency_status;

alter table idempotency_record
    add constraint ck_idempotency_status check (status_code between 0 and 599);
