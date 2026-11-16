package ru.study.flakyradar.ingest;

/** Исключения приёма отчётов. */
public final class IngestExceptions {

    private IngestExceptions() {
    }

    /** В запросе нет ни одного файла отчёта. */
    public static class NoReportsException extends RuntimeException {
        public NoReportsException() {
            super("Не передан ни один файл отчёта (поле report)");
        }
    }

    /** Отчёт корректен, но в нём нет ни одного теста. */
    public static class EmptyReportException extends RuntimeException {
        public EmptyReportException() {
            super("В отчёте нет ни одного теста");
        }
    }
}
