package ru.study.flakyradar.report;

public record ParsedTestCase(
        String suite,
        String name,
        TestStatus status,
        long durationMs,
        ParsedFailure failure,
        int failedAttempts) {
    public String fullName() {
        return suite.isEmpty() ? name : suite + "." + name;
    }

    public boolean passedAfterRetry() {
        return status == TestStatus.PASSED && failedAttempts > 0;
    }
}
