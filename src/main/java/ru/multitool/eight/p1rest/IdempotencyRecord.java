package ru.multitool.eight.p1rest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Сохранённый результат изменяющего запроса: ключ операции, тело ответа и статус.
 * Уникальность ключа гарантирует база, а не приложение.
 */
@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 120)
    private String key;

    @Column(name = "operation", nullable = false, length = 120)
    private String operation;

    @Column(name = "response_body", nullable = false)
    private String responseBody;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected IdempotencyRecord() {
        this.createdAt = Instant.now();
    }

    public IdempotencyRecord(String key, String operation, String responseBody, int statusCode) {
        this.key = key;
        this.operation = operation;
        this.responseBody = responseBody;
        this.statusCode = statusCode;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return this.id;
    }

    public String getKey() {
        return this.key;
    }

    public String getOperation() {
        return this.operation;
    }

    public String getResponseBody() {
        return this.responseBody;
    }

    public int getStatusCode() {
        return this.statusCode;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }
}
