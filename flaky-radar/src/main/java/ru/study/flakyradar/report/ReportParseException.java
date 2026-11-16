package ru.study.flakyradar.report;

/** Отчёт не удалось разобрать: битый XML, неизвестный корень, тест без имени. */
public class ReportParseException extends RuntimeException {

    public ReportParseException(String message) {
        super(message);
    }

    public ReportParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
