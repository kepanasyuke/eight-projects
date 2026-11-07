package ru.multitool.eight.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import ru.multitool.eight.p6xml.XmlService;

/**
 * Сплошные проходы по шаблонам XSD и по длинам подписываемых данных:
 * по одному тесту на каждое конкретное значение.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class PatternSweepTest {

    private final XmlService xml = new XmlService();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String invoice(String number, String inn, String money) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <invoice xmlns="urn:eight:invoice" version="1">
                  <number>%s</number>
                  <issuedAt>2026-11-06</issuedAt>
                  <seller><name>ООО «Продавец»</name><inn>%s</inn></seller>
                  <buyer><name>АО «Покупатель»</name><inn>7709876543</inn></buyer>
                  <items><item line="1"><name>Услуга</name><quantity>1</quantity><price>%s</price></item></items>
                  <total>%s</total>
                </invoice>
                """.formatted(number, inn, money, money);
    }

    static IntStream innLengths() {
        return IntStream.rangeClosed(1, 13);
    }

    @ParameterizedTest(name = "ИНН длиной {0}")
    @MethodSource("innLengths")
    @DisplayName("ИНН принимается только длиной 10 или 12 цифр")
    void innLengthSweep(int length) {
        String inn = "7".repeat(length);

        boolean valid = this.xml.validate(invoice("IN-2026/000142", inn, "100.00")).valid();

        assertThat(valid).as("длина " + length).isEqualTo(length == 10 || length == 12);
    }

    static Stream<Arguments> numbers() {
        return Stream.of(
                Arguments.of("AB-2026/000000", true),
                Arguments.of("ZZ-1999/999999", true),
                Arguments.of("IN-2026/000142", true),
                Arguments.of("A-2026/000000", false),
                Arguments.of("ABC-2026/000000", false),
                Arguments.of("AB-2026/0000000", false),
                Arguments.of("AB-2026/00000", false),
                Arguments.of("AB/2026/000000", false),
                Arguments.of("AB-2026-000000", false),
                Arguments.of("AB-2026/00000a", false),
                Arguments.of("ab-2026/000000", false),
                Arguments.of("AB-2026 /000000", false)
        );
    }

    @ParameterizedTest(name = "номер {0} → {1}")
    @MethodSource("numbers")
    @DisplayName("шаблон номера счёта: две буквы, дефис, год, слэш, шесть цифр")
    void invoiceNumberPattern(String number, boolean expected) {
        boolean valid = this.xml.validate(invoice(number, "7701234567", "100.00")).valid();

        assertThat(valid).as(number).isEqualTo(expected);
    }

    static Stream<Arguments> money() {
        return Stream.of(
                Arguments.of("0.00", true),
                Arguments.of("0.01", true),
                Arguments.of("1.0", true),
                Arguments.of("1.000", true),
                Arguments.of("1234567890123456.78", true),
                Arguments.of("12345678901234567.89", false),
                Arguments.of("-0.01", false),
                Arguments.of("1e3", false),
                Arguments.of(".5", true),
                Arguments.of("1,00", false)
        );
    }

    @ParameterizedTest(name = "сумма {0} → {1}")
    @MethodSource("money")
    @DisplayName("денежный тип: до 18 цифр, до двух знаков, неотрицательный")
    void moneyPattern(String money, boolean expected) {
        boolean valid = this.xml.validate(invoice("IN-2026/000142", "7701234567", money)).valid();

        assertThat(valid).as(money).isEqualTo(expected);
    }

    static IntStream payloadLengths() {
        return IntStream.rangeClosed(0, 12);
    }

    @ParameterizedTest(name = "полезная нагрузка длиной {0}")
    @MethodSource("payloadLengths")
    @DisplayName("подпись и проверка работают для любой длины входа")
    void payloadLengthSweep(int length) throws Exception {
        String payload = "a".repeat(length);

        JsonNode signed = this.objectMapper.readTree(this.mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/v7/crypto/sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(Map.of("payload", payload))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        JsonNode report = this.objectMapper.readTree(this.mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/v7/crypto/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(Map.of(
                                "payload", payload,
                                "signatureBase64", signed.get("signatureBase64").asText()))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(signed.get("payloadBytes").asInt()).isEqualTo(length);
        assertThat(report.get("valid").asBoolean()).as("длина " + length).isTrue();
    }
}
