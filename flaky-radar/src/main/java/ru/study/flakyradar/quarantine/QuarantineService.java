package ru.study.flakyradar.quarantine;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.study.flakyradar.history.TestCase;
import ru.study.flakyradar.history.TestCaseRepository;
import ru.study.flakyradar.project.Project;
import ru.study.flakyradar.project.ProjectService;
import ru.study.flakyradar.quarantine.QuarantineDtos.QuarantineRequest;
import ru.study.flakyradar.quarantine.QuarantineDtos.QuarantineResponse;
import ru.study.flakyradar.quarantine.QuarantineExceptions.AlreadyQuarantinedException;
import ru.study.flakyradar.quarantine.QuarantineExceptions.QuarantineNotFoundException;
import ru.study.flakyradar.history.TestCaseExceptions.TestCaseNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class QuarantineService {

    private final QuarantineRepository repository;
    private final TestCaseRepository testCaseRepository;
    private final ProjectService projectService;
    private final Clock clock;

    public QuarantineService(QuarantineRepository repository, TestCaseRepository testCaseRepository,
                             ProjectService projectService, Clock clock) {
        this.repository = repository;
        this.testCaseRepository = testCaseRepository;
        this.projectService = projectService;
        this.clock = clock;
    }

    @Transactional
    public QuarantineResponse add(String projectKey, String apiKey, QuarantineRequest request) {
        Project project = projectService.authenticate(projectKey, apiKey);
        TestCase testCase = testCaseRepository.findByProjectIdAndFullName(project.getId(), request.testName())
                .orElseThrow(() -> new TestCaseNotFoundException(request.testName()));
        Instant now = clock.instant();

        repository.findByTestCaseIdAndRemovedAtIsNull(testCase.getId()).ifPresent(existing -> {
            if (existing.isActiveAt(now)) {
                throw new AlreadyQuarantinedException(testCase.getFullName());
            }
            // срок истёк, но запись не закрыта: закрываем, чтобы не мешала уникальному индексу
            existing.remove(now);
            repository.flush();
        });

        Quarantine saved = repository.save(new Quarantine(project.getId(), testCase, request.reason().strip(),
                request.createdBy(), now, request.expiresAt()));
        return QuarantineResponse.from(saved);
    }

    @Transactional
    public void remove(String projectKey, String apiKey, long id) {
        Project project = projectService.authenticate(projectKey, apiKey);
        Quarantine quarantine = repository.findByIdAndProjectId(id, project.getId())
                .filter(q -> q.getRemovedAt() == null)
                .orElseThrow(() -> new QuarantineNotFoundException(id));
        quarantine.remove(clock.instant());
    }

    public List<QuarantineResponse> findActive(String projectKey) {
        Project project = projectService.require(projectKey);
        return repository.findActive(project.getId(), clock.instant()).stream()
                .map(QuarantineResponse::from)
                .toList();
    }

    /** Какие из тестов сейчас в карантине. Для приёма отчётов и gate. */
    public Set<Long> activeTestCaseIds(Long projectId, Collection<Long> testCaseIds) {
        if (testCaseIds.isEmpty()) {
            return Set.of();
        }
        return repository.findActiveTestCaseIds(projectId, testCaseIds, clock.instant());
    }
}
