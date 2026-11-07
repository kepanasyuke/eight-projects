package ru.eight.p3tx;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {

    List<AuditRecord> findTop10ByOrderByIdDesc();

    long countByKind(String kind);
}
