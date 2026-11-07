package ru.eight.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "work_item")
public class WorkItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkItemStatus status = WorkItemStatus.NEW;

    @Column(name = "priority", nullable = false)
    private int priority = 3;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private Assignee assignee;

    @OneToMany(mappedBy = "workItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<WorkNote> notes = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected WorkItem() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public WorkItem(String title, String description, int priority, Assignee assignee) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.assignee = assignee;
        this.status = WorkItemStatus.NEW;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void addNote(String author, String content) {
        WorkNote note = new WorkNote(this, author, content);
        this.notes.add(note);
        this.updatedAt = Instant.now();
    }

    public void changeStatus(WorkItemStatus target) {
        this.status = WorkItemStateMachine.transition(this.status, target);
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return this.id;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.updatedAt = Instant.now();
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public WorkItemStatus getStatus() {
        return this.status;
    }

    public int getPriority() {
        return this.priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
        this.updatedAt = Instant.now();
    }

    public Assignee getAssignee() {
        return this.assignee;
    }

    public void setAssignee(Assignee assignee) {
        this.assignee = assignee;
        this.updatedAt = Instant.now();
    }

    public List<WorkNote> getNotes() {
        return this.notes;
    }

    public long getVersion() {
        return this.version;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }
}
