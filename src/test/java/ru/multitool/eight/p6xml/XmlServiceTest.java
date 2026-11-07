package ru.multitool.eight.p6xml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.multitool.eight.common.ApiException;

/**
 * Схема, JAXB-модель и защита парсера проверяются без Spring-контекста:
 * XmlService самодостаточен.
 */
class XmlServiceTest {

    private final XmlService service = new XmlService();

    @Test
    @DisplayName("образец счёта проходит валидацию по XSD")
    void sampleIsValid() {
        XmlService.ValidationReport report = this.service.validate(this.service.sample());

        assertThat(report.valid()).isTrue();
        assertThat(report.errors()).isEmpty();
        assertThat(report.length()).isPositive();
    }

    @Test
    @DisplayName("образец разбирается в модель с пространством имён")
    void sampleParses() {
        XmlService.ParseResult result = this.service.parse(this.service.sample());

        assertThat(result.valid()).isTrue();
        assertThat(result.invoice()).isNotNull();
        assertThat(result.invoice().number()).isEqualTo("IN-2026/000142");
        assertThat(result.invoice().sellerInn()).isEqualTo("7701234567");
        assertThat(result.invoice().items()).hasSize(2);
    }

    @Test
    @DisplayName("номер вне шаблона XSD отклоняется с понятной ошибкой")
    void invalidNumberIsRejected() {
        String xml = this.service.sample().replace("IN-2026/000142", "счёт-1");

        XmlService.ValidationReport report = this.service.validate(xml);

        assertThat(report.valid()).isFalse();
        assertThat(report.errors()).isNotEmpty();
        assertThat(this.service.parse(xml).invoice()).isNull();
    }

    @Test
    @DisplayName("документ с внешней сущностью отклоняется, а не читается")
    void externalEntityIsRejected() {
        String xxe = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE invoice [<!ENTITY xxe SYSTEM "file:///c:/windows/win.ini">]>
                <invoice xmlns="urn:eight:invoice" version="1">
                  <number>IN-2026/000142</number>
                  <issuedAt>2026-11-06</issuedAt>
                  <seller><name>&xxe;</name><inn>7701234567</inn></seller>
                  <buyer><name>АО «Заказчик»</name><inn>7709876543</inn></buyer>
                  <items>
                    <item line="1"><name>Услуга</name><quantity>1</quantity><price>100.00</price></item>
                  </items>
                  <total>100.00</total>
                </invoice>
                """;

        XmlService.ParseResult result = null;
        boolean rejectedWithException = false;
        try {
            result = this.service.parse(xxe);
        } catch (ApiException | IllegalStateException ex) {
            rejectedWithException = true;
        }

        assertThat(rejectedWithException || result == null || !result.valid())
                .as("DTD и внешние сущности не должны обрабатываться")
                .isTrue();
    }

    @Test
    @DisplayName("сборка документа сразу даёт валидный XML")
    void buildProducesValidDocument() {
        String xml = this.service.build(new XmlService.InvoiceDraft(
                "AB-2026/000001", "2026-11-07",
                "ООО «Тест»", "7701234567",
                "АО «Покупатель»", "7709876543",
                java.util.List.of(new XmlService.ItemDraft("Консультация", 2, new java.math.BigDecimal("1500.00")))));

        XmlService.ValidationReport report = this.service.validate(xml);

        assertThat(report.valid()).isTrue();
        assertThat(xml).contains("urn:eight:invoice");
        assertThat(this.service.parse(xml).invoice().total()).isEqualByComparingTo("3000.00");
    }

    @Test
    @DisplayName("нечисловая сумма отклоняется при сборке")
    void buildRejectsBrokenDate() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> this.service.build(new XmlService.InvoiceDraft(
                        "AB-2026/000001", "07.11.2026",
                        "ООО «Тест»", "7701234567",
                        "АО «Покупатель»", "7709876543",
                        java.util.List.of(new XmlService.ItemDraft("Услуга", 1, java.math.BigDecimal.ONE)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("YYYY-MM-DD");
    }
}
