package ru.study.flakyradar.api;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ru.study.flakyradar.analysis.GateDecision;
import ru.study.flakyradar.analysis.Verdict;
import ru.study.flakyradar.framework.BaseRadarApiTest;
import ru.study.flakyradar.framework.RadarSteps;
import ru.study.flakyradar.framework.RadarTestData;
import ru.study.flakyradar.framework.RunUpload;
import ru.study.flakyradar.report.TestStatus;
import ru.study.flakyradar.run.RunDtos.ResultView;
import ru.study.flakyradar.run.RunDtos.RunDetails;
import ru.study.testfw.http.RestAssuredSpecs;
import ru.study.testfw.junitxml.JUnitXmlBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

@Epic("Flaky Radar")
@Feature("Приём отчётов")
@DisplayName("API: приём JUnit XML")
class IngestApiTest extends BaseRadarApiTest {

    @Test
    @Tag("smoke")
    @Story("Загрузка отчёта")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Отчёт Surefire принят: 201, Location, счётчики, вердикты и решение gate")
    void uploadSurefireReport() {
        RunUpload upload = RunUpload.ofFiles(List.of(fixture("surefire.xml")))
                .branch("main").ciRunId(RadarTestData.ciRunId()).buildUrl("https://ci.example/run/1");

        RunDetails details = client.upload(project.key(), project.apiKey(), upload)
                .then().spec(RestAssuredSpecs.status(201))
                .header("Location", matchesPattern(".*/api/v1/projects/" + project.key() + "/runs/\\d+"))
                .extract().as(RunDetails.class);

        assertThat(details.run().total()).isEqualTo(6);
        assertThat(details.run().passed()).isEqualTo(3);
        assertThat(details.run().failed()).isEqualTo(2);
        assertThat(details.run().skipped()).isEqualTo(1);
        assertThat(details.run().buildUrl()).isEqualTo("https://ci.example/run/1");
        assertThat(details.gate().decision()).isEqualTo(GateDecision.FAIL);

        ResultView updateBook = RadarSteps.problem(details, "ru.study.library.api.BookCrudApiTest.updateBook");
        assertThat(updateBook.status()).isEqualTo(TestStatus.FAILED);
        assertThat(updateBook.verdict()).isEqualTo(Verdict.NEW_FAILURE);
        assertThat(updateBook.message()).isEqualTo("expected: <200> but was: <500>");
        assertThat(updateBook.signatureId()).isNotNull();

        ResultView listBooks = RadarSteps.problem(details, "ru.study.library.api.BookCrudApiTest.listBooks");
        assertThat(listBooks.passedAfterRetry()).isTrue();
        assertThat(listBooks.verdict()).isNull();
        assertThat(details.gate().warnings()).extracting("testName")
                .contains("ru.study.library.api.BookCrudApiTest.listBooks");
    }

    @Test
    @Story("Загрузка отчёта")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Отчёты Playwright и pytest тоже принимаются")
    void uploadPlaywrightAndPytest() {
        RunDetails playwright = steps.upload(project, RunUpload.ofFiles(List.of(fixture("playwright.xml"))));
        RunDetails pytest = steps.upload(project, RunUpload.ofFiles(List.of(fixture("pytest.xml"))));

        assertThat(playwright.run().total()).isEqualTo(4);
        assertThat(playwright.problems()).extracting(ResultView::testName)
                .containsExactly("checkout.spec.ts.Checkout › pays with card");
        assertThat(pytest.run().total()).isEqualTo(4);
        assertThat(pytest.problems()).extracting(ResultView::testName)
                .containsExactlyInAnyOrder("tests.test_api.test_create_order[eu]", "tests.test_api.test_retry");
    }

    @Test
    @Story("Загрузка отчёта")
    @DisplayName("Несколько файлов в одном запросе складываются в один прогон")
    void multipleFiles() {
        byte[] first = JUnitXmlBuilder.report().passed("ru.demo.ATest", "a").toBytes();
        byte[] second = JUnitXmlBuilder.report().passed("ru.demo.BTest", "b").failed("ru.demo.BTest", "c", "E", "x").toBytes();

        RunDetails details = steps.upload(project, RunUpload.ofFiles(List.of(first, second)));

        assertThat(details.run().total()).isEqualTo(3);
        assertThat(details.run().failed()).isEqualTo(1);
    }

