package ru.eight.showcase;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Метаданные витрины: восемь проектов, их темы, гифки и точки входа API.
 * Один источник правды для главной страницы, страниц проектов и игры.
 */
@RestController
@RequestMapping("/api/showcase")
public class ShowcaseController {

    private static final List<ProjectView> PROJECTS = List.of(
            new ProjectView("p1", 1, "REST API по контракту", "DTO, валидация, пагинация, идемпотентность",
                    "/projects/p1.html", "/gifs/p1.gif", "/svg/p1.svg", "#38bdf8",
                    List.of("Spring MVC", "Jakarta Validation", "Idempotency-Key", "RFC 7807-ошибки"),
                    List.of("GET /api/v1/work-items", "POST /api/v1/work-items")),
            new ProjectView("p2", 2, "JPA без N+1", "fetch join, EntityGraph, DTO-проекция, @Version",
                    "/projects/p2.html", "/gifs/p2.gif", "/svg/p2.svg", "#a78bfa",
                    List.of("Hibernate", "Статистика запросов", "Оптимистичные блокировки", "SELECT FOR UPDATE"),
                    List.of("GET /api/v2/jpa/naive", "GET /api/v2/jpa/fetch-join", "POST /api/v2/jpa/optimistic")),
            new ProjectView("p3", 3, "Границы транзакций", "REQUIRED, REQUIRES_NEW, откат, self-invocation",
                    "/projects/p3.html", "/gifs/p3.gif", "/svg/p3.svg", "#f472b6",
                    List.of("@Transactional", "Пропагация", "AOP-прокси", "Независимый аудит"),
                    List.of("POST /api/v3/tx/transfer", "POST /api/v3/tx/propagation-demo")),
            new ProjectView("p4", 4, "Многопоточность", "CompletableFuture, тайм-ауты, гонки, дедлок, пул",
                    "/projects/p4.html", "/gifs/p4.gif", "/svg/p4.svg", "#fbbf24",
                    List.of("ExecutorService", "orTimeout", "Atomic-классы", "AbortPolicy"),
                    List.of("GET /api/v4/concurrency/parallel", "GET /api/v4/concurrency/race",
                            "GET /api/v4/concurrency/deadlock")),
            new ProjectView("p5", 5, "Брокер и Outbox", "партиции, группы, повторы, DLQ, дедупликация",
                    "/projects/p5.html", "/gifs/p5.gif", "/svg/p5.svg", "#34d399",
                    List.of("Kafka-семантика", "ack/nack", "DLQ", "Transactional Outbox"),
                    List.of("GET /api/v5/bus/scenario", "POST /api/v5/bus/outbox")),
            new ProjectView("p6", 6, "XML и XSD", "JAXB, include схем, валидация, защита от XXE",
                    "/projects/p6.html", "/gifs/p6.gif", "/svg/p6.svg", "#22d3ee",
                    List.of("JAXB", "SchemaFactory", "Пространства имён", "XXE-защита"),
                    List.of("POST /api/v6/xml/validate", "POST /api/v6/xml/parse", "GET /api/v6/xml/sample")),
            new ProjectView("p7", 7, "Подпись и сертификаты", "JCA/JCE, X.509, KeyStore, проверка цепочки",
                    "/projects/p7.html", "/gifs/p7.gif", "/svg/p7.svg", "#c084fc",
                    List.of("Signature", "PKCS12", "X.509", "Готово к КриптоПро JCP"),
                    List.of("POST /api/v7/crypto/sign", "POST /api/v7/crypto/verify",
                            "GET /api/v7/crypto/certificate")),
            new ProjectView("p8", 8, "WebSocket и состояния", "жизненный цикл соединений, машина состояний, лимиты",
                    "/projects/p8.html", "/gifs/p8.gif", "/svg/p8.svg", "#fb7185",
                    List.of("Spring WebSocket", "Машина состояний", "Лимиты сообщений", "Освобождение ресурсов"),
                    List.of("WS /ws/state", "GET /api/v8/ws/schema"))
    );

    @GetMapping("/projects")
    public List<ProjectView> projects() {
        return PROJECTS;
    }

    public record ProjectView(String key, int number, String title, String subtitle, String page, String gif, String visual,
                              String accent, List<String> skills, List<String> endpoints) {
    }
}
