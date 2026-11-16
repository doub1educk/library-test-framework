package ru.study.flakyradar.notify;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Настройки отправки сводок в Telegram (префикс {@code flaky-radar.telegram}).
 *
 * @param enabled      включена ли отправка
 * @param baseUrl      адрес Bot API (в тестах подменяется на WireMock)
 * @param botToken     токен бота
 * @param chatId       чат или канал, куда слать
 * @param timeout      таймаут запроса: медленный Telegram не должен тормозить ответ CI
 * @param notifyOnPass слать ли сводку, когда всё зелёное
 */
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
