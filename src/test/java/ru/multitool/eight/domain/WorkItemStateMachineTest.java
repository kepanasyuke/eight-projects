package ru.multitool.eight.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Машина состояний — единственное место, где описаны допустимые переходы,
 * поэтому проверяется отдельно от Spring-контекста.
 */
class WorkItemStateMachineTest {

    @ParameterizedTest
    @CsvSource({
            "NEW, IN_PROGRESS",
            "NEW, CANCELLED",
            "IN_PROGRESS, BLOCKED",
            "IN_PROGRESS, DONE",
            "IN_PROGRESS, CANCELLED",
            "BLOCKED, IN_PROGRESS",
            "BLOCKED, CANCELLED"
    })
    @DisplayName("допустимые переходы разрешены")
    void allowedTransitions(WorkItemStatus from, WorkItemStatus to) {
        assertThat(WorkItemStateMachine.canTransition(from, to)).isTrue();
        assertThat(WorkItemStateMachine.transition(from, to)).isEqualTo(to);
    }

    @ParameterizedTest
    @CsvSource({
            "NEW, DONE",
            "NEW, NEW",
            "NEW, BLOCKED",
            "DONE, IN_PROGRESS",
            "CANCELLED, NEW",
            "BLOCKED, DONE"
    })
    @DisplayName("недопустимые переходы отклоняются исключением")
    void forbiddenTransitions(WorkItemStatus from, WorkItemStatus to) {
        assertThat(WorkItemStateMachine.canTransition(from, to)).isFalse();
        assertThatThrownBy(() -> WorkItemStateMachine.transition(from, to))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("недопустим");
    }

    @Test
    @DisplayName("из финальных состояний переходов нет")
    void finalStatesAreClosed() {
        assertThat(WorkItemStateMachine.allowedTargets(WorkItemStatus.DONE)).isEmpty();
        assertThat(WorkItemStateMachine.allowedTargets(WorkItemStatus.CANCELLED)).isEmpty();
    }

    @Test
    @DisplayName("список переходов упорядочен и полон")
    void allowedTargetsAreSorted() {
        List<WorkItemStatus> targets = WorkItemStateMachine.allowedTargets(WorkItemStatus.IN_PROGRESS);

        assertThat(targets).containsExactly(
                WorkItemStatus.BLOCKED, WorkItemStatus.DONE, WorkItemStatus.CANCELLED);
        assertThat(WorkItemStateMachine.states()).hasSize(WorkItemStatus.values().length);
    }
}
