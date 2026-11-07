package ru.eight.p2jpa;

import ru.eight.domain.WorkItemStatus;

/**
 * DTO-проекция: одна выборка вместо N+1, наружу уходят только нужные поля.
 */
public record WorkItemSummary(
        Long id,
        String title,
        String assigneeName,
        long notesCount,
        WorkItemStatus status
) {
}
