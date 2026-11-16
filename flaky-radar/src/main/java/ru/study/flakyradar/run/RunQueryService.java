package ru.study.flakyradar.run;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.study.flakyradar.analysis.GateDecision;
import ru.study.flakyradar.analysis.GateEvaluator;
import ru.study.flakyradar.config.FlakyRadarProperties;
import ru.study.flakyradar.history.TestResult;
import ru.study.flakyradar.history.TestResultRepository;
import ru.study.flakyradar.history.TestRun;
import ru.study.flakyradar.history.TestRunRepository;
import ru.study.flakyradar.project.Project;
import ru.study.flakyradar.project.ProjectService;
import ru.study.flakyradar.quarantine.QuarantineService;
import ru.study.flakyradar.run.RunDtos.GateItem;
import ru.study.flakyradar.run.RunDtos.GateResponse;
import ru.study.flakyradar.run.RunDtos.ResultView;
import ru.study.flakyradar.run.RunDtos.RunDetails;
import ru.study.flakyradar.run.RunDtos.RunView;
import ru.study.flakyradar.run.RunExceptions.RunNotFoundException;

import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class RunQueryService {
    static final int MAX_LIMIT = 200;

    private final TestRunRepository runRepository;
    private final TestResultRepository resultRepository;
    private final ProjectService projectService;
    private final QuarantineService quarantineService;
    private final GateEvaluator gateEvaluator;
    private final FlakyRadarProperties properties;

    public RunQueryService(TestRunRepository runRepository, TestResultRepository resultRepository,
                           ProjectService projectService, QuarantineService quarantineService,
                           GateEvaluator gateEvaluator, FlakyRadarProperties properties) {
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.projectService = projectService;
        this.quarantineService = quarantineService;
        this.gateEvaluator = gateEvaluator;
        this.properties = properties;
    }

    public List<RunView> list(String projectKey, String branch, int limit) {
        Project project = projectService.require(projectKey);
        PageRequest page = PageRequest.of(0, Math.clamp(limit, 1, MAX_LIMIT));
        List<TestRun> runs = branch == null || branch.isBlank()
                ? runRepository.findByProjectIdOrderByIdDesc(project.getId(), page)
                : runRepository.findByProjectIdAndBranchOrderByIdDesc(project.getId(), branch, page);
        return runs.stream().map(RunView::from).toList();
    }

    public RunDetails details(String projectKey, long runId) {
        Project project = projectService.require(projectKey);
        TestRun run = runRepository.findByIdAndProjectId(runId, project.getId())
                .orElseThrow(() -> new RunNotFoundException(runId));
        return details(project, run);
    }

    public GateResponse gate(String projectKey, long runId) {
        return details(projectKey, runId).gate();
    }

    public RunDetails details(Project project, TestRun run) {
        List<TestResult> problems = resultRepository.findProblemsByRunId(run.getId());
        Set<Long> quarantined = quarantineService.activeTestCaseIds(project.getId(),
                problems.stream().map(r -> r.getTestCase().getId()).toList());

        List<ResultView> views = problems.stream()
                .map(r -> ResultView.from(r, quarantined.contains(r.getTestCase().getId())))
                .toList();
        GateDecision decision = gateEvaluator.decide(problems.stream()
                .map(r -> r.outcome(quarantined.contains(r.getTestCase().getId())))
                .toList());

        List<GateItem> blocking = views.stream()
                .filter(v -> GateEvaluator.isBlocking(toOutcome(v)))
                .map(GateItem::from)
                .toList();
        List<GateItem> warnings = views.stream()
                .filter(v -> GateEvaluator.isWarning(toOutcome(v)))
                .map(GateItem::from)
                .toList();

        String dashboardUrl = properties.baseUrl() + "/projects/" + project.getKey() + "/runs/" + run.getId();
        return new RunDetails(project.getKey(), RunView.from(run),
                new GateResponse(run.getId(), decision, blocking, warnings), views, dashboardUrl);
    }

    private static GateEvaluator.Outcome toOutcome(ResultView view) {
        return new GateEvaluator.Outcome(view.verdict(), view.quarantined(), view.passedAfterRetry());
    }
}
