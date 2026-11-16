package ru.study.library.api;

import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ru.study.library.book.BookRequest;
import ru.study.library.book.BookResponse;
import ru.study.library.framework.base.BaseApiTest;
import ru.study.library.framework.client.Specs;
import ru.study.library.framework.data.BookDataFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

@Epic("Библиотека")
@Feature("Валидация и обработка ошибок")
@DisplayName("API: негативные сценарии")
class BookValidationApiTest extends BaseApiTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("ru.study.library.framework.data.BookDataFactory#invalidBooks")
    @Story("Валидация входных данных")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Некорректная книга отклоняется с 400 и указанием поля")
    void invalidBookRejected(String description, BookRequest request, String invalidField) {
        bookClient.create(request)
                .then().spec(Specs.problem(400))
                .body("errors." + invalidField, notNullValue());

        Allure.step("Книга не сохранилась в БД", () -> {
            assertThat(bookRepository.count()).isZero();
        });
    }

    @Test
    @Story("Уникальность ISBN")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Повторное создание книги с тем же ISBN -> 409 Conflict")
    void duplicateIsbnRejected() {
        BookResponse existing = bookSteps.createRandomBook();
        BookRequest duplicate = BookDataFactory.withIsbn(BookDataFactory.validBook(), existing.isbn());

        bookClient.create(duplicate)
                .then().spec(Specs.problem(409))
                .body("detail", containsString(existing.isbn()));
    }

    @Test
    @Story("Обработка некорректного JSON")
    @Severity(SeverityLevel.MINOR)
    @DisplayName("Сломанный JSON -> 400 без стектрейса в ответе")
    void malformedJsonRejected() {
        bookClient.createRaw("{ \"title\": \"Без закрывающей скобки\" ")
                .then().spec(Specs.problem(400))
                .body("trace", org.hamcrest.Matchers.nullValue());
    }

    @Test
    @Story("Получение книги")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Несуществующая книга -> 404 Problem Details")
    void unknownBookReturns404() {
        bookClient.getById(999_999)
                .then().spec(Specs.problem(404))
                .body("detail", containsString("999999"));
    }
}
