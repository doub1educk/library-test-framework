package ru.study.flakyradar.run;

public final class RunExceptions {
    private RunExceptions() {
    }

    public static class RunNotFoundException extends RuntimeException {
        public RunNotFoundException(long id) {
            super("Прогон " + id + " не найден");
        }
    }
}
