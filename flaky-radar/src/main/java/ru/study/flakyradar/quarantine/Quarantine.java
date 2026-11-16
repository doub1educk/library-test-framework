package ru.study.flakyradar.quarantine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import ru.study.flakyradar.history.TestCase;

import java.time.Instant;

/**
 * Карантин теста: его падения не блокируют CI. Запись активна, пока не снята вручную
 * ({@code removedAt}) и не истёк срок ({@code expiresAt}, если задан).
 */
@Entity
@Table(name = "quarantine")
public class Quarantine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_case_id", nullable = false, updatable = false)
    private TestCase testCase;

    @Column(nullable = false, length = 1024)
    private String reason;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    protected Quarantine() {
        // для JPA
    }

    public Quarantine(Long projectId, TestCase testCase, String reason, String createdBy, Instant createdAt,
                      Instant expiresAt) {
        this.projectId = projectId;
        this.testCase = testCase;
        this.reason = reason;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isActiveAt(Instant moment) {
        return removedAt == null && (expiresAt == null || expiresAt.isAfter(moment));
    }

    public void remove(Instant moment) {
        if (removedAt == null) {
            removedAt = moment;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public TestCase getTestCase() {
        return testCase;
    }

    public String getReason() {
        return reason;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }
}
