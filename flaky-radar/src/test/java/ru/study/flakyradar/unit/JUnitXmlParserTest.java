package ru.study.flakyradar.unit;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.study.flakyradar.report.JUnitXmlParser;
import ru.study.flakyradar.report.ParsedReport;
import ru.study.flakyradar.report.ParsedTestCase;
import ru.study.flakyradar.report.ReportParseException;
import ru.study.flakyradar.report.TestStatus;
import ru.study.testfw.junitxml.JUnitXmlBuilder;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@Tag("unit")
@Epic("Flaky Radar")
@Feature("Разбор JUnit XML")
@DisplayName("Unit: JUnitXmlParser")
class JUnitXmlParserTest {

    private final JUnitXmlParser parser = new JUnitXmlParser();

    @Nested
    @Story("Эталонные отчёты разных инструментов")
    @DisplayName("Эталонные отчёты")
    class Fixtures {

        @Test
        @DisplayName("Surefire: статусы, flakyFailure и пропуск разобраны правильно")
        void surefire() {
            ParsedReport report = parseFixture("surefire.xml");

            assertThat(report.testCases())
                    .extracting(ParsedTestCase::name, ParsedTestCase::status, ParsedTestCase::failedAttempts)
                    .containsExactly(
                            tuple("createBook", TestStatus.PASSED, 0),
                            tuple("getBook", TestStatus.PASSED, 0),
                            tuple("updateBook", TestStatus.FAILED, 0),
                            tuple("deleteBook", TestStatus.ERROR, 0),
                            tuple("searchByAuthor", TestStatus.SKIPPED, 0),
                            tuple("listBooks", TestStatus.PASSED, 1));
            assertThat(report.failedCount()).isEqualTo(2);
            assertThat(report.count(TestStatus.SKIPPED)).isEqualTo(1);
        }

        @Test
        @DisplayName("Surefire: у упавшего теста есть тип, сообщение и стек; длительность в миллисекундах")
        void surefireFailureDetails() {
            ParsedTestCase updateBook = byName(parseFixture("surefire.xml"), "updateBook");

            assertThat(updateBook.fullName()).isEqualTo("ru.study.library.api.BookCrudApiTest.updateBook");
            assertThat(updateBook.durationMs()).isEqualTo(305);
            assertThat(updateBook.failure().type()).isEqualTo("org.opentest4j.AssertionFailedError");
            assertThat(updateBook.failure().message()).isEqualTo("expected: <200> but was: <500>");
            assertThat(updateBook.failure().stackTrace()).contains("BookCrudApiTest.java:88");
        }

        @Test
        @DisplayName("Surefire flakyFailure: стек берётся из <stackTrace>, system-out не попадает")
        void surefireFlakyFailure() {
            ParsedTestCase listBooks = byName(parseFixture("surefire.xml"), "listBooks");

            assertThat(listBooks.passedAfterRetry()).isTrue();
            assertThat(listBooks.failure().type()).isEqualTo("java.net.SocketTimeoutException");
            assertThat(listBooks.failure().stackTrace())
                    .contains("BookClient.findAll")
                    .doesNotContain("шум");
        }

        @Test
        @DisplayName("Playwright: classname = файл спеки, имя с describe, CDATA с ошибкой")
        void playwright() {
            ParsedReport report = parseFixture("playwright.xml");

            assertThat(report.testCases())
                    .extracting(ParsedTestCase::fullName, ParsedTestCase::status)
                    .containsExactly(
                            tuple("checkout.spec.ts.Checkout › adds item to cart", TestStatus.PASSED),
                            tuple("checkout.spec.ts.Checkout › pays with card", TestStatus.FAILED),
                            tuple("checkout.spec.ts.Checkout › applies coupon", TestStatus.SKIPPED),
                            tuple("login.spec.ts.Login › signs in", TestStatus.PASSED));
            assertThat(byName(report, "Checkout › pays with card").failure().stackTrace())
                    .contains("toBeVisible()");
        }

        @Test
        @DisplayName("pytest: десятичная запятая во времени, повтор теста внутри отчёта сливается")
        void pytest() {
            ParsedReport report = parseFixture("pytest.xml");

            assertThat(report.total()).isEqualTo(4);
            assertThat(byName(report, "test_create_order[eu]").durationMs()).isEqualTo(250);
            ParsedTestCase retried = byName(report, "test_retry");
            assertThat(retried.status()).isEqualTo(TestStatus.PASSED);
            assertThat(retried.failedAttempts()).isEqualTo(1);
            assertThat(retried.durationMs()).isEqualTo(190);
            assertThat(retried.failure().message()).isEqualTo("TimeoutError: timed out");
        }
    }

    @Nested
    @Story("Повторы и слияние")
    @DisplayName("Повторы")
    class Retries {

