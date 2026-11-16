package ru.study.flakyradar.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ru.study.flakyradar.analysis.GateDecision;

import java.time.Instant;

@Entity
@Table(name = "test_run")
public class TestRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(nullable = false)
    private String branch;

    @Column(name = "commit_sha", nullable = false, length = 64)
    private String commitSha;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "ci_run_id", length = 128)
    private String ciRunId;

    @Column(name = "build_url", length = 1024)
    private String buildUrl;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private int passed;

    @Column(nullable = false)
    private int failed;

    @Column(nullable = false)
    private int skipped;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GateDecision decision;

    protected TestRun() {
    }

    public TestRun(Long projectId, String branch, String commitSha, int attempt, String ciRunId, String buildUrl,
                   Instant receivedAt) {
        this.projectId = projectId;
        this.branch = branch;
        this.commitSha = commitSha;
        this.attempt = attempt;
        this.ciRunId = ciRunId;
        this.buildUrl = buildUrl;
        this.receivedAt = receivedAt;
        this.decision = GateDecision.PASS;
    }

    public void setCounts(int total, int passed, int failed, int skipped) {
        this.total = total;
        this.passed = passed;
        this.failed = failed;
        this.skipped = skipped;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getBranch() {
        return branch;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public int getAttempt() {
        return attempt;
    }

    public String getCiRunId() {
        return ciRunId;
    }

    public String getBuildUrl() {
        return buildUrl;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public int getTotal() {
        return total;
    }

    public int getPassed() {
        return passed;
    }

    public int getFailed() {
        return failed;
    }

    public int getSkipped() {
        return skipped;
    }

    public GateDecision getDecision() {
        return decision;
    }

    public void setDecision(GateDecision decision) {
        this.decision = decision;
    }
}
