package ru.study.flakyradar.unit;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.study.flakyradar.analysis.GateDecision;
import ru.study.flakyradar.analysis.GateEvaluator;
import ru.study.flakyradar.analysis.GateEvaluator.Outcome;
import ru.study.flakyradar.analysis.Verdict;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Epic("Flaky Radar")
@Feature("Решение для CI (gate)")
@DisplayName("Unit: GateEvaluator")
class GateEvaluatorTest {

    private static final Outcome GREEN = new Outcome(null, false, false);

    private final GateEvaluator evaluator = new GateEvaluator();

    @ParameterizedTest(name = "{0} -> {2}")
    @Story("Таблица решений")
    @DisplayName("Решение по набору исходов")
    @MethodSource("cases")
    void decide(String description, List<Outcome> outcomes, GateDecision expected) {
        assertThat(evaluator.decide(outcomes)).isEqualTo(expected);
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("пустой прогон", List.of(), GateDecision.PASS),
                Arguments.of("всё зелёное", List.of(GREEN, GREEN), GateDecision.PASS),
                Arguments.of("новое падение", List.of(GREEN, failed(Verdict.NEW_FAILURE, false)), GateDecision.FAIL),
                Arguments.of("давно сломан", List.of(failed(Verdict.LONG_BROKEN, false)), GateDecision.FAIL),
                Arguments.of("только флаки", List.of(GREEN, failed(Verdict.KNOWN_FLAKY, false)), GateDecision.WARN),
                Arguments.of("новое падение в карантине", List.of(failed(Verdict.NEW_FAILURE, true)), GateDecision.WARN),
                Arguments.of("давно сломан в карантине", List.of(failed(Verdict.LONG_BROKEN, true)), GateDecision.WARN),
                Arguments.of("прошёл после повтора", List.of(new Outcome(null, false, true)), GateDecision.WARN),
                Arguments.of("флаки + новое падение", List.of(failed(Verdict.KNOWN_FLAKY, false),
                        failed(Verdict.NEW_FAILURE, false)), GateDecision.FAIL));
    }

    private static Outcome failed(Verdict verdict, boolean quarantined) {
        return new Outcome(verdict, quarantined, false);
    }
}