        @Test
        @DisplayName("rerunFailure: упал во всех повторах, считаются все неудачные попытки")
        void rerunFailure() {
            String xml = JUnitXmlBuilder.report()
                    .rerunFailed("S", "t", "java.lang.AssertionError", "boom")
                    .toXml();

            ParsedTestCase tc = parse(xml).testCases().getFirst();

            assertThat(tc.status()).isEqualTo(TestStatus.FAILED);
            assertThat(tc.failedAttempts()).isEqualTo(1);
            assertThat(tc.passedAfterRetry()).isFalse();
        }

        @Test
        @DisplayName("Повтор SKIPPED не перекрывает реальный результат")
        void skippedRepeatIsIgnored() {
            String xml = JUnitXmlBuilder.report()
                    .failed("S", "t", "java.lang.AssertionError", "boom")
                    .skipped("S", "t")
                    .toXml();

            assertThat(parse(xml).testCases().getFirst().status()).isEqualTo(TestStatus.FAILED);
        }

        @Test
        @DisplayName("Несколько файлов: один и тот же тест из разных отчётов сливается")
        void mergeAcrossFiles() {
            ParsedReport first = parse(JUnitXmlBuilder.report().failed("S", "t", "E", "x").passed("S", "other").toXml());
            ParsedReport second = parse(JUnitXmlBuilder.report().passed("S", "t").toXml());

            ParsedReport merged = ParsedReport.merge(List.of(first, second));

            assertThat(merged.total()).isEqualTo(2);
            ParsedTestCase t = byName(merged, "t");
            assertThat(t.passedAfterRetry()).isTrue();
        }
    }

    @Nested
    @Story("Некорректные и опасные отчёты")
    @DisplayName("Ошибки")
    class Errors {

        @Test
        @DisplayName("Битый XML -> ReportParseException с понятным сообщением")
        void brokenXml() {
            assertThatThrownBy(() -> parse("<testsuite><testcase name='a'>"))
                    .isInstanceOf(ReportParseException.class)
                    .hasMessageContaining("XML");
        }

        @Test
        @DisplayName("Чужой корневой элемент -> ReportParseException")
        void wrongRoot() {
            assertThatThrownBy(() -> parse("<html><body/></html>"))
                    .isInstanceOf(ReportParseException.class)
                    .hasMessageContaining("<html>");
        }

        @Test
        @DisplayName("testcase без name -> ReportParseException с номером строки")
        void testCaseWithoutName() {
            assertThatThrownBy(() -> parse("<testsuite name='s'>\n<testcase classname='c'/>\n</testsuite>"))
                    .isInstanceOf(ReportParseException.class)
                    .hasMessageContaining("строка 2");
        }

        @Test
        @DisplayName("XXE: внешняя сущность не раскрывается, содержимое файлов сервера не утекает")
        void xxeIsBlocked() {
            InputStream xxe = getClass().getResourceAsStream("/reports/xxe.xml");

            assertThatThrownBy(() -> parser.parse(xxe)).isInstanceOf(ReportParseException.class);
        }

        @Test
        @DisplayName("Пустой testsuites -> отчёт без тестов (решение об ошибке принимает сервис)")
        void emptySuites() {
            assertThat(parse("<testsuites/>").total()).isZero();
        }
    }

    @ParameterizedTest(name = "time=\"{0}\" -> {1} мс")
    @Story("Длительность")
    @DisplayName("Разные форматы времени")
    @CsvSource(delimiter = ';', value = {
            "1.5;1500",
            "0,25;250",
            "'1,234.5';1234500",
            "abc;0",
            "-3;0",
            "'';0"})
    void durationFormats(String time, long expectedMs) {
        String xml = "<testsuite name='s'><testcase name='t' classname='c' time='" + time + "'/></testsuite>";

        assertThat(parse(xml).testCases().getFirst().durationMs()).isEqualTo(expectedMs);
    }

    @Test
    @Story("Имена")
    @DisplayName("Нет classname -> берётся имя testsuite; вложенные testsuite поддерживаются")
    void classnameFallsBackToSuite() {
        String xml = """
                <testsuites>
                  <testsuite name="outer">
                    <testsuite name="inner"><testcase name="a"/></testsuite>
                    <testcase name="b"/>
                  </testsuite>
                </testsuites>""";

        assertThat(parse(xml).testCases()).extracting(ParsedTestCase::fullName)
                .containsExactly("inner.a", "outer.b");
    }

    private ParsedReport parseFixture(String name) {
        return parser.parse(getClass().getResourceAsStream("/reports/" + name));
    }

    private ParsedReport parse(String xml) {
        return parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static ParsedTestCase byName(ParsedReport report, String name) {
        return report.testCases().stream().filter(tc -> tc.name().equals(name)).findFirst().orElseThrow();
    }
}
