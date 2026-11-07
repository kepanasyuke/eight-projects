package ru.multitool.eight.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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
 * Матрица криптографических сценариев: длина подписи для разных полезных нагрузок,
 * проверка подлинности, подмена данных и паспорт сертификата.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class CryptoCasesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode json(MvcResult result) throws Exception {
        return this.objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode sign(String payload) throws Exception {
        return json(this.mockMvc.perform(MockMvcRequestBuilders.post("/api/v7/crypto/sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(Map.of("payload", payload))))
                .andReturn());
    }

    private JsonNode verify(String payload, String signature) throws Exception {
        return json(this.mockMvc.perform(MockMvcRequestBuilders.post("/api/v7/crypto/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(Map.of(
                                "payload", payload, "signatureBase64", signature))))
                .andReturn());
    }

    static Stream<Arguments> payloads() {
        return Stream.of(
                Arguments.of("пустая строка", ""),
                Arguments.of("один символ", "a"),
                Arguments.of("кириллица", "Платёж на 300 000 рублей"),
                Arguments.of("длинный текст", "x".repeat(5000)),
                Arguments.of("юникод и эмодзи", "Подпись ✅ 2026")
        );
    }

    @ParameterizedTest(name = "подпись: {0}")
    @MethodSource("payloads")
    @DisplayName("длина полезной нагрузки считается в байтах UTF-8")
    void signPayloadBytes(String name, String payload) throws Exception {
        JsonNode signed = sign(payload);

        assertThat(signed.get("payloadBytes").asInt())
                .as(name)
                .isEqualTo(payload.getBytes(StandardCharsets.UTF_8).length);
        assertThat(signed.get("signatureBase64").asText()).isNotBlank();
        assertThat(signed.get("algorithm").asText()).isNotBlank();
    }

    @ParameterizedTest(name = "проверка: {0}")
    @MethodSource("payloads")
    @DisplayName("подпись проверяется для любой полезной нагрузки")
    void verifyOwnSignature(String name, String payload) throws Exception {
        JsonNode signed = sign(payload);

        JsonNode report = verify(payload, signed.get("signatureBase64").asText());

        assertThat(report.get("valid").asBoolean()).as(name).isTrue();
        assertThat(report.get("reason").asText()).isEqualTo("SIGNATURE_VALID");
    }

    @Test
    @DisplayName("изменение данных ломает проверку подписи")
    void tamperedPayloadFails() throws Exception {
        JsonNode signed = sign("Платёж на 300 000 рублей");

        JsonNode report = verify("Платёж на 300 000 рублей ", signed.get("signatureBase64").asText());

        assertThat(report.get("valid").asBoolean()).isFalse();
        assertThat(report.get("reason").asText()).isEqualTo("SIGNATURE_INVALID");
    }

    @Test
    @DisplayName("некорректный base64 отклоняется как MALFORMED")
    void malformedSignature() throws Exception {
        JsonNode report = verify("текст", "!!!не base64!!!");

        assertThat(report.get("valid").asBoolean()).isFalse();
        assertThat(report.get("reason").asText()).isEqualTo("SIGNATURE_MALFORMED");
    }

    @Test
    @DisplayName("пустая подпись не проходит проверку")
    void emptySignature() throws Exception {
        JsonNode report = verify("текст", "");

        assertThat(report.get("valid").asBoolean()).isFalse();
        assertThat(report.get("reason").asText()).isIn("VERIFY_FAILED", "SIGNATURE_MALFORMED", "SIGNATURE_INVALID");
    }

    static Stream<Arguments> tamperPayloads() {
        return Stream.of(
                Arguments.of("Платёж на 300 000 рублей"),
                Arguments.of("Договор № " + UUID.randomUUID())
        );
    }

    @ParameterizedTest(name = "подмена: {0}")
    @MethodSource("tamperPayloads")
    @DisplayName("исходные данные валидны, изменённые — нет")
    void tamperDemo(String payload) throws Exception {
        JsonNode report = json(this.mockMvc.perform(MockMvcRequestBuilders.get("/api/v7/crypto/tamper")
                        .param("payload", payload))
                .andReturn());

        assertThat(report.get("originalValid").asBoolean()).isTrue();
        assertThat(report.get("tamperedValid").asBoolean()).isFalse();
        assertThat(report.get("tamperedReason").asText()).isEqualTo("SIGNATURE_INVALID");
    }

    @Test
    @DisplayName("паспорт сертификата содержит ключевые поля")
    void certificateInfo() throws Exception {
        JsonNode info = json(this.mockMvc.perform(MockMvcRequestBuilders.get("/api/v7/crypto/certificate"))
                .andReturn());

        assertThat(info.get("subject").asText()).isNotBlank();
        assertThat(info.get("issuer").asText()).isNotBlank();
        assertThat(info.get("signatureAlgorithm").asText()).isNotBlank();
        assertThat(info.get("keyAlgorithm").asText()).isNotBlank();
        assertThat(info.get("serialNumber").asText()).isNotBlank();
        assertThat(info.get("chainLength").asInt()).isGreaterThanOrEqualTo(1);
        String fingerprint = info.get("sha256Fingerprint").asText();
        assertThat(fingerprint.replace(":", "").replace(" ", "").toUpperCase())
                .hasSize(64)
                .matches("[0-9A-F]{64}");
        assertThat(info.get("notAfter").isNull()).isFalse();
    }

    @Test
    @DisplayName("цепочка сертификатов не пуста и содержит субъектов")
    void chainIsNotEmpty() throws Exception {
        JsonNode chain = json(this.mockMvc.perform(MockMvcRequestBuilders.get("/api/v7/crypto/chain"))
                .andReturn());

        assertThat(chain.isArray()).isTrue();
        assertThat(chain.size()).isGreaterThanOrEqualTo(1);
        assertThat(chain.get(0).asText()).isNotBlank();
    }
}
