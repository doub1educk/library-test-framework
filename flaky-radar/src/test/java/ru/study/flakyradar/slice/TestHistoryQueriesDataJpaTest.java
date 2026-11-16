package ru.study.flakyradar.slice;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import ru.study.flakyradar.analysis.HistoryPoint;
import ru.study.flakyradar.history.TestCase;
import ru.study.flakyradar.history.TestCaseRepository;
import ru.study.flakyradar.history.TestHistoryQueries;
import ru.study.flakyradar.history.TestResult;
import ru.study.flakyradar.history.TestResultRepository;
import ru.study.flakyradar.history.TestRun;
import ru.study.flakyradar.history.TestRunRepository;
import ru.study.flakyradar.project.Project;
import ru.study.flakyradar.project.ProjectRepository;
import ru.study.flakyradar.report.TestStatus;
import ru.study.testfw.containers.PostgresContainerConfiguration;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("slice")
@Epic("Flaky Radar")
@Feature("Слой данных")
@DisplayName("Slice: история тестов (@DataJpaTest + PostgreSQL)")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresContainerConfiguration.class, TestHistoryQueries.class})
class TestHistoryQueriesDataJpaTest {

    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

    @Autowired
    private ProjectRepository projects;
    @Autowired
    private TestRunRepository runs;
    @Autowired
    private TestCaseRepository testCases;
    @Autowired
    private TestResultRepository results;
    @Autowired
    private TestHistoryQueries historyQueries;

    private Project project;

    @BeforeEach
    void setUp() {
        project = projects.save(new Project("slice-" + System.nanoTime(), "slice", "0".repeat(64), NOW));
    }

    @Test
    @DisplayName("Окно истории: последние N результатов каждого теста в хронологическом порядке")
    void lastResultsWindow() {
        TestCase a = testCases.save(new TestCase(project.getId(), "S", "a", "S.a", NOW));
        TestCase b = testCases.save(new TestCase(project.getId(), "S", "b", "S.b", NOW));
        TestStatus[] statuses = {TestStatus.PASSED, TestStatus.FAILED, TestStatus.PASSED, TestStatus.FAILED, TestStatus.ERROR};
        for (int i = 0; i < statuses.length; i++) {
            TestRun run = run("c" + i, "main");
            results.save(new TestResult(run, a, statuses[i], 10, 0, null, null, null, false));
            if (i == 0) {
                results.save(new TestResult(run, b, TestStatus.PASSED, 10, 1, null, null, null, false));
            }
        }
        results.flush();

        Map<Long, List<HistoryPoint>> history = historyQueries.lastResults(List.of(a.getId(), b.getId()), 3);

        assertThat(history.get(a.getId())).extracting(HistoryPoint::status)
                .containsExactly(TestStatus.PASSED, TestStatus.FAILED, TestStatus.ERROR);
        assertThat(history.get(a.getId())).extracting(HistoryPoint::commitSha).containsExactly("c2", "c3", "c4");
        assertThat(history.get(b.getId())).singleElement()
                .satisfies(p -> assertThat(p.passedAfterRetry()).isTrue());
    }

    @Test
    @DisplayName("Тест без истории в результат не попадает; пустой список id не ломает запрос")
    void emptyHistory() {
        TestCase fresh = testCases.save(new TestCase(project.getId(), "S", "fresh", "S.fresh", NOW));

        assertThat(historyQueries.lastResults(List.of(fresh.getId()), 30)).isEmpty();
        assertThat(historyQueries.lastResults(List.of(), 30)).isEmpty();
    }

    @Test
    @DisplayName("Ограничение UNIQUE: имя теста уникально в пределах проекта")
    void uniqueTestNamePerProject() {
        testCases.saveAndFlush(new TestCase(project.getId(), "S", "a", "S.a", NOW));

        assertThatThrownBy(() -> testCases.saveAndFlush(new TestCase(project.getId(), "S", "a", "S.a", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Идемпотентность на уровне БД: (проект, ciRunId, attempt) уникальны")
    void uniqueCiRun() {
        runs.saveAndFlush(new TestRun(project.getId(), "main", "c1", 1, "42", null, NOW));

        assertThatThrownBy(() -> runs.saveAndFlush(new TestRun(project.getId(), "main", "c1", 1, "42", null, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private TestRun run(String commit, String branch) {
        TestRun run = new TestRun(project.getId(), branch, commit, 1, null, null, NOW);
        run.setCounts(1, 1, 0, 0);
        return runs.save(run);
    }
}
