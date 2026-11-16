package ru.study.library.book;

public class DuplicateIsbnException extends RuntimeException {

    public DuplicateIsbnException(String isbn) {
        super("Книга с ISBN " + isbn + " уже существует");
    }
}
