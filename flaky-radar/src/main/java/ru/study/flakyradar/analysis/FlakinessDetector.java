package ru.study.flakyradar.analysis;

import ru.study.flakyradar.report.TestStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Определяет, нестабилен ли тест, по его последним результатам.
 *
 * <p>Правила по убыванию силы доказательства:
 * <ol>
 *     <li>{@link FlakinessReason#PASSED_AFTER_RETRY}: в каком-то прогоне тест прошёл только после повтора;</li>
 *     <li>{@link FlakinessReason#SAME_COMMIT_MIXED}: на одном коммите есть и успех, и падение,
 *         хотя код не менялся (сюда попадают перезапуски CI с attempt &gt; 1);</li>
 *     <li>{@link FlakinessReason#FREQUENT_FLIPS}: в окне из {@code window} результатов не меньше
 *         {@code minFlips} переключений pass/fail (правило действует при выборке от {@code minRuns}).</li>
 * </ol>
 * Пропущенные (SKIPPED) результаты в анализе не участвуют.
 */
public final class FlakinessDetector {

    private final DetectionPolicy policy;

    public FlakinessDetector(DetectionPolicy policy) {
        this.policy = policy;
    }

    /** @param history результаты в хронологическом порядке, самый новый последним */
    public FlakinessAssessment assess(List<HistoryPoint> history) {
        List<HistoryPoint> sample = lastExecuted(history);
        int flips = countFlips(sample);

        if (sample.stream().anyMatch(HistoryPoint::passedAfterRetry)) {
            return FlakinessAssessment.flaky(FlakinessReason.PASSED_AFTER_RETRY, flips, sample.size());
        }
        if (hasMixedOutcomeOnSameCommit(sample)) {
            return FlakinessAssessment.flaky(FlakinessReason.SAME_COMMIT_MIXED, flips, sample.size());
        }
        if (sample.size() >= policy.minRuns() && flips >= policy.minFlips()) {
            return FlakinessAssessment.flaky(FlakinessReason.FREQUENT_FLIPS, flips, sample.size());
        }
        return FlakinessAssessment.stable(flips, sample.size());
    }

    private List<HistoryPoint> lastExecuted(List<HistoryPoint> history) {
        List<HistoryPoint> executed = history.stream()
                .filter(p -> p.status() != TestStatus.SKIPPED)
                .toList();
        int from = Math.max(0, executed.size() - policy.window());
        return executed.subList(from, executed.size());
    }

    static int countFlips(List<HistoryPoint> sample) {
        int flips = 0;
        for (int i = 1; i < sample.size(); i++) {
            if (sample.get(i).status().isFailure() != sample.get(i - 1).status().isFailure()) {
                flips++;
            }
        }
        return flips;
    }

    static boolean hasMixedOutcomeOnSameCommit(List<HistoryPoint> sample) {
        // для каждого коммита: бит 1 = был успех, бит 2 = было падение
        Map<String, Integer> outcomes = new HashMap<>();
        for (HistoryPoint point : sample) {
            int bit = point.status().isFailure() ? 2 : 1;
            int merged = outcomes.merge(point.commitSha(), bit, (a, b) -> a | b);
            if (merged == 3) {
                return true;
            }
        }
        return false;
    }
}
