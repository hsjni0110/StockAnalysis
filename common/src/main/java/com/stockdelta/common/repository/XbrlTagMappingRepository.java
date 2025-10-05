package com.stockdelta.common.repository;

import com.stockdelta.common.entity.XbrlTagMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface XbrlTagMappingRepository extends JpaRepository<XbrlTagMapping, Long> {

    /**
     * Find mapping by source tag and taxonomy
     */
    Optional<XbrlTagMapping> findBySourceTagAndTaxonomy(String sourceTag, String taxonomy);

    /**
     * Find all mappings for a fundamental concept
     */
    List<XbrlTagMapping> findByFundamentalConcept(String fundamentalConcept);

    /**
     * Find all mappings for a taxonomy
     */
    List<XbrlTagMapping> findByTaxonomy(String taxonomy);

    /**
     * Find mappings with confidence score above threshold
     */
    @Query("SELECT xtm FROM XbrlTagMapping xtm WHERE xtm.confidenceScore >= :minScore")
    List<XbrlTagMapping> findByMinConfidenceScore(@Param("minScore") java.math.BigDecimal minScore);

    /**
     * Find mappings by rule source
     */
    List<XbrlTagMapping> findByRuleSource(String ruleSource);

    /**
     * Find all distinct fundamental concepts
     */
    @Query("SELECT DISTINCT xtm.fundamentalConcept FROM XbrlTagMapping xtm")
    List<String> findDistinctFundamentalConcepts();

    /**
     * Count mappings for a fundamental concept
     */
    long countByFundamentalConcept(String fundamentalConcept);

    /**
     * Check if mapping exists for source tag and taxonomy
     */
    boolean existsBySourceTagAndTaxonomy(String sourceTag, String taxonomy);
}
