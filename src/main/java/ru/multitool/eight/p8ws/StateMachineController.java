package ru.multitool.eight.p8ws;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.multitool.eight.domain.WorkItemStateMachine;
import ru.multitool.eight.domain.WorkItemStatus;

/**
 * Проект 8. Описание машины состояний и лимитов для клиента.
 */
@RestController
@RequestMapping("/api/v8/ws")
public class StateMachineController {

    private final StateSocketHandler handler;

    public StateMachineController(StateSocketHandler handler) {
        this.handler = handler;
    }

    @GetMapping("/schema")
    public SchemaView schema() {
        Map<String, List<String>> transitions = new LinkedHashMap<>();
        for (WorkItemStatus status : WorkItemStatus.values()) {
            List<String> targets = new ArrayList<>();
            for (WorkItemStatus target : WorkItemStateMachine.allowedTargets(status)) {
                targets.add(target.name());
            }
            transitions.put(status.name(), List.copyOf(targets));
        }
        SchemaView view = new SchemaView("ws://localhost:8080/ws/state", transitions,
                WorkItemStateMachine.states().stream().map(Enum::name).toList(), this.handler.activeSessions());
        return view;
    }

    public record SchemaView(String endpoint, Map<String, List<String>> transitions, List<String> states,
                             int activeSessions) {
    }
}
