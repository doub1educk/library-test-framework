package ru.study.flakyradar.analysis;

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
