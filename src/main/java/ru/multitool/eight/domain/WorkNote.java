package ru.multitool.eight.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "work_note")
public class WorkNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_item_id", nullable = false)
    private WorkItem workItem;

    @Column(name = "author", nullable = false, length = 80)
    private String author;

    @Column(name = "content", nullable = false, length = 1000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected WorkNote() {
        this.createdAt = Instant.now();
    }

    public WorkNote(WorkItem workItem, String author, String content) {
        this.workItem = workItem;
        this.author = author;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return this.id;
    }

    public WorkItem getWorkItem() {
        return this.workItem;
    }

    public String getAuthor() {
        return this.author;
    }

    public String getContent() {
        return this.content;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }
}
