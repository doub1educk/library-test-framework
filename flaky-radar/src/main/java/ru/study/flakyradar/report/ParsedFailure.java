package ru.study.flakyradar.report;

/**
 * Описание падения из отчёта.
 *
 * @param type       значение атрибута {@code type} (класс исключения или условный тип), может быть пустым
 * @param message    значение атрибута {@code message}, может быть пустым
 * @param stackTrace текст внутри элемента (обычно стек вызовов), может быть пустым
 */
public record ParsedFailure(String type, String message, String stackTrace) {

    public ParsedFailure {
        type = blankToEmpty(type);
        message = blankToEmpty(message);
        stackTrace = blankToEmpty(stackTrace);
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.strip();
    }
}
