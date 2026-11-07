package ru.multitool.eight.p2jpa;

import java.util.List;

/**
 * Отчёт о запросе: сколько SQL-операторов реально ушло в базу и сколько строк вернулось.
 */
public record QueryReport(
        String mode,
        long statements,
        int rows,
        long durationMs,
        List<Row> sample,
        String comment
) {

    public record Row(Long id, String title, String assigneeName, long notesCount) {
    }
}
