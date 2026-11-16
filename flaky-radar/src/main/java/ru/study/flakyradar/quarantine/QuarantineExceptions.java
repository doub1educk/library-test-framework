package ru.study.flakyradar.quarantine;

/** Исключения фичи «карантин». */
public final class QuarantineExceptions {

    private QuarantineExceptions() {
    }

    public static class AlreadyQuarantinedException extends RuntimeException {
        public AlreadyQuarantinedException(String testName) {
            super("Тест '" + testName + "' уже в карантине");
        }
    }

    public static class QuarantineNotFoundException extends RuntimeException {
        public QuarantineNotFoundException(long id) {
            super("Запись карантина " + id + " не найдена или уже снята");
        }
    }
}
