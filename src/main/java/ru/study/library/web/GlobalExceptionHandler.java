package ru.study.library.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.study.library.book.BookNotFoundException;
import ru.study.library.book.DuplicateIsbnException;
import ru.study.library.isbn.InvalidIsbnException;
import ru.study.library.isbn.IsbnServiceUnavailableException;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Единый формат ошибок по RFC 9457 (Problem Details).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BookNotFoundException.class)
    public ProblemDetail handleNotFound(BookNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Книга не найдена", e.getMessage());
    }

    @ExceptionHandler(DuplicateIsbnException.class)
    public ProblemDetail handleDuplicate(DuplicateIsbnException e) {
        return problem(HttpStatus.CONFLICT, "Дубликат ISBN", e.getMessage());
    }

    @ExceptionHandler(InvalidIsbnException.class)
    public ProblemDetail handleInvalidIsbn(InvalidIsbnException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Некорректный ISBN", e.getMessage());
    }

    @ExceptionHandler(IsbnServiceUnavailableException.class)
    public ProblemDetail handleIsbnServiceDown(IsbnServiceUnavailableException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Внешний сервис недоступен", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> Objects.requireNonNullElse(fe.getDefaultMessage(), "некорректное значение"),
                        (first, second) -> first,
                        TreeMap::new));
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Ошибка валидации", "Запрос содержит некорректные поля");
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException e) {
        return problem(HttpStatus.BAD_REQUEST, "Некорректный JSON", "Тело запроса не удалось разобрать");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        return problemDetail;
    }
}
