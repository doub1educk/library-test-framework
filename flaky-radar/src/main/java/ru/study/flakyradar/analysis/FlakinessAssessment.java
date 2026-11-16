package ru.study.flakyradar.analysis;

/**
 * Итог анализа истории теста.
 *
 * @param flaky      тест нестабилен
 * @param reason     самое сильное из сработавших правил
 * @param flips      число переключений pass/fail в окне
 * @param sampleSize сколько результатов участвовало в анализе
 */
public record FlakinessAssessment(boolean flaky, FlakinessReason reason, int flips, int sampleSize) {

    public static FlakinessAssessment stable(int flips, int sampleSize) {
        return new FlakinessAssessment(false, FlakinessReason.NONE, flips, sampleSize);
    }

    public static FlakinessAssessment flaky(FlakinessReason reason, int flips, int sampleSize) {
        return new FlakinessAssessment(true, reason, flips, sampleSize);
    }

    /** Доля переключений: 0 у стабильного теста, 1 если результат меняется каждый прогон. */
    public double flipRate() {
        return sampleSize < 2 ? 0 : (double) flips / (sampleSize - 1);
    }
}
