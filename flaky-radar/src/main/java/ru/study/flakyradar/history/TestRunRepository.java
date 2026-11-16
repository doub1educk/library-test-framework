package ru.study.flakyradar.history;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TestRunRepository extends JpaRepository<TestRun, Long> {

    Optional<TestRun> findByProjectIdAndCiRunIdAndAttempt(Long projectId, String ciRunId, int attempt);

    Optional<TestRun> findByIdAndProjectId(Long id, Long projectId);

    List<TestRun> findByProjectIdOrderByIdDesc(Long projectId, Pageable pageable);

    List<TestRun> findByProjectIdAndBranchOrderByIdDesc(Long projectId, String branch, Pageable pageable);

    Optional<TestRun> findFirstByProjectIdOrderByIdDesc(Long projectId);

    long countByProjectId(Long projectId);
}
