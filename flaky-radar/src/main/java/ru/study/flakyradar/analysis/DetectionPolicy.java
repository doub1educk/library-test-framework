package ru.study.flakyradar.analysis;

/**
 * Пороги анализа.
 *
 * @param window       сколько последних (не пропущенных) результатов теста анализировать
 * @param minRuns      минимальный размер выборки для правила переключений
 * @param minFlips     столько переключений pass/fail в окне делают тест флаки
 * @param brokenStreak столько падений подряд на ветке (включая текущее) дают вердикт LONG_BROKEN
 */
public record DetectionPolicy(int window, int minRuns, int minFlips, int brokenStreak) {

    public static final DetectionPolicy DEFAULT = new DetectionPolicy(30, 5, 3, 3);

    public DetectionPolicy {
        if (window < 2 || minRuns < 2 || minFlips < 1 || brokenStreak < 2) {
            throw new IllegalArgumentException("Некорректные пороги анализа: window=%d, minRuns=%d, minFlips=%d, brokenStreak=%d"
                    .formatted(window, minRuns, minFlips, brokenStreak));
        }
        if (minRuns > window) {
            throw new IllegalArgumentException("minRuns не может быть больше window");
        }
    }
}
