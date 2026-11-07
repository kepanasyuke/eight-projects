package ru.multitool.eight.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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
 * Матрица игрового API: границы имени, уровня, счёта и времени,
 * лимиты таблицы лидеров, описание уровней и агрегированная статистика.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class GameApiMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private MvcResult postScore(String player, int level, int score, int seconds) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("player", player);
        payload.put("level", level);
        payload.put("score", score);
        payload.put("seconds", seconds);
        return this.mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/game/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(payload)))
                .andReturn();
    }

    static Stream<Arguments> playerNames() {
        return Stream.of(
                Arguments.of("пустое имя", "", 400),
                Arguments.of("пробелы", "   ", 400),
                Arguments.of("один символ", "a", 400),
                Arguments.of("два символа", "ab", 200),
                Arguments.of("девятнадцать символов", "a".repeat(19), 200),
                Arguments.of("двадцать символов", "a".repeat(20), 200),
                Arguments.of("двадцать один символ", "a".repeat(21), 400)
        );
    }

    @ParameterizedTest(name = "имя: {0}")
    @MethodSource("playerNames")
    @DisplayName("имя игрока занимает от 2 до 20 символов")
    void playerNameBoundary(String name, String player, int expected) throws Exception {
        MvcResult result = postScore(player, 1, 100, 10);

        assertThat(result.getResponse().getStatus()).as(name).isEqualTo(expected);
    }

    static Stream<Arguments> levelBoundaries() {
        return Stream.of(
                Arguments.of(0, 400),
                Arguments.of(1, 200),
                Arguments.of(4, 200),
                Arguments.of(8, 200),
                Arguments.of(9, 400),
                Arguments.of(-1, 400)
        );
    }

    @ParameterizedTest(name = "уровень {0} → {1}")
    @MethodSource("levelBoundaries")
    @DisplayName("уровень принимает значения 1..8")
    void levelBoundary(int level, int expected) throws Exception {
        MvcResult result = postScore("level-" + UUID.randomUUID().toString().substring(0, 6), level, 100, 10);

        assertThat(result.getResponse().getStatus()).isEqualTo(expected);
    }

    static Stream<Arguments> numericBoundaries() {
        return Stream.of(
                Arguments.of("счёт -1", -1, 10, 400),
                Arguments.of("счёт 0", 0, 10, 200),
                Arguments.of("счёт 1 000 000", 1000000, 10, 200),
                Arguments.of("время -1", 100, -1, 400),
                Arguments.of("время 0", 100, 0, 200),
                Arguments.of("время 3600", 100, 3600, 200)
        );
    }

    @ParameterizedTest(name = "{0} → {3}")
    @MethodSource("numericBoundaries")
    @DisplayName("счёт и время не могут быть отрицательными")
    void numericBoundary(String name, int score, int seconds, int expected) throws Exception {
        MvcResult result = postScore("num-" + UUID.randomUUID().toString().substring(0, 6), 3, score, seconds);

        assertThat(result.getResponse().getStatus()).as(name).isEqualTo(expected);
    }

    @Test
    @DisplayName("имя на кириллице сохраняется без искажений")
    void cyrillicPlayerName() throws Exception {
        String player = "Игрок-" + UUID.randomUUID().toString().substring(0, 6);

        MvcResult result = postScore(player, 8, 9500, 240);

        JsonNode payload = this.objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(payload.get("player").asText()).isEqualTo(player);
        assertThat(payload.get("level").asInt()).isEqualTo(8);
    }

    static Stream<Arguments> limits() {
        return Stream.of(
                Arguments.of(0, 1),
                Arguments.of(1, 1),
                Arguments.of(10, 10),
                Arguments.of(50, 50),
                Arguments.of(51, 50),
                Arguments.of(1000, 50)
        );
    }

    @ParameterizedTest(name = "limit={0} → не больше {1}")
    @MethodSource("limits")
    @DisplayName("лимит таблицы лидеров ограничен 1..50")
    void leaderboardLimit(int limit, int maxSize) throws Exception {
        MvcResult result = this.mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/game/leaderboard").param("limit", String.valueOf(limit)))
                .andReturn();

        JsonNode payload = this.objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(payload.isArray()).isTrue();
        assertThat(payload.size()).isLessThanOrEqualTo(maxSize);
    }

    @Test
    @DisplayName("описание восьми уровней содержит проект, цвет и количество навыков")
    void levelsDescribeProjects() throws Exception {
        MvcResult result = this.mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/game/levels"))
                .andReturn();

        JsonNode levels = this.objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(levels.size()).isEqualTo(8);
        for (int index = 0; index < levels.size(); index++) {
            JsonNode level = levels.get(index);
            assertThat(level.get("id").asInt()).isEqualTo(index + 1);
            assertThat(level.get("key").asText()).isEqualTo("p" + (index + 1));
            assertThat(level.get("accent").asText()).startsWith("#");
            assertThat(level.get("orbs").asInt()).isBetween(6, 10);
            assertThat(level.get("hint").asText()).isNotBlank();
        }
    }

    @Test
    @DisplayName("статистика знает про все восемь уровней")
    void statsCoverAllLevels() throws Exception {
        MvcResult result = this.mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/game/stats"))
                .andReturn();

        JsonNode stats = this.objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(stats.get("levels").asInt()).isEqualTo(8);
        assertThat(stats.get("runsPerLevel").size()).isEqualTo(8);
        assertThat(stats.get("totalRuns").asLong()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("сохранённый результат появляется в таблице лидеров")
    void savedScoreAppearsInLeaderboard() throws Exception {
        String player = "board-" + UUID.randomUUID().toString().substring(0, 6);
        postScore(player, 7, 8888, 199);

        MvcResult result = this.mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/game/leaderboard").param("limit", "50"))
                .andReturn();

        JsonNode payload = this.objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        boolean found = false;
        for (JsonNode score : payload) {
            if (player.equals(score.get("player").asText())) {
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    @DisplayName("лидерборд отсортирован по очкам убыванию")
    void leaderboardIsSorted() throws Exception {
        MvcResult result = this.mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/game/leaderboard").param("limit", "50"))
                .andReturn();

        JsonNode payload = this.objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        int previous = Integer.MAX_VALUE;
        for (JsonNode score : payload) {
            assertThat(score.get("score").asInt()).isLessThanOrEqualTo(previous);
            previous = score.get("score").asInt();
        }
    }
}
