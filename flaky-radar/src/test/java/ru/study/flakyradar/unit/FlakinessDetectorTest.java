package ru.study.flakyradar.unit;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.study.flakyradar.analysis.DetectionPolicy;
import ru.study.flakyradar.analysis.FlakinessAssessment;
import ru.study.flakyradar.analysis.FlakinessDetector;
import ru.study.flakyradar.analysis.FlakinessReason;
import ru.study.flakyradar.analysis.HistoryPoint;
import ru.study.flakyradar.report.TestStatus;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Epic("Flaky Radar")
@Feature("Определение нестабильных тестов")
@DisplayName("Unit: FlakinessDetector")
class FlakinessDetectorTest {

    private final FlakinessDetector detector = new FlakinessDetector(DetectionPolicy.DEFAULT);

    @Test
    @Story("Стабильные тесты")
    @DisplayName("Всегда зелёный тест стабилен")
    void alwaysGreen() {
        FlakinessAssessment result = detector.assess(sequence("PPPPPPPPPP"));

        assertThat(result.flaky()).isFalse();
        assertThat(result.reason()).isEqualTo(FlakinessReason.NONE);
        assertThat(result.flips()).isZero();
    }

    @Test
    @Story("Стабильные тесты")
    @DisplayName("Тест сломался и остаётся сломанным: одно переключение, это не флаки")
    void brokenOnce() {
        FlakinessAssessment result = detector.assess(sequence("PPPPPFFFFF"));

        assertThat(result.flaky()).isFalse();
        assertThat(result.flips()).isEqualTo(1);
    }

    @Test
    @Story("Правило переключений")
    @DisplayName("3 переключения pass/fail в окне -> FREQUENT_FLIPS")
    void frequentFlips() {
        FlakinessAssessment result = detector.assess(sequence("PPFPPFP"));

        assertThat(result.flaky()).isTrue();
        assertThat(result.reason()).isEqualTo(FlakinessReason.FREQUENT_FLIPS);
        assertThat(result.flips()).isEqualTo(4);
        assertThat(result.flipRate()).isEqualTo(4.0 / 6);
    }

    @Test
    @Story("Правило переключений")
    @DisplayName("Мало данных (меньше minRuns) -> правило переключений не применяется")
    void tooFewRuns() {
        assertThat(detector.assess(sequence("PFPF")).flaky()).isFalse();
    }

    @Test
    @Story("Правило переключений")
    @DisplayName("Старые переключения за пределами окна в 30 результатов забываются")
    void oldFlipsForgotten() {
        List<HistoryPoint> history = new ArrayList<>(sequence("PFPFPFPF"));
        history.addAll(sequence("P".repeat(30)));

        FlakinessAssessment result = detector.assess(history);

        assertThat(result.flaky()).isFalse();
        assertThat(result.sampleSize()).isEqualTo(30);
    }

    @Test
    @Story("Правило переключений")
    @DisplayName("Пропуски (SKIPPED) не считаются ни успехом, ни падением")
    void skippedIgnored() {
        FlakinessAssessment result = detector.assess(sequence("PSPSPSFSF"));

        assertThat(result.flips()).isEqualTo(1);
        assertThat(result.sampleSize()).isEqualTo(5);
    }

    @Test
    @Story("Один коммит")
    @DisplayName("На одном коммите и прошёл, и упал -> SAME_COMMIT_MIXED даже при малой истории")
    void sameCommitMixed() {
        List<HistoryPoint> history = List.of(
                HistoryPoint.of("abc", "main", TestStatus.FAILED),
                HistoryPoint.of("abc", "main", TestStatus.PASSED));

        FlakinessAssessment result = detector.assess(history);

        assertThat(result.flaky()).isTrue();
        assertThat(result.reason()).isEqualTo(FlakinessReason.SAME_COMMIT_MIXED);
    }

    @Test
    @Story("Один коммит")
    @DisplayName("ERROR тоже считается падением для правила одного коммита")
    void errorCountsAsFailure() {
        List<HistoryPoint> history = List.of(
                HistoryPoint.of("abc", "main", TestStatus.PASSED),
                HistoryPoint.of("abc", "main", TestStatus.ERROR));

        assertThat(detector.assess(history).reason()).isEqualTo(FlakinessReason.SAME_COMMIT_MIXED);
    }

    @Test
    @Story("Повтор внутри прогона")
    @DisplayName("Прошёл только после повтора -> PASSED_AFTER_RETRY (сильнее остальных правил)")
    void passedAfterRetry() {
        List<HistoryPoint> history = new ArrayList<>(sequence("PFPFPF"));
        history.add(new HistoryPoint("z", "main", TestStatus.PASSED, true));

        assertThat(detector.assess(history).reason()).isEqualTo(FlakinessReason.PASSED_AFTER_RETRY);
    }

    @Test
    @Story("Стабильные тесты")
    @DisplayName("Пустая история -> стабилен")
    void emptyHistory() {
        FlakinessAssessment result = detector.assess(List.of());

        assertThat(result.flaky()).isFalse();
        assertThat(result.flipRate()).isZero();
    }

    @ParameterizedTest(name = "minFlips={0}")
    @Story("Пороги")
    @DisplayName("Порог переключений настраивается")
    @ValueSource(ints = {2, 3, 4})
    void configurableThreshold(int minFlips) {
        FlakinessDetector custom = new FlakinessDetector(new DetectionPolicy(30, 5, minFlips, 3));

        // P->F, F->P, P->F: ровно 3 переключения
        FlakinessAssessment result = custom.assess(sequence("PPPFPPF"));

        assertThat(result.flips()).isEqualTo(3);
        assertThat(result.flaky()).isEqualTo(minFlips <= 3);
    }

    @Test
    @Story("Пороги")
    @DisplayName("Некорректные пороги отклоняются при старте")
    void invalidPolicy() {
        assertThatThrownBy(() -> new DetectionPolicy(1, 5, 3, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DetectionPolicy(10, 20, 3, 3)).isInstanceOf(IllegalArgumentException.class);
    }

    /** P = прошёл, F = упал, S = пропущен; каждый результат на своём коммите. */
    static List<HistoryPoint> sequence(String pattern) {
        List<HistoryPoint> points = new ArrayList<>();
        for (int i = 0; i < pattern.length(); i++) {
            TestStatus status = switch (pattern.charAt(i)) {
                case 'P' -> TestStatus.PASSED;
                case 'F' -> TestStatus.FAILED;
                case 'S' -> TestStatus.SKIPPED;
                default -> throw new IllegalArgumentException("неизвестный символ " + pattern.charAt(i));
            };
            points.add(HistoryPoint.of("c" + i, "main", status));
        }
        return points;
    }
}
