package ru.multitool.eight.p1rest;

import java.time.Instant;
import ru.multitool.eight.domain.WorkItem;
import ru.multitool.eight.domain.WorkItemStatus;

/**
 * Исходящий DTO. Сущности наружу не отдаются: границы сессии и сериализация не смешиваются.
 */
public record WorkItemResponse(
        Long id,
        String title,
        String description,
        WorkItemStatus status,
        int priority,
        Long assigneeId,
        String assigneeName,
        int notesCount,
        long version,
        Instant createdAt,
        Instant updatedAt
) {

    public static WorkItemResponse from(WorkItem item) {
        return new WorkItemResponse(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getStatus(),
                item.getPriority(),
                item.getAssignee() == null ? null : item.getAssignee().getId(),
                item.getAssignee() == null ? null : item.getAssignee().getName(),
                item.getNotes().size(),
                item.getVersion(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
