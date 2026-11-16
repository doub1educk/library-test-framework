package ru.study.library.api;

import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.study.library.book.BookRequest;
import ru.study.library.book.BookResponse;
import ru.study.library.framework.base.BaseApiTest;
import ru.study.library.framework.client.Specs;
import ru.study.library.framework.data.BookDataFactory;
import ru.study.library.framework.wiremock.IsbnServiceMock;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Библиотека")
@Feature("Интеграция с внешним сервисом ISBN (WireMock)")
@DisplayName("API: интеграция с ISBN-сервисом")
class IsbnIntegrationApiTest extends BaseApiTest {

    @Test
    @Story("ISBN не прошёл проверку")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Внешний сервис говорит, что ISBN невалиден -> 422, книга не сохраняется")
    void invalidIsbnFromExternalService() {
        BookRequest request = BookDataFactory.validBook();
        IsbnServiceMock.isbnIsInvalid(request.isbn());

        bookClient.create(request).then().spec(Specs.problem(422));

        IsbnServiceMock.verifyCalled(1, request.isbn());
        Allure.step("В БД книг нет", () -> {
            assertThat(bookRepository.count()).isZero();
        });
    }

    @Test
    @Story("Отказ внешнего сервиса")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Внешний сервис отвечает 500 -> наш сервис отвечает 503")
    void externalServiceError() {
        IsbnServiceMock.respondsWithError(500);

        bookClient.create(BookDataFactory.validBook()).then().spec(Specs.problem(503));
    }

    @Test
    @Story("Отказ внешнего сервиса")
    @Severity(SeverityLevel.NORMAL)
    @Description("Таймаут клиента в тестах 1 секунда, мок отвечает через 5 секунд. "
            + "Приложение должно прервать ожидание, вернуть 503 и не повторять запрос.")
    @DisplayName("Внешний сервис отвечает слишком долго -> срабатывает таймаут, 503")
    void externalServiceTimeout() {
        Duration mockDelay = Duration.ofSeconds(5);
        IsbnServiceMock.respondsSlowly(mockDelay);
        BookRequest request = BookDataFactory.validBook();

        long startedAt = System.nanoTime();
        bookClient.create(request).then().spec(Specs.problem(503));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        Allure.step("Ответ пришёл до того, как мок ответил: " + elapsed.toMillis() + " мс", () -> {
            assertThat(elapsed).isLessThan(mockDelay.minusSeconds(1));
        });
        IsbnServiceMock.verifyCalled(1, request.isbn());
    }

    @Test
    @Story("Порядок проверок")
    @Severity(SeverityLevel.MINOR)
    @DisplayName("При дубликате ISBN внешний сервис не вызывается повторно")
    void duplicateDoesNotCallExternalService() {
        BookResponse existing = bookSteps.createRandomBook();

        bookClient.create(BookDataFactory.withIsbn(BookDataFactory.validBook(), existing.isbn()))
                .then().spec(Specs.problem(409));

        IsbnServiceMock.verifyCalled(1, existing.isbn());
    }
}
