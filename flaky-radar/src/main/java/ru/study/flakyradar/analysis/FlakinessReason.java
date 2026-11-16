package ru.study.flakyradar.analysis;

public enum FlakinessReason {
    PASSED_AFTER_RETRY,
    SAME_COMMIT_MIXED,
    FREQUENT_FLIPS,
    NONE
}
