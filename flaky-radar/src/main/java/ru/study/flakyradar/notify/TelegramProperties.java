package ru.study.flakyradar.notify;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("flaky-radar.telegram")
public record TelegramProperties(boolean enabled, String baseUrl, String botToken, String chatId, Duration timeout,
                                 boolean notifyOnPass) {
    public TelegramProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.telegram.org" : baseUrl;
        timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
    }

    public boolean configured() {
        return enabled && botToken != null && !botToken.isBlank() && chatId != null && !chatId.isBlank();
    }
}
