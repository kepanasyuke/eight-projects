package ru.eight.domain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Машина состояний рабочей задачи. Переходы описаны явно, проверка недопустимого
 * перехода выбрасывает {@link IllegalStateException} — так модель нельзя обойти.
 */
public final class WorkItemStateMachine {

    private static final Map<WorkItemStatus, Set<WorkItemStatus>> ALLOWED = buildTransitions();

    private WorkItemStateMachine() {
    }

    private static Map<WorkItemStatus, Set<WorkItemStatus>> buildTransitions() {
        Map<WorkItemStatus, Set<WorkItemStatus>> transitions = new EnumMap<>(WorkItemStatus.class);
        transitions.put(WorkItemStatus.NEW, Set.of(WorkItemStatus.IN_PROGRESS, WorkItemStatus.CANCELLED));
        transitions.put(WorkItemStatus.IN_PROGRESS,
                Set.of(WorkItemStatus.BLOCKED, WorkItemStatus.DONE, WorkItemStatus.CANCELLED));
        transitions.put(WorkItemStatus.BLOCKED, Set.of(WorkItemStatus.IN_PROGRESS, WorkItemStatus.CANCELLED));
        transitions.put(WorkItemStatus.DONE, Set.of());
        transitions.put(WorkItemStatus.CANCELLED, Set.of());
        return Map.copyOf(transitions);
    }

    public static boolean canTransition(WorkItemStatus from, WorkItemStatus to) {
        Set<WorkItemStatus> targets = ALLOWED.getOrDefault(from, Set.of());
        return targets.contains(to);
    }

    public static WorkItemStatus transition(WorkItemStatus from, WorkItemStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Переход " + from + " -> " + to + " недопустим");
        }
        return to;
    }

    public static List<WorkItemStatus> allowedTargets(WorkItemStatus from) {
        List<WorkItemStatus> targets = new ArrayList<>(ALLOWED.getOrDefault(from, Set.of()));
        targets.sort(Enum::compareTo);
        return targets;
    }

    public static List<WorkItemStatus> states() {
        return List.of(WorkItemStatus.values());
    }
}
