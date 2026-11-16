package ru.study.flakyradar.unit;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.study.flakyradar.analysis.FailureFingerprint;
import ru.study.flakyradar.analysis.SignatureNormalizer;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Epic("Flaky Radar")
@Feature("Подписи ошибок")
@DisplayName("Unit: SignatureNormalizer")
class SignatureNormalizerTest {

    private final SignatureNormalizer normalizer = new SignatureNormalizer();

    @ParameterizedTest(name = "{0}")
    @Story("Правила нормализации")
    @DisplayName("Каждое правило заменяет изменчивую часть на метку")
    @CsvSource(delimiter = '|', textBlock = """
            UUID                        | user 3f2b8c1e-9a4d-4e2f-8b7a-1c2d3e4f5a6b not found | user <UUID> not found
            порт localhost              | Connection refused: localhost:54321                 | Connection refused: localhost:<PORT>
            порт в URL                  | GET http://db-host:5432/api failed                  | GET http://db-host:<PORT>/api failed
            IP-адрес                    | connect to 10.1.2.3 timed out                       | connect to <IP> timed out
            ISO-время                   | at 2026-11-15T10:20:30.123Z expired                 | at <TS> expired
            дата                        | report for 2026-11-15 is late                       | report for <DATE> is late
            время суток                 | started at 12:34:56                                 | started at <TIME>
            hex-адрес                   | segfault at 0x7ffe12ab                              | segfault at <HEX>
            hashCode объекта            | expected ru.demo.Book@1b6d3586                      | expected ru.demo.Book@<HEX>
            длительность                | Timeout 30000ms exceeded                            | Timeout <DUR> exceeded
            длительность через пробел   | waited 2.5 s                                        | waited <DUR>
            числа из 2+ цифр            | expected 200 but was 500                            | expected <N> but was <N>
            одиночные цифры остаются    | expected 1 element but got 2                        | expected 1 element but got 2
            временный путь              | cannot read /tmp/junit123/data.json                 | cannot read <TMP>
            лишние пробелы              | a     b                                             | a b
            """)
    void rules(String rule, String input, String expected) {
        assertThat(normalizer.normalize(input)).isEqualTo(expected);
    }

    @Test
    @Story("Устойчивость")
    @DisplayName("Одна и та же ошибка с разными портами и id даёт одну подпись")
    void sameErrorDifferentNoiseSameHash() {
        FailureFingerprint first = normalizer.fingerprint("java.net.ConnectException",
                "Connection refused: localhost/127.0.0.1:54321 (order 81723)",
                stack("ru.demo.OrderClient.create(OrderClient.java:42)"));
        FailureFingerprint second = normalizer.fingerprint("java.net.ConnectException",
                "Connection refused: localhost/127.0.0.1:60001 (order 90001)",
                stack("ru.demo.OrderClient.create(OrderClient.java:42)"));

        assertThat(first.hash()).isEqualTo(second.hash()).hasSize(64);
    }

    @Test
    @Story("Устойчивость")
    @DisplayName("Сдвиг номеров строк после правки файла не меняет подпись")
    void lineNumbersIgnored() {
        FailureFingerprint before = normalizer.fingerprint("java.lang.AssertionError", "boom",
                stack("ru.demo.CartTest.addsItem(CartTest.java:42)"));
        FailureFingerprint after = normalizer.fingerprint("java.lang.AssertionError", "boom",
                stack("ru.demo.CartTest.addsItem(CartTest.java:57)"));

        assertThat(before.hash()).isEqualTo(after.hash());
        assertThat(before.normalizedText()).contains("ru.demo.CartTest.addsItem(CartTest.java)");
    }

    @Test
    @Story("Устойчивость")
    @DisplayName("Разные ошибки дают разные подписи")
    void differentErrorsDifferentHash() {
        FailureFingerprint timeout = normalizer.fingerprint("java.net.SocketTimeoutException", "Read timed out", "");
        FailureFingerprint refused = normalizer.fingerprint("java.net.ConnectException", "Connection refused", "");
        FailureFingerprint sameTypeOtherPlace = normalizer.fingerprint("java.net.SocketTimeoutException", "Read timed out",
                stack("ru.demo.PaymentClient.pay(PaymentClient.java:10)"));

        assertThat(timeout.hash()).isNotEqualTo(refused.hash()).isNotEqualTo(sameTypeOtherPlace.hash());
    }

