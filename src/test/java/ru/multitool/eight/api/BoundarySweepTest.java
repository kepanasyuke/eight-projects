package ru.multitool.eight.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Сплошной проход по границам полей: каждая длина проверяется отдельно,
 * чтобы в отчёте было видно конкретное значение, а не диапазон.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class BoundarySweepTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    static IntStream titleLengths() {
        return IntStream.concat(IntStream.rangeClosed(1, 6), IntStream.rangeClosed(118, 122));
    }

    @ParameterizedTest(name = "заголовок длиной {0}")
    @MethodSource("titleLengths")
    @DisplayName("границы заголовка: 3..120 включительно")
    void titleSweep(int length) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "a".repeat(length));
        payload.put("priority", 3);
        payload.put("assigneeId", 1);

        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/work-items")
                        .header("Idempotency-Key", "sweep-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(payload)))
                .andReturn();

        int expected = length >= 3 && length <= 120 ? 201 : 400;
        assertThat(result.getResponse().getStatus()).as("длина " + length).isEqualTo(expected);
    }

    static IntStream descriptionLengths() {
        return IntStream.rangeClosed(1998, 2002);
    }

    @ParameterizedTest(name = "описание длиной {0}")
    @MethodSource("descriptionLengths")
    @DisplayName("границы описания: до 2000 символов")
    void descriptionSweep(int length) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "Задача " + UUID.randomUUID());
        payload.put("description", "a".repeat(length));
        payload.put("priority", 3);
        payload.put("assigneeId", 1);

        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/work-items")
                        .header("Idempotency-Key", "sweep-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(payload)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).as("длина " + length)
                .isEqualTo(length <= 2000 ? 201 : 400);
    }

    static IntStream authorLengths() {
        return IntStream.concat(IntStream.rangeClosed(0, 4), IntStream.rangeClosed(78, 82));
    }

    @ParameterizedTest(name = "автор заметки длиной {0}")
    @MethodSource("authorLengths")
    @DisplayName("границы автора заметки: 1..80 символов")
    void noteAuthorSweep(int length) throws Exception {
        long id = createItem();
        String author = "a".repeat(length);

        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/work-items/{id}/notes", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(Map.of("author", author, "content", "текст"))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).as("длина " + length)
                .isEqualTo(length >= 1 && length <= 80 ? 200 : 400);
    }

    static IntStream playerLengths() {
        return IntStream.rangeClosed(1, 21);
    }

    @ParameterizedTest(name = "имя игрока длиной {0}")
    @MethodSource("playerLengths")
    @DisplayName("границы имени игрока: 2..20 символов")
    void playerSweep(int length) throws Exception {
        Map<String, Object> payload = Map.of(
                "player", "a".repeat(length),
                "level", 1,
                "score", 10,
                "seconds", 5);

        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.post("/api/game/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(payload)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).as("длина " + length)
                .isEqualTo(length >= 2 && length <= 20 ? 200 : 400);
    }

    static Stream<Integer> levels() {
        return Stream.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
    }

    @ParameterizedTest(name = "уровень {0}")
    @MethodSource("levels")
    @DisplayName("уровень 1..8 принимается, остальное отклоняется")
    void levelSweep(int level) throws Exception {
        Map<String, Object> payload = Map.of(
                "player", "sweep",
                "level", level,
                "score", 100,
                "seconds", 60);

        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.post("/api/game/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(payload)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(level >= 1 && level <= 8 ? 200 : 400);
    }

    static Stream<Integer> pageSizes() {
        return Stream.of(0, 1, 2, 3, 5, 10, 50, 99, 100, 101, 200);
    }

    @ParameterizedTest(name = "размер страницы {0}")
    @MethodSource("pageSizes")
    @DisplayName("размер страницы ограничен 1..100")
    void pageSizeSweep(int size) throws Exception {
        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/work-items")
                        .param("size", String.valueOf(size)).param("page", "0"))
                .andReturn();

        int expected = Math.max(1, Math.min(size, 100));
        com.fasterxml.jackson.databind.JsonNode payload = this.objectMapper.readTree(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(payload.get("size").asInt()).isEqualTo(expected);
    }

    private long createItem() throws Exception {
        Map<String, Object> payload = Map.of(
                "title", "Заметки " + UUID.randomUUID(),
                "priority", 3,
                "assigneeId", 1);
        MvcResult created = this.mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/work-items")
                        .header("Idempotency-Key", "sweep-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(payload)))
                .andReturn();
        return this.objectMapper.readTree(created.getResponse().getContentAsString(
                java.nio.charset.StandardCharsets.UTF_8)).get("id").asLong();
    }
}
