package ru.multitool.eight.p3tx;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import ru.multitool.eight.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Счёт для демонстрации границ транзакций: списание и зачисление в одной транзакции.
 */
@Entity
@Table(name = "ledger_account")
public class LedgerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "balance", nullable = false, precision = 18, scale = 2)
    private BigDecimal balance;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected LedgerAccount() {
        this.balance = BigDecimal.ZERO;
    }

    public LedgerAccount(String code, BigDecimal initial) {
        this.code = code;
        this.balance = initial;
    }

    public void withdraw(BigDecimal amount) {
        BigDecimal next = this.balance.subtract(amount);
        if (next.signum() < 0) {
            throw ApiException.badRequest("INSUFFICIENT_FUNDS",
                    "Недостаточно средств на счёте " + this.code,
                    java.util.List.of("баланс: " + this.balance + ", требуется: " + amount));
        }
        this.balance = next;
    }

    public void deposit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    public Long getId() {
        return this.id;
    }

    public String getCode() {
        return this.code;
    }

    public BigDecimal getBalance() {
        return this.balance;
    }

    public long getVersion() {
        return this.version;
    }
}
