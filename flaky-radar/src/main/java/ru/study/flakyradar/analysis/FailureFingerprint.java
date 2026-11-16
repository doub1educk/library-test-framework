package ru.study.flakyradar.analysis;

public record FailureFingerprint(String exceptionType, String normalizedText, String hash) {
}
