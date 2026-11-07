package ru.eight.p3tx;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Аудит операции. Пишется в отдельной транзакции, чтобы пережить откат бизнес-операции.
 */
@Entity
@Table(name = "audit_record")
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kind", nullable = false, length = 40)
    private String kind;

    @Column(name = "message", nullable = false, length = 400)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AuditRecord() {
        this.createdAt = Instant.now();
    }

    public AuditRecord(String kind, String message) {
        this.kind = kind;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return this.id;
    }

    public String getKind() {
        return this.kind;
    }

    public String getMessage() {
        return this.message;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }
}
