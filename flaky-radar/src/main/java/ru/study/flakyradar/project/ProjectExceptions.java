package ru.study.flakyradar.project;

/** Исключения фичи «проекты». */
public final class ProjectExceptions {

    private ProjectExceptions() {
    }

    public static class ProjectNotFoundException extends RuntimeException {
        public ProjectNotFoundException(String key) {
            super("Проект '" + key + "' не найден");
        }
    }

    public static class ProjectAlreadyExistsException extends RuntimeException {
        public ProjectAlreadyExistsException(String key) {
            super("Проект '" + key + "' уже существует");
        }
    }

    /** Неверный или отсутствующий API-ключ проекта. */
    public static class InvalidApiKeyException extends RuntimeException {
        public InvalidApiKeyException() {
            super("Неверный API-ключ проекта (заголовок X-Api-Key)");
        }
    }

    /** Неверный или отсутствующий токен администратора. */
    public static class InvalidAdminTokenException extends RuntimeException {
        public InvalidAdminTokenException() {
            super("Неверный токен администратора (заголовок X-Admin-Token)");
        }
    }
}
