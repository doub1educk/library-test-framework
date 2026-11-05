package ru.study.library.isbn;

public class InvalidIsbnException extends RuntimeException {

    public InvalidIsbnException(String isbn) {
        super("ISBN " + isbn + " не прошёл проверку во внешнем сервисе");
    }
}
