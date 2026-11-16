package ru.study.flakyradar.run;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.study.flakyradar.run.RunDtos.GateResponse;
import ru.study.flakyradar.run.RunDtos.RunDetails;
import ru.study.flakyradar.run.RunDtos.RunView;

import java.util.List;

@Tag(name = "Прогоны")
@RestController
@RequestMapping("/api/v1/projects/{key}/runs")
public class RunController {

    static final String MARKDOWN = "text/markdown;charset=UTF-8";

    private final RunQueryService runQueryService;
    private final SummaryRenderer summaryRenderer;

    public RunController(RunQueryService runQueryService, SummaryRenderer summaryRenderer) {
        this.runQueryService = runQueryService;
        this.summaryRenderer = summaryRenderer;
    }

    @Operation(summary = "Последние прогоны проекта")
    @GetMapping
    public List<RunView> list(@PathVariable String key,
                              @RequestParam(required = false) String branch,
                              @RequestParam(defaultValue = "20") int limit) {
        return runQueryService.list(key, branch, limit);
    }

    @Operation(summary = "Прогон: счётчики, решение gate, упавшие тесты с вердиктами")
    @GetMapping("/{runId}")
    public RunDetails details(@PathVariable String key, @PathVariable long runId) {
        return runQueryService.details(key, runId);
    }

    @Operation(summary = "Решение для CI",
            description = "Всегда 200. CI проверяет поле decision: PASS и WARN пропускают сборку, FAIL блокирует.")
    @GetMapping("/{runId}/gate")
    public GateResponse gate(@PathVariable String key, @PathVariable long runId) {
        return runQueryService.gate(key, runId);
    }

    @Operation(summary = "Сводка в markdown (для комментария в PR и Job Summary)")
    @GetMapping(value = "/{runId}/summary.md", produces = MARKDOWN)
    public String summary(@PathVariable String key, @PathVariable long runId) {
        return summaryRenderer.markdown(runQueryService.details(key, runId));
    }
}
