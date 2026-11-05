package ru.study.library.framework.assertions;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.SoftAssertions;
import ru.study.library.book.BookRequest;
import ru.study.library.book.BookResponse;

/**
 * Собственный AssertJ-ассерт: тесты читаются как предложения
 * BookAssert.assertThat(book).hasGeneratedId().matches(request).isAvailable();
 */
public class BookAssert extends AbstractAssert<BookAssert, BookResponse> {

    private BookAssert(BookResponse actual) {
        super(actual, BookAssert.class);
    }

    public static BookAssert assertThat(BookResponse actual) {
        return new BookAssert(actual);
    }

    public BookAssert hasGeneratedId() {
        isNotNull();
        if (actual.id() == null || actual.id() <= 0) {
            failWithMessage("Ожидался сгенерированный положительный id, но был <%s>", actual.id());
        }
        return this;
    }

    /** Мягкие проверки: в отчёте будут видны ВСЕ несовпавшие поля, а не только первое. */
    public BookAssert matches(BookRequest expected) {
        isNotNull();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(actual.title()).as("title").isEqualTo(expected.title());
            softly.assertThat(actual.author()).as("author").isEqualTo(expected.author());
            softly.assertThat(actual.isbn()).as("isbn").isEqualTo(expected.isbn());
            softly.assertThat(actual.publishedYear()).as("publishedYear").isEqualTo(expected.publishedYear());
        });
        return this;
    }

    public BookAssert isAvailable() {
        isNotNull();
        if (!actual.available()) {
            failWithMessage("Ожидалось, что книга доступна");
        }
        return this;
    }

    public BookAssert hasCreationTimestamp() {
        isNotNull();
        if (actual.createdAt() == null) {
            failWithMessage("Ожидалось заполненное поле createdAt");
        }
        return this;
    }
}
