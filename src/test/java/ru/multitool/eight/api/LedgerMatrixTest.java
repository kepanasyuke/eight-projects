package ru.multitool.eight.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Матрица денежных операций: границы сумм, неизвестные счета, откат с независимым
 * аудитом и сравнение self-invocation с вызовом через бин.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class LedgerMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String openAccount(String balance) throws Exception {
        String code = "T-" + UUID.randomUUID().toString().substring(0, 8);
        this.mockMvc.perform(MockMvcRequestBuilders.post("/api/v3/tx/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(Map.of("code", code, "initial", balance))))
                .andReturn();
        return code;
    }

    private MvcResult transfer(String source, String target, String amount) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceCode", source);
        payload.put("targetCode", target);
        payload.put("amount", new BigDecimal(amount));
        return this.mockMvc.perform(MockMvcRequestBuilders.post("/api/v3/tx/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(payload)))
                .andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return this.objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    static Stream<Arguments> amounts() {
        return Stream.of(
                Arguments.of("0", 400),
                Arguments.of("-0.01", 400),
                Arguments.of("-100", 400),
                Arguments.of("0.01", 200),
                Arguments.of("1.00", 200),
                Arguments.of("99.99", 200),
                Arguments.of("100.00", 200),
                Arguments.of("100.01", 400),
                Arguments.of("1000.00", 400)
        );
    }

    @ParameterizedTest(name = "перевод {0} → {1}")
    @MethodSource("amounts")
    @DisplayName("сумма перевода положительна и не превышает баланс")
    void amountBoundaries(String amount, int expected) throws Exception {
        String source = openAccount("100.00");
        String target = openAccount("0.00");

        MvcResult result = transfer(source, target, amount);

        assertThat(result.getResponse().getStatus()).as(amount).isEqualTo(expected);
    }

    @Test
    @DisplayName("успешный перевод двигает оба баланса и пишет проводку")
    void successfulTransferMovesMoney() throws Exception {
        String source = openAccount("500.00");
        String target = openAccount("10.00");

        JsonNode result = json(transfer(source, target, "150.00"));

        assertThat(result.get("sourceBalance").decimalValue()).isEqualByComparingTo("350.00");
        assertThat(result.get("targetBalance").decimalValue()).isEqualByComparingTo("160.00");
        assertThat(result.get("entryId").asLong()).isPositive();
    }

    static Stream<Arguments> unknownAccounts() {
        return Stream.of(
                Arguments.of("источник", true),
                Arguments.of("получатель", false)
        );
    }

    @ParameterizedTest(name = "неизвестный {0}")
    @MethodSource("unknownAccounts")
    @DisplayName("неизвестный счёт даёт 404")
    void unknownAccount(String who, boolean sourceMissing) throws Exception {
        String existing = openAccount("100.00");
        String absent = "NOPE-" + UUID.randomUUID();

        MvcResult result = sourceMissing
                ? transfer(absent, existing, "1.00")
                : transfer(existing, absent, "1.00");

        assertThat(result.getResponse().getStatus()).as(who).isEqualTo(404);
    }

    @Test
    @DisplayName("перевод на тот же счёт не меняет баланс")
    void sameAccountTransfer() throws Exception {
        String code = openAccount("100.00");

        MvcResult result = transfer(code, code, "10.00");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("sourceBalance").decimalValue()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("аудит пишется в отдельной транзакции и переживает откат")
    void auditSurvivesRollback() throws Exception {
        String source = openAccount("50.00");
        String target = openAccount("0.00");

        Map<String, Object> payload = Map.of("sourceCode", source, "targetCode", target, "amount", 500.00);
        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.post("/api/v3/tx/transfer-with-audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(payload)))
                .andReturn();

        JsonNode report = json(result);
        assertThat(report.get("rolledBack").asBoolean()).isTrue();
        assertThat(report.get("auditRecordsWritten").asLong()).isPositive();
        assertThat(report.get("message").asText()).contains("Недостаточно средств");
        JsonNode state = report.get("state");
        assertThat(state.get(0).get("balance").decimalValue()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("демонстрационный перевод с аудитом всегда откатывает движение денег")
    void demoTransferRollsBackMoney() throws Exception {
        String source = openAccount("200.00");
        String target = openAccount("0.00");

        Map<String, Object> payload = Map.of("sourceCode", source, "targetCode", target, "amount", 100.00);
        JsonNode report = json(this.mockMvc.perform(MockMvcRequestBuilders.post("/api/v3/tx/transfer-with-audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(payload)))
                .andReturn());

        assertThat(report.get("rolledBack").asBoolean()).isTrue();
        assertThat(report.get("state").get(0).get("balance").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(report.get("state").get(1).get("balance").decimalValue()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("отчёт о пропагации показывает разницу между this и бином")
    void propagationReport() throws Exception {
        JsonNode report = json(this.mockMvc.perform(MockMvcRequestBuilders.post("/api/v3/tx/propagation-demo"))
                .andReturn());

        assertThat(report.get("directCallFailed").asBoolean()).isTrue();
        assertThat(report.get("auditRecordsAfterDirectCall").asInt()).isZero();
        assertThat(report.get("beanCallFailed").asBoolean()).isTrue();
        assertThat(report.get("auditRecordsAfterBeanCall").asInt()).isEqualTo(1);
        assertThat(report.get("explanation").asText()).contains("прокси");
    }

    @Test
    @DisplayName("открытый счёт появляется в списке и в аудите нет пустых записей")
    void openedAccountIsVisible() throws Exception {
        String code = openAccount("77.00");

        JsonNode accounts = json(this.mockMvc.perform(MockMvcRequestBuilders.get("/api/v3/tx/accounts"))
                .andReturn());
        boolean found = false;
        for (JsonNode account : accounts) {
            if (code.equals(account.get("code").asText())) {
                found = true;
                assertThat(account.get("balance").decimalValue()).isEqualByComparingTo("77.00");
            }
        }
        assertThat(found).isTrue();

        JsonNode audit = json(this.mockMvc.perform(MockMvcRequestBuilders.get("/api/v3/tx/audit"))
                .andReturn());
        assertThat(audit.isArray()).isTrue();
    }

    @Test
    @DisplayName("повторный код счёта отклоняется ограничением уникальности")
    void duplicateAccountCodeRejected() throws Exception {
        String code = openAccount("1.00");

        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.post("/api/v3/tx/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(Map.of("code", code, "initial", "5.00"))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(result).get("code").asText()).isEqualTo("DATA_INTEGRITY_VIOLATION");
    }

    @Test
    @DisplayName("проводка после успешного перевода попадает в журнал")
    void entryIsRecorded() throws Exception {
        String source = openAccount("300.00");
        String target = openAccount("0.00");
        transfer(source, target, "120.00");

        JsonNode entries = json(this.mockMvc.perform(MockMvcRequestBuilders.get("/api/v3/tx/entries"))
                .andReturn());

        boolean found = false;
        for (JsonNode entry : entries) {
            if (source.equals(entry.get("sourceAccount").asText()) && target.equals(entry.get("targetAccount").asText())) {
                found = true;
                assertThat(entry.get("amount").decimalValue()).isEqualByComparingTo("120.00");
            }
        }
        assertThat(found).isTrue();
    }
}
