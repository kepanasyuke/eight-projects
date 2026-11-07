package ru.multitool.eight.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "assignee")
public class Assignee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "email", nullable = false, unique = true, length = 160)
    private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Assignee() {
        this.createdAt = Instant.now();
    }

    public Assignee(String name, String email) {
        this.name = name;
        this.email = email;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return this.email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }
}
