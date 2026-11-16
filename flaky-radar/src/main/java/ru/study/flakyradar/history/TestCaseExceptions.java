package ru.study.flakyradar.history;

public final class TestCaseExceptions {
    private TestCaseExceptions() {
    }

    public static class TestCaseNotFoundException extends RuntimeException {
        public TestCaseNotFoundException(String name) {
            super("Тест '" + name + "' не найден в проекте");
        }

        public TestCaseNotFoundException(long id) {
            super("Тест с id " + id + " не найден в проекте");
        }
    }

    public static class SignatureNotFoundException extends RuntimeException {
        public SignatureNotFoundException(long id) {
            super("Подпись ошибки " + id + " не найдена");
        }
    }
}
