package ru.eight.p2jpa;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.eight.domain.WorkItemStatus;

/**
 * Проект 2. Сравнение способов загрузки связей по числу реальных SQL-запросов.
 */
@RestController
@RequestMapping("/api/v2/jpa")
public class JpaDemoController {

    private final JpaShowcaseService service;

    public JpaDemoController(JpaShowcaseService service) {
        this.service = service;
    }

    @GetMapping("/naive")
    public QueryReport naive(@RequestParam(defaultValue = "NEW") WorkItemStatus status) {
        QueryReport report = this.service.naive(status);
        return report;
    }

    @GetMapping("/fetch-join")
    public QueryReport fetchJoin(@RequestParam(defaultValue = "NEW") WorkItemStatus status) {
        QueryReport report = this.service.fetchJoin(status);
        return report;
    }

    @GetMapping("/entity-graph")
    public QueryReport entityGraph(@RequestParam(defaultValue = "NEW") WorkItemStatus status) {
        QueryReport report = this.service.entityGraph(status);
        return report;
    }

    @GetMapping("/projection")
    public QueryReport projection(@RequestParam(defaultValue = "NEW") WorkItemStatus status) {
        QueryReport report = this.service.projection(status);
        return report;
    }

    @PostMapping("/optimistic")
    public JpaShowcaseService.OptimisticReport optimistic(@RequestParam Long id) {
        JpaShowcaseService.OptimisticReport report = this.service.optimisticConflict(id);
        return report;
    }

    @PostMapping("/pessimistic")
    public JpaShowcaseService.LockReport pessimistic(@RequestParam Long id,
                                                     @RequestParam(defaultValue = "120") long holdMillis) {
        JpaShowcaseService.LockReport report = this.service.pessimisticLock(id, holdMillis);
        return report;
    }
}
