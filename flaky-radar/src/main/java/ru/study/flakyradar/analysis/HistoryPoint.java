package ru.study.flakyradar.analysis;

import ru.study.flakyradar.report.TestStatus;

public record HistoryPoint(String commitSha, String branch, TestStatus status, boolean passedAfterRetry) {
    public static HistoryPoint of(String commitSha, String branch, TestStatus status) {
        return new HistoryPoint(commitSha, branch, status, false);
    }
}
