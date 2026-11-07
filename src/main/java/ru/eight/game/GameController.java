package ru.eight.game;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Игровой модуль: восемь уровней платформера, по одному на каждый проект,
 * сохранение результатов и таблица лидеров.
 */
@RestController
@RequestMapping("/api/game")
public class GameController {

    private static final List<LevelView> LEVELS = List.of(
            new LevelView(1, "p1", "Контракт", "REST API по OpenAPI", "#38bdf8", "Собери запросы: DTO, валидация, 201", 6, 1),
            new LevelView(2, "p2", "N+1", "JPA и Hibernate", "#a78bfa", "Не дай ленивым связям размножиться", 7, 1),
            new LevelView(3, "p3", "Границы", "Транзакции", "#f472b6", "Перепрыгни откат и сохрани аудит", 7, 2),
            new LevelView(4, "p4", "Гонка", "Многопоточность", "#fbbf24", "Потоки бегут вразнобой — не потеряй счёт", 8, 2),
            new LevelView(5, "p5", "Очередь", "Kafka и RabbitMQ", "#34d399", "Сообщения летят в DLQ — перехвати их", 8, 3),
            new LevelView(6, "p6", "Схема", "XML и XSD", "#22d3ee", "XXE крадётся снаружи — закрой сущности", 9, 3),
            new LevelView(7, "p7", "Подпись", "КриптоПро и JCA", "#c084fc", "Подпиши ключом и не потеряй сертификат", 9, 4),
            new LevelView(8, "p8", "Состояние", "WebSocket и машина состояний", "#fb7185", "Дойди до DONE без нелегальных переходов", 10, 4)
    );

    private final GameScoreRepository scores;

    public GameController(GameScoreRepository scores) {
        this.scores = scores;
    }

    @GetMapping("/levels")
    public List<LevelView> levels() {
        return LEVELS;
    }

    @PostMapping("/scores")
    public ScoreView save(@Valid @RequestBody ScoreRequest request) {
        int level = Math.min(Math.max(request.level(), 1), LEVELS.size());
        GameScore saved = this.scores.save(new GameScore(request.player().trim(), level,
                Math.max(request.score(), 0), Math.max(request.seconds(), 0)));
        ScoreView view = ScoreView.from(saved);
        return view;
    }

    @GetMapping("/leaderboard")
    public List<ScoreView> leaderboard(@RequestParam(defaultValue = "10") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        List<ScoreView> views = this.scores.findAllByOrderByScoreDescCreatedAtAsc(PageRequest.of(0, safeLimit))
                .stream().map(ScoreView::from).toList();
        return views;
    }

    @GetMapping("/stats")
    public StatsView stats() {
        Map<String, Long> perLevel = new LinkedHashMap<>();
        for (LevelView level : LEVELS) {
            perLevel.put(level.title(), this.scores.countByLevel(level.id()));
        }
        StatsView view = new StatsView(this.scores.count(), perLevel, LEVELS.size());
        return view;
    }

    public record ScoreRequest(
            @NotBlank(message = "имя игрока обязательно")
            @Size(min = 2, max = 20, message = "имя от 2 до 20 символов")
            String player,

            @Min(value = 1, message = "уровень от 1")
            @Max(value = 8, message = "уровень до 8")
            int level,

            @Min(value = 0, message = "счёт не может быть отрицательным")
            int score,

            @Min(value = 0, message = "время не может быть отрицательным")
            int seconds
    ) {
    }

    public record ScoreView(Long id, String player, int level, int score, int seconds, Instant createdAt) {
        public static ScoreView from(GameScore score) {
            return new ScoreView(score.getId(), score.getPlayer(), score.getLevel(), score.getScore(),
                    score.getSeconds(), score.getCreatedAt());
        }
    }

    public record StatsView(long totalRuns, Map<String, Long> runsPerLevel, int levels) {
    }

    public record LevelView(int id, String key, String title, String projectTitle, String accent, String hint,
                            int orbs, int enemySpeed) {
    }
}
