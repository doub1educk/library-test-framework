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
import ru.study.flakyradar.framework.RunUpload;
import ru.study.flakyradar.run.RunDtos.RunDetails;
import ru.study.flakyradar.testcase.TestCaseDtos.HistoryEntry;
import ru.study.flakyradar.testcase.TestCaseDtos.TestCaseView;
import ru.study.flakyradar.testcase.TestCaseDtos.TestHistoryView;
import ru.study.testfw.junitxml.JUnitXmlBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.study.flakyradar.framework.RadarTestData.ASSERTION;
import static ru.study.flakyradar.framework.RadarTestData.SUITE;
import static ru.study.flakyradar.framework.RadarTestData.fullName;

@Epic("Flaky Radar")
@Feature("Вердикт прогона")
@DisplayName("API: вердикты и история")
class VerdictApiTest extends BaseRadarApiTest {

    private static final String LOGIN = "logsIn";

    @Test
    @Story("Давно сломан")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Новое падение, ещё одно, третье подряд на main -> NEW_FAILURE, NEW_FAILURE, LONG_BROKEN")
    void longBrokenOnThirdFailure() {
        steps.upload(project, passing());

        Verdict first = verdictOf(steps.upload(project, failing()));
        Verdict second = verdictOf(steps.upload(project, failing()));
        RunDetails third = steps.upload(project, failing());

        assertThat(first).isEqualTo(Verdict.NEW_FAILURE);
        assertThat(second).isEqualTo(Verdict.NEW_FAILURE);
        assertThat(verdictOf(third)).isEqualTo(Verdict.LONG_BROKEN);
        assertThat(third.gate().decision()).as("давно сломанный тест вне карантина блокирует").isEqualTo(GateDecision.FAIL);
    }

    @Test
    @Story("Давно сломан")
    @DisplayName("Падения на feature-ветке не делают тест давно сломанным на main")
    void streakIsPerBranch() {
        steps.upload(project, RunUpload.of(failing()).branch("feature/a"));
        steps.upload(project, RunUpload.of(failing()).branch("feature/a"));

        RunDetails onMain = steps.upload(project, RunUpload.of(failing()).branch("main"));

        assertThat(verdictOf(onMain)).isEqualTo(Verdict.NEW_FAILURE);
    }

    @Test
    @Story("Новое падение")
    @DisplayName("Полностью зелёный прогон -> PASS, пустые списки blocking и warnings")
    void greenRun() {
        RunDetails details = steps.upload(project, passing());

        assertThat(details.gate().decision()).isEqualTo(GateDecision.PASS);
        assertThat(details.gate().blocking()).isEmpty();
        assertThat(details.gate().warnings()).isEmpty();
        assertThat(details.problems()).isEmpty();
    }

    @Test
    @Story("История")
    @DisplayName("История теста: самые новые первыми, с веткой, коммитом и вердиктом")
    void historyNewestFirst() {
        RunDetails r1 = steps.upload(project, RunUpload.of(passing()).branch("main"));
        RunDetails r2 = steps.upload(project, RunUpload.of(failing()).branch("feature/b"));

        TestCaseView test = steps.test(project, fullName(LOGIN));
        TestHistoryView history = steps.history(project, test.id());

        assertThat(history.results()).extracting(HistoryEntry::runId).containsExactly(r2.run().id(), r1.run().id());
        HistoryEntry latest = history.results().getFirst();
        assertThat(latest.branch()).isEqualTo("feature/b");
        assertThat(latest.commitSha()).isEqualTo(r2.run().commitSha());
        assertThat(latest.verdict()).isEqualTo(Verdict.NEW_FAILURE);
        assertThat(history.test().lastStatus()).hasToString("FAILED");
    }

    private static Verdict verdictOf(RunDetails details) {
        return RadarSteps.problem(details, fullName(LOGIN)).verdict();
    }

    private static JUnitXmlBuilder failing() {
        return JUnitXmlBuilder.report().failed(SUITE, LOGIN, ASSERTION, "expected: <true> but was: <false>");
    }

    private static JUnitXmlBuilder passing() {
        return JUnitXmlBuilder.report().passed(SUITE, LOGIN);
    }
}
