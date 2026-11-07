package ru.multitool.eight.p6xml;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Таблица граничных случаев XSD: шаблоны номера и ИНН, точность суммы,
 * обязательность элементов, пространство имён и защита парсера.
 */
class XmlValidationMatrixTest {

    private final XmlService service = new XmlService();

    private static String document(String number, String issuedAt, String sellerInn, String buyerInn,
                                   String items, String total, String version) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <invoice xmlns="urn:eight:invoice" version="%s">
                  <number>%s</number>
                  <issuedAt>%s</issuedAt>
                  <seller><name>ООО «Продавец»</name><inn>%s</inn></seller>
                  <buyer><name>АО «Покупатель»</name><inn>%s</inn></buyer>
                  <items>%s</items>
                  <total>%s</total>
                </invoice>
                """.formatted(version, number, issuedAt, sellerInn, buyerInn, items, total);
    }

    private static String item(int line, String name, String quantity, String price) {
        return "<item line=\"" + line + "\"><name>" + name + "</name><quantity>" + quantity
                + "</quantity><price>" + price + "</price></item>";
    }

    private static String validItems() {
        return item(1, "Разработка", "1", "180000.00");
    }

    private static String valid() {
        return document("IN-2026/000142", "2026-11-06", "7701234567", "7709876543",
                validItems(), "180000.00", "1");
    }

    static Stream<Arguments> validDocuments() {
        return Stream.of(
                Arguments.of("базовый документ", valid()),
                Arguments.of("две позиции", document("AB-2026/000001", "2026-01-01", "7701234567",
                        "7709876543", validItems() + item(2, "Тесты", "3", "24000.00"), "252000.00", "1")),
                Arguments.of("нулевая сумма", document("IN-2026/000142", "2026-11-06", "7701234567",
                        "7709876543", validItems(), "0.00", "1")),
                Arguments.of("двенадцатизначный ИНН", document("IN-2026/000142", "2026-11-06",
                        "770123456789", "770987654321", validItems(), "180000.00", "2")),
                Arguments.of("максимальный номер периода", document("ZZ-2026/999999", "2026-12-31",
                        "7701234567", "7709876543", validItems(), "180000.00", "1")),
                Arguments.of("юникод в наименовании", document("IN-2026/000142", "2026-11-06",
                        "7701234567", "7709876543", item(1, "Услуга «под ключ» — этап 1", "1", "10.50"),
                        "10.50", "1")),
                Arguments.of("максимум 18 цифр", document("IN-2026/000142", "2026-11-06", "7701234567",
                        "7709876543", validItems(), "1234567890123456.78", "1"))
        );
    }

    @ParameterizedTest(name = "допустим: {0}")
    @MethodSource("validDocuments")
    @DisplayName("корректные документы проходят схему")
    void validCases(String name, String xml) {
        assertThat(this.service.validate(xml).valid()).as(name).isTrue();
    }

    static Stream<Arguments> invalidDocuments() {
        return Stream.of(
                Arguments.of("строчный номер", document("in-2026/000142", "2026-11-06", "7701234567",
                        "7709876543", validItems(), "180000.00", "1")),
                Arguments.of("короткий год", document("IN-26/000142", "2026-11-06", "7701234567",
                        "7709876543", validItems(), "180000.00", "1")),
                Arguments.of("пять цифр в номере", document("IN-2026/00014", "2026-11-06", "7701234567",
                        "7709876543", validItems(), "180000.00", "1")),
                Arguments.of("номер без дефиса", document("IN2026000142", "2026-11-06", "7701234567",
                        "7709876543", validItems(), "180000.00", "1")),
                Arguments.of("дата в русском формате", document("IN-2026/000142", "06.11.2026", "7701234567",
                        "7709876543", validItems(), "180000.00", "1")),
                Arguments.of("девять цифр ИНН", document("IN-2026/000142", "2026-11-06", "770123456",
                        "7709876543", validItems(), "180000.00", "1")),
                Arguments.of("одиннадцать цифр ИНН", document("IN-2026/000142", "2026-11-06", "77012345678",
                        "7709876543", validItems(), "180000.00", "1")),
                Arguments.of("буквы в ИНН", document("IN-2026/000142", "2026-11-06", "77012abc67",
                        "7709876543", validItems(), "180000.00", "1")),
                Arguments.of("отрицательная сумма", document("IN-2026/000142", "2026-11-06", "7701234567",
                        "7709876543", validItems(), "-1.00", "1")),
                Arguments.of("три знака после точки", document("IN-2026/000142", "2026-11-06", "7701234567",
                        "7709876543", validItems(), "1.234", "1")),
                Arguments.of("сумма из 19 цифр", document("IN-2026/000142", "2026-11-06", "7701234567",
                        "7709876543", validItems(), "12345678901234567.89", "1")),
                Arguments.of("нулевое количество", document("IN-2026/000142", "2026-11-06", "7701234567",
                        "7709876543", item(1, "Услуга", "0", "10.00"), "10.00", "1")),
                Arguments.of("отрицательная цена", document("IN-2026/000142", "2026-11-06", "7701234567",
                        "7709876543", item(1, "Услуга", "1", "-10.00"), "10.00", "1")),
                Arguments.of("нулевая строка позиции", document("IN-2026/000142", "2026-11-06", "7701234567",
                        "7709876543", item(0, "Услуга", "1", "10.00"), "10.00", "1")),
                Arguments.of("пустой список позиций", document("IN-2026/000142", "2026-11-06", "7701234567",
                        "7709876543", "", "0.00", "1")),
                Arguments.of("версия ноль", document("IN-2026/000142", "2026-11-06", "7701234567",
                        "7709876543", validItems(), "180000.00", "0")),
                Arguments.of("незакрытый тег", valid().replace("</total>", "")),
                Arguments.of("пустой документ", ""),
                Arguments.of("текст вместо XML", "не xml"),
                Arguments.of("чужое пространство имён",
                        valid().replace("urn:eight:invoice", "urn:eight:other")),
                Arguments.of("лишний элемент",
                        valid().replace("</invoice>", "<comment>лишнее</comment></invoice>")),
                Arguments.of("отсутствует total", valid().replaceAll("<total>.*</total>", "")),
                Arguments.of("отсутствует seller", valid().replaceAll("<seller>.*</seller>", ""))
        );
    }

    @ParameterizedTest(name = "отклонён: {0}")
    @MethodSource("invalidDocuments")
    @DisplayName("нарушения схемы отклоняются с ошибками")
    void invalidCases(String name, String xml) {
        XmlService.ValidationReport report = this.service.validate(xml);

        assertThat(report.valid()).as(name).isFalse();
        assertThat(report.errors()).as(name).isNotEmpty();
    }

    static Stream<Arguments> doctypes() {
        return Stream.of(
                Arguments.of("внешняя сущность",
                        "<!DOCTYPE invoice [<!ENTITY xxe SYSTEM \"file:///c:/windows/win.ini\">]>", true),
                Arguments.of("внутренняя сущность",
                        "<!DOCTYPE invoice [<!ENTITY xxe \"подмена\">]>", true),
                Arguments.of("внешний DTD",
                        "<!DOCTYPE invoice SYSTEM \"http://example.com/invoice.dtd\">", false)
        );
    }

    @ParameterizedTest(name = "отклонён DOCTYPE: {0}")
    @MethodSource("doctypes")
    @DisplayName("документ с DOCTYPE и подстановкой сущности не разбирается")
    void doctypeIsRejectedOnParse(String name, String doctype, boolean usesEntity) {
        String xml = valid().replaceFirst("<invoice", doctype + "\n<invoice");
        if (usesEntity) {
            xml = xml.replace("ООО «Продавец»", "&xxe;");
        }

        XmlService.ParseResult parsed = null;
        boolean rejected = false;
        try {
            parsed = this.service.parse(xml);
        } catch (RuntimeException ex) {
            rejected = true;
        }

        assertThat(rejected || parsed == null || !parsed.valid()).as(name).isTrue();
    }

    @Test
    @DisplayName("ссылка на внешнюю сущность не подставляется в документ")
    void externalEntityIsNotResolved() {
        String xml = valid()
                .replaceFirst("<invoice", "<!DOCTYPE invoice [<!ENTITY xxe SYSTEM \"file:///c:/windows/win.ini\">]>\n<invoice")
                .replace("ООО «Продавец»", "&xxe;");

        XmlService.ParseResult parsed = null;
        boolean rejected = false;
        try {
            parsed = this.service.parse(xml);
        } catch (RuntimeException ex) {
            rejected = true;
        }

        assertThat(rejected || parsed == null || !parsed.valid()).isTrue();
        assertThat(parsed == null || parsed.invoice() == null).isTrue();
    }

    @Test
    @DisplayName("отчёт валидации содержит длину документа и комментарий")
    void reportDescribesDocument() {
        XmlService.ValidationReport report = this.service.validate(valid());

        assertThat(report.length()).isEqualTo(valid().length());
        assertThat(report.comment()).contains("XSD");
    }

    static Stream<Arguments> drafts() {
        return Stream.of(
                Arguments.of("одна позиция", 1, 1, "2026-11-07", "AB-2026/000001", true),
                Arguments.of("три позиции", 3, 2, "2026-11-07", "AB-2026/000001", true),
                Arguments.of("дата с точками", 1, 1, "07.11.2026", "AB-2026/000001", false),
                Arguments.of("пустой номер", 1, 1, "2026-11-07", "", false),
                Arguments.of("номер вне шаблона", 1, 1, "2026-11-07", "счёт-1", false)
        );
    }

    @ParameterizedTest(name = "сборка: {0}")
    @MethodSource("drafts")
    @DisplayName("сборка либо даёт валидный документ, либо отвергает входные данные")
    void buildCases(String name, int items, int quantity, String date, String number, boolean expectValid) {
        java.util.List<XmlService.ItemDraft> list = new java.util.ArrayList<>();
        for (int index = 0; index < items; index++) {
            list.add(new XmlService.ItemDraft("Позиция " + index, quantity,
                    new java.math.BigDecimal("100.00")));
        }

        boolean produced = false;
        try {
            String xml = this.service.build(new XmlService.InvoiceDraft(number, date,
                    "ООО «Тест»", "7701234567", "АО «Покупатель»", "7709876543", list));
            produced = this.service.validate(xml).valid();
        } catch (RuntimeException ex) {
            produced = false;
        }

        assertThat(produced).as(name).isEqualTo(expectValid);
    }
}
