package ru.study.flakyradar.analysis;

import ru.study.flakyradar.report.TestStatus;

/**
 * Один результат теста в истории (в хронологическом порядке, самый новый последним).
 *
 * @param commitSha        коммит прогона
 * @param branch           ветка прогона
 * @param status           итоговый статус
 * @param passedAfterRetry тест прошёл только после повтора внутри прогона
 */
public record HistoryPoint(String commitSha, String branch, TestStatus status, boolean passedAfterRetry) {

    public static HistoryPoint of(String commitSha, String branch, TestStatus status) {
        return new HistoryPoint(commitSha, branch, status, false);
    }
}
