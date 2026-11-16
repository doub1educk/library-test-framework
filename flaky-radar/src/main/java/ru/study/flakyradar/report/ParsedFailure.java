package ru.study.flakyradar.report;

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
