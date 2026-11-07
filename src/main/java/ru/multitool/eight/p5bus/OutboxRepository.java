package ru.multitool.eight.p5bus;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

    List<OutboxMessage> findTop20ByStatusOrderByIdAsc(OutboxMessage.Status status);

    List<OutboxMessage> findTop10ByOrderByIdDesc();

    long countByStatus(OutboxMessage.Status status);
}
