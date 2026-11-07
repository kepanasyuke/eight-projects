package ru.multitool.eight.api;

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
 * Матрица способов загрузки связей: каждый режим проверяется на всех статусах,
 * плюс конфликт версий и пессимистичная блокировка.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class JpaModeMatrixTest {

    private static final String[] MODES = {"naive", "fetch-join", "entity-graph", "projection"};
    private static final String[] STATUSES = {"NEW", "IN_PROGRESS", "BLOCKED", "DONE", "CANCELLED"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    static Stream<Arguments> modeAndStatus() {
        Stream.Builder<Arguments> builder = Stream.builder();
        for (String mode : MODES) {
            for (String status : STATUSES) {
                builder.add(Arguments.of(mode, status));
            }
        }
        return builder.build();
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("modeAndStatus")
    @DisplayName("отчёт содержит режим, число запросов и строки")
    void reportPerMode(String mode, String status) throws Exception {
        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.get("/api/v2/jpa/" + mode)
                        .param("status", status))
                .andReturn();

        JsonNode report = this.objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(report.get("mode").asText()).isEqualTo(mode);
        assertThat(report.get("statements").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(report.get("rows").asInt()).isGreaterThanOrEqualTo(0);
        assertThat(report.get("comment").asText()).isNotBlank();
    }

    @Test
    @DisplayName("наивная выборка делает не меньше запросов, чем проекция")
    void naiveIsNotCheaperThanProjection() throws Exception {
        long naive = statements("naive");
        long projection = statements("projection");

        assertThat(naive).isGreaterThanOrEqualTo(projection);
    }

    private long statements(String mode) throws Exception {
        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.get("/api/v2/jpa/" + mode)
                        .param("status", "IN_PROGRESS"))
                .andReturn();
        return this.objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("statements").asLong();
    }

    @Test
    @DisplayName("конфликт версий обнаруживается оптимистичной блокировкой")
    void optimisticConflictDetected() throws Exception {
        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.post("/api/v2/jpa/optimistic")
                        .param("id", "3"))
                .andReturn();

        JsonNode report = this.objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(report.get("conflictDetected").asBoolean()).isTrue();
        assertThat(report.get("message").asText()).contains("Устаревшая версия");
    }

    @Test
    @DisplayName("пессимистичная блокировка удерживает строку заявленное время")
    void pessimisticHoldsRow() throws Exception {
        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.post("/api/v2/jpa/pessimistic")
                        .param("id", "1")
                        .param("holdMillis", "40"))
                .andReturn();

        JsonNode report = this.objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(report.get("locked").asBoolean()).isTrue();
        assertThat(report.get("heldMillis").asLong()).isGreaterThanOrEqualTo(35);
    }

    static Stream<Arguments> unknownId() {
        return Stream.of(
                Arguments.of("optimistic", 987654),
                Arguments.of("pessimistic", 987654)
        );
    }

    @ParameterizedTest(name = "{0} для несуществующей задачи")
    @MethodSource("unknownId")
    @DisplayName("операция над отсутствующей задачей даёт 404")
    void unknownItem(String mode, int id) throws Exception {
        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.post("/api/v2/jpa/" + mode)
                        .param("id", String.valueOf(id)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("неизвестный статус в отчёте даёт 400")
    void unknownStatus() throws Exception {
        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.get("/api/v2/jpa/naive")
                        .param("status", "WHATEVER"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("отчёт проекции содержит только нужные поля строки")
    void projectionRowsCarryNeededFields() throws Exception {
        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.get("/api/v2/jpa/projection")
                        .param("status", "IN_PROGRESS"))
                .andReturn();

        JsonNode report = this.objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        if (report.get("rows").asInt() > 0) {
            JsonNode row = report.get("sample").get(0);
            assertThat(row.has("title")).isTrue();
            assertThat(row.has("notesCount")).isTrue();
        }
    }
}
