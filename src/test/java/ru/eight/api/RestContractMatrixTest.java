package ru.eight.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Матрица контракта REST: границы полей, пагинация, фильтры, переходы статусов,
 * идемпотентность и коды ошибок для отсутствующих записей.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RestContractMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String body(String title, Integer priority, Long assigneeId, String description) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (title != null) {
            payload.put("title", title);
        }
        if (description != null) {
            payload.put("description", description);
        }
        if (priority != null) {
            payload.put("priority", priority);
        }
        if (assigneeId != null) {
            payload.put("assigneeId", assigneeId);
        }
        return this.objectMapper.writeValueAsString(payload);
    }

    private MvcResult create(String title, int priority, long assigneeId) throws Exception {
        return this.mockMvc.perform(post("/api/v1/work-items")
                        .header("Idempotency-Key", "matrix-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(title, priority, assigneeId, null)))
                .andReturn();
    }

    private long createId(String title) throws Exception {
        MvcResult result = create(title, 3, 1L);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return this.objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    static Stream<Arguments> titleBoundaries() {
        return Stream.of(
                Arguments.of("пустой заголовок", "", 400),
                Arguments.of("длина 1", "a", 400),
                Arguments.of("длина 2", "ab", 400),
                Arguments.of("длина 3", "abc", 201),
                Arguments.of("длина 119", "a".repeat(119), 201),
                Arguments.of("длина 120", "a".repeat(120), 201),
                Arguments.of("длина 121", "a".repeat(121), 400),
                Arguments.of("только пробелы", "     ", 400)
        );
    }

    @ParameterizedTest(name = "заголовок: {0}")
    @MethodSource("titleBoundaries")
    @DisplayName("длина заголовка ограничена 3..120 символами")
    void titleBoundary(String name, String title, int expected) throws Exception {
        MvcResult result = this.mockMvc.perform(post("/api/v1/work-items")
                        .header("Idempotency-Key", "matrix-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(title, 3, 1L, null)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).as(name).isEqualTo(expected);
    }

    static Stream<Arguments> descriptionBoundaries() {
        return Stream.of(
                Arguments.of("пустое описание", "", 201),
                Arguments.of("описание 2000", "a".repeat(2000), 201),
                Arguments.of("описание 2001", "a".repeat(2001), 400),
                Arguments.of("описание отсутствует", null, 201)
        );
    }

    @ParameterizedTest(name = "описание: {0}")
    @MethodSource("descriptionBoundaries")
    @DisplayName("описание не длиннее 2000 символов и необязательно")
    void descriptionBoundary(String name, String description, int expected) throws Exception {
        MvcResult result = this.mockMvc.perform(post("/api/v1/work-items")
                        .header("Idempotency-Key", "matrix-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Задача " + UUID.randomUUID(), 3, 1L, description)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).as(name).isEqualTo(expected);
    }

    static Stream<Arguments> priorityBoundaries() {
        return Stream.of(
                Arguments.of(0, 400),
                Arguments.of(1, 201),
                Arguments.of(2, 201),
                Arguments.of(3, 201),
                Arguments.of(4, 201),
                Arguments.of(5, 201),
                Arguments.of(6, 400),
                Arguments.of(-1, 400)
        );
    }

    @ParameterizedTest(name = "приоритет {0} → {1}")
    @MethodSource("priorityBoundaries")
    @DisplayName("приоритет принимает значения 1..5")
    void priorityBoundary(int priority, int expected) throws Exception {
        MvcResult result = this.mockMvc.perform(post("/api/v1/work-items")
                        .header("Idempotency-Key", "matrix-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Задача " + UUID.randomUUID(), priority, 1L, null)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(expected);
    }

    static Stream<Arguments> assigneeCases() {
        return Stream.of(
                Arguments.of("без исполнителя", null, 400),
                Arguments.of("ноль", 0L, 400),
                Arguments.of("отрицательный", -1L, 400),
                Arguments.of("несуществующий", 999L, 404),
                Arguments.of("первый из справочника", 1L, 201),
                Arguments.of("второй из справочника", 2L, 201),
                Arguments.of("третий из справочника", 3L, 201)
        );
    }

    @ParameterizedTest(name = "исполнитель: {0}")
    @MethodSource("assigneeCases")
    @DisplayName("исполнитель обязателен и должен существовать")
    void assigneeBoundary(String name, Long assigneeId, int expected) throws Exception {
        MvcResult result = this.mockMvc.perform(post("/api/v1/work-items")
                        .header("Idempotency-Key", "matrix-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Задача " + UUID.randomUUID(), 3, assigneeId, null)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).as(name).isEqualTo(expected);
    }

    @Test
    @DisplayName("битый JSON даёт 400 с кодом MALFORMED_JSON")
    void malformedJson() throws Exception {
        MvcResult result = this.mockMvc.perform(post("/api/v1/work-items")
                        .header("Idempotency-Key", "matrix-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(this.objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText())
                .isEqualTo("MALFORMED_JSON");
    }

    static Stream<Arguments> pagingCases() {
        return Stream.of(
                Arguments.of(-1, 5, 0, 5),
                Arguments.of(0, 0, 0, 1),
                Arguments.of(0, 1, 0, 1),
                Arguments.of(0, 3, 0, 3),
                Arguments.of(0, 100, 0, 100),
                Arguments.of(0, 101, 0, 100),
                Arguments.of(2, 2, 2, 2)
        );
    }

    @ParameterizedTest(name = "page={0}, size={1} → page={2}, size={3}")
    @MethodSource("pagingCases")
    @DisplayName("пагинация нормализует границы")
    void pagination(int page, int size, int expectedPage, int expectedSize) throws Exception {
        MvcResult result = this.mockMvc.perform(get("/api/v1/work-items")
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();

        JsonNode payload = this.objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(payload.get("page").asInt()).isEqualTo(expectedPage);
        assertThat(payload.get("size").asInt()).isEqualTo(expectedSize);
        assertThat(payload.get("items").size()).isLessThanOrEqualTo(expectedSize);
    }

    static Stream<Arguments> statuses() {
        return Stream.of("NEW", "IN_PROGRESS", "BLOCKED", "DONE", "CANCELLED")
                .map(status -> Arguments.of(status));
    }

    @ParameterizedTest(name = "фильтр {0}")
    @MethodSource("statuses")
    @DisplayName("фильтр по статусу возвращает только этот статус")
    void statusFilter(String status) throws Exception {
        MvcResult result = this.mockMvc.perform(get("/api/v1/work-items")
                        .param("status", status)
                        .param("size", "100"))
                .andReturn();

        JsonNode items = this.objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
        List<String> found = new ArrayList<>();
        items.forEach(item -> found.add(item.get("status").asText()));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(found).allMatch(status::equals);
    }

    @Test
    @DisplayName("неизвестный статус в фильтре даёт 400 INVALID_PARAMETER")
    void unknownStatusRejected() throws Exception {
        MvcResult result = this.mockMvc.perform(get("/api/v1/work-items").param("status", "FLYING"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(this.objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText())
                .isEqualTo("INVALID_PARAMETER");
    }

    static Stream<Arguments> transitions() {
        return Stream.of(
                Arguments.of("IN_PROGRESS", 200),
                Arguments.of("CANCELLED", 200),
                Arguments.of("BLOCKED", 409),
                Arguments.of("DONE", 409),
                Arguments.of("NEW", 409)
        );
    }

    @ParameterizedTest(name = "NEW → {0} → {1}")
    @MethodSource("transitions")
    @DisplayName("переход из NEW разрешён только в IN_PROGRESS и CANCELLED")
    void patchTransitions(String target, int expected) throws Exception {
        long id = createId("Переход " + UUID.randomUUID());

        MvcResult result = this.mockMvc.perform(patch("/api/v1/work-items/{id}/status", id)
                        .param("target", target))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(expected);
        if (expected == 200) {
            assertThat(this.objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("status").asText()).isEqualTo(target);
        }
    }

    @Test
    @DisplayName("неизвестный целевой статус даёт 400")
    void patchUnknownTarget() throws Exception {
        long id = createId("Переход " + UUID.randomUUID());

        this.mockMvc.perform(patch("/api/v1/work-items/{id}/status", id).param("target", "DONE_"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }

    static Stream<Arguments> missingRecords() {
        return Stream.of(
                Arguments.of("GET", 404),
                Arguments.of("PUT", 404),
                Arguments.of("PATCH", 404),
                Arguments.of("DELETE", 404)
        );
    }

    @ParameterizedTest(name = "{0} отсутствующей задачи → {1}")
    @MethodSource("missingRecords")
    @DisplayName("операции над отсутствующей задачей дают 404")
    void missingRecord(String method, int expected) throws Exception {
        long id = 900000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1000);
        int status;
        switch (method) {
            case "GET" -> status = this.mockMvc.perform(get("/api/v1/work-items/{id}", id))
                    .andReturn().getResponse().getStatus();
            case "PUT" -> status = this.mockMvc.perform(put("/api/v1/work-items/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("Обновление", 3, 1L, null)))
                    .andReturn().getResponse().getStatus();
            case "PATCH" -> status = this.mockMvc.perform(patch("/api/v1/work-items/{id}/status", id)
                            .param("target", "IN_PROGRESS"))
                    .andReturn().getResponse().getStatus();
            default -> status = this.mockMvc.perform(delete("/api/v1/work-items/{id}", id))
                    .andReturn().getResponse().getStatus();
        }

        assertThat(status).isEqualTo(expected);
    }

    static Stream<Arguments> noteCases() {
        return Stream.of(
                Arguments.of("автор пустой", "", "текст", 400),
                Arguments.of("автор из пробелов", "   ", "текст", 400),
                Arguments.of("автор 81 символ", "a".repeat(81), "текст", 400),
                Arguments.of("автор 80 символов", "a".repeat(80), "текст", 200),
                Arguments.of("текст пустой", "автор", "", 400),
                Arguments.of("текст 1001 символ", "автор", "a".repeat(1001), 400),
                Arguments.of("текст 1000 символов", "автор", "a".repeat(1000), 200),
                Arguments.of("обычная заметка", "Анна", "Согласовано", 200)
        );
    }

    @ParameterizedTest(name = "заметка: {0}")
    @MethodSource("noteCases")
    @DisplayName("заметка проверяется по автору и длине текста")
    void noteValidation(String name, String author, String content, int expected) throws Exception {
        long id = createId("Заметки " + UUID.randomUUID());

        MvcResult result = this.mockMvc.perform(post("/api/v1/work-items/{id}/notes", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(Map.of("author", author, "content", content))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).as(name).isEqualTo(expected);
    }

    @Test
    @DisplayName("повтор с тем же ключом и другим телом возвращает первый результат")
    void idempotencyReplaysFirstResult() throws Exception {
        String key = "replay-" + UUID.randomUUID();

        MvcResult first = this.mockMvc.perform(post("/api/v1/work-items")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Первое тело", 2, 1L, null)))
                .andReturn();
        MvcResult second = this.mockMvc.perform(post("/api/v1/work-items")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Второе тело", 4, 2L, null)))
                .andReturn();

        JsonNode firstBody = this.objectMapper.readTree(first.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        JsonNode secondBody = this.objectMapper.readTree(second.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(secondBody.get("id").asLong()).isEqualTo(firstBody.get("id").asLong());
        assertThat(secondBody.get("title").asText()).isEqualTo("Первое тело");
    }

    @Test
    @DisplayName("разные ключи создают разные задачи")
    void differentKeysCreateDifferentItems() throws Exception {
        long first = createId("Задача A " + UUID.randomUUID());
        long second = createId("Задача B " + UUID.randomUUID());

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    @DisplayName("удаление задачи освобождает идентификатор")
    void deleteRemovesItem() throws Exception {
        long id = createId("Удаляемая " + UUID.randomUUID());

        this.mockMvc.perform(delete("/api/v1/work-items/{id}", id))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());
        this.mockMvc.perform(get("/api/v1/work-items/{id}", id))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    @DisplayName("обновление меняет заголовок и сохраняет идентификатор")
    void updateChangesTitle() throws Exception {
        long id = createId("Старый " + UUID.randomUUID());
        String updated = "Новый " + UUID.randomUUID();

        MvcResult result = this.mockMvc.perform(put("/api/v1/work-items/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(updated, 5, 2L, "описание")))
                .andReturn();

        JsonNode payload = this.objectMapper.readTree(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(payload.get("id").asLong()).isEqualTo(id);
        assertThat(payload.get("title").asText()).isEqualTo(updated);
        assertThat(payload.get("priority").asInt()).isEqualTo(5);
    }

    @Test
    @DisplayName("создание без ключа идемпотентности даёт 400 MISSING_HEADER")
    void missingIdempotencyKey() throws Exception {
        MvcResult result = this.mockMvc.perform(post("/api/v1/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Без ключа", 3, 1L, null)))
                .andReturn();

        assertThat(this.objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText())
                .isEqualTo("MISSING_HEADER");
    }

    @Test
    @DisplayName("ответ содержит notesCount и version")
    void responseCarriesMetadata() throws Exception {
        MvcResult result = this.mockMvc.perform(get("/api/v1/work-items").param("size", "1")).andReturn();

        JsonNode item = this.objectMapper.readTree(result.getResponse().getContentAsString()).get("items").get(0);
        assertThat(item.has("notesCount")).isTrue();
        assertThat(item.has("version")).isTrue();
        assertThat(item.has("createdAt")).isTrue();
    }

    @Test
    @DisplayName("справочник исполнителей отдаёт id, имя и почту")
    void assigneesDirectory() throws Exception {
        MvcResult result = this.mockMvc.perform(get("/api/v1/assignees")).andReturn();

        JsonNode payload = this.objectMapper.readTree(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(payload.isArray()).isTrue();
        assertThat(payload.size()).isGreaterThanOrEqualTo(3);
        assertThat(payload.get(0).has("id")).isTrue();
        assertThat(payload.get(0).get("name").asText()).isNotBlank();
        assertThat(payload.get(0).get("email").asText()).contains("@");
    }

    @Test
    @DisplayName("заметки задачи читаются списком после добавления")
    void notesListed() throws Exception {
        long id = createId("Задача с заметкой " + UUID.randomUUID());

        this.mockMvc.perform(post("/api/v1/work-items/{id}/notes", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"Консоль\",\"content\":\"проверка списка заметок\"}"))
                .andReturn();

        MvcResult result = this.mockMvc.perform(get("/api/v1/work-items/{id}/notes", id)).andReturn();
        JsonNode payload = this.objectMapper.readTree(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(payload.size()).isEqualTo(1);
        assertThat(payload.get(0).get("author").asText()).isEqualTo("Консоль");
        assertThat(payload.get(0).get("content").asText()).isEqualTo("проверка списка заметок");
    }

    @Test
    @DisplayName("заметки отсутствующей задачи дают 404")
    void notesOfMissingTask() throws Exception {
        MvcResult result = this.mockMvc.perform(get("/api/v1/work-items/{id}/notes", 999_999L)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(this.objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText())
                .isEqualTo("NOT_FOUND");
    }
}
