package ru.study.flakyradar.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.study.flakyradar.analysis.GateDecision;
import ru.study.flakyradar.ingest.RunIngestedEvent;
import ru.study.flakyradar.run.RunDtos.RunDetails;
import ru.study.flakyradar.run.RunQueryService;
import ru.study.flakyradar.run.SummaryRenderer;

import java.net.http.HttpClient;
import java.util.Map;

/**
 * Отправляет сводку прогона в Telegram после коммита транзакции приёма.
 * Ошибки Telegram только логируются: уведомление не должно ломать загрузку отчёта из CI.
 */
@Component
public class TelegramNotifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);

    private final TelegramProperties properties;
    private final RunQueryService runQueryService;
    private final SummaryRenderer renderer;
    private final RestClient restClient;

    public TelegramNotifier(TelegramProperties properties, RunQueryService runQueryService, SummaryRenderer renderer,
                            RestClient.Builder builder) {
        this.properties = properties;
        this.runQueryService = runQueryService;
        this.renderer = renderer;
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRunIngested(RunIngestedEvent event) {
        if (!properties.configured()) {
            return;
        }
        try {
            RunDetails details = runQueryService.details(event.projectKey(), event.runId());
            if (details.gate().decision() == GateDecision.PASS && !properties.notifyOnPass()) {
                return;
            }
            send(renderer.plainText(details));
        } catch (RuntimeException e) {
            log.warn("Не удалось отправить сводку прогона {} в Telegram: {}", event.runId(), e.getMessage());
        }
    }

    void send(String text) {
        try {
            restClient.post()
                    // токен вида 123:abc: шаблон URI закодировал бы двоеточие, поэтому путь собирается строкой
                    .uri("/bot" + properties.botToken() + "/sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chat_id", properties.chatId(), "text", text, "disable_web_page_preview", true))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new IllegalStateException("Telegram Bot API недоступен: " + e.getMessage(), e);
        }
    }
}
