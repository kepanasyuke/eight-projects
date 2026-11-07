package ru.multitool.eight.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * Единая обработка ошибок: прикладные исключения, валидация, конфликты записи.
 * Каждый метод имеет один выход — один return в конце.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
        ResponseEntity<ApiError> response = ResponseEntity.status(ex.getStatus())
                .body(ApiError.of(ex.getCode(), ex.getMessage(), ex.getDetails(), request.getRequestURI()));
        return response;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.add(error.getField() + ": " + error.getDefaultMessage());
        }
        ResponseEntity<ApiError> response = ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("VALIDATION_FAILED", "Тело запроса не прошло валидацию", details, request.getRequestURI()));
        return response;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        List<String> details = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            details.add(violation.getPropertyPath() + ": " + violation.getMessage());
        }
        ResponseEntity<ApiError> response = ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("VALIDATION_FAILED", "Параметры запроса не прошли валидацию", details, request.getRequestURI()));
        return response;
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        ResponseEntity<ApiError> response = ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("MISSING_HEADER", "Не передан обязательный заголовок " + ex.getHeaderName(),
                        request.getRequestURI()));
        return response;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        ResponseEntity<ApiError> response = ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("MALFORMED_JSON", "Тело запроса не является корректным JSON", request.getRequestURI()));
        return response;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimistic(ObjectOptimisticLockingFailureException ex,
                                                     HttpServletRequest request) {
        ResponseEntity<ApiError> response = ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("OPTIMISTIC_LOCK", "Запись изменена параллельной транзакцией, повторите чтение",
                        request.getRequestURI()));
        return response;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        LOG.warn("Нарушение целостности при обработке {}", request.getRequestURI());
        ResponseEntity<ApiError> response = ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("DATA_INTEGRITY_VIOLATION", "Операция нарушает ограничения базы данных",
                        request.getRequestURI()));
        return response;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        String value = ex.getValue() == null ? "null" : String.valueOf(ex.getValue());
        ResponseEntity<ApiError> response = ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("INVALID_PARAMETER",
                        "Параметр " + ex.getName() + " не принимает значение " + value, request.getRequestURI()));
        return response;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        ResponseEntity<ApiError> response = ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("INVALID_STATE", ex.getMessage(), request.getRequestURI()));
        return response;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleMissingResource(NoResourceFoundException ex, HttpServletRequest request) {
        ResponseEntity<ApiError> response = ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("NOT_FOUND", "Ресурс " + request.getRequestURI() + " не найден",
                        request.getRequestURI()));
        return response;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        LOG.error("Необработанная ошибка на {}", request.getRequestURI(), ex);
        ResponseEntity<ApiError> response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "Внутренняя ошибка сервиса", request.getRequestURI()));
        return response;
    }
}
