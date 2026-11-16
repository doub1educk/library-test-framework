package ru.study.flakyradar.report;

/**
 * Один тест из отчёта после слияния повторов.
 *
 * @param suite          classname (или имя testsuite, если classname не задан)
 * @param name           имя теста
 * @param status         итоговый статус (по последнему выполнению)
 * @param durationMs     суммарная длительность всех выполнений
 * @param failure        последнее падение (в т.ч. из flakyFailure), null если падений не было
 * @param failedAttempts сколько выполнений внутри отчёта упало (ретраи Surefire, pytest, Playwright)
 */
public record ParsedTestCase(
        String suite,
        String name,
        TestStatus status,
        long durationMs,
        ParsedFailure failure,
        int failedAttempts) {

    /** Идентичность теста внутри проекта. */
    public String fullName() {
        return suite.isEmpty() ? name : suite + "." + name;
    }

    /** Тест прошёл только после повтора: прямое доказательство нестабильности. */
    public boolean passedAfterRetry() {
        return status == TestStatus.PASSED && failedAttempts > 0;
    }
}
