package ru.study.flakyradar.project;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.study.flakyradar.config.FlakyRadarProperties;
import ru.study.flakyradar.project.ProjectDtos.CreateProjectRequest;
import ru.study.flakyradar.project.ProjectDtos.ProjectCredentials;
import ru.study.flakyradar.project.ProjectDtos.ProjectResponse;
import ru.study.flakyradar.project.ProjectExceptions.InvalidAdminTokenException;
import ru.study.flakyradar.project.ProjectExceptions.InvalidApiKeyException;
import ru.study.flakyradar.project.ProjectExceptions.ProjectAlreadyExistsException;
import ru.study.flakyradar.project.ProjectExceptions.ProjectNotFoundException;

import java.time.Clock;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository repository;
    private final FlakyRadarProperties properties;
    private final Clock clock;

    public ProjectService(ProjectRepository repository, FlakyRadarProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ProjectCredentials create(CreateProjectRequest request, String adminToken) {
        requireAdmin(adminToken);
        if (repository.existsByKey(request.key())) {
            throw new ProjectAlreadyExistsException(request.key());
        }
        String apiKey = ApiKeys.generate();
        Project project = repository.save(new Project(request.key(), request.name().strip(), ApiKeys.hash(apiKey), clock.instant()));
        return new ProjectCredentials(project.getKey(), project.getName(), apiKey);
    }

    /** Перевыпуск ключа: старый сразу перестаёт работать. */
    @Transactional
    public ProjectCredentials rotateApiKey(String key, String adminToken) {
        requireAdmin(adminToken);
        Project project = require(key);
        String apiKey = ApiKeys.generate();
        project.setApiKeyHash(ApiKeys.hash(apiKey));
        return new ProjectCredentials(project.getKey(), project.getName(), apiKey);
    }

    public List<ProjectResponse> findAll() {
        return repository.findAll(Sort.by("key")).stream().map(ProjectResponse::from).toList();
    }

    public ProjectResponse get(String key) {
        return ProjectResponse.from(require(key));
    }

    /** Проект по ключу или 404. Для внутреннего использования другими сервисами. */
    public Project require(String key) {
        return repository.findByKey(key).orElseThrow(() -> new ProjectNotFoundException(key));
    }

    /** Проект, если API-ключ подходит. Иначе 404 (нет проекта) или 401 (неверный ключ). */
    public Project authenticate(String key, String apiKey) {
        Project project = require(key);
        if (!ApiKeys.matches(apiKey, project.getApiKeyHash())) {
            throw new InvalidApiKeyException();
        }
        return project;
    }

    private void requireAdmin(String adminToken) {
        if (!ApiKeys.secretEquals(adminToken, properties.adminToken())) {
            throw new InvalidAdminTokenException();
        }
    }
}
