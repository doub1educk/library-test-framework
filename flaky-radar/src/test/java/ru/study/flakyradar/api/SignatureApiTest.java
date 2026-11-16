package ru.study.flakyradar.api;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.study.flakyradar.framework.BaseRadarApiTest;
import ru.study.flakyradar.framework.RadarSteps;
import ru.study.flakyradar.run.RunDtos.RunDetails;
import ru.study.flakyradar.testcase.TestCaseDtos.SignatureDetails;
import ru.study.flakyradar.testcase.TestCaseDtos.SignatureView;
import ru.study.testfw.http.RestAssuredSpecs;
import ru.study.testfw.junitxml.JUnitXmlBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.study.flakyradar.framework.RadarTestData.ASSERTION;
import static ru.study.flakyradar.framework.RadarTestData.SUITE;
import static ru.study.flakyradar.framework.RadarTestData.fullName;

@Epic("Flaky Radar")
@Feature("Группировка падений по подписи")
@DisplayName("API: подписи ошибок")
class SignatureApiTest extends BaseRadarApiTest {

    private static final String CONNECT = "java.net.ConnectException";

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @Story("Одна причина -> одна группа")
    @DisplayName("Разные тесты и прогоны с ошибкой, отличающейся только портом и id, попадают в одну группу")
    void sameCauseGroupedTogether() {
        RunDetails first = steps.upload(project, JUnitXmlBuilder.report()
                .error(SUITE, "createsOrder", CONNECT, "Connection refused: localhost:54321 (request 1001)")
                .error(SUITE, "refundsOrder", CONNECT, "Connection refused: localhost:60123 (request 2002)"));
        steps.upload(project, JUnitXmlBuilder.report()
                .error(SUITE, "createsOrder", CONNECT, "Connection refused: localhost:41000 (request 3003)"));

        List<SignatureView> signatures = steps.signatures(project);

        assertThat(signatures).singleElement().satisfies(s -> {
            assertThat(s.exceptionType()).isEqualTo(CONNECT);
            assertThat(s.occurrences()).isEqualTo(3);
            assertThat(s.affectedTests()).isEqualTo(2);
            assertThat(s.normalizedText()).contains("Connection refused: localhost:<PORT> (request <N>)");
            assertThat(s.sampleMessage()).contains("54321");
        });
        assertThat(RadarSteps.problem(first, fullName("createsOrder")).signatureId())
                .isEqualTo(RadarSteps.problem(first, fullName("refundsOrder")).signatureId());
    }

    @Test
    @Story("Разные причины -> разные группы")
    @DisplayName("Разные ошибки дают разные группы, свежие сверху")
    void differentCausesSeparated() {
        steps.upload(project, JUnitXmlBuilder.report()
                .failed(SUITE, "a", ASSERTION, "expected: <1> but was: <2>"));
        steps.upload(project, JUnitXmlBuilder.report()
                .error(SUITE, "b", CONNECT, "Connection refused"));

        List<SignatureView> signatures = steps.signatures(project);

        assertThat(signatures).hasSize(2);
        assertThat(signatures.getFirst().exceptionType()).isEqualTo(CONNECT);
    }

    @Test
    @Story("Детали группы")
    @DisplayName("Детали группы: где и когда встречалась ошибка; чужая подпись -> 404")
    void signatureDetails() {
        RunDetails run = steps.upload(project, JUnitXmlBuilder.report()
                .error(SUITE, "createsOrder", CONNECT, "Connection refused"));
        long signatureId = RadarSteps.problem(run, fullName("createsOrder")).signatureId();

        SignatureDetails details = client.signature(project.key(), signatureId)
                .then().spec(RestAssuredSpecs.status(200))
                .extract().as(SignatureDetails.class);

        assertThat(details.occurrences()).singleElement().satisfies(o -> {
            assertThat(o.runId()).isEqualTo(run.run().id());
            assertThat(o.testName()).isEqualTo(fullName("createsOrder"));
        });
        client.signature(project.key(), signatureId + 1000).then().spec(RestAssuredSpecs.problem(404));
    }

    @Test
    @Story("Повтор внутри прогона")
    @DisplayName("Ошибка из flakyFailure тоже попадает в группу: видно, из-за чего тест нестабилен")
    void flakyFailureHasSignature() {
        RunDetails run = steps.upload(project, JUnitXmlBuilder.report()
                .flakyPassed(SUITE, "listsOrders", "java.net.SocketTimeoutException", "Read timed out"));

        assertThat(RadarSteps.problem(run, fullName("listsOrders")).signatureId()).isNotNull();
        assertThat(steps.signatures(project)).singleElement()
                .satisfies(s -> assertThat(s.exceptionType()).isEqualTo("java.net.SocketTimeoutException"));
    }
}
