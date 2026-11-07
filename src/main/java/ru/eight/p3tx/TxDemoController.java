package ru.eight.p3tx;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Проект 3. Управление счетами и демонстрация отката с независимым аудитом.
 */
@RestController
@RequestMapping("/api/v3/tx")
public class TxDemoController {

    private final LedgerService service;

    public TxDemoController(LedgerService service) {
        this.service = service;
    }

    @PostMapping("/accounts")
    public LedgerService.LedgerAccountView open(@RequestBody Map<String, String> body) {
        String code = body.getOrDefault("code", "ACC-" + System.nanoTime());
        BigDecimal initial = new BigDecimal(body.getOrDefault("initial", "1000"));
        LedgerService.LedgerAccountView view = this.service.open(code, initial);
        return view;
    }

    @GetMapping("/accounts")
    public List<LedgerService.LedgerAccountView> accounts() {
        List<LedgerService.LedgerAccountView> views = this.service.balances();
        return views;
    }

    @GetMapping("/entries")
    public List<LedgerService.LedgerEntryView> entries() {
        List<LedgerService.LedgerEntryView> views = this.service.entries();
        return views;
    }

    @GetMapping("/audit")
    public List<LedgerService.AuditView> audit() {
        List<LedgerService.AuditView> views = this.service.auditTrail();
        return views;
    }

    @PostMapping("/transfer")
    public LedgerService.TransferResult transfer(@RequestBody TransferRequest request) {
        LedgerService.TransferResult result = this.service.transfer(request.sourceCode(), request.targetCode(),
                request.amount());
        return result;
    }

    @PostMapping("/transfer-with-audit")
    public LedgerService.RollbackReport transferWithAudit(@RequestBody TransferRequest request) {
        LedgerService.RollbackReport report = this.service.transferWithAuditDemo(request.sourceCode(),
                request.targetCode(), request.amount());
        return report;
    }

    @PostMapping("/propagation-demo")
    public LedgerService.PropagationReport propagationDemo() {
        LedgerService.PropagationReport report = this.service.demoSelfInvocation();
        return report;
    }

    public record TransferRequest(String sourceCode, String targetCode, BigDecimal amount) {
    }
}
