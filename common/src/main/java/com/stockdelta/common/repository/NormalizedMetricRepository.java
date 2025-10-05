package com.stockdelta.common.repository;

import com.stockdelta.common.entity.NormalizedMetric;
import com.stockdelta.common.entity.NormalizedMetric.NormalizedMetricId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NormalizedMetricRepository extends JpaRepository<NormalizedMetric, NormalizedMetricId> {

    /**
     * Find all metrics for a specific filing
     */
    @Query("SELECT nm FROM NormalizedMetric nm WHERE nm.id.filingId = :filingId")
    List<NormalizedMetric> findByFilingId(@Param("filingId") Long filingId);

    /**
     * Find metrics by filing ID and basis (QoQ, YoY, Abs)
     */
    @Query("SELECT nm FROM NormalizedMetric nm WHERE nm.id.filingId = :filingId AND nm.id.basis = :basis")
    List<NormalizedMetric> findByFilingIdAndBasis(@Param("filingId") Long filingId, @Param("basis") String basis);

    /**
     * Find a specific metric by filing ID, metric name, and basis
     */
    Optional<NormalizedMetric> findById(NormalizedMetricId id);

    /**
     * Find all QoQ metrics for a filing
     */
    @Query("SELECT nm FROM NormalizedMetric nm WHERE nm.id.filingId = :filingId AND nm.id.basis = 'QoQ'")
    List<NormalizedMetric> findQoQMetrics(@Param("filingId") Long filingId);

    /**
     * Find all YoY metrics for a filing
     */
    @Query("SELECT nm FROM NormalizedMetric nm WHERE nm.id.filingId = :filingId AND nm.id.basis = 'YoY'")
    List<NormalizedMetric> findYoYMetrics(@Param("filingId") Long filingId);

    /**
     * Find all absolute value metrics for a filing
     */
    @Query("SELECT nm FROM NormalizedMetric nm WHERE nm.id.filingId = :filingId AND nm.id.basis = 'Abs'")
    List<NormalizedMetric> findAbsoluteMetrics(@Param("filingId") Long filingId);

    /**
     * Delete all metrics for a filing
     */
    @Query("DELETE FROM NormalizedMetric nm WHERE nm.id.filingId = :filingId")
    void deleteByFilingId(@Param("filingId") Long filingId);

    /**
     * Check if metrics exist for a filing
     */
    @Query("SELECT CASE WHEN COUNT(nm) > 0 THEN true ELSE false END FROM NormalizedMetric nm WHERE nm.id.filingId = :filingId")
    boolean existsByFilingId(@Param("filingId") Long filingId);

    /**
     * Count metrics by filing ID
     */
    @Query("SELECT COUNT(nm) FROM NormalizedMetric nm WHERE nm.id.filingId = :filingId")
    long countByFilingId(@Param("filingId") Long filingId);

    /**
     * Find distinct metric names for a filing
     */
    @Query("SELECT DISTINCT nm.id.metric FROM NormalizedMetric nm WHERE nm.id.filingId = :filingId")
    List<String> findDistinctMetricsByFilingId(@Param("filingId") Long filingId);
}
