package ru.study.flakyradar.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import ru.study.flakyradar.report.TestStatus;

import java.time.Instant;

@Entity
@Table(name = "test_case")
public class TestCase {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "test_case_seq")
    @SequenceGenerator(name = "test_case_seq", sequenceName = "test_case_seq", allocationSize = 50)
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(nullable = false, length = 1024)
    private String suite;

    @Column(nullable = false, length = 1024)
    private String name;

    @Column(name = "full_name", nullable = false, length = 2048)
    private String fullName;

    @Column(nullable = false)
    private boolean flaky;

    @Column(name = "flip_count", nullable = false)
    private int flipCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_status", length = 16)
    private TestStatus lastStatus;

    @Column(name = "first_seen", nullable = false, updatable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    protected TestCase() {
    }

    public TestCase(Long projectId, String suite, String name, String fullName, Instant seenAt) {
        this.projectId = projectId;
        this.suite = suite;
        this.name = name;
        this.fullName = fullName;
        this.firstSeen = seenAt;
        this.lastSeen = seenAt;
    }

    public void recordResult(TestStatus status, boolean flaky, int flipCount, Instant seenAt) {
        if (status != TestStatus.SKIPPED) {
            this.lastStatus = status;
        }
        this.flaky = flaky;
        this.flipCount = flipCount;
        this.lastSeen = seenAt;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getSuite() {
        return suite;
    }

    public String getName() {
        return name;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isFlaky() {
        return flaky;
    }

    public int getFlipCount() {
        return flipCount;
    }

    public TestStatus getLastStatus() {
        return lastStatus;
    }

    public Instant getFirstSeen() {
        return firstSeen;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }
}
