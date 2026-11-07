package ru.multitool.eight.p1rest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.multitool.eight.common.ApiException;
import ru.multitool.eight.common.PageResponse;
import ru.multitool.eight.domain.Assignee;
import ru.multitool.eight.domain.AssigneeRepository;
import ru.multitool.eight.domain.WorkItem;
import ru.multitool.eight.domain.WorkItemRepository;
import ru.multitool.eight.domain.WorkItemStatus;

/**
 * Сервис CRUD для рабочей задачи: валидация контракта, пагинация, смена статуса.
 * Маппинг в DTO выполняется внутри транзакции, потому что open-in-view отключён.
 */
@Service
public class WorkItemService {

    private static final int MAX_PAGE_SIZE = 100;

    private final WorkItemRepository items;
    private final AssigneeRepository assignees;

    public WorkItemService(WorkItemRepository items, AssigneeRepository assignees) {
        this.items = items;
        this.assignees = assignees;
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkItemResponse> list(WorkItemStatus status, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Order.desc("priority"), Sort.Order.asc("id")));
        Page<WorkItem> found = status == null
                ? this.items.findAll(pageable)
                : this.items.findByStatusWithAssignee(status, pageable);
        PageResponse<WorkItemResponse> response = PageResponse.of(found, WorkItemResponse::from);
        return response;
    }

    @Transactional(readOnly = true)
    public WorkItemResponse get(Long id) {
        WorkItem item = loadItem(id);
        WorkItemResponse response = WorkItemResponse.from(item);
        return response;
    }

    @Transactional
    public WorkItemResponse create(WorkItemRequest request) {
        Assignee assignee = loadAssignee(request.assigneeId());
        WorkItem item = new WorkItem(request.title().trim(), request.description(), request.priority(), assignee);
        WorkItem saved = this.items.save(item);
        WorkItemResponse response = WorkItemResponse.from(saved);
        return response;
    }

    @Transactional
    public WorkItemResponse update(Long id, WorkItemRequest request) {
        WorkItem item = loadItem(id);
        Assignee assignee = loadAssignee(request.assigneeId());
        item.setTitle(request.title().trim());
        item.setDescription(request.description());
        item.setPriority(request.priority());
        item.setAssignee(assignee);
        WorkItemResponse response = WorkItemResponse.from(item);
        return response;
    }

    @Transactional
    public WorkItemResponse changeStatus(Long id, WorkItemStatus target) {
        WorkItem item = loadItem(id);
        item.changeStatus(target);
        WorkItemResponse response = WorkItemResponse.from(item);
        return response;
    }

    @Transactional
    public WorkItemResponse addNote(Long id, NoteRequest request) {
        WorkItem item = loadItem(id);
        item.addNote(request.author().trim(), request.content().trim());
        WorkItemResponse response = WorkItemResponse.from(item);
        return response;
    }

    @Transactional
    public void delete(Long id) {
        WorkItem item = loadItem(id);
        this.items.delete(item);
    }

    private WorkItem loadItem(Long id) {
        WorkItem item = this.items.findById(id)
                .orElseThrow(() -> ApiException.notFound("Задача " + id + " не найдена"));
        return item;
    }

    private Assignee loadAssignee(Long id) {
        Assignee assignee = this.assignees.findById(id)
                .orElseThrow(() -> ApiException.notFound("Исполнитель " + id + " не найден"));
        return assignee;
    }
}
