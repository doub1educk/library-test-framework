package ru.study.flakyradar.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import ru.study.flakyradar.analysis.DetectionPolicy;

/**
 * Настройки сервиса (префикс {@code flaky-radar}).
 *
 * @param adminToken токен администратора для создания проектов
 * @param publicUrl  внешний адрес сервиса, используется в ссылках сводок
 * @param detection  пороги анализа нестабильности
 * @param ingest     ограничения приёма отчётов
 */
@Validated
@ConfigurationProperties("flaky-radar")
public record FlakyRadarProperties(
        @NotBlank String adminToken,
        @NotBlank String publicUrl,
        @Valid @NotNull Detection detection,
        @Valid @NotNull Ingest ingest) {

    public record Detection(@Min(2) int window, @Min(2) int minRuns, @Min(1) int minFlips, @Min(2) int brokenStreak) {

        public DetectionPolicy toPolicy() {
            return new DetectionPolicy(window, minRuns, minFlips, brokenStreak);
        }
    }

    public record Ingest(@Min(100) int maxMessageLength) {
    }

    /** Адрес без завершающего слэша. */
    public String baseUrl() {
        return publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }
}
