package ru.study.flakyradar.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Проект (репозиторий команды), в который CI отправляет отчёты. */
@Entity
@Table(name = "project")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_key", nullable = false, unique = true, length = 64)
    private String key;

    @Column(nullable = false)
    private String name;

    /** SHA-256 от API-ключа: сам ключ показывается один раз при создании и нигде не хранится. */
    @Column(name = "api_key_hash", nullable = false, length = 64)
    private String apiKeyHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Project() {
        // для JPA
    }

    public Project(String key, String name, String apiKeyHash, Instant createdAt) {
        this.key = key;
        this.name = name;
        this.apiKeyHash = apiKeyHash;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public void setApiKeyHash(String apiKeyHash) {
        this.apiKeyHash = apiKeyHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
