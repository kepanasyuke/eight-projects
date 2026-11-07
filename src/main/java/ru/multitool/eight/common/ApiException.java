package ru.multitool.eight.common;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Прикладная ошибка с кодом, HTTP-статусом и списком подробностей.
 */
public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final transient List<String> details;

    public ApiException(String code, String message, HttpStatus status) {
        this(code, message, status, List.of());
    }

    public ApiException(String code, String message, HttpStatus status, List<String> details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = List.copyOf(details);
    }

    public static ApiException notFound(String message) {
        return new ApiException("NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(code, message, HttpStatus.CONFLICT);
    }

    public static ApiException badRequest(String code, String message, List<String> details) {
        return new ApiException(code, message, HttpStatus.BAD_REQUEST, details);
    }

    public String getCode() {
        return this.code;
    }

    public HttpStatus getStatus() {
        return this.status;
    }

    public List<String> getDetails() {
        return this.details;
    }
}
