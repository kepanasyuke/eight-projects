package ru.multitool.eight.p1rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.multitool.eight.common.ApiException;

/**
 * Идемпотентность изменяющих запросов через ключ операции.
 *
 * <p>Порядок: сначала резервируется ключ (вставка в таблицу с уникальным индексом),
 * затем выполняется бизнес-операция, затем сохраняется тело ответа. Повторный запрос
 * с тем же ключом получает сохранённый результат, а не создаёт новую сущность.
 *
 * <p>Класс намеренно не транзакционный: вставка-резерв должна фиксироваться отдельно,
 * иначе нарушение уникальности отравит транзакцию вызывающего кода.
 */
@Service
public class IdempotencyService {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyService.class);
    private static final int CREATED = 201;

    private final IdempotencyRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public <T> T execute(String key, String operation, Class<T> responseType, Supplier<T> action) {
        Optional<IdempotencyRecord> existing = this.repository.findByKey(key);
        T result = existing.isPresent()
                ? replay(existing.get(), responseType)
                : createOnce(key, operation, responseType, action);
        return result;
    }

    private <T> T createOnce(String key, String operation, Class<T> responseType, Supplier<T> action) {
        boolean reserved = reserve(key, operation);
        T result = reserved
                ? runAndStore(key, responseType, action)
                : replayLatest(key, responseType);
        return result;
    }

    private <T> T runAndStore(String key, Class<T> responseType, Supplier<T> action) {
        T value = action.get();
        String body = write(value);
        IdempotencyRecord stored = this.repository.findByKey(key).orElseThrow(
                () -> new ApiException("IDEMPOTENCY_LOST", "Резерв ключа идемпотентности потерян",
                        HttpStatus.INTERNAL_SERVER_ERROR));
        IdempotencyRecord completed = new IdempotencyRecord(stored.getKey(), stored.getOperation(), body, CREATED);
        this.repository.delete(stored);
        this.repository.save(completed);
        LOG.info("Ключ {} зафиксирован, ответ сохранён", key);
        return value;
    }

    private <T> T replayLatest(String key, Class<T> responseType) {
        IdempotencyRecord record = this.repository.findByKey(key).orElseThrow(
                () -> new ApiException("IDEMPOTENCY_CONFLICT", "Операция с этим ключом уже выполняется",
                        HttpStatus.CONFLICT));
        return replay(record, responseType);
    }

    private <T> T replay(IdempotencyRecord record, Class<T> responseType) {
        if (record.getStatusCode() == 0) {
            throw new ApiException("OPERATION_IN_PROGRESS",
                    "Операция с этим ключом ещё выполняется, повторите запрос позже", HttpStatus.CONFLICT);
        }
        T value = read(record.getResponseBody(), responseType);
        return value;
    }

    private boolean reserve(String key, String operation) {
        boolean reserved = true;
        try {
            this.repository.saveAndFlush(new IdempotencyRecord(key, operation, "", 0));
        } catch (DataIntegrityViolationException ex) {
            reserved = false;
            LOG.info("Ключ {} уже зарезервирован параллельным запросом", key);
        }
        return reserved;
    }

    private String write(Object value) {
        String json;
        try {
            json = this.objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ApiException("SERIALIZATION_FAILED", "Не удалось сохранить результат операции",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return json;
    }

    private <T> T read(String json, Class<T> responseType) {
        T value;
        try {
            value = this.objectMapper.readValue(json, responseType);
        } catch (JsonProcessingException ex) {
            throw new ApiException("DESERIALIZATION_FAILED", "Не удалось прочитать сохранённый результат",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return value;
    }
}
