package ru.multitool.eight.p5bus;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Проект 5. Топики, партиции, группы потребителей, повторы, DLQ и outbox.
 */
@RestController
@RequestMapping("/api/v5/bus")
public class BusController {

    private final BusService service;

    public BusController(BusService service) {
        this.service = service;
    }

    @GetMapping("/scenario")
    public InMemoryBroker.DeliveryReport scenario(@RequestParam(defaultValue = "false") boolean poison,
                                                 @RequestParam(defaultValue = "6") int events) {
        InMemoryBroker.DeliveryReport report = this.service.runScenario(poison, events);
        return report;
    }

    @GetMapping("/state")
    public BusService.BrokerState state() {
        BusService.BrokerState state = this.service.state();
        return state;
    }

    @PostMapping("/outbox")
    public OutboxPublisher.OutboxView outbox(@RequestParam(required = false) String payload) {
        OutboxPublisher.OutboxView view = this.service.enqueueOutbox(payload);
        return view;
    }
}
