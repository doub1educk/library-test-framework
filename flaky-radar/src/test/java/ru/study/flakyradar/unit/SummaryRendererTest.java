package ru.study.flakyradar.unit;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ru.study.flakyradar.analysis.GateDecision;
import ru.study.flakyradar.analysis.Verdict;
import ru.study.flakyradar.report.TestStatus;
import ru.study.flakyradar.run.RunDtos.GateItem;
import ru.study.flakyradar.run.RunDtos.GateResponse;
import ru.study.flakyradar.run.RunDtos.RunDetails;
import ru.study.flakyradar.run.RunDtos.RunView;
import ru.study.flakyradar.run.SummaryRenderer;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Epic("Flaky Radar")
@Feature("Сводки: markdown для PR и Telegram")
@DisplayName("Unit: SummaryRenderer")
class SummaryRendererTest {

    private final SummaryRenderer renderer = new SummaryRenderer();

    @Test
    @Story("Markdown для PR")
    @DisplayName("Зелёный прогон: заголовок PASS, без таблиц, ссылки на дашборд и CI")
    void greenRun() {
        String md = renderer.markdown(details(GateDecision.PASS, List.of(), List.of(), "https://ci/1"));

        assertThat(md)
                .startsWith("### 🟢 Flaky Radar: PASS — всё зелёное")
                .contains("ветка `main` · коммит `abcdef1` · попытка 2")
                .doesNotContain("Блокируют")
                .contains("[Открыть прогон в Flaky Radar](http://radar/projects/demo/runs/7) · [CI](https://ci/1)");
    }

    @Test
    @Story("Markdown для PR")
    @DisplayName("Длинный список обрезается до 20 строк с припиской «…и ещё N»; спецсимволы экранируются")
    void longListTruncated() {
        List<GateItem> blocking = IntStream.range(0, 25)
                .mapToObj(i -> new GateItem("Suite.t" + i, TestStatus.FAILED, Verdict.NEW_FAILURE, false, false,
                        "a|b `x`\nвторая строка"))
                .toList();

        String md = renderer.markdown(details(GateDecision.FAIL, blocking, List.of(), null));

        assertThat(md).contains("**Блокируют сборку (25)**").contains("…и ещё 5").doesNotContain("Suite.t24`");
        assertThat(md).contains("| a\\|b 'x' |").doesNotContain("вторая строка");
        assertThat(md).doesNotContain("[CI]");
    }

    @Test
    @Story("Подписи вердиктов")
    @DisplayName("Карантин важнее вердикта, прошедший после повтора помечается отдельно")
    void labels() {
        List<GateItem> warnings = List.of(
                new GateItem("S.q", TestStatus.FAILED, Verdict.NEW_FAILURE, true, false, "m"),
                new GateItem("S.r", TestStatus.PASSED, null, false, true, "m"),
                new GateItem("S.f", TestStatus.FAILED, Verdict.KNOWN_FLAKY, false, false, "m"));

        String md = renderer.markdown(details(GateDecision.WARN, List.of(), warnings, null));

        assertThat(md).contains("`S.q` | в карантине").contains("`S.r` | прошёл после повтора")
                .contains("`S.f` | известный флаки");
        assertThat(renderer.verdictText(null)).isEmpty();
        assertThat(renderer.verdictText(Verdict.LONG_BROKEN)).isEqualTo("давно сломан");
    }

    @Test
    @Story("Telegram")
    @DisplayName("Текст для Telegram: проект, решение, блокирующие тесты и ссылка")
    void plainText() {
        List<GateItem> blocking = List.of(new GateItem("S.broken", TestStatus.FAILED, Verdict.LONG_BROKEN, false, false, "m"));

        String text = renderer.plainText(details(GateDecision.FAIL, blocking, List.of(
                new GateItem("S.f", TestStatus.FAILED, Verdict.KNOWN_FLAKY, false, false, "m")), null));

        assertThat(text)
                .startsWith("🔴 demo: FAIL")
                .contains("main @ abcdef1, попытка 2: 8/10 прошло")
                .contains("• S.broken [давно сломан]")
                .contains("Не блокируют: 1")
                .endsWith("http://radar/projects/demo/runs/7");
    }

    private static RunDetails details(GateDecision decision, List<GateItem> blocking, List<GateItem> warnings, String buildUrl) {
        RunView run = new RunView(7L, "main", "abcdef1234567890", 2, "ci-1", buildUrl,
                Instant.parse("2026-11-16T10:00:00Z"), 10, 8, 2, 0, decision);
        return new RunDetails("demo", run, new GateResponse(7L, decision, blocking, warnings), List.of(),
                "http://radar/projects/demo/runs/7");
    }
}
