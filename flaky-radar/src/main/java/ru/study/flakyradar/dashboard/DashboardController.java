package ru.study.flakyradar.dashboard;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import ru.study.flakyradar.history.TestCaseExceptions.SignatureNotFoundException;
import ru.study.flakyradar.history.TestCaseExceptions.TestCaseNotFoundException;
import ru.study.flakyradar.project.ProjectDtos.ProjectResponse;
import ru.study.flakyradar.project.ProjectExceptions.ProjectNotFoundException;
import ru.study.flakyradar.quarantine.QuarantineService;
import ru.study.flakyradar.run.RunDtos.RunView;
import ru.study.flakyradar.run.RunExceptions.RunNotFoundException;
import ru.study.flakyradar.run.RunQueryService;
import ru.study.flakyradar.run.SummaryRenderer;
import ru.study.flakyradar.project.ProjectService;
import ru.study.flakyradar.testcase.TestCaseQueryService;

import java.util.List;

@Controller
public class DashboardController {
    private final ProjectService projectService;
    private final RunQueryService runQueryService;
    private final TestCaseQueryService testCaseQueryService;
    private final QuarantineService quarantineService;
    private final SummaryRenderer summaryRenderer;

    public DashboardController(ProjectService projectService, RunQueryService runQueryService,
                               TestCaseQueryService testCaseQueryService, QuarantineService quarantineService,
                               SummaryRenderer summaryRenderer) {
        this.projectService = projectService;
        this.runQueryService = runQueryService;
        this.testCaseQueryService = testCaseQueryService;
        this.quarantineService = quarantineService;
        this.summaryRenderer = summaryRenderer;
    }

    public record ProjectRow(ProjectResponse project, RunView lastRun, int flakyTests) {
    }

    @GetMapping("/")
    public String index(Model model) {
        List<ProjectRow> rows = projectService.findAll().stream()
                .map(p -> new ProjectRow(p,
                        runQueryService.list(p.key(), null, 1).stream().findFirst().orElse(null),
                        testCaseQueryService.flaky(p.key()).size()))
                .toList();
        model.addAttribute("projects", rows);
        return "index";
    }

    @GetMapping("/projects/{key}")
    public String project(@PathVariable String key, @RequestParam(required = false) String branch, Model model) {
        model.addAttribute("project", projectService.get(key));
        model.addAttribute("branch", branch);
        model.addAttribute("runs", runQueryService.list(key, branch, 30));
        model.addAttribute("flaky", testCaseQueryService.flaky(key));
        model.addAttribute("signatures", testCaseQueryService.signatures(key, 10));
        model.addAttribute("quarantine", quarantineService.findActive(key));
        return "project";
    }

    @GetMapping("/projects/{key}/runs/{runId}")
    public String run(@PathVariable String key, @PathVariable long runId, Model model) {
        var details = runQueryService.details(key, runId);
        model.addAttribute("project", projectService.get(key));
        model.addAttribute("details", details);
        model.addAttribute("markdown", summaryRenderer.markdown(details));
        return "run";
    }

    @GetMapping("/projects/{key}/tests/{testId}")
    public String test(@PathVariable String key, @PathVariable long testId, Model model) {
        model.addAttribute("project", projectService.get(key));
        var history = testCaseQueryService.history(key, testId);
        model.addAttribute("history", history);
        model.addAttribute("timeline", history.results().reversed());
        return "test";
    }

    @GetMapping("/projects/{key}/signatures/{signatureId}")
    public String signature(@PathVariable String key, @PathVariable long signatureId, Model model) {
        model.addAttribute("project", projectService.get(key));
        model.addAttribute("details", testCaseQueryService.signature(key, signatureId));
        return "signature";
    }

    @ExceptionHandler({ProjectNotFoundException.class, RunNotFoundException.class, TestCaseNotFoundException.class,
            SignatureNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(RuntimeException e, Model model) {
        model.addAttribute("message", e.getMessage());
        return "not-found";
    }
}
