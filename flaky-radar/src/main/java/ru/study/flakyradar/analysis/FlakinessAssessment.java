package ru.study.flakyradar.analysis;

public record FlakinessAssessment(boolean flaky, FlakinessReason reason, int flips, int sampleSize) {
    public static FlakinessAssessment stable(int flips, int sampleSize) {
        return new FlakinessAssessment(false, FlakinessReason.NONE, flips, sampleSize);
    }

    public static FlakinessAssessment flaky(FlakinessReason reason, int flips, int sampleSize) {
        return new FlakinessAssessment(true, reason, flips, sampleSize);
    }

    public double flipRate() {
        return sampleSize < 2 ? 0 : (double) flips / (sampleSize - 1);
    }
}
