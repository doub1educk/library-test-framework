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
import ru.study.flakyradar.framework.BaseRadarApiTest;
import ru.study.flakyradar.framework.RadarSteps;
import ru.study.flakyradar.quarantine.QuarantineDtos.QuarantineRequest;
import ru.study.flakyradar.quarantine.QuarantineDtos.QuarantineResponse;
import ru.study.flakyradar.run.RunDtos.GateResponse;
import ru.study.flakyradar.run.RunDtos.RunDetails;
import ru.study.testfw.http.RestAssuredSpecs;
import ru.study.testfw.junitxml.JUnitXmlBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static ru.study.flakyradar.framework.RadarTestData.ASSERTION;
import static ru.study.flakyradar.framework.RadarTestData.SUITE;
import static ru.study.flakyradar.framework.RadarTestData.fullName;

@Epic("Flaky Radar")
@Feature("Карантин и gate")
@DisplayName("API: карантин и решение для CI")
class QuarantineGateApiTest extends BaseRadarApiTest {

    private static final String REFUND = "refundsOrder";

    @Test
    @Tag("smoke")
    @Story("Gate учитывает карантин")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Новое падение -> FAIL; тест в карантин -> тот же прогон WARN; снят с карантина -> снова FAIL")
    void quarantineChangesGateOfExistingRun() {
        RunDetails run = steps.upload(project, failing());
        assertThat(run.gate().decision()).isEqualTo(GateDecision.FAIL);
        assertThat(run.gate().blocking()).extracting("testName").containsExactly(fullName(REFUND));

        QuarantineResponse quarantine = steps.quarantine(project, fullName(REFUND), "JIRA-42: нестабильный стенд оплаты");
        GateResponse afterQuarantine = steps.gate(project, run.run().id());
        assertThat(afterQuarantine.decision()).isEqualTo(GateDecision.WARN);
        assertThat(afterQuarantine.blocking()).isEmpty();
        assertThat(afterQuarantine.warnings()).singleElement()
                .satisfies(item -> assertThat(item.quarantined()).isTrue());

        steps.removeQuarantine(project, quarantine.id());
        assertThat(steps.gate(project, run.run().id()).decision()).isEqualTo(GateDecision.FAIL);
    }

    @Test
    @Story("Gate учитывает карантин")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Падение теста в карантине при загрузке -> WARN, результат помечен quarantined")
    void failureOfQuarantinedTestDoesNotBlock() {
        steps.upload(project, passing());
        steps.quarantine(project, fullName(REFUND), "ждём фикс");

        RunDetails run = steps.upload(project, failing());

        assertThat(run.gate().decision()).isEqualTo(GateDecision.WARN);
        assertThat(RadarSteps.problem(run, fullName(REFUND)).quarantined()).isTrue();
    }

    @Test
    @Story("Gate учитывает карантин")
    @DisplayName("Карантин одного теста не спасает от нового падения другого")
    void quarantineIsPerTest() {
        steps.upload(project, passing());
        steps.quarantine(project, fullName(REFUND), "ждём фикс");

        RunDetails run = steps.upload(project, failing()
                .failed(SUITE, "createsOrder", ASSERTION, "expected: <201> but was: <500>"));

        assertThat(run.gate().decision()).isEqualTo(GateDecision.FAIL);
        assertThat(run.gate().blocking()).extracting("testName").containsExactly(fullName("createsOrder"));
    }

    @Test
    @Story("API карантина")
    @DisplayName("Список активного карантина; повторная отправка того же теста -> 409")
    void listAndDuplicate() {
        steps.upload(project, failing());
        steps.quarantine(project, fullName(REFUND), "первый раз");

        client.quarantine(project.key()).then().spec(RestAssuredSpecs.status(200))
                .body("$", hasSize(1))
                .body("[0].testName", org.hamcrest.Matchers.equalTo(fullName(REFUND)))
                .body("[0].createdAt", notNullValue());
        client.addQuarantine(project.key(), project.apiKey(), new QuarantineRequest(fullName(REFUND), "второй раз", null, null))
                .then().spec(RestAssuredSpecs.problem(409));
    }

    @Test
    @Story("API карантина")
    @DisplayName("Неизвестный тест -> 404, без ключа -> 401, срок в прошлом -> 400, снятие несуществующей записи -> 404")
    void negativeCases() {
        steps.upload(project, failing());

        client.addQuarantine(project.key(), project.apiKey(), new QuarantineRequest("no.such.Test", "x", null, null))
                .then().spec(RestAssuredSpecs.problem(404));
        client.addQuarantine(project.key(), null, new QuarantineRequest(fullName(REFUND), "x", null, null))
                .then().spec(RestAssuredSpecs.problem(401));
        client.addQuarantine(project.key(), project.apiKey(),
                        new QuarantineRequest(fullName(REFUND), "x", null, Instant.now().minus(1, ChronoUnit.DAYS)))
                .then().spec(RestAssuredSpecs.problem(400))
                .body("errors.expiresAt", notNullValue());
        client.removeQuarantine(project.key(), project.apiKey(), 999_999).then().spec(RestAssuredSpecs.problem(404));
    }

    @Test
    @Story("API карантина")
    @DisplayName("Карантин со сроком: пока срок не истёк, тест не блокирует")
    void quarantineWithExpiry() {
        steps.upload(project, passing());
        client.addQuarantine(project.key(), project.apiKey(), new QuarantineRequest(fullName(REFUND), "на сутки", "qa",
                        Instant.now().plus(1, ChronoUnit.DAYS)))
                .then().spec(RestAssuredSpecs.status(201))
                .body("expiresAt", notNullValue());

        assertThat(steps.upload(project, failing()).gate().decision()).isEqualTo(GateDecision.WARN);
    }

    private static JUnitXmlBuilder failing() {
        return JUnitXmlBuilder.report()
                .passed(SUITE, "createsOrder")
                .failed(SUITE, REFUND, ASSERTION, "expected: <REFUNDED> but was: <PENDING>");
    }

    private static JUnitXmlBuilder passing() {
        return JUnitXmlBuilder.report().passed(SUITE, "createsOrder").passed(SUITE, REFUND);
    }
}
