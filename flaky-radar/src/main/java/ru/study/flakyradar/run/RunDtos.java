package ru.study.flakyradar.run;

import ru.study.flakyradar.analysis.GateDecision;
import ru.study.flakyradar.analysis.Verdict;
import ru.study.flakyradar.history.TestResult;
import ru.study.flakyradar.history.TestRun;
import ru.study.flakyradar.report.TestStatus;

import java.time.Instant;
import java.util.List;

public final class RunDtos {
    private RunDtos() {
    }

    public record RunView(
            Long id,
            String branch,
            String commitSha,
            int attempt,
            String ciRunId,
            String buildUrl,
            Instant receivedAt,
            int total,
            int passed,
            int failed,
            int skipped,
            GateDecision decision) {
        public static RunView from(TestRun run) {
            return new RunView(run.getId(), run.getBranch(), run.getCommitSha(), run.getAttempt(), run.getCiRunId(),
                    run.getBuildUrl(), run.getReceivedAt(), run.getTotal(), run.getPassed(), run.getFailed(),
                    run.getSkipped(), run.getDecision());
        }

        public String shortSha() {
            return commitSha.length() > 7 ? commitSha.substring(0, 7) : commitSha;
        }
    }

    public record ResultView(
            Long testCaseId,
            String testName,
            TestStatus status,
            long durationMs,
            int failedAttempts,
            Verdict verdict,
            boolean quarantined,
            boolean passedAfterRetry,
            String message,
            Long signatureId) {
        static ResultView from(TestResult r, boolean quarantinedNow) {
            return new ResultView(r.getTestCase().getId(), r.getTestCase().getFullName(), r.getStatus(),
                    r.getDurationMs(), r.getFailedAttempts(), r.getVerdict(), quarantinedNow, r.passedAfterRetry(),
                    r.getMessage(), r.getSignature() == null ? null : r.getSignature().getId());
        }
    }

    public record GateItem(
            String testName,
            TestStatus status,
            Verdict verdict,
            boolean quarantined,
            boolean passedAfterRetry,
            String message) {
        static GateItem from(ResultView r) {
            return new GateItem(r.testName(), r.status(), r.verdict(), r.quarantined(), r.passedAfterRetry(), r.message());
        }
    }

    public record GateResponse(Long runId, GateDecision decision, List<GateItem> blocking, List<GateItem> warnings) {
    }

    public record RunDetails(
            String projectKey,
            RunView run,
            GateResponse gate,
            List<ResultView> problems,
            String dashboardUrl) {
    }
}
