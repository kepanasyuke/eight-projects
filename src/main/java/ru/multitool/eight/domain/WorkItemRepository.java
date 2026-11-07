package ru.multitool.eight.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import ru.multitool.eight.p2jpa.WorkItemSummary;

public interface WorkItemRepository extends JpaRepository<WorkItem, Long> {

    Page<WorkItem> findByStatus(WorkItemStatus status, Pageable pageable);

    long countByStatus(WorkItemStatus status);

    List<WorkItem> findByAssigneeId(Long assigneeId);

    @EntityGraph(attributePaths = {"assignee"})
    @Query(value = "select w from WorkItem w where w.status = :status",
            countQuery = "select count(w) from WorkItem w where w.status = :status")
    Page<WorkItem> findByStatusWithAssignee(@Param("status") WorkItemStatus status, Pageable pageable);

    @Query("select distinct w from WorkItem w join fetch w.assignee left join fetch w.notes where w.status = :status")
    List<WorkItem> findAllDeepByStatus(@Param("status") WorkItemStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WorkItem w where w.id = :id")
    Optional<WorkItem> findByIdForUpdate(@Param("id") Long id);

    @Query("select new ru.multitool.eight.p2jpa.WorkItemSummary(w.id, w.title, a.name, size(w.notes), w.status) "
            + "from WorkItem w join w.assignee a where w.status = :status order by w.id")
    List<WorkItemSummary> summarizeByStatus(@Param("status") WorkItemStatus status);
}
