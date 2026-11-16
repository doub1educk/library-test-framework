package ru.study.flakyradar.ingest;

public final class IngestExceptions {
    private IngestExceptions() {
    }

    public static class NoReportsException extends RuntimeException {
        public NoReportsException() {
            super("Не передан ни один файл отчёта (поле report)");
        }
    }

    public static class EmptyReportException extends RuntimeException {
        public EmptyReportException() {
            super("В отчёте нет ни одного теста");
        }
    }
}
