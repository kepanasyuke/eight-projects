package ru.eight.p7crypto;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Проект 7. Подпись по JCA, проверка подписи, паспорт сертификата и демонстрация подмены данных.
 */
@RestController
@RequestMapping("/api/v7/crypto")
public class CryptoController {

    private final CryptoService service;

    public CryptoController(CryptoService service) {
        this.service = service;
    }

    @GetMapping("/certificate")
    public CryptoService.CertificateInfo certificate() {
        CryptoService.CertificateInfo info = this.service.certificateInfo();
        return info;
    }

    @GetMapping("/chain")
    public List<String> chain() {
        List<String> chain = this.service.chain();
        return chain;
    }

    @PostMapping("/sign")
    public CryptoService.SignResult sign(@RequestBody SignRequest request) {
        CryptoService.SignResult result = this.service.sign(request.payload());
        return result;
    }

    @PostMapping("/verify")
    public CryptoService.VerifyReport verify(@RequestBody VerifyRequest request) {
        CryptoService.VerifyReport report = this.service.verify(request.payload(), request.signatureBase64());
        return report;
    }

    @GetMapping("/tamper")
    public CryptoService.TamperReport tamper(@RequestParam(defaultValue = "Платёж на 300 000 рублей") String payload) {
        CryptoService.TamperReport report = this.service.tamperDemo(payload);
        return report;
    }

    public record SignRequest(String payload) {
    }

    public record VerifyRequest(String payload, String signatureBase64) {
    }
}
