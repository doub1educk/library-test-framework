package ru.study.flakyradar.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Результат разбора одного или нескольких файлов отчёта. */
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

    /**
     * Объединяет отчёты нескольких файлов. Если один и тот же тест встретился в разных файлах
     * (например, отчёт каждого ретрая лежит отдельно), выполнения сливаются так же, как внутри файла.
     */
    public static ParsedReport merge(List<ParsedReport> reports) {
        Map<String, ParsedTestCase> merged = new LinkedHashMap<>();
        for (ParsedReport report : reports) {
            for (ParsedTestCase tc : report.testCases()) {
                merged.merge(tc.fullName(), tc, ParsedReport::combine);
            }
        }
        return new ParsedReport(new ArrayList<>(merged.values()));
    }

    /**
     * Слияние двух выполнений одного теста: итоговый статус по последнему,
     * упавшие выполнения суммируются, сохраняется последнее известное падение.
     */
    static ParsedTestCase combine(ParsedTestCase earlier, ParsedTestCase later) {
        long duration = earlier.durationMs() + later.durationMs();
        // пропуск в одном из выполнений не перекрывает реальный результат
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
