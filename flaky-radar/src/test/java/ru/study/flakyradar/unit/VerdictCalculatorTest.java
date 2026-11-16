package ru.study.flakyradar.unit;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ru.study.flakyradar.analysis.DetectionPolicy;
import ru.study.flakyradar.analysis.HistoryPoint;
import ru.study.flakyradar.analysis.Verdict;
import ru.study.flakyradar.analysis.VerdictCalculator;
import ru.study.flakyradar.report.TestStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Epic("Flaky Radar")
@Feature("Вердикт по падению")
@DisplayName("Unit: VerdictCalculator")
class VerdictCalculatorTest {

    private final VerdictCalculator calculator = new VerdictCalculator(DetectionPolicy.DEFAULT);

    @Test
    @Story("Новое падение")
    @DisplayName("Тест раньше проходил -> NEW_FAILURE")
    void previouslyGreen() {
        List<HistoryPoint> history = List.of(point("c1", "main", TestStatus.PASSED), point("c2", "main", TestStatus.PASSED));

        assertThat(calculator.verdictForFailure(false, history, "main", "c3")).isEqualTo(Verdict.NEW_FAILURE);
    }

    @Test
    @Story("Новое падение")
    @DisplayName("Новый тест без истории -> NEW_FAILURE")
    void brandNewTest() {
        assertThat(calculator.verdictForFailure(false, List.of(), "main", "c1")).isEqualTo(Verdict.NEW_FAILURE);
    }

    @Test
    @Story("Известный флаки")
    @DisplayName("Тест помечен флаки -> KNOWN_FLAKY, даже если раньше падал подряд")
    void knownFlakyWins() {
        List<HistoryPoint> history = List.of(point("c1", "main", TestStatus.FAILED), point("c2", "main", TestStatus.FAILED));

        assertThat(calculator.verdictForFailure(true, history, "main", "c3")).isEqualTo(Verdict.KNOWN_FLAKY);
    }

    @Test
    @Story("Известный флаки")
    @DisplayName("На этом же коммите тест уже проходил -> KNOWN_FLAKY (код тот же)")
    void passedOnSameCommit() {
        List<HistoryPoint> history = List.of(point("c1", "main", TestStatus.PASSED));

        assertThat(calculator.verdictForFailure(false, history, "feature", "c1")).isEqualTo(Verdict.KNOWN_FLAKY);
    }

    @Test
    @Story("Давно сломан")
    @DisplayName("Третье падение подряд на ветке -> LONG_BROKEN")
    void thirdFailureInRow() {
        List<HistoryPoint> history = List.of(
                point("c1", "main", TestStatus.PASSED),
                point("c2", "main", TestStatus.FAILED),
                point("c3", "main", TestStatus.ERROR));

        assertThat(calculator.verdictForFailure(false, history, "main", "c4")).isEqualTo(Verdict.LONG_BROKEN);
    }

    @Test
    @Story("Давно сломан")
    @DisplayName("Второе падение подряд -> ещё NEW_FAILURE")
    void secondFailureInRow() {
        List<HistoryPoint> history = List.of(point("c1", "main", TestStatus.PASSED), point("c2", "main", TestStatus.FAILED));

        assertThat(calculator.verdictForFailure(false, history, "main", "c3")).isEqualTo(Verdict.NEW_FAILURE);
    }

    @Test
    @Story("Давно сломан")
    @DisplayName("Падения на других ветках и пропуски не прерывают и не удлиняют серию")
    void otherBranchesAndSkipsIgnored() {
        List<HistoryPoint> history = List.of(
                point("c1", "main", TestStatus.FAILED),
                point("x1", "feature", TestStatus.PASSED),
                point("c2", "main", TestStatus.SKIPPED),
                point("c3", "main", TestStatus.FAILED));

        assertThat(calculator.verdictForFailure(false, history, "main", "c4")).isEqualTo(Verdict.LONG_BROKEN);
        assertThat(calculator.verdictForFailure(false, history, "feature", "x2")).isEqualTo(Verdict.NEW_FAILURE);
    }

    @Test
    @Story("Давно сломан")
    @DisplayName("Порог серии настраивается")
    void configurableStreak() {
        VerdictCalculator strict = new VerdictCalculator(new DetectionPolicy(30, 5, 3, 2));
        List<HistoryPoint> history = List.of(point("c1", "main", TestStatus.FAILED));

        assertThat(strict.verdictForFailure(false, history, "main", "c2")).isEqualTo(Verdict.LONG_BROKEN);
    }

    private static HistoryPoint point(String commit, String branch, TestStatus status) {
        return HistoryPoint.of(commit, branch, status);
    }
}
