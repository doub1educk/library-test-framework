package ru.study.flakyradar.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.InputStreamSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.study.flakyradar.analysis.DetectionPolicy;
import ru.study.flakyradar.analysis.FailureFingerprint;
import ru.study.flakyradar.analysis.FlakinessAssessment;
import ru.study.flakyradar.analysis.FlakinessDetector;
import ru.study.flakyradar.analysis.GateEvaluator;
import ru.study.flakyradar.analysis.HistoryPoint;
import ru.study.flakyradar.analysis.SignatureNormalizer;
import ru.study.flakyradar.analysis.Verdict;
import ru.study.flakyradar.analysis.VerdictCalculator;
import ru.study.flakyradar.config.FlakyRadarProperties;
import ru.study.flakyradar.history.FailureSignature;
import ru.study.flakyradar.history.FailureSignatureRepository;
import ru.study.flakyradar.history.TestCase;
import ru.study.flakyradar.history.TestCaseRepository;
import ru.study.flakyradar.history.TestHistoryQueries;
import ru.study.flakyradar.history.TestResult;
import ru.study.flakyradar.history.TestResultRepository;
import ru.study.flakyradar.history.TestRun;
import ru.study.flakyradar.history.TestRunRepository;
import ru.study.flakyradar.ingest.IngestExceptions.EmptyReportException;
import ru.study.flakyradar.ingest.IngestExceptions.NoReportsException;
import ru.study.flakyradar.project.Project;
import ru.study.flakyradar.project.ProjectService;
import ru.study.flakyradar.quarantine.QuarantineService;
import ru.study.flakyradar.report.JUnitXmlParser;
import ru.study.flakyradar.report.ParsedFailure;
import ru.study.flakyradar.report.ParsedReport;
import ru.study.flakyradar.report.ParsedTestCase;
import ru.study.flakyradar.report.TestStatus;
import ru.study.flakyradar.run.RunDtos.RunDetails;
import ru.study.flakyradar.run.RunQueryService;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class IngestService {
    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final ProjectService projectService;
    private final QuarantineService quarantineService;
    private final RunQueryService runQueryService;
    private final TestRunRepository runRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestResultRepository resultRepository;
    private final FailureSignatureRepository signatureRepository;
    private final TestHistoryQueries historyQueries;
    private final JUnitXmlParser parser;
    private final SignatureNormalizer normalizer;
    private final FlakinessDetector flakinessDetector;
    private final VerdictCalculator verdictCalculator;
    private final GateEvaluator gateEvaluator;
    private final DetectionPolicy policy;
    private final FlakyRadarProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public IngestService(ProjectService projectService, QuarantineService quarantineService,
                         RunQueryService runQueryService, TestRunRepository runRepository,
                         TestCaseRepository testCaseRepository, TestResultRepository resultRepository,
                         FailureSignatureRepository signatureRepository, TestHistoryQueries historyQueries,
                         JUnitXmlParser parser, SignatureNormalizer normalizer, FlakinessDetector flakinessDetector,
                         VerdictCalculator verdictCalculator, GateEvaluator gateEvaluator, DetectionPolicy policy,
                         FlakyRadarProperties properties, ApplicationEventPublisher events, Clock clock) {
        this.projectService = projectService;
        this.quarantineService = quarantineService;
        this.runQueryService = runQueryService;
        this.runRepository = runRepository;
        this.testCaseRepository = testCaseRepository;
        this.resultRepository = resultRepository;
        this.signatureRepository = signatureRepository;
        this.historyQueries = historyQueries;
        this.parser = parser;
        this.normalizer = normalizer;
        this.flakinessDetector = flakinessDetector;
        this.verdictCalculator = verdictCalculator;
        this.gateEvaluator = gateEvaluator;
        this.policy = policy;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    public record IngestResult(RunDetails details, boolean duplicate) {
    }

    @Transactional
    public IngestResult ingest(String projectKey, String apiKey, IngestRequest request,
                               List<? extends InputStreamSource> reports) {
        Project project = projectService.authenticate(projectKey, apiKey);

        if (request.ciRunId() != null) {
            var existing = runRepository.findByProjectIdAndCiRunIdAndAttempt(
                    project.getId(), request.ciRunId(), request.attempt());
            if (existing.isPresent()) {
                log.info("Повторная загрузка прогона ciRunId={} attempt={} проекта {}: возвращаем существующий",
                        request.ciRunId(), request.attempt(), projectKey);
                return new IngestResult(runQueryService.details(project, existing.get()), true);
            }
        }

        ParsedReport report = parseAll(reports);
        if (report.total() == 0) {
            throw new EmptyReportException();
        }

        Instant now = clock.instant();
        TestRun run = new TestRun(project.getId(), request.branch().strip(), request.commit().strip(),
                request.attempt(), request.ciRunId(), request.buildUrl(), now);
        run.setCounts(report.total(), report.count(TestStatus.PASSED), report.failedCount(),
                report.count(TestStatus.SKIPPED));
        runRepository.save(run);

        Map<String, TestCase> testCases = resolveTestCases(project, report, now);
        List<Long> testCaseIds = testCases.values().stream().map(TestCase::getId).toList();
        Map<Long, List<HistoryPoint>> history = historyQueries.lastResults(testCaseIds, policy.window());
        Set<Long> quarantined = quarantineService.activeTestCaseIds(project.getId(), testCaseIds);
        SignatureCache signatures = new SignatureCache(project.getId(), now);

        List<TestResult> results = new ArrayList<>(report.total());
        List<GateEvaluator.Outcome> outcomes = new ArrayList<>();
        for (ParsedTestCase parsed : report.testCases()) {
            TestCase testCase = testCases.get(parsed.fullName());
            List<HistoryPoint> prior = history.getOrDefault(testCase.getId(), List.of());
            boolean inQuarantine = quarantined.contains(testCase.getId());

            Verdict verdict = parsed.status().isFailure()
                    ? verdictCalculator.verdictForFailure(testCase.isFlaky(), prior, run.getBranch(), run.getCommitSha())
                    : null;
            FailureSignature signature = parsed.failure() != null && (parsed.status().isFailure() || parsed.passedAfterRetry())
                    ? signatures.resolve(parsed.failure(), parsed.suite())
                    : null;

            results.add(new TestResult(run, testCase, parsed.status(), parsed.durationMs(), parsed.failedAttempts(),
                    message(parsed.failure()), signature, verdict, inQuarantine));
            outcomes.add(new GateEvaluator.Outcome(verdict, inQuarantine, parsed.passedAfterRetry()));

            List<HistoryPoint> withCurrent = new ArrayList<>(prior);
            withCurrent.add(new HistoryPoint(run.getCommitSha(), run.getBranch(), parsed.status(), parsed.passedAfterRetry()));
            FlakinessAssessment assessment = flakinessDetector.assess(withCurrent);
            testCase.recordResult(parsed.status(), assessment.flaky(), assessment.flips(), now);
        }
        resultRepository.saveAll(results);
        run.setDecision(gateEvaluator.decide(outcomes));

        log.info("Принят прогон {} проекта {}: {} тестов, упало {}, решение {}",
                run.getId(), projectKey, run.getTotal(), run.getFailed(), run.getDecision());
        events.publishEvent(new RunIngestedEvent(project.getKey(), run.getId()));
        resultRepository.flush();
        return new IngestResult(runQueryService.details(project, run), false);
    }

    private ParsedReport parseAll(List<? extends InputStreamSource> reports) {
        if (reports == null || reports.isEmpty()) {
            throw new NoReportsException();
        }
        List<ParsedReport> parsed = new ArrayList<>(reports.size());
        for (InputStreamSource source : reports) {
            try (InputStream input = source.getInputStream()) {
                parsed.add(parser.parse(input));
            } catch (IOException e) {
                throw new UncheckedIOException("Не удалось прочитать файл отчёта", e);
            }
        }
        return ParsedReport.merge(parsed);
    }

    private Map<String, TestCase> resolveTestCases(Project project, ParsedReport report, Instant now) {
        List<String> names = report.testCases().stream().map(ParsedTestCase::fullName).toList();
        Map<String, TestCase> byName = new HashMap<>();
        for (int from = 0; from < names.size(); from += 1000) {
            testCaseRepository.findByProjectIdAndFullNameIn(project.getId(), names.subList(from, Math.min(names.size(), from + 1000)))
                    .forEach(tc -> byName.put(tc.getFullName(), tc));
        }
        List<TestCase> created = report.testCases().stream()
                .filter(tc -> !byName.containsKey(tc.fullName()))
                .map(tc -> new TestCase(project.getId(), tc.suite(), tc.name(), tc.fullName(), now))
                .toList();
        testCaseRepository.saveAll(created).forEach(tc -> byName.put(tc.getFullName(), tc));
        return byName;
    }

    private String message(ParsedFailure failure) {
        if (failure == null) {
            return null;
        }
        String text = failure.message().isBlank() ? failure.stackTrace() : failure.message();
        if (text.isBlank()) {
            text = failure.type();
        }
        int max = properties.ingest().maxMessageLength();
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private final class SignatureCache {
        private final Long projectId;
        private final Instant now;
        private final Map<String, FailureSignature> byHash = new HashMap<>();

        SignatureCache(Long projectId, Instant now) {
            this.projectId = projectId;
            this.now = now;
        }

        FailureSignature resolve(ParsedFailure failure, String testClass) {
            FailureFingerprint fingerprint = normalizer.fingerprint(
                    failure.type(), failure.message(), failure.stackTrace(), testClass);
            FailureSignature signature = byHash.computeIfAbsent(fingerprint.hash(), hash -> load(fingerprint, failure));
            signature.recordOccurrence(now);
            return signature;
        }

        private FailureSignature load(FailureFingerprint fingerprint, ParsedFailure failure) {
            var found = signatureRepository.findByProjectIdAndHash(projectId, fingerprint.hash());
            if (found.isPresent()) {
                return found.get();
            }
            String sample = failure.message().isBlank() ? failure.stackTrace() : failure.message();
            return signatureRepository.save(new FailureSignature(projectId, fingerprint.hash(),
                    emptyToNull(fingerprint.exceptionType()), fingerprint.normalizedText(), truncate(sample), now));
        }

        private String truncate(String text) {
            return text.length() <= 2000 ? text : text.substring(0, 2000) + "…";
        }

        private String emptyToNull(String value) {
            return value == null || value.isEmpty() ? null : value;
        }
    }
}
