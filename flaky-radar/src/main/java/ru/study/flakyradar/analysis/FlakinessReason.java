package ru.study.flakyradar.analysis;

/** Почему тест признан нестабильным. */
public enum FlakinessReason {
    /** Внутри одного прогона тест упал, а при повторе прошёл. */
    PASSED_AFTER_RETRY,
    /** На одном и том же коммите тест и проходил, и падал. */
    SAME_COMMIT_MIXED,
    /** Слишком много переключений pass/fail в последних результатах. */
    FREQUENT_FLIPS,
    /** Тест стабилен. */
    NONE
}
