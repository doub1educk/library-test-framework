package ru.study.flakyradar.report;

public enum TestStatus {
    PASSED,
    FAILED,
    ERROR,
    SKIPPED;

    public boolean isFailure() {
        return this == FAILED || this == ERROR;
    }
}