    @Test
    @Story("Кадры стека")
    @DisplayName("Кадры JUnit, JDK, Spring и RestAssured пропускаются, берутся до 3 кадров проекта")
    void frameworkFramesSkipped() {
        String stackTrace = """
                java.lang.AssertionError: boom
                \tat org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:39)
                \tat java.base/java.lang.reflect.Method.invoke(Method.java:580)
                \tat ru.demo.A.a(A.java:1)
                \tat org.springframework.test.context.TestContextManager.run(TestContextManager.java:10)
                \tat io.restassured.internal.RequestSpecificationImpl.get(RequestSpecificationImpl.groovy:5)
                \tat ru.demo.B.b(B.java:2)
                \tat ru.demo.C.c(C.java:3)
                \tat ru.demo.D.d(D.java:4)
                """;

        FailureFingerprint fingerprint = normalizer.fingerprint("java.lang.AssertionError", "boom", stackTrace);

        assertThat(fingerprint.normalizedText()).isEqualTo("""
                java.lang.AssertionError
                boom
                  at ru.demo.A.a(A.java)
                  at ru.demo.B.b(B.java)
                  at ru.demo.C.c(C.java)""");
    }

    @Test
    @Story("Кадры стека")
    @DisplayName("Кадры самого тестового класса не входят в подпись: одна причина у разных тестов = одна группа")
    void testClassFramesSkipped() {
        String createsOrder = """
                java.net.ConnectException: Connection refused
                \tat ru.demo.client.OrderClient.send(OrderClient.java:30)
                \tat ru.demo.CheckoutTest.createsOrder(CheckoutTest.java:12)""";
        String refundsOrder = """
                java.net.ConnectException: Connection refused
                \tat ru.demo.client.OrderClient.send(OrderClient.java:30)
                \tat ru.demo.CheckoutTest$Nested.refundsOrder(CheckoutTest.java:40)""";

        FailureFingerprint first = normalizer.fingerprint("java.net.ConnectException", "Connection refused",
                createsOrder, "ru.demo.CheckoutTest");
        FailureFingerprint second = normalizer.fingerprint("java.net.ConnectException", "Connection refused",
                refundsOrder, "ru.demo.CheckoutTest");

        assertThat(first.hash()).isEqualTo(second.hash());
        assertThat(first.normalizedText()).endsWith("at ru.demo.client.OrderClient.send(OrderClient.java)");
    }

    @Test
    @Story("Разные инструменты")
    @DisplayName("Playwright: type=FAILURE игнорируется, тип и суть берутся из тела ошибки")
    void playwrightFailure() {
        String body = """
                  [chromium] › checkout.spec.ts:42:5 › Checkout › pays with card ──────
                    Error: expect(locator).toBeVisible() failed
                    Timeout: 5000ms
                      at /home/runner/work/shop/tests/checkout.spec.ts:57:41
                """;

        FailureFingerprint fingerprint = normalizer.fingerprint("FAILURE", "checkout.spec.ts:42:5 pays with card", body);

        assertThat(fingerprint.exceptionType()).isEqualTo("Error");
        assertThat(fingerprint.normalizedText()).isEqualTo("""
                Error
                expect(locator).toBeVisible() failed
                  at /home/runner/work/shop/tests/checkout.spec.ts""");
    }

    @Test
    @Story("Разные инструменты")
    @DisplayName("pytest: тип из строки «E  AssertionError», кадр файла без номера строки")
    void pytestFailure() {
        String body = """
                def test_create_order(region):
                >       assert response.status_code == 201
                E       AssertionError: assert 503 == 201

                tests/test_api.py:27: AssertionError""";

        FailureFingerprint fingerprint = normalizer.fingerprint("", "AssertionError: assert 503 == 201", body);

        assertThat(fingerprint.exceptionType()).isEqualTo("AssertionError");
        assertThat(fingerprint.normalizedText()).isEqualTo("""
                AssertionError
                assert <N> == <N>
                  at tests/test_api.py""");
    }

    @Test
    @Story("Разные инструменты")
    @DisplayName("Нет type и стека: тип извлекается из сообщения")
    void typeFromMessage() {
        FailureFingerprint fingerprint = normalizer.fingerprint(null, "java.lang.IllegalStateException: bad state", null);

        assertThat(fingerprint.exceptionType()).isEqualTo("java.lang.IllegalStateException");
        assertThat(fingerprint.normalizedText()).isEqualTo("java.lang.IllegalStateException\nbad state");
    }

    @Test
    @Story("Разные инструменты")
    @DisplayName("Ничего не известно, кроме условного типа: подпись всё равно строится")
    void onlyPlaceholderType() {
        FailureFingerprint fingerprint = normalizer.fingerprint("FAILURE", "", "");

        assertThat(fingerprint.exceptionType()).isEqualTo("FAILURE");
        assertThat(fingerprint.hash()).hasSize(64);
    }

    private static String stack(String frame) {
        return "Exception\n\tat org.junit.Assert.fail(Assert.java:89)\n\tat " + frame + "\n";
    }
}
