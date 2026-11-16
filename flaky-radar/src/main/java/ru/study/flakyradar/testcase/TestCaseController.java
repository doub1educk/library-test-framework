package ru.study.flakyradar.testcase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.study.flakyradar.testcase.TestCaseDtos.SignatureDetails;
import ru.study.flakyradar.testcase.TestCaseDtos.SignatureView;
import ru.study.flakyradar.testcase.TestCaseDtos.TestCaseView;
import ru.study.flakyradar.testcase.TestCaseDtos.TestHistoryView;

import java.util.List;

@Tag(name = "Тесты и ошибки")
@RestController
@RequestMapping("/api/v1/projects/{key}")
public class TestCaseController {

    private final TestCaseQueryService queryService;

    public TestCaseController(TestCaseQueryService queryService) {
        this.queryService = queryService;
    }

    @Operation(summary = "Тесты проекта", description = "С flaky=true только нестабильные, отсортированные по числу переключений.")
    @GetMapping("/tests")
    public List<TestCaseView> tests(@PathVariable String key,
                                    @RequestParam(defaultValue = "false") boolean flaky,
                                    @RequestParam(defaultValue = "100") int limit) {
        return flaky ? queryService.flaky(key) : queryService.all(key, limit);
    }

    @Operation(summary = "История теста (последние результаты, самые новые первыми)")
    @GetMapping("/tests/{testId}/history")
    public TestHistoryView history(@PathVariable String key, @PathVariable long testId) {
        return queryService.history(key, testId);
    }

    @Operation(summary = "Группы ошибок (подписи)")
    @GetMapping("/signatures")
    public List<SignatureView> signatures(@PathVariable String key, @RequestParam(defaultValue = "20") int limit) {
        return queryService.signatures(key, limit);
    }

    @Operation(summary = "Группа ошибок: где и когда встречалась")
    @GetMapping("/signatures/{signatureId}")
    public SignatureDetails signature(@PathVariable String key, @PathVariable long signatureId) {
        return queryService.signature(key, signatureId);
    }
}
