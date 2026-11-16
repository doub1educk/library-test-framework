package ru.study.flakyradar.quarantine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface QuarantineRepository extends JpaRepository<Quarantine, Long> {

    @Query("""
            select q from Quarantine q join fetch q.testCase
            where q.projectId = :projectId and q.removedAt is null
              and (q.expiresAt is null or q.expiresAt > :now)
            order by q.createdAt desc
            """)
    List<Quarantine> findActive(@Param("projectId") Long projectId, @Param("now") Instant now);

    @Query("""
            select q.testCase.id from Quarantine q
            where q.projectId = :projectId and q.testCase.id in :testCaseIds and q.removedAt is null
              and (q.expiresAt is null or q.expiresAt > :now)
            """)
    Set<Long> findActiveTestCaseIds(@Param("projectId") Long projectId,
                                    @Param("testCaseIds") Collection<Long> testCaseIds,
                                    @Param("now") Instant now);

    /** Незакрытая запись (возможно, уже истёкшая): у теста она одна благодаря частичному уникальному индексу. */
    Optional<Quarantine> findByTestCaseIdAndRemovedAtIsNull(Long testCaseId);

    Optional<Quarantine> findByIdAndProjectId(Long id, Long projectId);
}
