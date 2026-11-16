package ru.study.testfw.db;

import io.qameta.allure.Allure;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Снимок таблиц БД на момент падения. Таблицы берутся из {@link SnapshotTablesOnFailure},
 * доступ к БД идёт через {@link JdbcTemplate} из Spring-контекста теста, поэтому расширение не знает
 * ни о сущностях, ни о репозиториях конкретного проекта.
 */
public class TableSnapshotOnFailureExtension implements AfterTestExecutionCallback {

    @Override
    public void afterTestExecution(ExtensionContext context) {
        if (context.getExecutionException().isEmpty()) {
            return;
        }
        SnapshotTablesOnFailure config = AnnotationSupport
                .findAnnotation(context.getRequiredTestClass(), SnapshotTablesOnFailure.class)
                .orElse(null);
        if (config == null) {
            return;
        }
        JdbcTemplate jdbc = SpringExtension.getApplicationContext(context).getBean(JdbcTemplate.class);
        for (String table : config.value()) {
            Allure.addAttachment("Снимок таблицы " + table + " на момент падения", "text/plain",
                    snapshot(jdbc, table, config.maxRows()));
        }
    }

    static String snapshot(JdbcTemplate jdbc, String table, int maxRows) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "select * from \"" + table + "\" order by 1 limit " + maxRows);
            if (rows.isEmpty()) {
                return "(таблица пуста)";
            }
            String header = String.join(" | ", rows.getFirst().keySet());
            return rows.stream()
                    .map(row -> row.values().stream()
                            .map(v -> Objects.toString(v, "null"))
                            .collect(Collectors.joining(" | ")))
                    .collect(Collectors.joining("\n", header + "\n", ""));
        } catch (RuntimeException e) {
            return "Не удалось прочитать таблицу: " + e.getMessage();
        }
    }
}
