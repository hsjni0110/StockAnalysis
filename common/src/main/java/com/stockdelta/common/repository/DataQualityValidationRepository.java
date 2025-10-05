package com.stockdelta.common.repository;

import com.stockdelta.common.entity.DataQualityValidation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DataQualityValidationRepository extends JpaRepository<DataQualityValidation, Long> {

    /**
     * Find all validations for a specific filing
     */
    List<DataQualityValidation> findByFilingId(Long filingId);

    /**
     * Find validations by filing ID and severity
     */
    List<DataQualityValidation> findByFilingIdAndSeverity(Long filingId, String severity);

    /**
     * Find errors for a filing
     */
    @Query("SELECT dqv FROM DataQualityValidation dqv WHERE dqv.filingId = :filingId AND dqv.severity = 'error'")
    List<DataQualityValidation> findErrorsByFilingId(@Param("filingId") Long filingId);

    /**
     * Find warnings for a filing
     */
    @Query("SELECT dqv FROM DataQualityValidation dqv WHERE dqv.filingId = :filingId AND dqv.severity = 'warning'")
    List<DataQualityValidation> findWarningsByFilingId(@Param("filingId") Long filingId);

    /**
     * Count errors for a filing
     */
    @Query("SELECT COUNT(dqv) FROM DataQualityValidation dqv WHERE dqv.filingId = :filingId AND dqv.severity = 'error'")
    long countErrorsByFilingId(@Param("filingId") Long filingId);

    /**
     * Count warnings for a filing
     */
    @Query("SELECT COUNT(dqv) FROM DataQualityValidation dqv WHERE dqv.filingId = :filingId AND dqv.severity = 'warning'")
    long countWarningsByFilingId(@Param("filingId") Long filingId);

    /**
     * Check if filing has errors
     */
    @Query("SELECT CASE WHEN COUNT(dqv) > 0 THEN true ELSE false END FROM DataQualityValidation dqv " +
           "WHERE dqv.filingId = :filingId AND dqv.severity = 'error'")
    boolean hasErrors(@Param("filingId") Long filingId);

    /**
     * Delete all validations for a filing
     */
    void deleteByFilingId(Long filingId);

    /**
     * Find validations by rule ID
     */
    List<DataQualityValidation> findByRuleId(String ruleId);

    /**
     * Find distinct rule IDs for a filing
     */
    @Query("SELECT DISTINCT dqv.ruleId FROM DataQualityValidation dqv WHERE dqv.filingId = :filingId")
    List<String> findDistinctRuleIdsByFilingId(@Param("filingId") Long filingId);
}
