package ru.study.flakyradar.project;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.study.flakyradar.project.ProjectDtos.CreateProjectRequest;
import ru.study.flakyradar.project.ProjectDtos.ProjectCredentials;
import ru.study.flakyradar.project.ProjectDtos.ProjectResponse;

import java.util.List;

@Tag(name = "Проекты")
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    static final String ADMIN_HEADER = "X-Admin-Token";

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Operation(summary = "Создать проект", description = "Возвращает API-ключ для CI. Ключ показывается один раз.",
            security = @SecurityRequirement(name = "adminToken"))
    @PostMapping
    public ResponseEntity<ProjectCredentials> create(
            @RequestHeader(name = ADMIN_HEADER, required = false) String adminToken,
            @Valid @RequestBody CreateProjectRequest request) {
        ProjectCredentials created = projectService.create(request, adminToken);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{key}").buildAndExpand(created.key()).toUri())
                .body(created);
    }

    @Operation(summary = "Перевыпустить API-ключ проекта", security = @SecurityRequirement(name = "adminToken"))
    @PostMapping("/{key}/api-key")
    public ProjectCredentials rotateApiKey(
            @RequestHeader(name = ADMIN_HEADER, required = false) String adminToken,
            @PathVariable String key) {
        return projectService.rotateApiKey(key, adminToken);
    }

    @Operation(summary = "Список проектов")
    @GetMapping
    public List<ProjectResponse> findAll() {
        return projectService.findAll();
    }

    @Operation(summary = "Проект по ключу")
    @GetMapping("/{key}")
    public ProjectResponse get(@PathVariable String key) {
        return projectService.get(key);
    }
}
