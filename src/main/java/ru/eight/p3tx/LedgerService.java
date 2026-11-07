package ru.eight.p3tx;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.eight.common.ApiException;

/**
 * Проект 3. Границы транзакций: REQUIRED, REQUIRES_NEW, откат бизнес-операции,
 * ловушка self-invocation (вызов this.method() не идёт через AOP-прокси).
 */
@Service
public class LedgerService {

    private static final Logger LOG = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerAccountRepository accounts;
    private final LedgerEntryRepository entries;
    private final AuditRecordRepository audits;
    private final AuditWriter auditWriter;
    private final LedgerService self;

    public LedgerService(LedgerAccountRepository accounts, LedgerEntryRepository entries,
                         AuditRecordRepository audits, AuditWriter auditWriter, @Lazy LedgerService self) {
        this.accounts = accounts;
        this.entries = entries;
        this.audits = audits;
        this.auditWriter = auditWriter;
        this.self = self;
    }

    @Transactional
    public LedgerAccountView open(String code, BigDecimal initial) {
        LedgerAccount account = new LedgerAccount(code, initial);
        LedgerAccount saved = this.accounts.save(account);
        LedgerAccountView view = LedgerAccountView.from(saved);
        return view;
    }

    @Transactional(readOnly = true)
    public List<LedgerAccountView> balances() {
        List<LedgerAccountView> views = new ArrayList<>();
        for (LedgerAccount account : this.accounts.findAll()) {
            views.add(LedgerAccountView.from(account));
        }
        views.sort((left, right) -> left.code().compareTo(right.code()));
        return views;
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryView> entries() {
        List<LedgerEntryView> views = this.entries.findTop20ByOrderByIdDesc().stream().map(LedgerEntryView::from).toList();
        return views;
    }

    @Transactional(readOnly = true)
    public List<AuditView> auditTrail() {
        List<AuditView> views = this.audits.findTop10ByOrderByIdDesc().stream().map(AuditView::from).toList();
        return views;
    }

    @Transactional
    public TransferResult transfer(String sourceCode, String targetCode, BigDecimal amount) {
        TransferResult result = moveMoney(sourceCode, targetCode, amount, "TRANSFER");
        return result;
    }

    /**
     * Аудит пишется в независимой транзакции, затем бизнес-операция падает.
     * Аудит должен остаться, деньги — нет.
     */
    public RollbackReport transferWithAuditDemo(String sourceCode, String targetCode, BigDecimal amount) {
        long auditBefore = this.audits.count();
        boolean rolledBack = false;
        String message = "Операция завершилась без ошибки";
        try {
            this.self.transferAndFailWithAudit(sourceCode, targetCode, amount);
        } catch (ApiException ex) {
            rolledBack = true;
            message = ex.getMessage();
            LOG.info("Ожидаемый откат демонстрационной операции: {}", ex.getMessage());
        }
        long auditAfter = this.audits.count();
        List<LedgerAccountView> state = balancesOf(sourceCode, targetCode);
        RollbackReport report = new RollbackReport(rolledBack, auditAfter - auditBefore, message, state);
        return report;
    }

    /**
     * Сравнение вызова через this и через отдельный бин с REQUIRES_NEW.
     */
    public PropagationReport demoSelfInvocation() {
        long before = this.audits.count();
        boolean directCallFailed = false;
        try {
            this.self.outerWithDirectSelfCall();
        } catch (ApiException ex) {
            directCallFailed = true;
        }
        long afterDirect = this.audits.count();

        boolean beanCallFailed = false;
        try {
            this.self.outerWithBeanCall();
        } catch (ApiException ex) {
            beanCallFailed = true;
        }
        long afterBean = this.audits.count();

        PropagationReport report = new PropagationReport(
                directCallFailed,
                (int) (afterDirect - before),
                beanCallFailed,
                (int) (afterBean - afterDirect),
                "Вызов this.метод() идёт мимо прокси: запись аудита попала в общую транзакцию и откатилась. "
                        + "Вызов через отдельный бин с REQUIRES_NEW переживает откат."
        );
        return report;
    }

    @Transactional
    public void transferAndFailWithAudit(String sourceCode, String targetCode, BigDecimal amount) {
        this.auditWriter.writeInNewTransaction("REQUIRES_NEW",
                "аудит зафиксирован до бизнес-операции " + sourceCode + " -> " + targetCode);
        moveMoney(sourceCode, targetCode, amount, "TRANSFER_FAIL");
        throw new ApiException("DEMO_FAILURE", "Демонстрационный сбой после движения денег", HttpStatus.CONFLICT);
    }

    @Transactional
    public void outerWithDirectSelfCall() {
        this.writeAuditInNewTransactionDirect("SELF_INVOCATION", "вызов через this — прокси не участвует");
        throw new ApiException("DEMO_SELF_INVOCATION", "Откат внешней транзакции", HttpStatus.CONFLICT);
    }

    /**
     * Аннотация REQUIRES_NEW здесь не сработает: метод вызывается через this.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeAuditInNewTransactionDirect(String kind, String message) {
        this.audits.save(new AuditRecord(kind, message));
    }

    @Transactional
    public void outerWithBeanCall() {
        this.auditWriter.writeInNewTransaction("REQUIRES_NEW", "вызов через бин — независимая транзакция");
        throw new ApiException("DEMO_REQUIRES_NEW", "Откат внешней транзакции", HttpStatus.CONFLICT);
    }

    private TransferResult moveMoney(String sourceCode, String targetCode, BigDecimal amount, String operation) {
        if (amount == null || amount.signum() <= 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Сумма должна быть положительной", List.of());
        }
        LedgerAccount source = loadAccount(sourceCode);
        LedgerAccount target = loadAccount(targetCode);
        source.withdraw(amount);
        target.deposit(amount);
        LedgerEntry entry = this.entries.save(new LedgerEntry(sourceCode, targetCode, amount, operation));
        TransferResult result = new TransferResult(entry.getId(), sourceCode, source.getBalance(), targetCode,
                target.getBalance(), amount);
        return result;
    }

    private List<LedgerAccountView> balancesOf(String... codes) {
        List<LedgerAccountView> views = new ArrayList<>();
        for (String code : codes) {
            this.accounts.findByCode(code).ifPresent(account -> views.add(LedgerAccountView.from(account)));
        }
        return views;
    }

    private LedgerAccount loadAccount(String code) {
        LedgerAccount account = this.accounts.findByCode(code)
                .orElseThrow(() -> ApiException.notFound("Счёт " + code + " не найден"));
        return account;
    }

    public record LedgerAccountView(Long id, String code, BigDecimal balance, long version) {
        public static LedgerAccountView from(LedgerAccount account) {
            return new LedgerAccountView(account.getId(), account.getCode(), account.getBalance(), account.getVersion());
        }
    }

    public record LedgerEntryView(Long id, String sourceAccount, String targetAccount, BigDecimal amount,
                                  String operation, java.time.Instant createdAt) {
        public static LedgerEntryView from(LedgerEntry entry) {
            return new LedgerEntryView(entry.getId(), entry.getSourceAccount(), entry.getTargetAccount(),
                    entry.getAmount(), entry.getOperation(), entry.getCreatedAt());
        }
    }

    public record AuditView(Long id, String kind, String message, java.time.Instant createdAt) {
        public static AuditView from(AuditRecord record) {
            return new AuditView(record.getId(), record.getKind(), record.getMessage(), record.getCreatedAt());
        }
    }

    public record TransferResult(Long entryId, String sourceCode, BigDecimal sourceBalance, String targetCode,
                                 BigDecimal targetBalance, BigDecimal amount) {
    }

    public record RollbackReport(boolean rolledBack, long auditRecordsWritten, String message,
                                 List<LedgerAccountView> state) {
    }

    public record PropagationReport(boolean directCallFailed, int auditRecordsAfterDirectCall,
                                    boolean beanCallFailed, int auditRecordsAfterBeanCall, String explanation) {
    }
}
