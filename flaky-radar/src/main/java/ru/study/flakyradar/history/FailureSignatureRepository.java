package ru.study.flakyradar.history;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FailureSignatureRepository extends JpaRepository<FailureSignature, Long> {

    Optional<FailureSignature> findByProjectIdAndHash(Long projectId, String hash);

    Optional<FailureSignature> findByIdAndProjectId(Long id, Long projectId);

    List<FailureSignature> findByProjectIdOrderByLastSeenDescOccurrencesDesc(Long projectId, Pageable pageable);

    /** Сколько разных тестов падало с каждой из подписей. */
    @Query("""
            select r.signature.id as signatureId, count(distinct r.testCase.id) as tests
            from TestResult r
            where r.signature.id in :ids
            group by r.signature.id
            """)
    List<AffectedTests> countAffectedTests(@Param("ids") Collection<Long> signatureIds);

    interface AffectedTests {
        Long getSignatureId();

        long getTests();
    }
}
