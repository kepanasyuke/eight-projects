package ru.eight.domain;

/**
 * Состояния рабочей задачи и допустимые переходы между ними.
 */
public enum WorkItemStatus {
    NEW,
    IN_PROGRESS,
    BLOCKED,
    DONE,
    CANCELLED
}
