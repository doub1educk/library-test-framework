package ru.study.flakyradar.api;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.study.flakyradar.framework.BaseRadarApiTest;
import ru.study.flakyradar.framework.RadarSteps;
import ru.study.flakyradar.run.RunDtos.RunDetails;
import ru.study.flakyradar.testcase.TestCaseDtos.TestCaseView;
import ru.study.testfw.http.RestAssuredSpecs;
import ru.study.testfw.junitxml.JUnitXmlBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static ru.study.flakyradar.framework.RadarTestData.ASSERTION;
import static ru.study.flakyradar.framework.RadarTestData.SUITE;
import static ru.study.flakyradar.framework.RadarTestData.TIMEOUT;
import static ru.study.flakyradar.framework.RadarTestData.fullName;

@Epic("Flaky Radar")
@Feature("Дашборд")
@DisplayName("UI: страницы дашборда (Thymeleaf)")
class DashboardPageTest extends BaseRadarApiTest {

    private RunDetails run;

    @BeforeEach
    void seed() {
        steps.upload(project, JUnitXmlBuilder.report().passed(SUITE, "paysWithCard"));
        run = steps.upload(project, JUnitXmlBuilder.report()
                .failed(SUITE, "paysWithCard", ASSERTION, "expected: <PAID> but was: <DECLINED>")
                .flakyPassed(SUITE, "listsOrders", TIMEOUT, "Read timed out"));
        steps.quarantine(project, fullName("paysWithCard"), "JIRA-7");
    }

    @Test
    @Story("Главная")
    @DisplayName("Главная: проект в списке с решением последнего прогона на момент загрузки")
    void indexPage() {
        String html = page("/");

        // карантин добавлен после загрузки: в списке решение на момент приёма, на странице прогона — текущее
        assertThat(html).contains(project.key()).contains("FAIL");
    }

    @Test
    @Story("Проект")
    @DisplayName("Страница проекта: прогоны, флаки-тесты, группы ошибок, карантин")
    void projectPage() {
        String html = page("/projects/" + project.key());

        assertThat(html)
                .contains("/runs/" + run.run().id()).doesNotContain("Прогонов пока нет")
                .contains(fullName("listsOrders"))
                .contains("java.net.SocketTimeoutException")
                .contains("JIRA-7");
    }

    @Test
    @Story("Прогон")
    @DisplayName("Страница прогона: тест в карантине среди неблокирующих, markdown-сводка")
    void runPage() {
        String html = page("/projects/" + project.key() + "/runs/" + run.run().id());

        assertThat(html)
                .contains(fullName("paysWithCard"))
                .contains("в карантине")
                .contains("прошёл после повтора")
                .contains("Flaky Radar: WARN");
    }

    @Test
    @Story("Тест")
    @DisplayName("Страница теста: полоска истории и вердикты")
    void testPage() {
        TestCaseView test = steps.test(project, fullName("paysWithCard"));

        String html = page("/projects/" + project.key() + "/tests/" + test.id());

        assertThat(html).contains("class=\"PASSED\"").contains("class=\"FAILED\"").contains("новое падение");
    }

    @Test
    @Story("Группа ошибок")
    @DisplayName("Страница группы ошибок: нормализованная подпись и где встречалась")
    void signaturePage() {
        long signatureId = RadarSteps.problem(run, fullName("paysWithCard")).signatureId();

        String html = page("/projects/" + project.key() + "/signatures/" + signatureId);

        assertThat(html).contains("expected: &lt;PAID&gt; but was: &lt;DECLINED&gt;").contains("#" + run.run().id());
    }

    @Test
    @Story("Ошибки")
    @DisplayName("Несуществующий проект -> страница 404")
    void notFoundPage() {
        client.page("/projects/no-such-project")
                .then().statusCode(404)
                .body(containsString("не найден"));
    }

    private String page(String path) {
        return client.page(path)
                .then().spec(RestAssuredSpecs.status(200))
                .contentType(containsString("text/html"))
                .extract().asString();
    }
}
