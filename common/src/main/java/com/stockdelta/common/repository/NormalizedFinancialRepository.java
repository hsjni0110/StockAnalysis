package com.stockdelta.common.repository;

import com.stockdelta.common.entity.NormalizedFinancial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NormalizedFinancialRepository extends JpaRepository<NormalizedFinancial, Long> {

    /**
     * Find all normalized financials for a specific filing
     */
    List<NormalizedFinancial> findByFilingId(Long filingId);

    /**
     * Find normalized financials by filing ID and concept
     */
    List<NormalizedFinancial> findByFilingIdAndConcept(Long filingId, String concept);

    /**
     * Find all normalized financials for a specific concept across all filings
     */
    List<NormalizedFinancial> findByConcept(String concept);

    /**
     * Find normalized financials with quality score above a threshold
     */
    @Query("SELECT nf FROM NormalizedFinancial nf WHERE nf.filingId = :filingId AND nf.qualityScore >= :minScore")
    List<NormalizedFinancial> findByFilingIdWithMinQuality(@Param("filingId") Long filingId,
                                                            @Param("minScore") java.math.BigDecimal minScore);

    /**
     * Find latest value for a concept by filing ID
     */
    @Query("SELECT nf FROM NormalizedFinancial nf WHERE nf.filingId = :filingId AND nf.concept = :concept " +
           "ORDER BY nf.endDate DESC, nf.qualityScore DESC LIMIT 1")
    Optional<NormalizedFinancial> findLatestByFilingIdAndConcept(@Param("filingId") Long filingId,
                                                                  @Param("concept") String concept);

    /**
     * Count normalized financials by filing ID
     */
    long countByFilingId(Long filingId);

    /**
     * Delete all normalized financials for a filing
     */
    void deleteByFilingId(Long filingId);

    /**
     * Find distinct concepts for a filing
     */
    @Query("SELECT DISTINCT nf.concept FROM NormalizedFinancial nf WHERE nf.filingId = :filingId")
    List<String> findDistinctConceptsByFilingId(@Param("filingId") Long filingId);

    /**
     * Check if normalized financials exist for a filing
     */
    boolean existsByFilingId(Long filingId);
}
