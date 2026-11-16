package ru.study.flakyradar.analysis;

import java.util.Collection;

/**
 * Решение по прогону.
 * <ul>
 *     <li>FAIL: есть падение с вердиктом NEW_FAILURE или LONG_BROKEN, и тест не в карантине;</li>
 *     <li>WARN: падения есть, но все они KNOWN_FLAKY или в карантине, либо тест прошёл лишь после повтора;</li>
 *     <li>PASS: всё зелёное.</li>
 * </ul>
 */
public final class GateEvaluator {

    public GateDecision decide(Collection<Outcome> outcomes) {
        boolean warn = false;
        for (Outcome outcome : outcomes) {
            if (isBlocking(outcome)) {
                return GateDecision.FAIL;
            }
            warn |= isWarning(outcome);
        }
        return warn ? GateDecision.WARN : GateDecision.PASS;
    }

    public static boolean isBlocking(Outcome outcome) {
        return outcome.verdict() != null && !outcome.quarantined() && outcome.verdict() != Verdict.KNOWN_FLAKY;
    }

    public static boolean isWarning(Outcome outcome) {
        return !isBlocking(outcome) && (outcome.verdict() != null || outcome.passedAfterRetry());
    }

    /**
     * Исход одного теста для gate.
     *
     * @param verdict          вердикт, если тест упал; null если не упал
     * @param quarantined      тест в карантине
     * @param passedAfterRetry тест прошёл только после повтора
     */
    public record Outcome(Verdict verdict, boolean quarantined, boolean passedAfterRetry) {
    }
}
