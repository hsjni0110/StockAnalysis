package com.stockdelta.common.repository;

import com.stockdelta.common.entity.FilingDelta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FilingDeltaRepository extends JpaRepository<FilingDelta, Long> {

    List<FilingDelta> findByFilingId(Long filingId);

    List<FilingDelta> findByFilingIdAndSection(Long filingId, String section);

    @Query("SELECT fd FROM FilingDelta fd WHERE fd.filingId = :filingId AND fd.section = :section ORDER BY fd.score DESC")
    List<FilingDelta> findByFilingIdAndSectionOrderByScoreDesc(@Param("filingId") Long filingId,
                                                                 @Param("section") String section);

    @Query("SELECT fd FROM FilingDelta fd WHERE fd.filingId = :filingId ORDER BY fd.score DESC")
    List<FilingDelta> findByFilingIdOrderByScoreDesc(@Param("filingId") Long filingId);

    void deleteByFilingId(Long filingId);

    long countByFilingId(Long filingId);
}
