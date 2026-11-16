package ru.study.flakyradar.history;

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
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import ru.study.flakyradar.analysis.GateEvaluator;
import ru.study.flakyradar.analysis.Verdict;
import ru.study.flakyradar.report.TestStatus;

@Entity
@Table(name = "test_result")
public class TestResult {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "test_result_seq")
    @SequenceGenerator(name = "test_result_seq", sequenceName = "test_result_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false, updatable = false)
    private TestRun run;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_case_id", nullable = false, updatable = false)
    private TestCase testCase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TestStatus status;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(columnDefinition = "text")
    private String message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signature_id")
    private FailureSignature signature;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Verdict verdict;

    @Column(nullable = false)
    private boolean quarantined;

    protected TestResult() {
    }

    public TestResult(TestRun run, TestCase testCase, TestStatus status, long durationMs, int failedAttempts,
                      String message, FailureSignature signature, Verdict verdict, boolean quarantined) {
        this.run = run;
        this.testCase = testCase;
        this.status = status;
        this.durationMs = durationMs;
        this.failedAttempts = failedAttempts;
        this.message = message;
        this.signature = signature;
        this.verdict = verdict;
        this.quarantined = quarantined;
    }

    public boolean passedAfterRetry() {
        return status == TestStatus.PASSED && failedAttempts > 0;
    }

    public GateEvaluator.Outcome outcome(boolean quarantinedNow) {
        return new GateEvaluator.Outcome(verdict, quarantinedNow, passedAfterRetry());
    }

    public Long getId() {
        return id;
    }

    public TestRun getRun() {
        return run;
    }

    public TestCase getTestCase() {
        return testCase;
    }

    public TestStatus getStatus() {
        return status;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public String getMessage() {
        return message;
    }

    public FailureSignature getSignature() {
        return signature;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public boolean isQuarantined() {
        return quarantined;
    }
}
