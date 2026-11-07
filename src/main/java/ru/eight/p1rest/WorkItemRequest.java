package ru.eight.p1rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Входящий DTO создания/обновления задачи. Валидация выполняется до входа в сервис.
 */
public record WorkItemRequest(
        @NotBlank(message = "заголовок обязателен")
        @Size(min = 3, max = 120, message = "длина заголовка от 3 до 120 символов")
        String title,

        @Size(max = 2000, message = "описание не длиннее 2000 символов")
        String description,

        @Min(value = 1, message = "приоритет от 1")
        @Max(value = 5, message = "приоритет до 5")
        int priority,

        @NotNull(message = "исполнитель обязателен")
        @Positive(message = "идентификатор исполнителя должен быть положительным")
        Long assigneeId
) {
}