    @Test
    @Story("Идемпотентность")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Повторная загрузка того же ciRunId и attempt -> 200 и тот же прогон, дубля нет")
    void idempotentUpload() {
        RunUpload upload = RunUpload.of(JUnitXmlBuilder.report().passed(RadarTestData.SUITE, "ok"))
                .ciRunId(RadarTestData.ciRunId());
        RunDetails first = steps.upload(project, upload);

        RunDetails second = client.upload(project.key(), project.apiKey(), upload)
                .then().spec(RestAssuredSpecs.status(200))
                .extract().as(RunDetails.class);

        assertThat(second.run().id()).isEqualTo(first.run().id());
        client.runs(project.key(), Map.of()).then().spec(RestAssuredSpecs.status(200)).body("$", hasSize(1));
    }

    @Test
    @Story("Идемпотентность")
    @DisplayName("Тот же ciRunId, но другая попытка -> новый прогон")
    void sameCiRunNextAttempt() {
        String ciRunId = RadarTestData.ciRunId();
        RunUpload upload = RunUpload.of(JUnitXmlBuilder.report().passed(RadarTestData.SUITE, "ok")).ciRunId(ciRunId);

        RunDetails first = steps.upload(project, upload);
        RunDetails second = steps.upload(project, upload.attempt(2));

        assertThat(second.run().id()).isNotEqualTo(first.run().id());
        assertThat(second.run().attempt()).isEqualTo(2);
    }

    @Test
    @Story("Некорректные запросы")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Битый XML -> 422, прогон не сохраняется")
    void brokenXml() {
        client.upload(project.key(), project.apiKey(), RunUpload.ofXml("<testsuite><testcase name='a'>"))
                .then().spec(RestAssuredSpecs.problem(422))
                .body("detail", containsString("XML"));

        client.runs(project.key(), Map.of()).then().body("$", hasSize(0));
    }

    @Test
    @Story("Некорректные запросы")
    @DisplayName("Отчёт без тестов -> 422")
    void emptyReport() {
        client.upload(project.key(), project.apiKey(), RunUpload.ofXml("<testsuites/>"))
                .then().spec(RestAssuredSpecs.problem(422));
    }

    @Test
    @Story("Некорректные запросы")
    @DisplayName("XXE-атака -> 422, содержимое файлов сервера не раскрывается")
    void xxeRejected() {
        client.upload(project.key(), project.apiKey(), RunUpload.ofFiles(List.of(fixture("xxe.xml"))))
                .then().spec(RestAssuredSpecs.problem(422));
    }

    @Test
    @Story("Некорректные запросы")
    @DisplayName("Нет файла отчёта -> 400")
    void noReport() {
        client.upload(project.key(), project.apiKey(), RunUpload.ofFiles(List.of()))
                .then().spec(RestAssuredSpecs.problem(400));
    }

    @Test
    @Story("Некорректные запросы")
    @DisplayName("Нет ветки и коммита, attempt = 0 -> 400 со списком полей")
    void missingMetadata() {
        RunUpload upload = RunUpload.of(JUnitXmlBuilder.report().passed(RadarTestData.SUITE, "ok"))
                .branch(null).commit(null).attempt(0);

        client.upload(project.key(), project.apiKey(), upload)
                .then().spec(RestAssuredSpecs.problem(400))
                .body("errors.branch", notNullValue())
                .body("errors.commit", notNullValue())
                .body("errors.attempt", notNullValue());
    }

    @Test
    @Story("Чтение прогонов")
    @DisplayName("Список прогонов: новые первыми, фильтр по ветке; несуществующий прогон -> 404")
    void listRuns() {
        steps.upload(project, RunUpload.of(JUnitXmlBuilder.report().passed(RadarTestData.SUITE, "ok")).branch("main"));
        RunDetails feature = steps.upload(project,
                RunUpload.of(JUnitXmlBuilder.report().passed(RadarTestData.SUITE, "ok")).branch("feature/x"));

        client.runs(project.key(), Map.of()).then().body("$", hasSize(2)).body("[0].id", org.hamcrest.Matchers.equalTo(feature.run().id().intValue()));
        client.runs(project.key(), Map.of("branch", "main")).then().body("$", hasSize(1));
        client.run(project.key(), 999_999).then().spec(RestAssuredSpecs.problem(404));
    }

    private byte[] fixture(String name) {
        try (InputStream in = getClass().getResourceAsStream("/reports/" + name)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
