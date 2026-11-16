package ru.study.flakyradar.quarantine;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.study.flakyradar.quarantine.QuarantineDtos.QuarantineRequest;
import ru.study.flakyradar.quarantine.QuarantineDtos.QuarantineResponse;

import java.util.List;

@Tag(name = "Карантин")
@RestController
@RequestMapping("/api/v1/projects/{key}/quarantine")
public class QuarantineController {

    private final QuarantineService quarantineService;

    public QuarantineController(QuarantineService quarantineService) {
        this.quarantineService = quarantineService;
    }

    @Operation(summary = "Отправить тест в карантин", description = "Падения теста в карантине не блокируют gate.",
            security = @SecurityRequirement(name = "apiKey"))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuarantineResponse add(@PathVariable String key,
                                  @RequestHeader(name = "X-Api-Key", required = false) String apiKey,
                                  @Valid @RequestBody QuarantineRequest request) {
        return quarantineService.add(key, apiKey, request);
    }

    @Operation(summary = "Активные записи карантина")
    @GetMapping
    public List<QuarantineResponse> findActive(@PathVariable String key) {
        return quarantineService.findActive(key);
    }

    @Operation(summary = "Снять тест с карантина", security = @SecurityRequirement(name = "apiKey"))
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String key,
                       @RequestHeader(name = "X-Api-Key", required = false) String apiKey,
                       @PathVariable long id) {
        quarantineService.remove(key, apiKey, id);
    }
}
