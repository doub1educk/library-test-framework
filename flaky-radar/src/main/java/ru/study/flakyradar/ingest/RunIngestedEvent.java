package ru.study.flakyradar.ingest;

/**
 * Прогон принят и сохранён. Публикуется внутри транзакции приёма, а слушатели
 * ({@code @TransactionalEventListener}) получают его только после коммита.
 */
public record RunIngestedEvent(String projectKey, Long runId) {
}
