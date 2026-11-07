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
 * Матрица брокера: число событий и его границы, дубликаты, DLQ при отравленном
 * сообщении, состояние партиций и публикация через outbox.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class BusMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode scenario(boolean poison, int events) throws Exception {
        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.get("/api/v5/bus/scenario")
                        .param("poison", String.valueOf(poison))
                        .param("events", String.valueOf(events)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return this.objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    static Stream<Arguments> eventCounts() {
        return Stream.of(
                Arguments.of(1, 1),
                Arguments.of(2, 2),
                Arguments.of(6, 6),
                Arguments.of(30, 30),
                Arguments.of(31, 30),
                Arguments.of(0, 1)
        );
    }

    @ParameterizedTest(name = "событий {0} → подтверждено {1}")
    @MethodSource("eventCounts")
    @DisplayName("число событий ограничено диапазоном 1..30")
    void eventCountIsClamped(int events, int acknowledged) throws Exception {
        JsonNode report = scenario(false, events);

        assertThat(report.get("acknowledged").asInt()).isEqualTo(acknowledged);
    }

    static Stream<Arguments> poisonCases() {
        return Stream.of(
                Arguments.of(false),
                Arguments.of(true)
        );
    }

    @ParameterizedTest(name = "poison={0}")
    @MethodSource("poisonCases")
    @DisplayName("отравленное сообщение уходит в DLQ, обычное — нет")
    void poisonHandling(boolean poison) throws Exception {
        JsonNode report = scenario(poison, 4);

        int deadLettered = report.get("deadLettered").asInt();
        int queueSize = report.get("deadLetterQueueSize").asInt();
        if (poison) {
            assertThat(deadLettered).as("отравленное сообщение").isPositive();
            assertThat(queueSize).as("очередь DLQ").isPositive();
        } else {
            assertThat(deadLettered).as("обычный прогон без DLQ").isZero();
            assertThat(queueSize).as("очередь DLQ накопительная").isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("повторная публикация того же сообщения дедуплицируется")
    void duplicateIsDeduplicated() throws Exception {
        JsonNode report = scenario(false, 3);

        assertThat(report.get("deduplicated").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("топик создаётся с тремя партициями")
    void partitionsAreCreated() throws Exception {
        JsonNode report = scenario(false, 5);

        JsonNode partitions = report.get("partitionSizes");
        assertThat(partitions.size()).isEqualTo(3);
        int total = 0;
        for (JsonNode size : partitions) {
            total += size.asInt();
        }
        assertThat(total).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("попытки ограничены настройкой повторов")
    void retriesAreLimited() throws Exception {
        JsonNode report = scenario(true, 2);

        assertThat(report.get("maxAttempts").asInt()).isBetween(1, 10);
        assertThat(report.get("retried").asInt()).isPositive();
    }

    @Test
    @DisplayName("состояние брокера содержит топик, партиции и список DLQ")
    void stateDescribesBroker() throws Exception {
        scenario(false, 2);

        JsonNode state = this.objectMapper.readTree(this.mockMvc.perform(MockMvcRequestBuilders
                        .get("/api/v5/bus/state"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(state.get("topic").asText()).isNotBlank();
        assertThat(state.get("partitionSizes").size()).isEqualTo(3);
        assertThat(state.get("deadLetters").isArray()).isTrue();
        assertThat(state.get("outboxMessages").isArray()).isTrue();
    }

    @Test
    @DisplayName("outbox сохраняет событие и публикует его после коммита")
    void outboxPublishesAfterCommit() throws Exception {
        JsonNode view = this.objectMapper.readTree(this.mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/v5/bus/outbox").param("payload", "{\"event\":\"order.paid\",\"orderId\":7}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(view.get("id").asLong()).isPositive();
        assertThat(view.get("topic").asText()).isEqualTo("payment-events");
        assertThat(view.get("publishedAfterCommit").asBoolean()).isTrue();
        assertThat(view.get("status").asText()).isIn("PENDING", "SENT");
    }

    @Test
    @DisplayName("outbox подставляет событие по умолчанию, если тело не передано")
    void outboxDefaultPayload() throws Exception {
        JsonNode view = this.objectMapper.readTree(this.mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/v5/bus/outbox"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(view.get("payload").asText()).contains("order.paid");
    }

    @Test
    @DisplayName("после отправки в outbox очередь не теряет событие")
    void outboxKeepsEventUntilSent() throws Exception {
        this.mockMvc.perform(MockMvcRequestBuilders.post("/api/v5/bus/outbox")
                .param("payload", "{\"event\":\"probe\"}"));

        JsonNode state = this.objectMapper.readTree(this.mockMvc.perform(MockMvcRequestBuilders
                        .get("/api/v5/bus/state"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(state.get("outboxMessages").size()).isGreaterThanOrEqualTo(1);
        assertThat(state.get("pendingOutbox").asLong()).isGreaterThanOrEqualTo(0);
    }
}
