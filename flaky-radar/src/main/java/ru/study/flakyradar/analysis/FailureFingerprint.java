package ru.study.flakyradar.analysis;

/**
 * Подпись ошибки: по ней падения разных прогонов объединяются в группы.
 *
 * @param exceptionType   тип исключения (может быть пустым, если отчёт его не сообщает)
 * @param normalizedText  нормализованный текст: тип, первая строка сообщения и значимые кадры стека
 * @param hash            SHA-256 от нормализованного текста (64 hex-символа)
 */
public record FailureFingerprint(String exceptionType, String normalizedText, String hash) {
}
