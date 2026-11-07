package ru.multitool.eight.p3tx;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_account", nullable = false, length = 40)
    private String sourceAccount;

    @Column(name = "target_account", nullable = false, length = 40)
    private String targetAccount;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "operation", nullable = false, length = 20)
    private String operation;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected LedgerEntry() {
        this.amount = BigDecimal.ZERO;
        this.createdAt = Instant.now();
    }

    public LedgerEntry(String sourceAccount, String targetAccount, BigDecimal amount, String operation) {
        this.sourceAccount = sourceAccount;
        this.targetAccount = targetAccount;
        this.amount = amount;
        this.operation = operation;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return this.id;
    }

    public String getSourceAccount() {
        return this.sourceAccount;
    }

    public String getTargetAccount() {
        return this.targetAccount;
    }

    public BigDecimal getAmount() {
        return this.amount;
    }

    public String getOperation() {
        return this.operation;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }
}
