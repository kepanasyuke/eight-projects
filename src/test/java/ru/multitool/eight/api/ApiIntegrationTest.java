package ru.multitool.eight.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Сквозные проверки API по всем восьми проектам: контракт, идемпотентность,
 * переходы состояний, откат транзакции, DLQ, подпись и результаты игры.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("список задач отдаётся страницей с метаданными")
    void listWorkItems() throws Exception {
        this.mockMvc.perform(get("/api/v1/work-items").param("page", "0").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.totalItems").isNumber());
    }

    @Test
    @DisplayName("без обязательного заголовка создания отвечает 400")
    void createWithoutIdempotencyKeyFails() throws Exception {
        this.mockMvc.perform(post("/api/v1/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Задача\",\"priority\":2,\"assigneeId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    @Test
    @DisplayName("невалидное тело запроса отклоняется с деталями полей")
    void createWithInvalidBodyFails() throws Exception {
        this.mockMvc.perform(post("/api/v1/work-items")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"priority\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @DisplayName("повтор с тем же ключом не создаёт вторую задачу")
    void idempotentCreateReturnsSameItem() throws Exception {
        String key = "test-" + UUID.randomUUID();
        String body = "{\"title\":\"Идемпотентная задача\",\"description\":\"проверка\",\"priority\":2,\"assigneeId\":1}";

        MvcResult first = this.mockMvc.perform(post("/api/v1/work-items")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult second = this.mockMvc.perform(post("/api/v1/work-items")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode firstBody = this.objectMapper.readTree(first.getResponse().getContentAsString());
        JsonNode secondBody = this.objectMapper.readTree(second.getResponse().getContentAsString());

        assertThat(secondBody.get("id").asLong()).isEqualTo(firstBody.get("id").asLong());
    }

    @Test
    @DisplayName("недопустимый переход статуса даёт 409 и не меняет задачу")
    void illegalTransitionIsRejected() throws Exception {
        this.mockMvc.perform(patch("/api/v1/work-items/{id}/status", 1L).param("target", "NEW"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        this.mockMvc.perform(get("/api/v1/work-items/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("отсутствующая задача даёт 404 в едином формате ошибки")
    void missingItemReturns404() throws Exception {
        this.mockMvc.perform(get("/api/v1/work-items/{id}", 987654L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("наивная выборка дороже проекции по числу SQL-запросов")
    void naiveLoadingIssuesMoreQueries() throws Exception {
        MvcResult naive = this.mockMvc.perform(get("/api/v2/jpa/naive").param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult projection = this.mockMvc.perform(get("/api/v2/jpa/projection").param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode naiveBody = this.objectMapper.readTree(naive.getResponse().getContentAsString());
        JsonNode projectionBody = this.objectMapper.readTree(projection.getResponse().getContentAsString());

        assertThat(projectionBody.get("statements").asInt())
                .as("проекция не должна порождать N+1")
                .isLessThanOrEqualTo(naiveBody.get("statements").asInt());
    }

    @Test
    @DisplayName("аудит пишется в отдельной транзакции и переживает откат перевода")
    void auditSurvivesRollback() throws Exception {
        MvcResult before = this.mockMvc.perform(get("/api/v3/tx/accounts")).andReturn();
        JsonNode beforeAccounts = this.objectMapper.readTree(before.getResponse().getContentAsString());

        MvcResult result = this.mockMvc.perform(post("/api/v3/tx/transfer-with-audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceCode\":\"ACC-001\",\"targetCode\":\"ACC-002\",\"amount\":999999.00}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode report = this.objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(report.get("rolledBack").asBoolean()).isTrue();
        assertThat(report.get("auditRecordsWritten").asLong()).isPositive();

        MvcResult after = this.mockMvc.perform(get("/api/v3/tx/accounts")).andReturn();
        JsonNode afterAccounts = this.objectMapper.readTree(after.getResponse().getContentAsString());
        assertThat(afterAccounts).isEqualTo(beforeAccounts);
    }

    @Test
    @DisplayName("отравленное сообщение уходит в DLQ после повторов")
    void poisonMessageGoesToDeadLetterQueue() throws Exception {
        this.mockMvc.perform(get("/api/v5/bus/scenario").param("poison", "true").param("events", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deadLettered").value(1))
                .andExpect(jsonPath("$.deadLetterQueueSize").value(1))
                .andExpect(jsonPath("$.deduplicated").value(1));

        this.mockMvc.perform(get("/api/v5/bus/scenario").param("poison", "false").param("events", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deadLettered").value(0))
                .andExpect(jsonPath("$.acknowledged").value(4));
    }

    @Test
    @DisplayName("outbox фиксирует событие вместе с транзакцией")
    void outboxIsPersisted() throws Exception {
        this.mockMvc.perform(post("/api/v5/bus/outbox").param("payload", "{\"event\":\"order.paid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.topic").value("payment-events"));

        this.mockMvc.perform(get("/api/v5/bus/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deadLetters").isArray())
                .andExpect(jsonPath("$.outboxMessages").isArray());
    }

    @Test
    @DisplayName("подпись формируется и проверяется, подмена данных обнаруживается")
    void signatureRoundTripAndTamper() throws Exception {
        String payload = "Платёж на 300 000 рублей";
        MvcResult signResult = this.mockMvc.perform(post("/api/v7/crypto/sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(Map.of("payload", payload))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode signed = this.objectMapper.readTree(signResult.getResponse().getContentAsString());
        String signature = signed.get("signatureBase64").asText();

        this.mockMvc.perform(post("/api/v7/crypto/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(Map.of("payload", payload,
                                "signatureBase64", signature))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.reason").value("SIGNATURE_VALID"));

        this.mockMvc.perform(get("/api/v7/crypto/tamper").param("payload", payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalValid").value(true))
                .andExpect(jsonPath("$.tamperedValid").value(false));
    }

    @Test
    @DisplayName("описание машины состояний отдаётся клиенту")
    void stateMachineSchemaIsExposed() throws Exception {
        this.mockMvc.perform(get("/api/v8/ws/schema"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endpoint").value("ws://localhost:8080/ws/state"))
                .andExpect(jsonPath("$.transitions.NEW").isArray())
                .andExpect(jsonPath("$.activeSessions").isNumber());
    }

    @Test
    @DisplayName("результат игры сохраняется и попадает в таблицу лидеров")
    void gameScoreIsSaved() throws Exception {
        String player = "тест-" + UUID.randomUUID().toString().substring(0, 6);

        this.mockMvc.perform(post("/api/game/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"player\":\"" + player + "\",\"level\":8,\"score\":9123,\"seconds\":214}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.player").value(player))
                .andExpect(jsonPath("$.score").value(9123));

        this.mockMvc.perform(get("/api/game/leaderboard").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.player=='" + player + "')]").exists());
    }

    @Test
    @DisplayName("слишком короткое имя игрока отклоняется валидацией")
    void gameScoreValidation() throws Exception {
        this.mockMvc.perform(post("/api/game/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"player\":\"x\",\"level\":1,\"score\":10,\"seconds\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        this.mockMvc.perform(post("/api/game/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"player\":\"игрок\",\"level\":99,\"score\":10,\"seconds\":5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("витрина отдаёт ровно восемь проектов со ссылками на гифки и страницы")
    void showcaseReturnsEightProjects() throws Exception {
        this.mockMvc.perform(get("/api/showcase/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].gif").value("/gifs/p1.gif"))
                .andExpect(jsonPath("$[7].page").value("/projects/p8.html"));
    }

    @Test
    @DisplayName("несуществующий статический ресурс даёт 404, а не 500")
    void missingStaticResourceIsNotFound() throws Exception {
        this.mockMvc.perform(get("/no-such-page.html"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
