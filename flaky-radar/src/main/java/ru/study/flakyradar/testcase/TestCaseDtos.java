package ru.study.flakyradar.testcase;

import ru.study.flakyradar.analysis.Verdict;
import ru.study.flakyradar.history.FailureSignature;
import ru.study.flakyradar.history.TestCase;
import ru.study.flakyradar.history.TestResult;
import ru.study.flakyradar.report.TestStatus;

import java.time.Instant;
import java.util.List;

/** DTO тестов и подписей ошибок. */
public final class TestCaseDtos {

    private TestCaseDtos() {
    }

    public record TestCaseView(
            Long id,
            String fullName,
            String suite,
            String name,
            boolean flaky,
            int flipCount,
            TestStatus lastStatus,
            Instant firstSeen,
            Instant lastSeen,
            boolean quarantined) {

        static TestCaseView from(TestCase tc, boolean quarantined) {
            return new TestCaseView(tc.getId(), tc.getFullName(), tc.getSuite(), tc.getName(), tc.isFlaky(),
                    tc.getFlipCount(), tc.getLastStatus(), tc.getFirstSeen(), tc.getLastSeen(), quarantined);
        }
    }

    public record HistoryEntry(
            Long runId,
            String branch,
            String commitSha,
            int attempt,
            Instant receivedAt,
            TestStatus status,
            long durationMs,
            int failedAttempts,
            Verdict verdict,
            String message,
            Long signatureId) {

        static HistoryEntry from(TestResult r) {
            return new HistoryEntry(r.getRun().getId(), r.getRun().getBranch(), r.getRun().getCommitSha(),
                    r.getRun().getAttempt(), r.getRun().getReceivedAt(), r.getStatus(), r.getDurationMs(),
                    r.getFailedAttempts(), r.getVerdict(), r.getMessage(),
                    r.getSignature() == null ? null : r.getSignature().getId());
        }

        public boolean passedAfterRetry() {
            return status == TestStatus.PASSED && failedAttempts > 0;
        }

        public String shortSha() {
            return commitSha.length() > 7 ? commitSha.substring(0, 7) : commitSha;
        }
    }

    /** История теста: самые новые результаты первыми. */
    public record TestHistoryView(TestCaseView test, List<HistoryEntry> results) {
    }

    public record SignatureView(
            Long id,
            String hash,
            String exceptionType,
            String normalizedText,
            String sampleMessage,
            int occurrences,
            long affectedTests,
            Instant firstSeen,
            Instant lastSeen) {

        static SignatureView from(FailureSignature s, long affectedTests) {
            return new SignatureView(s.getId(), s.getHash(), s.getExceptionType(), s.getNormalizedText(),
                    s.getSampleMessage(), s.getOccurrences(), affectedTests, s.getFirstSeen(), s.getLastSeen());
        }

        public String shortHash() {
            return hash.substring(0, 10);
        }
    }

    public record SignatureOccurrence(Long runId, String branch, String commitSha, Instant receivedAt,
                                      Long testCaseId, String testName, TestStatus status) {

        static SignatureOccurrence from(TestResult r) {
            return new SignatureOccurrence(r.getRun().getId(), r.getRun().getBranch(), r.getRun().getCommitSha(),
                    r.getRun().getReceivedAt(), r.getTestCase().getId(), r.getTestCase().getFullName(), r.getStatus());
        }
    }

    public record SignatureDetails(SignatureView signature, List<SignatureOccurrence> occurrences) {
    }
}
