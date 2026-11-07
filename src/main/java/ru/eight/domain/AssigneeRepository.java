package ru.eight.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssigneeRepository extends JpaRepository<Assignee, Long> {

    Optional<Assignee> findByEmail(String email);

    List<Assignee> findAllByOrderByNameAsc();
}
