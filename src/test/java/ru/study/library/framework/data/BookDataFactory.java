package ru.study.library.framework.data;

import net.datafaker.Faker;
import org.junit.jupiter.params.provider.Arguments;
import ru.study.library.book.BookRequest;

import java.util.stream.Stream;

/**
 * Генерация тестовых данных. Каждый вызов даёт новую уникальную книгу,
 * поэтому тесты не зависят друг от друга и от порядка запуска.
 */
public final class BookDataFactory {

    private static final Faker FAKER = new Faker();

    private BookDataFactory() {
    }

    public static BookRequest validBook() {
        return new BookRequest(
                FAKER.book().title(),
                FAKER.book().author(),
                randomIsbn(),
                FAKER.number().numberBetween(1900, 2025));
    }

    public static BookRequest validBookOf(String author) {
        BookRequest base = validBook();
        return new BookRequest(base.title(), author, base.isbn(), base.publishedYear());
    }

    public static BookRequest withIsbn(BookRequest source, String isbn) {
        return new BookRequest(source.title(), source.author(), isbn, source.publishedYear());
    }

    public static BookRequest withTitle(BookRequest source, String title) {
        return new BookRequest(title, source.author(), source.isbn(), source.publishedYear());
    }

    public static String randomIsbn() {
        return "978" + FAKER.number().digits(10);
    }

    /** Негативные данные для параметризованных тестов: (описание, запрос, поле с ошибкой). */
    public static Stream<Arguments> invalidBooks() {
        BookRequest v = validBook();
        return Stream.of(
                Arguments.of("пустое название", new BookRequest("", v.author(), v.isbn(), v.publishedYear()), "title"),
                Arguments.of("слишком длинное название", new BookRequest("x".repeat(256), v.author(), v.isbn(), v.publishedYear()), "title"),
                Arguments.of("нет автора", new BookRequest(v.title(), null, v.isbn(), v.publishedYear()), "author"),
                Arguments.of("ISBN из 10 цифр", new BookRequest(v.title(), v.author(), "1234567890", v.publishedYear()), "isbn"),
                Arguments.of("ISBN с буквами", new BookRequest(v.title(), v.author(), "978ABCDEFGHIJ", v.publishedYear()), "isbn"),
                Arguments.of("год раньше книгопечатания", new BookRequest(v.title(), v.author(), v.isbn(), 1000), "publishedYear"),
                Arguments.of("год из далёкого будущего", new BookRequest(v.title(), v.author(), v.isbn(), 2500), "publishedYear"),
                Arguments.of("нет года издания", new BookRequest(v.title(), v.author(), v.isbn(), null), "publishedYear")
        );
    }
}
