package ru.study.flakyradar.analysis;

import ru.study.flakyradar.report.TestStatus;

import java.util.List;

public final class VerdictCalculator {
    private final DetectionPolicy policy;

    public VerdictCalculator(DetectionPolicy policy) {
        this.policy = policy;
    }

    public Verdict verdictForFailure(boolean knownFlaky, List<HistoryPoint> priorHistory, String branch, String commitSha) {
        if (knownFlaky || passedBeforeOnSameCommit(priorHistory, commitSha)) {
            return Verdict.KNOWN_FLAKY;
        }
        int streak = trailingFailuresOnBranch(priorHistory, branch) + 1;
        return streak >= policy.brokenStreak() ? Verdict.LONG_BROKEN : Verdict.NEW_FAILURE;
    }

    static boolean passedBeforeOnSameCommit(List<HistoryPoint> history, String commitSha) {
        return history.stream()
                .anyMatch(p -> p.commitSha().equals(commitSha) && p.status() == TestStatus.PASSED);
    }

    static int trailingFailuresOnBranch(List<HistoryPoint> history, String branch) {
        int streak = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            HistoryPoint point = history.get(i);
            if (!point.branch().equals(branch) || point.status() == TestStatus.SKIPPED) {
                continue;
            }
            if (!point.status().isFailure()) {
                break;
            }
            streak++;
        }
        return streak;
    }
}
