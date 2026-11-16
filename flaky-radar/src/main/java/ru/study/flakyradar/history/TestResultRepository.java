package ru.study.flakyradar.history;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TestResultRepository extends JpaRepository<TestResult, Long> {
    @Query("""
            select r from TestResult r
              join fetch r.testCase
              left join fetch r.signature
            where r.run.id = :runId
              and (r.status in (ru.study.flakyradar.report.TestStatus.FAILED, ru.study.flakyradar.report.TestStatus.ERROR)
                   or r.failedAttempts > 0)
            order by r.testCase.fullName
            """)
    List<TestResult> findProblemsByRunId(@Param("runId") Long runId);

    @Query("""
            select r from TestResult r
              join fetch r.run
              left join fetch r.signature
            where r.testCase.id = :testCaseId
            order by r.run.id desc
            """)
    List<TestResult> findHistory(@Param("testCaseId") Long testCaseId, Pageable pageable);

    @Query("""
            select r from TestResult r
              join fetch r.run
              join fetch r.testCase
            where r.signature.id = :signatureId
            order by r.run.id desc
            """)
    List<TestResult> findBySignature(@Param("signatureId") Long signatureId, Pageable pageable);
}
