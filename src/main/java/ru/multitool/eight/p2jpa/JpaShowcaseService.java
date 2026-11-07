package ru.multitool.eight.p2jpa;

import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.multitool.eight.common.ApiException;
import ru.multitool.eight.domain.WorkItem;
import ru.multitool.eight.domain.WorkItemRepository;
import ru.multitool.eight.domain.WorkItemStatus;

/**
 * Проект 2. JPA/Hibernate: N+1, fetch join, EntityGraph, DTO-проекция,
 * @Version и оптимистичные конфликты, пессимистичная блокировка.
 */
@Service
public class JpaShowcaseService {

    private static final int PAGE_SIZE = 20;

    private final WorkItemRepository items;
    private final EntityManagerFactory entityManagerFactory;
    private final TransactionTemplate transactionTemplate;

    public JpaShowcaseService(WorkItemRepository items, EntityManagerFactory entityManagerFactory,
                              PlatformTransactionManager transactionManager) {
        this.items = items;
        this.entityManagerFactory = entityManagerFactory;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public QueryReport naive(WorkItemStatus status) {
        QueryReport report = measure("naive", "Ленивые связи: 1 запрос на страницу и по 1–2 на каждую строку",
                () -> naiveRows(status));
        return report;
    }

    @Transactional(readOnly = true)
    public QueryReport fetchJoin(WorkItemStatus status) {
        QueryReport report = measure("fetch-join", "Один запрос с join fetch по исполнителю и заметкам",
                () -> fetchJoinRows(status));
        return report;
    }

    @Transactional(readOnly = true)
    public QueryReport entityGraph(WorkItemStatus status) {
        QueryReport report = measure("entity-graph",
                "@EntityGraph подгружает исполнителя; заметки всё ещё ленивые — остаточные запросы",
                () -> entityGraphRows(status));
        return report;
    }

    @Transactional(readOnly = true)
    public QueryReport projection(WorkItemStatus status) {
        QueryReport report = measure("projection", "DTO-проекция: нужные поля одним запросом, сущности не поднимаются",
                () -> projectionRows(status));
        return report;
    }

    public OptimisticReport optimisticConflict(Long id) {
        WorkItem detached = this.transactionTemplate.execute(session -> this.items.findById(id)
                .orElseThrow(() -> ApiException.notFound("Задача " + id + " не найдена")));
        long versionBefore = detached.getVersion();

        this.transactionTemplate.executeWithoutResult(session -> {
            WorkItem fresh = this.items.findById(id).orElseThrow();
            fresh.setTitle(fresh.getTitle() + " [правка параллельной транзакции]");
        });

        boolean detected = false;
        String message = "Конфликт не обнаружен";
        try {
            this.transactionTemplate.executeWithoutResult(session -> this.items.save(detached));
        } catch (ObjectOptimisticLockingFailureException ex) {
            detected = true;
            message = "Устаревшая версия отклонена: запись изменена параллельной транзакцией";
        }

        long versionAfter = this.transactionTemplate.execute(session -> this.items.findById(id)
                .orElseThrow(() -> ApiException.notFound("Задача " + id + " не найдена"))).getVersion();
        OptimisticReport report = new OptimisticReport(id, detected, versionBefore, versionAfter, message);
        return report;
    }

    public LockReport pessimisticLock(Long id, long holdMillis) {
        long startedAt = System.currentTimeMillis();
        this.transactionTemplate.executeWithoutResult(session -> {
            WorkItem item = this.items.findByIdForUpdate(id)
                    .orElseThrow(() -> ApiException.notFound("Задача " + id + " не найдена"));
            item.setPriority(Math.max(1, item.getPriority()));
            parkQuietly(holdMillis);
        });
        long heldMillis = System.currentTimeMillis() - startedAt;
        LockReport report = new LockReport(id, true, heldMillis, "SELECT ... FOR UPDATE держит строку до коммита");
        return report;
    }

    private static void parkQuietly(long millis) {
        try {
            Thread.sleep(Math.max(millis, 0L));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private QueryReport measure(String mode, String comment, Supplier<List<QueryReport.Row>> loader) {
        Statistics statistics = statistics();
        statistics.clear();
        long startedAt = System.nanoTime();
        List<QueryReport.Row> rows = loader.get();
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
        List<QueryReport.Row> sample = rows.subList(0, Math.min(rows.size(), 10));
        QueryReport report = new QueryReport(mode, statistics.getPrepareStatementCount(), rows.size(), durationMs,
                List.copyOf(sample), comment);
        return report;
    }

    private Statistics statistics() {
        Statistics statistics = this.entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        return statistics;
    }

    private List<QueryReport.Row> naiveRows(WorkItemStatus status) {
        List<WorkItem> found = this.items.findByStatus(status, PageRequest.of(0, PAGE_SIZE)).getContent();
        List<QueryReport.Row> rows = mapItems(found);
        return rows;
    }

    private List<QueryReport.Row> fetchJoinRows(WorkItemStatus status) {
        List<WorkItem> found = this.items.findAllDeepByStatus(status);
        List<QueryReport.Row> rows = mapItems(found);
        return rows;
    }

    private List<QueryReport.Row> entityGraphRows(WorkItemStatus status) {
        List<WorkItem> found = this.items.findByStatusWithAssignee(status, PageRequest.of(0, PAGE_SIZE)).getContent();
        List<QueryReport.Row> rows = mapItems(found);
        return rows;
    }

    private List<QueryReport.Row> projectionRows(WorkItemStatus status) {
        List<WorkItemSummary> summaries = this.items.summarizeByStatus(status);
        List<QueryReport.Row> rows = summaries.stream()
                .map(summary -> new QueryReport.Row(summary.id(), summary.title(), summary.assigneeName(),
                        summary.notesCount()))
                .toList();
        return rows;
    }

    private List<QueryReport.Row> mapItems(List<WorkItem> found) {
        List<QueryReport.Row> rows = new ArrayList<>();
        for (WorkItem item : found) {
            String assigneeName = item.getAssignee() == null ? null : item.getAssignee().getName();
            rows.add(new QueryReport.Row(item.getId(), item.getTitle(), assigneeName, item.getNotes().size()));
        }
        return rows;
    }

    public record OptimisticReport(Long id, boolean conflictDetected, long versionBefore, long versionAfter,
                                   String message) {
    }

    public record LockReport(Long id, boolean locked, long heldMillis, String message) {
    }
}
