package ru.multitool.eight.p3tx;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, Long> {

    Optional<LedgerAccount> findByCode(String code);
}
