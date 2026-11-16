package ru.study.flakyradar.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ParsedReport(List<ParsedTestCase> testCases) {
    public ParsedReport {
        testCases = List.copyOf(testCases);
    }

    public int total() {
        return testCases.size();
    }

    public int count(TestStatus status) {
        return (int) testCases.stream().filter(tc -> tc.status() == status).count();
    }

    public int failedCount() {
        return (int) testCases.stream().filter(tc -> tc.status().isFailure()).count();
    }

    public static ParsedReport merge(List<ParsedReport> reports) {
        Map<String, ParsedTestCase> merged = new LinkedHashMap<>();
        for (ParsedReport report : reports) {
            for (ParsedTestCase tc : report.testCases()) {
                merged.merge(tc.fullName(), tc, ParsedReport::combine);
            }
        }
        return new ParsedReport(new ArrayList<>(merged.values()));
    }

    static ParsedTestCase combine(ParsedTestCase earlier, ParsedTestCase later) {
        long duration = earlier.durationMs() + later.durationMs();
        if (later.status() == TestStatus.SKIPPED) {
            return withDuration(earlier, duration);
        }
        if (earlier.status() == TestStatus.SKIPPED) {
            return withDuration(later, duration);
        }
        int failedAttempts = earlier.failedAttempts() + later.failedAttempts()
                + (earlier.status().isFailure() ? 1 : 0);
        ParsedFailure failure = later.failure() != null ? later.failure() : earlier.failure();
        return new ParsedTestCase(later.suite(), later.name(), later.status(), duration, failure, failedAttempts);
    }

    private static ParsedTestCase withDuration(ParsedTestCase tc, long duration) {
        return new ParsedTestCase(tc.suite(), tc.name(), tc.status(), duration, tc.failure(), tc.failedAttempts());
    }
}
