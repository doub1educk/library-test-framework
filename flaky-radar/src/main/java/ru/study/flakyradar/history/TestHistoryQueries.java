package ru.study.flakyradar.history;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.study.flakyradar.analysis.HistoryPoint;
import ru.study.flakyradar.report.TestStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Запросы истории, которые неудобно выражать через JPA. Окно последних N результатов
 * для многих тестов сразу берётся одним запросом с оконной функцией row_number().
 */
@Repository
public class TestHistoryQueries {

    /** Ограничение на число параметров в IN, чтобы не упереться в лимит драйвера на больших отчётах. */
    private static final int CHUNK = 1000;

    private final JdbcClient jdbc;

    public TestHistoryQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Последние {@code window} результатов каждого теста, в хронологическом порядке (самый новый последним).
     */
    public Map<Long, List<HistoryPoint>> lastResults(Collection<Long> testCaseIds, int window) {
        Map<Long, List<HistoryPoint>> history = new HashMap<>();
        List<Long> ids = List.copyOf(testCaseIds);
        for (int from = 0; from < ids.size(); from += CHUNK) {
            List<Long> chunk = ids.subList(from, Math.min(ids.size(), from + CHUNK));
            jdbc.sql("""
                            select h.test_case_id, h.commit_sha, h.branch, h.status, h.failed_attempts
                            from (select r.test_case_id, t.commit_sha, t.branch, r.status, r.failed_attempts, r.run_id,
                                         row_number() over (partition by r.test_case_id order by r.run_id desc) as rn
                                  from test_result r
                                           join test_run t on t.id = r.run_id
                                  where r.test_case_id in (:ids)) h
                            where h.rn <= :window
                            order by h.test_case_id, h.run_id
                            """)
                    .param("ids", chunk)
                    .param("window", window)
                    .query((rs, rowNum) -> {
                        TestStatus status = TestStatus.valueOf(rs.getString("status"));
                        HistoryPoint point = new HistoryPoint(rs.getString("commit_sha"), rs.getString("branch"),
                                status, status == TestStatus.PASSED && rs.getInt("failed_attempts") > 0);
                        history.computeIfAbsent(rs.getLong("test_case_id"), id -> new ArrayList<>()).add(point);
                        return point;
                    })
                    .list();
        }
        return history;
    }
}
