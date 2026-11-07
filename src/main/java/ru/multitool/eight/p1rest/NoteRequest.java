package ru.multitool.eight.p1rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoteRequest(
        @NotBlank(message = "автор обязателен")
        @Size(max = 80, message = "имя автора не длиннее 80 символов")
        String author,

        @NotBlank(message = "текст заметки обязателен")
        @Size(max = 1000, message = "заметка не длиннее 1000 символов")
        String content
) {
}
