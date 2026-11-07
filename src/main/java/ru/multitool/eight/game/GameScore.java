package ru.multitool.eight.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "game_score")
public class GameScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player", nullable = false, length = 40)
    private String player;

    @Column(name = "level", nullable = false)
    private int level;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "seconds", nullable = false)
    private int seconds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected GameScore() {
        this.createdAt = Instant.now();
    }

    public GameScore(String player, int level, int score, int seconds) {
        this.player = player;
        this.level = level;
        this.score = score;
        this.seconds = seconds;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return this.id;
    }

    public String getPlayer() {
        return this.player;
    }

    public int getLevel() {
        return this.level;
    }

    public int getScore() {
        return this.score;
    }

    public int getSeconds() {
        return this.seconds;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }
}
