package ru.study.flakyradar.api;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.study.flakyradar.analysis.GateDecision;
import ru.study.flakyradar.analysis.Verdict;
import ru.study.flakyradar.framework.BaseRadarApiTest;
import ru.study.flakyradar.framework.RadarSteps;
import ru.study.flakyradar.framework.RadarTestData;
import ru.study.flakyradar.framework.RunUpload;
import ru.study.flakyradar.run.RunDtos.RunDetails;
import ru.study.flakyradar.testcase.TestCaseDtos.TestCaseView;
import ru.study.testfw.junitxml.JUnitXmlBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.study.flakyradar.framework.RadarTestData.ASSERTION;
import static ru.study.flakyradar.framework.RadarTestData.SUITE;
import static ru.study.flakyradar.framework.RadarTestData.TIMEOUT;
import static ru.study.flakyradar.framework.RadarTestData.fullName;

@Epic("Flaky Radar")
@Feature("Определение нестабильных тестов")
@DisplayName("API: флаки-тесты")
class FlakyDetectionApiTest extends BaseRadarApiTest {

    private static final String PAYMENT = "paysWithCard";

    @Test
    @Story("Перезапуск CI на том же коммите")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Attempt 1 упал, attempt 2 на том же коммите прошёл -> тест флаки, следующее падение KNOWN_FLAKY, gate WARN")
    void rerunOnSameCommitMakesTestFlaky() {
        String commit = RadarTestData.commitSha();
        String ciRunId = RadarTestData.ciRunId();

        RunDetails attempt1 = steps.upload(project, RunUpload.of(failing(PAYMENT)).commit(commit).ciRunId(ciRunId).attempt(1));
        assertThat(RadarSteps.problem(attempt1, fullName(PAYMENT)).verdict()).isEqualTo(Verdict.NEW_FAILURE);
        assertThat(attempt1.gate().decision()).isEqualTo(GateDecision.FAIL);

        RunDetails attempt2 = steps.upload(project, RunUpload.of(passing(PAYMENT)).commit(commit).ciRunId(ciRunId).attempt(2));
        assertThat(attempt2.gate().decision()).isEqualTo(GateDecision.PASS);
        assertThat(steps.flakyTests(project)).extracting(TestCaseView::fullName).containsExactly(fullName(PAYMENT));

        RunDetails nextCommit = steps.upload(project, failing(PAYMENT));
        assertThat(RadarSteps.problem(nextCommit, fullName(PAYMENT)).verdict()).isEqualTo(Verdict.KNOWN_FLAKY);
        assertThat(nextCommit.gate().decision()).isEqualTo(GateDecision.WARN);
        assertThat(nextCommit.gate().blocking()).isEmpty();
    }

    @Test
    @Story("Перезапуск CI на том же коммите")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Прошёл на attempt 1, упал на attempt 2 того же коммита -> сразу KNOWN_FLAKY, а не новое падение")
    void failureAfterPassOnSameCommit() {
        String commit = RadarTestData.commitSha();

        steps.upload(project, RunUpload.of(passing(PAYMENT)).commit(commit).attempt(1));
        RunDetails attempt2 = steps.upload(project, RunUpload.of(failing(PAYMENT)).commit(commit).attempt(2));

        assertThat(RadarSteps.problem(attempt2, fullName(PAYMENT)).verdict()).isEqualTo(Verdict.KNOWN_FLAKY);
        assertThat(attempt2.gate().decision()).isEqualTo(GateDecision.WARN);
    }

    @Test
    @Story("Частые переключения")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Pass/fail чередуются на разных коммитах: после 3 переключений тест становится флаки")
    void frequentFlipsAcrossCommits() {
        steps.upload(project, passing(PAYMENT));
        steps.upload(project, failing(PAYMENT));
        steps.upload(project, passing(PAYMENT));
        steps.upload(project, failing(PAYMENT));
        assertThat(steps.flakyTests(project)).as("после 3 переключений и 4 прогонов выборка ещё мала").isEmpty();

        steps.upload(project, passing(PAYMENT));

        List<TestCaseView> flaky = steps.flakyTests(project);
        assertThat(flaky).singleElement().satisfies(t -> {
            assertThat(t.fullName()).isEqualTo(fullName(PAYMENT));
            assertThat(t.flipCount()).isEqualTo(4);
        });
        RunDetails next = steps.upload(project, failing(PAYMENT));
        assertThat(RadarSteps.problem(next, fullName(PAYMENT)).verdict()).isEqualTo(Verdict.KNOWN_FLAKY);
    }

    @Test
    @Story("Повтор внутри прогона")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Surefire flakyFailure: тест прошёл после повтора -> флаки с первого же прогона, gate WARN")
    void surefireRetryMakesTestFlakyImmediately() {
        RunDetails details = steps.upload(project, JUnitXmlBuilder.report()
                .passed(SUITE, "stable")
                .flakyPassed(SUITE, PAYMENT, TIMEOUT, "Read timed out"));

        assertThat(details.gate().decision()).isEqualTo(GateDecision.WARN);
        assertThat(RadarSteps.problem(details, fullName(PAYMENT)).passedAfterRetry()).isTrue();
        assertThat(steps.flakyTests(project)).extracting(TestCaseView::fullName).containsExactly(fullName(PAYMENT));
    }

    @Test
    @Story("Стабильные тесты")
    @DisplayName("Стабильный тест, который начал падать, не считается флаки")
    void brokenTestIsNotFlaky() {
        steps.upload(project, passing(PAYMENT));
        steps.upload(project, passing(PAYMENT));
        steps.upload(project, failing(PAYMENT));
        steps.upload(project, failing(PAYMENT));

        assertThat(steps.flakyTests(project)).isEmpty();
        assertThat(steps.test(project, fullName(PAYMENT)).flipCount()).isEqualTo(1);
    }

    @Test
    @Story("Стабильные тесты")
    @DisplayName("Пропущенный тест не влияет на анализ и не меняет последний статус")
    void skippedDoesNotChangeState() {
        steps.upload(project, failing(PAYMENT));
        steps.upload(project, JUnitXmlBuilder.report().skipped(SUITE, PAYMENT));

        TestCaseView test = steps.test(project, fullName(PAYMENT));
        assertThat(test.flaky()).isFalse();
        assertThat(test.lastStatus()).hasToString("FAILED");
    }

    private static JUnitXmlBuilder failing(String test) {
        return JUnitXmlBuilder.report()
                .passed(SUITE, "opensCheckout")
                .failed(SUITE, test, ASSERTION, "expected: <200> but was: <502>");
    }

    private static JUnitXmlBuilder passing(String test) {
        return JUnitXmlBuilder.report()
                .passed(SUITE, "opensCheckout")
                .passed(SUITE, test);
    }
}
