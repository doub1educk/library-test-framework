package ru.study.flakyradar.framework;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.qameta.allure.Step;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

public final class TelegramMock {
    public static final String BOT_TOKEN = "123:test-token";
    public static final String CHAT_ID = "-1001";
    private static final String SEND_MESSAGE = "/bot" + BOT_TOKEN + "/sendMessage";

    private static final WireMockServer SERVER = new WireMockServer(options().dynamicPort());

    static {
        SERVER.start();
        Runtime.getRuntime().addShutdownHook(new Thread(SERVER::stop));
    }

    private TelegramMock() {
    }

    public static String baseUrl() {
        return SERVER.baseUrl();
    }

    public static void resetToDefault() {
        SERVER.resetAll();
        SERVER.stubFor(post(urlPathEqualTo(SEND_MESSAGE)).willReturn(okJson("{\"ok\": true}")));
    }

    @Step("Telegram: Bot API отвечает ошибкой HTTP {status}")
    public static void respondsWithError(int status) {
        SERVER.stubFor(post(urlPathEqualTo(SEND_MESSAGE)).willReturn(aResponse().withStatus(status)));
    }

    public static List<String> sentMessages() {
        return SERVER.findAll(postRequestedFor(urlPathEqualTo(SEND_MESSAGE))).stream()
                .map(LoggedRequest::getBodyAsString)
                .toList();
    }
}
