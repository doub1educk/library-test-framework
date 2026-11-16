package ru.study.flakyradar.api;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.study.flakyradar.framework.BaseRadarApiTest;
import ru.study.flakyradar.framework.RadarTestData;
import ru.study.flakyradar.framework.TelegramMock;
import ru.study.flakyradar.run.RunDtos.RunDetails;
import ru.study.testfw.http.RestAssuredSpecs;
import ru.study.testfw.junitxml.JUnitXmlBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static ru.study.flakyradar.framework.RadarTestData.ASSERTION;
import static ru.study.flakyradar.framework.RadarTestData.SUITE;

@Epic("Flaky Radar")
@Feature("Сводки: markdown для PR и Telegram")
@DisplayName("API: сводки и уведомления")
class NotificationApiTest extends BaseRadarApiTest {

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @Story("Markdown для PR")
    @DisplayName("summary.md: решение, счётчики, таблица блокирующих тестов и ссылка на дашборд")
    void markdownSummary() {
        RunDetails run = steps.upload(project, JUnitXmlBuilder.report()
                .passed(SUITE, "ok")
                .failed(SUITE, "breaks", ASSERTION, "expected: <a> but was: <b|c>"));

        String markdown = client.summary(project.key(), run.run().id())
                .then().spec(RestAssuredSpecs.status(200))
                .contentType(containsString("text/markdown"))
                .extract().asString();

        assertThat(markdown)
                .contains("Flaky Radar: FAIL")
                .contains("| 2 | 1 | 1 | 0 |")
                .contains("**Блокируют сборку (1)**")
                .contains("`" + SUITE + ".breaks` | новое падение | expected: <a> but was: <b\\|c>")
                .contains(RadarTestData.PUBLIC_URL + "/projects/" + project.key() + "/runs/" + run.run().id());
    }

    @Test
    @Story("Telegram")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Прогон с падением -> в Telegram уходит сводка в нужный чат")
    void telegramMessageOnFailure() {
        steps.upload(project, JUnitXmlBuilder.report().failed(SUITE, "breaks", ASSERTION, "boom"));

        assertThat(TelegramMock.sentMessages()).singleElement().satisfies(body -> {
            assertThat(body).contains("\"chat_id\":\"" + TelegramMock.CHAT_ID + "\"");
            assertThat(body).contains(project.key()).contains("FAIL").contains(SUITE + ".breaks");
        });
    }

    @Test
    @Story("Telegram")
    @DisplayName("Зелёный прогон в Telegram не шлём (notify-on-pass=false)")
    void noTelegramOnPass() {
        steps.upload(project, JUnitXmlBuilder.report().passed(SUITE, "ok"));

        assertThat(TelegramMock.sentMessages()).isEmpty();
    }

    @Test
    @Story("Telegram")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Telegram недоступен (500) -> загрузка отчёта всё равно успешна")
    void telegramFailureDoesNotBreakIngest() {
        TelegramMock.respondsWithError(500);

        RunDetails run = steps.upload(project, JUnitXmlBuilder.report().failed(SUITE, "breaks", ASSERTION, "boom"));

        assertThat(run.run().id()).isNotNull();
        assertThat(TelegramMock.sentMessages()).hasSize(1);
    }
}
