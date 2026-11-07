package ru.multitool.eight.p5bus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Transactional Outbox: событие сохраняется в той же транзакции, что и бизнес-изменение,
 * а публикуется после коммита.
 */
@Entity
@Table(name = "outbox_message")
public class OutboxMessage {

    public enum Status {
        PENDING,
        SENT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic", nullable = false, length = 60)
    private String topic;

    @Column(name = "payload", nullable = false, length = 4000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;

    protected OutboxMessage() {
        this.createdAt = Instant.now();
        this.status = Status.PENDING;
    }

    public OutboxMessage(String topic, String payload) {
        this.topic = topic;
        this.payload = payload;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    public void markSent() {
        this.status = Status.SENT;
        this.sentAt = Instant.now();
        this.attempts = this.attempts + 1;
    }

    public void markAttempt() {
        this.attempts = this.attempts + 1;
    }

    public Long getId() {
        return this.id;
    }

    public String getTopic() {
        return this.topic;
    }

    public String getPayload() {
        return this.payload;
    }

    public Status getStatus() {
        return this.status;
    }

    public int getAttempts() {
        return this.attempts;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public Instant getSentAt() {
        return this.sentAt;
    }
}
