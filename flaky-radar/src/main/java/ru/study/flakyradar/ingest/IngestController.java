package ru.study.flakyradar.ingest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.study.flakyradar.ingest.IngestService.IngestResult;
import ru.study.flakyradar.run.RunDtos.RunDetails;

import java.util.List;

@Tag(name = "Приём отчётов")
@RestController
@RequestMapping("/api/v1/projects/{key}/runs")
public class IngestController {

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @Operation(summary = "Загрузить отчёт JUnit XML",
            description = """
                    multipart/form-data: один или несколько файлов в поле report и метаданные прогона.
                    Ответ 201 с деталями прогона и решением gate. Повторная загрузка с теми же ciRunId и attempt
                    возвращает уже сохранённый прогон с кодом 200.
                    Пример: curl -H "X-Api-Key: $KEY" -F report=@TEST-a.xml -F branch=main -F commit=$SHA -F attempt=1 ...""",
            security = @SecurityRequirement(name = "apiKey"))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RunDetails> upload(
            @PathVariable String key,
            @RequestHeader(name = "X-Api-Key", required = false) String apiKey,
            @RequestPart(name = "report", required = false) List<MultipartFile> reports,
            @Valid @ModelAttribute IngestRequest request) {
        IngestResult result = ingestService.ingest(key, apiKey, request, reports == null ? List.of() : reports);
        if (result.duplicate()) {
            return ResponseEntity.ok(result.details());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(result.details().run().id()).toUri())
                .body(result.details());
    }
}
