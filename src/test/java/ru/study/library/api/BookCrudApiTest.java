package ru.study.library.api;

import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ru.study.library.book.BookRequest;
import ru.study.library.book.BookResponse;
import ru.study.library.framework.assertions.BookAssert;
import ru.study.library.framework.base.BaseApiTest;
import ru.study.library.framework.client.Specs;
import ru.study.library.framework.data.BookDataFactory;
import ru.study.library.framework.wiremock.IsbnServiceMock;

import java.util.List;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;

@Epic("Библиотека")
@Feature("CRUD книг через REST API")
@Owner("student")
@DisplayName("API: CRUD книг")
class BookCrudApiTest extends BaseApiTest {

    @Test
    @Tag("smoke")
    @Story("Создание книги")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("POST: книга создаётся, возвращается 201, Location и тело по JSON-схеме")
    void createBook() {
        BookRequest request = BookDataFactory.validBook();

        Response response = Allure.step("Отправить POST " + Specs.BOOKS_PATH, () -> bookClient.create(request));

        Allure.step("Проверить статус, Location и соответствие JSON-схеме", () -> {
            response.then()
                    .statusCode(201)
                    .header("Location", matchesPattern(".*/api/v1/books/\\d+$"))
                    .body(matchesJsonSchemaInClasspath("schemas/book.json"));
        });

        BookResponse created = response.as(BookResponse.class);
        Allure.step("Проверить поля созданной книги", () -> {
            BookAssert.assertThat(created)
                    .hasGeneratedId()
                    .matches(request)
                    .isAvailable()
                    .hasCreationTimestamp();
        });
        IsbnServiceMock.verifyCalled(1, request.isbn());
    }

    @Test
    @Tag("smoke")
    @Story("Получение книги")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("GET by id: возвращает ранее созданную книгу")
    void getBookById() {
        BookResponse created = bookSteps.createRandomBook();

        BookResponse fetched = bookSteps.getBook(created.id());

        Allure.step("Сравнить полученную книгу с созданной", () -> {
            assertThat(fetched).usingRecursiveComparison()
                    .ignoringFields("createdAt")
                    .isEqualTo(created);
        });
    }

    @Test
    @Story("Обновление книги")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("PUT: название обновляется, ISBN-сервис повторно не вызывается")
    void updateBook() {
        BookRequest original = BookDataFactory.validBook();
        BookResponse created = bookSteps.createBook(original);
        BookRequest changed = BookDataFactory.withTitle(original, "Обновлённое название");

        BookResponse updated = Allure.step("Отправить PUT с новым названием", () ->
                bookClient.update(created.id(), changed)
                        .then().spec(Specs.status(200))
                        .extract().as(BookResponse.class));

        Allure.step("Проверить, что изменилось только название", () -> {
            BookAssert.assertThat(updated).matches(changed);
            assertThat(updated.id()).isEqualTo(created.id());
        });
        IsbnServiceMock.verifyCalled(1, original.isbn());
    }

    @Test
    @Story("Удаление книги")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("DELETE: книга удаляется и больше не находится")
    void deleteBook() {
        BookResponse created = bookSteps.createRandomBook();

        bookSteps.deleteBook(created.id());

        bookSteps.assertBookAbsent(created.id());
        Allure.step("Проверить, что в БД книги тоже нет", () -> {
            assertThat(bookRepository.existsById(created.id())).isFalse();
        });
    }

    @Test
    @Story("Поиск книг")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("GET ?author=: поиск по части имени автора без учёта регистра")
    void searchByAuthor() {
        bookSteps.createBook(BookDataFactory.validBookOf("Лев Толстой"));
        bookSteps.createBook(BookDataFactory.validBookOf("Лев Толстой"));
        bookSteps.createBook(BookDataFactory.validBookOf("Антон Чехов"));

        List<BookResponse> found = bookSteps.findByAuthor("толст");

        Allure.step("Найдены только книги Толстого", () -> {
            assertThat(found)
                    .hasSize(2)
                    .allSatisfy(book -> assertThat(book.author()).isEqualTo("Лев Толстой"));
        });
    }
}
