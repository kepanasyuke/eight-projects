package ru.multitool.eight.p3tx;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findTop20ByOrderByIdDesc();
}
