package ru.multitool.eight.common;

import java.time.Instant;
import java.util.List;

/**
 * Единый контракт ошибки API.
 */
public record ApiError(
        String code,
        String message,
        List<String> details,
        Instant timestamp,
        String path
) {

    public static ApiError of(String code, String message, String path) {
        return new ApiError(code, message, List.of(), Instant.now(), path);
    }

    public static ApiError of(String code, String message, List<String> details, String path) {
        return new ApiError(code, message, List.copyOf(details), Instant.now(), path);
    }
}
