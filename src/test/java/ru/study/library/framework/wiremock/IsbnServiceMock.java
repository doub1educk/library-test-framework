package ru.study.library.framework.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.qameta.allure.Step;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Эмуляция внешнего сервиса проверки ISBN.
 * <p>
 * Сервер один на всю JVM: Spring кэширует контекст между тестовыми классами,
 * и если бы у каждого класса был свой WireMock на новом порту, закэшированный
 * контекст продолжал бы ходить на старый порт. Поэтому singleton + reset перед каждым тестом.
 */
public final class IsbnServiceMock {

    private static final WireMockServer SERVER = new WireMockServer(options().dynamicPort());

    static {
        SERVER.start();
        Runtime.getRuntime().addShutdownHook(new Thread(SERVER::stop));
    }

    private IsbnServiceMock() {
    }

    public static String baseUrl() {
        return SERVER.baseUrl();
    }

    /** Сброс к поведению по умолчанию: любой ISBN валиден. Без @Step, чтобы не засорять отчёт. */
    public static void resetToDefault() {
        SERVER.resetAll();
        SERVER.stubFor(get(urlPathMatching("/api/isbn/.*")).willReturn(okJson("{\"valid\": true}")));
    }

    @Step("ISBN-сервис: ISBN {isbn} считается невалидным")
    public static void isbnIsInvalid(String isbn) {
        SERVER.stubFor(get(urlPathEqualTo("/api/isbn/" + isbn)).willReturn(okJson("{\"valid\": false}")));
    }

    @Step("ISBN-сервис: отвечает ошибкой HTTP {status}")
    public static void respondsWithError(int status) {
        SERVER.stubFor(get(urlPathMatching("/api/isbn/.*")).willReturn(aResponse().withStatus(status)));
    }

    @Step("ISBN-сервис: отвечает с задержкой {delay}")
    public static void respondsSlowly(Duration delay) {
        SERVER.stubFor(get(urlPathMatching("/api/isbn/.*"))
                .willReturn(okJson("{\"valid\": true}").withFixedDelay((int) delay.toMillis())));
    }

    @Step("Проверить, что ISBN-сервис вызван {times} раз(а) для ISBN {isbn}")
    public static void verifyCalled(int times, String isbn) {
        SERVER.verify(exactly(times), getRequestedFor(urlPathEqualTo("/api/isbn/" + isbn)));
    }
}
