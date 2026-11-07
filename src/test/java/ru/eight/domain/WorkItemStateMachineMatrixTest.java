package ru.eight.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Полная матрица переходов: все пары состояний проверяются таблицей,
 * ожидания берутся из спецификации машины состояний, а не из реализации.
 */
class WorkItemStateMachineMatrixTest {

    private static final java.util.Map<WorkItemStatus, List<WorkItemStatus>> EXPECTED = java.util.Map.of(
            WorkItemStatus.NEW, List.of(WorkItemStatus.IN_PROGRESS, WorkItemStatus.CANCELLED),
            WorkItemStatus.IN_PROGRESS,
            List.of(WorkItemStatus.BLOCKED, WorkItemStatus.DONE, WorkItemStatus.CANCELLED),
            WorkItemStatus.BLOCKED, List.of(WorkItemStatus.IN_PROGRESS, WorkItemStatus.CANCELLED),
            WorkItemStatus.DONE, List.of(),
            WorkItemStatus.CANCELLED, List.of()
    );

    static Stream<Arguments> allPairs() {
        return Stream.of(WorkItemStatus.values())
                .flatMap(from -> Stream.of(WorkItemStatus.values())
                        .map(to -> Arguments.of(from, to, EXPECTED.get(from).contains(to))));
    }

    @ParameterizedTest(name = "{0} → {1} допустим: {2}")
    @MethodSource("allPairs")
    @DisplayName("матрица 5×5 совпадает со спецификацией")
    void fullMatrix(WorkItemStatus from, WorkItemStatus to, boolean allowed) {
        assertThat(WorkItemStateMachine.canTransition(from, to)).isEqualTo(allowed);
        if (allowed) {
            assertThat(WorkItemStateMachine.transition(from, to)).isEqualTo(to);
        }
    }

    static Stream<Arguments> forbiddenPairs() {
        return allPairs()
                .map(Arguments::get)
                .filter(values -> !(Boolean) values[2])
                .map(values -> Arguments.of(values[0], values[1]));
    }

    @ParameterizedTest(name = "{0} → {1} отклоняется")
    @MethodSource("forbiddenPairs")
    @DisplayName("каждый запрещённый переход даёт исключение с обоими состояниями")
    void forbiddenPairsThrow(WorkItemStatus from, WorkItemStatus to) {
        assertThatThrownBy(() -> WorkItemStateMachine.transition(from, to))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(from.name())
                .hasMessageContaining(to.name());
    }

    @ParameterizedTest
    @EnumSource(WorkItemStatus.class)
    @DisplayName("список допустимых переходов совпадает со спецификацией")
    void allowedTargetsPerState(WorkItemStatus from) {
        assertThat(WorkItemStateMachine.allowedTargets(from))
                .containsExactlyElementsOf(EXPECTED.get(from));
    }

    @ParameterizedTest
    @EnumSource(WorkItemStatus.class)
    @DisplayName("состояние не переходит само в себя")
    void noSelfTransition(WorkItemStatus state) {
        assertThat(WorkItemStateMachine.canTransition(state, state)).isFalse();
    }

    @Test
    @DisplayName("перечень состояний неизменяем и содержит все значения")
    void statesAreImmutable() {
        List<WorkItemStatus> states = WorkItemStateMachine.states();

        assertThat(states).hasSize(5);
        assertThatThrownBy(() -> states.add(WorkItemStatus.NEW))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("повторный вызов transition идемпотентен по результату")
    void transitionIsPure() {
        assertThat(WorkItemStateMachine.transition(WorkItemStatus.NEW, WorkItemStatus.IN_PROGRESS))
                .isEqualTo(WorkItemStateMachine.transition(WorkItemStatus.NEW, WorkItemStatus.IN_PROGRESS));
    }
}
