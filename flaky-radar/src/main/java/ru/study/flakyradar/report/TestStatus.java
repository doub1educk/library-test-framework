package ru.study.flakyradar.report;

/** Итоговый статус теста в прогоне. */
public enum TestStatus {
    PASSED,
    FAILED,
    ERROR,
    SKIPPED;

    /** FAILED и ERROR оба считаются падением: для анализа нестабильности разницы нет. */
    public boolean isFailure() {
        return this == FAILED || this == ERROR;
    }
}
