package ru.eight.p3tx;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Отдельный бин для записи аудита: вызов идёт через прокси, поэтому REQUIRES_NEW
 * действительно открывает независимую транзакцию.
 */
@Service
public class AuditWriter {

    private final AuditRecordRepository audits;

    public AuditWriter(AuditRecordRepository audits) {
        this.audits = audits;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeInNewTransaction(String kind, String message) {
        this.audits.save(new AuditRecord(kind, message));
    }
}
