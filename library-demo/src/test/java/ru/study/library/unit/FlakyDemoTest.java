package ru.study.library.unit;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("flaky-demo")
@Epic("Библиотека")
@Feature("Демонстрация нестабильного теста")
@DisplayName("DEMO: нестабильный тест для Flaky Radar")
class FlakyDemoTest {
    @Test
    @Description("Падает, только если задана переменная окружения LIBRARY_DEMO_FLAKY=fail")
    @DisplayName("Ответ внешнего сервиса приходит вовремя (иногда нет)")
    void sometimesTimesOut() {
        boolean slowNetwork = "fail".equalsIgnoreCase(System.getenv("LIBRARY_DEMO_FLAKY"));

        assertThat(slowNetwork)
                .as("Read timed out after 1000 ms: localhost:%d", 40000 + (int) (System.nanoTime() % 20000))
                .isFalse();
    }
}
