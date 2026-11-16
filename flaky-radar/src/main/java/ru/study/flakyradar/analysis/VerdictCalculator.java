package ru.study.flakyradar.analysis;

import ru.study.flakyradar.report.TestStatus;

import java.util.List;

/**
 * Вердикт для упавшего теста по истории <b>до</b> текущего прогона. Правила проверяются по порядку:
 * <ol>
 *     <li>{@link Verdict#KNOWN_FLAKY}: тест уже помечен нестабильным, или на этом же коммите он раньше проходил
 *         (код тот же, значит, падение не вызвано изменениями);</li>
 *     <li>{@link Verdict#LONG_BROKEN}: вместе с текущим падением набирается {@code brokenStreak}
 *         падений подряд на той же ветке;</li>
 *     <li>иначе {@link Verdict#NEW_FAILURE}.</li>
 * </ol>
 */
public final class VerdictCalculator {

    private final DetectionPolicy policy;

    public VerdictCalculator(DetectionPolicy policy) {
        this.policy = policy;
    }

    /**
     * @param knownFlaky   тест помечен нестабильным по итогам прошлых прогонов
     * @param priorHistory результаты до текущего прогона, самый новый последним
     * @param branch       ветка текущего прогона
     * @param commitSha    коммит текущего прогона
     */
    public Verdict verdictForFailure(boolean knownFlaky, List<HistoryPoint> priorHistory, String branch, String commitSha) {
        if (knownFlaky || passedBeforeOnSameCommit(priorHistory, commitSha)) {
            return Verdict.KNOWN_FLAKY;
        }
        int streak = trailingFailuresOnBranch(priorHistory, branch) + 1;
        return streak >= policy.brokenStreak() ? Verdict.LONG_BROKEN : Verdict.NEW_FAILURE;
    }

    static boolean passedBeforeOnSameCommit(List<HistoryPoint> history, String commitSha) {
        return history.stream()
                .anyMatch(p -> p.commitSha().equals(commitSha) && p.status() == TestStatus.PASSED);
    }

    static int trailingFailuresOnBranch(List<HistoryPoint> history, String branch) {
        int streak = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            HistoryPoint point = history.get(i);
            if (!point.branch().equals(branch) || point.status() == TestStatus.SKIPPED) {
                continue;
            }
            if (!point.status().isFailure()) {
                break;
            }
            streak++;
        }
        return streak;
    }
}
