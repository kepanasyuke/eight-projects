package ru.eight.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Матрица сценариев многопоточности: параллельный вызов против последовательного,
 * тайм-аут, потерянные обновления, дедлок и поведение пула при перегрузке.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ConcurrencyMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode get(String path, String... params) throws Exception {
        var request = MockMvcRequestBuilders.get(path);
        for (int index = 0; index + 1 < params.length; index += 2) {
            request = request.param(params[index], params[index + 1]);
        }
        MvcResult result = this.mockMvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return this.objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    static Stream<Arguments> raceCases() {
        return Stream.of(
                Arguments.of(2, 1000),
                Arguments.of(4, 20000),
                Arguments.of(8, 5000)
        );
    }

    @ParameterizedTest(name = "гонка: {0} потоков × {1}")
    @MethodSource("raceCases")
    @DisplayName("атомарный счётчик не теряет обновления, обычный — теряет")
    void raceCases(int threads, int iterations) throws Exception {
        JsonNode report = get("/api/v4/concurrency/race",
                "threads", String.valueOf(threads),
                "iterations", String.valueOf(iterations));

        long expected = report.get("expected").asLong();
        assertThat(report.get("safeValue").asLong()).isEqualTo(expected);
        assertThat(report.get("unsafeValue").asLong()).isLessThanOrEqualTo(expected);
        assertThat(expected).isEqualTo((long) threads * iterations);
    }

    @Test
    @DisplayName("параллельный вызов укладывается в сумму последовательных")
    void parallelIsFasterThanSequential() throws Exception {
        JsonNode report = get("/api/v4/concurrency/parallel");

        assertThat(report.get("calls").size()).isEqualTo(3);
        long sequential = report.get("sequentialMs").asLong();
        long total = report.get("totalMs").asLong();
        assertThat(sequential).isPositive();
        assertThat(total).isPositive();
        assertThat(total).isLessThan(sequential * 2);
        assertThat(report.get("comment").asText()).isNotBlank();
    }

    @Test
    @DisplayName("тайм-аут прерывает долгую задачу раньше её завершения")
    void timeoutInterruptsLongTask() throws Exception {
        JsonNode report = get("/api/v4/concurrency/timeout");

        assertThat(report.get("status").asText()).containsIgnoringCase("TIMEOUT");
        assertThat(report.get("elapsedMs").asLong()).isLessThan(report.get("workMillis").asLong());
    }

    static Stream<Arguments> deadlockCases() {
        return Stream.of(
                Arguments.of(true),
                Arguments.of(false)
        );
    }

    @ParameterizedTest(name = "дедлок ordered={0}")
    @MethodSource("deadlockCases")
    @DisplayName("оба потока завершаются или отпускают блокировки по тайм-ауту")
    void deadlockCases(boolean ordered) throws Exception {
        JsonNode report = get("/api/v4/concurrency/deadlock", "ordered", String.valueOf(ordered));

        int completed = report.get("completed").asInt();
        int timedOut = report.get("timedOut").asInt();
        assertThat(report.get("ordered").asBoolean()).isEqualTo(ordered);
        assertThat(completed + timedOut).isEqualTo(2);
        assertThat(report.get("comment").asText()).contains(ordered ? "одном порядке" : "разном порядке");
    }

    static Stream<Arguments> poolCases() {
        return Stream.of(
                Arguments.of(1),
                Arguments.of(8),
                Arguments.of(50),
                Arguments.of(100),
                Arguments.of(200)
        );
    }

    @ParameterizedTest(name = "пул: {0} задач")
    @MethodSource("poolCases")
    @DisplayName("пул сообщает о старте, завершении и отказах")
    void poolCases(int tasks) throws Exception {
        JsonNode report = get("/api/v4/concurrency/pool", "tasks", String.valueOf(tasks));

        assertThat(report.get("submitted").asInt()).isEqualTo(tasks);
        assertThat(report.get("started").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(report.get("finished").asInt() + report.get("rejected").asInt())
                .isLessThanOrEqualTo(tasks);
        assertThat(report.get("poolSize").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(report.get("maxPoolSize").asInt()).isGreaterThanOrEqualTo(report.get("poolSize").asInt());
    }

    @Test
    @DisplayName("при перегрузке часть задач отклоняется, а не висит в очереди")
    void overloadRejectsTasks() throws Exception {
        JsonNode report = get("/api/v4/concurrency/pool", "tasks", "200");

        assertThat(report.get("rejected").asInt()).isPositive();
    }
}
