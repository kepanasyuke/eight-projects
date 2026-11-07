package ru.eight.p1rest;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.eight.common.PageResponse;
import ru.eight.domain.WorkItemStatus;

/**
 * Проект 1. REST API по контракту: валидация DTO, пагинация, коды ошибок,
 * идемпотентное создание по заголовку Idempotency-Key.
 */
@RestController
@RequestMapping("/api/v1/work-items")
public class WorkItemController {

    private static final String CREATE_OPERATION = "create-work-item";

    private final WorkItemService service;
    private final IdempotencyService idempotency;

    public WorkItemController(WorkItemService service, IdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @GetMapping
    public PageResponse<WorkItemResponse> list(@RequestParam(required = false) WorkItemStatus status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        PageResponse<WorkItemResponse> response = this.service.list(status, page, size);
        return response;
    }

    @GetMapping("/{id}")
    public WorkItemResponse get(@PathVariable Long id) {
        WorkItemResponse response = this.service.get(id);
        return response;
    }

    @PostMapping
    public ResponseEntity<WorkItemResponse> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                   @Valid @RequestBody WorkItemRequest request) {
        WorkItemResponse created = this.idempotency.execute(idempotencyKey, CREATE_OPERATION,
                WorkItemResponse.class, () -> this.service.create(request));
        ResponseEntity<WorkItemResponse> response = ResponseEntity.status(HttpStatus.CREATED).body(created);
        return response;
    }

    @PutMapping("/{id}")
    public WorkItemResponse update(@PathVariable Long id, @Valid @RequestBody WorkItemRequest request) {
        WorkItemResponse response = this.service.update(id, request);
        return response;
    }

    @PatchMapping("/{id}/status")
    public WorkItemResponse changeStatus(@PathVariable Long id, @RequestParam WorkItemStatus target) {
        WorkItemResponse response = this.service.changeStatus(id, target);
        return response;
    }

    @PostMapping("/{id}/notes")
    public WorkItemResponse addNote(@PathVariable Long id, @Valid @RequestBody NoteRequest request) {
        WorkItemResponse response = this.service.addNote(id, request);
        return response;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        this.service.delete(id);
        ResponseEntity<Void> response = ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        return response;
    }
}
