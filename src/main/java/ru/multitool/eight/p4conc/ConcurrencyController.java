package ru.multitool.eight.p4conc;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Проект 4. Управляемые демонстрации: параллельные вызовы, тайм-аут,
 * потерянные обновления, дедлок и перегрузка пула.
 */
@RestController
@RequestMapping("/api/v4/concurrency")
public class ConcurrencyController {

    private final ConcurrencyService service;

    public ConcurrencyController(ConcurrencyService service) {
        this.service = service;
    }

    @GetMapping("/parallel")
    public ConcurrencyService.ParallelReport parallel() {
        ConcurrencyService.ParallelReport report = this.service.parallel();
        return report;
    }

    @GetMapping("/timeout")
    public ConcurrencyService.TimeoutReport timeout() {
        ConcurrencyService.TimeoutReport report = this.service.timeout();
        return report;
    }

    @GetMapping("/race")
    public ConcurrencyService.RaceReport race(@RequestParam(defaultValue = "4") int threads,
                                             @RequestParam(defaultValue = "20000") int iterations) {
        ConcurrencyService.RaceReport report = this.service.race(threads, iterations);
        return report;
    }

    @GetMapping("/deadlock")
    public ConcurrencyService.DeadlockReport deadlock(@RequestParam(defaultValue = "false") boolean ordered) {
        ConcurrencyService.DeadlockReport report = this.service.deadlock(ordered);
        return report;
    }

    @GetMapping("/pool")
    public ConcurrencyService.PoolReport pool(@RequestParam(defaultValue = "100") int tasks) {
        ConcurrencyService.PoolReport report = this.service.pool(tasks);
        return report;
    }
}
