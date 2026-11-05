package ru.study.library.isbn;

public class IsbnServiceUnavailableException extends RuntimeException {

    public IsbnServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
