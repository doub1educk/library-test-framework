package ru.study.flakyradar.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Группа одинаковых (после нормализации) ошибок внутри проекта. */
@Entity
@Table(name = "failure_signature")
public class FailureSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(nullable = false, length = 64, updatable = false)
    private String hash;

    @Column(name = "exception_type", length = 512)
    private String exceptionType;

    @Column(name = "normalized_text", nullable = false, columnDefinition = "text")
    private String normalizedText;

    /** Пример исходного (ненормализованного) сообщения, чтобы человеку было понятнее. */
    @Column(name = "sample_message", columnDefinition = "text")
    private String sampleMessage;

    @Column(nullable = false)
    private int occurrences;

    @Column(name = "first_seen", nullable = false, updatable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    protected FailureSignature() {
        // для JPA
    }

    public FailureSignature(Long projectId, String hash, String exceptionType, String normalizedText,
                            String sampleMessage, Instant seenAt) {
        this.projectId = projectId;
        this.hash = hash;
        this.exceptionType = exceptionType;
        this.normalizedText = normalizedText;
        this.sampleMessage = sampleMessage;
        this.firstSeen = seenAt;
        this.lastSeen = seenAt;
    }

    public void recordOccurrence(Instant seenAt) {
        occurrences++;
        lastSeen = seenAt;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getHash() {
        return hash;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public String getNormalizedText() {
        return normalizedText;
    }

    public String getSampleMessage() {
        return sampleMessage;
    }

    public int getOccurrences() {
        return occurrences;
    }

    public Instant getFirstSeen() {
        return firstSeen;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }
}
