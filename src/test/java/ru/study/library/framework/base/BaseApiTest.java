package ru.study.library.framework.base;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.study.library.book.BookRepository;
import ru.study.library.framework.annotation.ApiTest;
import ru.study.library.framework.client.BookClient;
import ru.study.library.framework.steps.BookSteps;
import ru.study.library.framework.wiremock.IsbnServiceMock;

/**
 * Базовый класс для API-тестов: поднятое приложение, чистая БД и сброшенный мок
 * внешнего сервиса перед каждым тестом.
 */
@ApiTest
public abstract class BaseApiTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected BookRepository bookRepository;

    protected BookClient bookClient;
    protected BookSteps bookSteps;

    @DynamicPropertySource
    static void externalServices(DynamicPropertyRegistry registry) {
        registry.add("isbn-service.base-url", IsbnServiceMock::baseUrl);
        registry.add("isbn-service.timeout", () -> "1s");
    }

    @BeforeEach
    void prepareEnvironment() {
        IsbnServiceMock.resetToDefault();
        bookRepository.deleteAll();
        bookClient = new BookClient(port);
        bookSteps = new BookSteps(bookClient);
    }
}
