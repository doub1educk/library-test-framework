package ru.study.flakyradar.run;

import org.springframework.stereotype.Component;
import ru.study.flakyradar.analysis.GateDecision;
import ru.study.flakyradar.analysis.Verdict;
import ru.study.flakyradar.run.RunDtos.GateItem;
import ru.study.flakyradar.run.RunDtos.RunDetails;
import ru.study.flakyradar.run.RunDtos.RunView;

import java.util.List;

@Component
public class SummaryRenderer {
    static final int MAX_ROWS = 20;
    private static final int MAX_MESSAGE = 140;

    public String markdown(RunDetails details) {
        RunView run = details.run();
        GateDecision decision = details.gate().decision();
        StringBuilder md = new StringBuilder();
        md.append("### ").append(icon(decision)).append(" Flaky Radar: ").append(decision)
                .append(" — ").append(headline(decision)).append("\n\n");
        md.append("Проект `").append(details.projectKey()).append("` · ветка `").append(run.branch())
                .append("` · коммит `").append(run.shortSha()).append("` · попытка ").append(run.attempt()).append("\n\n");
        md.append("| Всего | Прошло | Упало | Пропущено |\n|---:|---:|---:|---:|\n")
                .append("| ").append(run.total()).append(" | ").append(run.passed()).append(" | ")
                .append(run.failed()).append(" | ").append(run.skipped()).append(" |\n\n");

        appendTable(md, "Блокируют сборку", details.gate().blocking());
        appendTable(md, "Не блокируют (флаки, карантин, прошли после повтора)", details.gate().warnings());

        md.append("[Открыть прогон в Flaky Radar](").append(details.dashboardUrl()).append(")");
        if (run.buildUrl() != null && !run.buildUrl().isBlank()) {
            md.append(" · [CI](").append(run.buildUrl()).append(")");
        }
        return md.append("\n").toString();
    }

    public String plainText(RunDetails details) {
        RunView run = details.run();
        GateDecision decision = details.gate().decision();
        StringBuilder text = new StringBuilder();
        text.append(icon(decision)).append(" ").append(details.projectKey()).append(": ").append(decision)
                .append(" (").append(headline(decision)).append(")\n");
        text.append(run.branch()).append(" @ ").append(run.shortSha()).append(", попытка ").append(run.attempt())
                .append(": ").append(run.passed()).append("/").append(run.total()).append(" прошло\n");
        List<GateItem> blocking = details.gate().blocking();
        if (!blocking.isEmpty()) {
            text.append("\nБлокируют:\n");
            blocking.stream().limit(10).forEach(item ->
                    text.append("• ").append(item.testName()).append(" [").append(label(item)).append("]\n"));
        }
        long flaky = details.gate().warnings().size();
        if (flaky > 0) {
            text.append("\nНе блокируют: ").append(flaky).append("\n");
        }
        return text.append("\n").append(details.dashboardUrl()).toString();
    }

    private static void appendTable(StringBuilder md, String title, List<GateItem> items) {
        if (items.isEmpty()) {
            return;
        }
        md.append("**").append(title).append(" (").append(items.size()).append(")**\n\n");
        md.append("| Тест | Вердикт | Ошибка |\n|---|---|---|\n");
        items.stream().limit(MAX_ROWS).forEach(item -> md.append("| `").append(cell(item.testName())).append("` | ")
                .append(label(item)).append(" | ").append(cell(shorten(item.message()))).append(" |\n"));
        if (items.size() > MAX_ROWS) {
            md.append("\n…и ещё ").append(items.size() - MAX_ROWS).append("\n");
        }
        md.append("\n");
    }

    static String label(GateItem item) {
        if (item.quarantined()) {
            return "в карантине";
        }
        if (item.verdict() == null) {
            return item.passedAfterRetry() ? "прошёл после повтора" : "";
        }
        return verdictLabel(item.verdict());
    }

    public String verdictText(Verdict verdict) {
        return verdict == null ? "" : verdictLabel(verdict);
    }

    public static String verdictLabel(Verdict verdict) {
        return switch (verdict) {
            case NEW_FAILURE -> "новое падение";
            case KNOWN_FLAKY -> "известный флаки";
            case LONG_BROKEN -> "давно сломан";
        };
    }

    private static String headline(GateDecision decision) {
        return switch (decision) {
            case PASS -> "всё зелёное";
            case WARN -> "есть нестабильные тесты, сборку не блокируем";
            case FAIL -> "есть новые или давно сломанные падения";
        };
    }

    private static String icon(GateDecision decision) {
        return switch (decision) {
            case PASS -> "🟢";
            case WARN -> "🟡";
            case FAIL -> "🔴";
        };
    }

    private static String shorten(String message) {
        if (message == null) {
            return "";
        }
        String firstLine = message.lines().findFirst().orElse("").strip();
        return firstLine.length() <= MAX_MESSAGE ? firstLine : firstLine.substring(0, MAX_MESSAGE) + "…";
    }

    private static String cell(String text) {
        return text.replace("|", "\\|").replace("`", "'").replace("\n", " ");
    }
}
