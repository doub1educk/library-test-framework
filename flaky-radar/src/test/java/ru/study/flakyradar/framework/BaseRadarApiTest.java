package ru.study.flakyradar.framework;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.study.testfw.db.DatabaseCleaner;

/**
 * База для API-тестов: перед каждым тестом чистая БД, сброшенный мок Telegram
 * и свежий проект со своим API-ключом. Тесты не влияют друг на друга.
 */
@RadarApiTest
public abstract class BaseRadarApiTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbc;

    protected RadarClient client;
    protected RadarSteps steps;
    protected TestProject project;

    @DynamicPropertySource
    static void externalServices(DynamicPropertyRegistry registry) {
        registry.add("flaky-radar.telegram.enabled", () -> "true");
        registry.add("flaky-radar.telegram.base-url", TelegramMock::baseUrl);
        registry.add("flaky-radar.telegram.bot-token", () -> TelegramMock.BOT_TOKEN);
        registry.add("flaky-radar.telegram.chat-id", () -> TelegramMock.CHAT_ID);
        registry.add("flaky-radar.telegram.timeout", () -> "1s");
    }

    @BeforeEach
    void prepareEnvironment() {
        DatabaseCleaner.truncateAll(jdbc);
        TelegramMock.resetToDefault();
        client = new RadarClient(port);
        steps = new RadarSteps(client);
        project = steps.createProject(RadarTestData.projectKey());
    }
}
