package ru.study.testfw.db;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.stream.Collectors;

public final class DatabaseCleaner {
    private static final String FLYWAY_HISTORY = "flyway_schema_history";

    private DatabaseCleaner() {
    }

    public static void truncateAll(JdbcTemplate jdbc) {
        List<String> tables = jdbc.queryForList(
                "select tablename from pg_tables where schemaname = 'public' and tablename <> ?",
                String.class, FLYWAY_HISTORY);
        if (tables.isEmpty()) {
            return;
        }
        String joined = tables.stream().map(t -> "\"" + t + "\"").collect(Collectors.joining(", "));
        jdbc.execute("truncate table " + joined + " restart identity cascade");
    }
}
