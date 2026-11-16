package ru.study.flakyradar.testcase;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.study.flakyradar.analysis.DetectionPolicy;
import ru.study.flakyradar.history.FailureSignature;
import ru.study.flakyradar.history.FailureSignatureRepository;
import ru.study.flakyradar.history.TestCase;
import ru.study.flakyradar.history.TestCaseRepository;
import ru.study.flakyradar.history.TestResultRepository;
import ru.study.flakyradar.project.Project;
import ru.study.flakyradar.project.ProjectService;
import ru.study.flakyradar.quarantine.QuarantineService;
import ru.study.flakyradar.testcase.TestCaseDtos.HistoryEntry;
import ru.study.flakyradar.testcase.TestCaseDtos.SignatureDetails;
import ru.study.flakyradar.testcase.TestCaseDtos.SignatureOccurrence;
import ru.study.flakyradar.testcase.TestCaseDtos.SignatureView;
import ru.study.flakyradar.testcase.TestCaseDtos.TestCaseView;
import ru.study.flakyradar.testcase.TestCaseDtos.TestHistoryView;
import ru.study.flakyradar.history.TestCaseExceptions.SignatureNotFoundException;
import ru.study.flakyradar.history.TestCaseExceptions.TestCaseNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class TestCaseQueryService {
    private static final int MAX_LIMIT = 500;

    private final ProjectService projectService;
    private final TestCaseRepository testCaseRepository;
    private final TestResultRepository resultRepository;
    private final FailureSignatureRepository signatureRepository;
    private final QuarantineService quarantineService;
    private final DetectionPolicy policy;

    public TestCaseQueryService(ProjectService projectService, TestCaseRepository testCaseRepository,
                                TestResultRepository resultRepository, FailureSignatureRepository signatureRepository,
                                QuarantineService quarantineService, DetectionPolicy policy) {
        this.projectService = projectService;
        this.testCaseRepository = testCaseRepository;
        this.resultRepository = resultRepository;
        this.signatureRepository = signatureRepository;
        this.quarantineService = quarantineService;
        this.policy = policy;
    }

    public List<TestCaseView> flaky(String projectKey) {
        Project project = projectService.require(projectKey);
        return withQuarantine(project, testCaseRepository.findByProjectIdAndFlakyTrueOrderByFlipCountDescFullNameAsc(project.getId()));
    }

    public List<TestCaseView> all(String projectKey, int limit) {
        Project project = projectService.require(projectKey);
        return withQuarantine(project, testCaseRepository.findByProjectIdOrderByFullNameAsc(
                project.getId(), PageRequest.of(0, Math.clamp(limit, 1, MAX_LIMIT))));
    }

    public TestHistoryView history(String projectKey, long testCaseId) {
        Project project = projectService.require(projectKey);
        TestCase testCase = testCaseRepository.findByIdAndProjectId(testCaseId, project.getId())
                .orElseThrow(() -> new TestCaseNotFoundException(testCaseId));
        List<HistoryEntry> results = resultRepository.findHistory(testCaseId, PageRequest.of(0, policy.window())).stream()
                .map(HistoryEntry::from)
                .toList();
        boolean quarantined = !quarantineService.activeTestCaseIds(project.getId(), List.of(testCaseId)).isEmpty();
        return new TestHistoryView(TestCaseView.from(testCase, quarantined), results);
    }

    public List<SignatureView> signatures(String projectKey, int limit) {
        Project project = projectService.require(projectKey);
        List<FailureSignature> signatures = signatureRepository.findByProjectIdOrderByLastSeenDescOccurrencesDesc(
                project.getId(), PageRequest.of(0, Math.clamp(limit, 1, MAX_LIMIT)));
        if (signatures.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> affected = signatureRepository.countAffectedTests(
                        signatures.stream().map(FailureSignature::getId).toList()).stream()
                .collect(Collectors.toMap(FailureSignatureRepository.AffectedTests::getSignatureId,
                        FailureSignatureRepository.AffectedTests::getTests));
        return signatures.stream()
                .map(s -> SignatureView.from(s, affected.getOrDefault(s.getId(), 0L)))
                .toList();
    }

    public SignatureDetails signature(String projectKey, long signatureId) {
        Project project = projectService.require(projectKey);
        FailureSignature signature = signatureRepository.findByIdAndProjectId(signatureId, project.getId())
                .orElseThrow(() -> new SignatureNotFoundException(signatureId));
        List<SignatureOccurrence> occurrences = resultRepository.findBySignature(signatureId, PageRequest.of(0, 50)).stream()
                .map(SignatureOccurrence::from)
                .toList();
        long affected = occurrences.stream().map(SignatureOccurrence::testCaseId).distinct().count();
        return new SignatureDetails(SignatureView.from(signature, affected), occurrences);
    }

    private List<TestCaseView> withQuarantine(Project project, List<TestCase> testCases) {
        Set<Long> quarantined = quarantineService.activeTestCaseIds(project.getId(),
                testCases.stream().map(TestCase::getId).toList());
        return testCases.stream()
                .map(tc -> TestCaseView.from(tc, quarantined.contains(tc.getId())))
                .toList();
    }
}
