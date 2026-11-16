package ru.study.flakyradar.analysis;

import java.util.Collection;

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

    public record Outcome(Verdict verdict, boolean quarantined, boolean passedAfterRetry) {
    }
}
