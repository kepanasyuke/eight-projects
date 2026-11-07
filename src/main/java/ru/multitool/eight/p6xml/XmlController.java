package ru.multitool.eight.p6xml;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Проект 6. Валидация по XSD, разбор защищённым парсером и сборка документа.
 */
@RestController
@RequestMapping("/api/v6/xml")
public class XmlController {

    private final XmlService service;

    public XmlController(XmlService service) {
        this.service = service;
    }

    @PostMapping(path = "/validate", consumes = {MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_XML_VALUE})
    public XmlService.ValidationReport validate(@RequestBody String xml) {
        XmlService.ValidationReport report = this.service.validate(xml);
        return report;
    }

    @PostMapping(path = "/parse", consumes = {MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_XML_VALUE})
    public XmlService.ParseResult parse(@RequestBody String xml) {
        XmlService.ParseResult result = this.service.parse(xml);
        return result;
    }

    @PostMapping("/build")
    public XmlBuildResponse build(@RequestBody XmlService.InvoiceDraft draft) {
        String xml = this.service.build(draft);
        XmlBuildResponse response = new XmlBuildResponse(this.service.validate(xml).valid(), xml);
        return response;
    }

    @GetMapping("/sample")
    public XmlBuildResponse sample() {
        String xml = this.service.sample();
        XmlBuildResponse response = new XmlBuildResponse(this.service.validate(xml).valid(), xml);
        return response;
    }

    public record XmlBuildResponse(boolean valid, String xml) {
    }
}
