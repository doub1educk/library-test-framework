package ru.study.flakyradar.report;

public class ReportParseException extends RuntimeException {
    public ReportParseException(String message) {
        super(message);
    }

    public ReportParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
