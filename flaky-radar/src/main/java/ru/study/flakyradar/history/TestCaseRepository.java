package ru.study.flakyradar.history;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByProjectIdAndFullNameIn(Long projectId, Collection<String> fullNames);

    Optional<TestCase> findByProjectIdAndFullName(Long projectId, String fullName);

    Optional<TestCase> findByIdAndProjectId(Long id, Long projectId);

    List<TestCase> findByProjectIdAndFlakyTrueOrderByFlipCountDescFullNameAsc(Long projectId);

    List<TestCase> findByProjectIdOrderByFullNameAsc(Long projectId, Pageable pageable);

    long countByProjectId(Long projectId);

    long countByProjectIdAndFlakyTrue(Long projectId);
}
