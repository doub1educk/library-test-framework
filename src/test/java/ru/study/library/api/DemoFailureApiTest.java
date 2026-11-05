package ru.study.library.api;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ru.study.library.book.BookRequest;
import ru.study.library.book.BookResponse;
import ru.study.library.framework.assertions.BookAssert;
import ru.study.library.framework.base.BaseApiTest;
import ru.study.library.framework.data.BookDataFactory;

/**
 * НАМЕРЕННО ПАДАЮЩИЙ тест для демонстрации на защите:
 * как выглядит упавший тест в Allure (категория «Дефекты продукта»,
 * мягкие проверки со всеми расхождениями, снимок БД во вложении).
 * <p>
 * По умолчанию исключён из прогона (тег demo).
 * Запуск: mvn test -Dgroups=demo -DexcludedGroups=none
 */
@Tag("demo")
@Epic("Библиотека")
@Feature("Демонстрация падения")
@DisplayName("DEMO: намеренно падающий тест")
class DemoFailureApiTest extends BaseApiTest {

    @Test
    @Story("Сравнение с ожидаемым результатом")
    @Issue("DEMO-1")
    @DisplayName("Ожидаем, что API приводит название к верхнему регистру (а оно так не делает)")
    void titleShouldBeUppercased() {
        bookSteps.createRandomBook();
        BookRequest request = BookDataFactory.validBook();

        BookResponse created = bookSteps.createBook(request);

        BookRequest wrongExpectation = new BookRequest(
                request.title().toUpperCase(), request.author(), request.isbn(), request.publishedYear() + 1);
        BookAssert.assertThat(created).matches(wrongExpectation);
    }
}
