package ru.eight.p1rest;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.eight.domain.Assignee;
import ru.eight.domain.AssigneeRepository;

/**
 * Проект 1. Справочник исполнителей для формы задачи: консоль получает список
 * из того же контракта, что и остальные данные, без жёстко прописанных значений на клиенте.
 */
@RestController
@RequestMapping("/api/v1/assignees")
public class AssigneeController {

    private final AssigneeRepository assignees;

    public AssigneeController(AssigneeRepository assignees) {
        this.assignees = assignees;
    }

    @GetMapping
    public List<AssigneeView> list() {
        List<AssigneeView> response = this.assignees.findAllByOrderByNameAsc().stream()
                .map(AssigneeController::toView)
                .toList();
        return response;
    }

    private static AssigneeView toView(Assignee assignee) {
        AssigneeView view = new AssigneeView(assignee.getId(), assignee.getName(), assignee.getEmail());
        return view;
    }

    public record AssigneeView(Long id, String name, String email) {
    }
}
