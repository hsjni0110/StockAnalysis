package com.stockdelta.common.repository;

import com.stockdelta.common.entity.XbrlMetric;
import com.stockdelta.common.entity.XbrlMetric.XbrlMetricId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface XbrlMetricRepository extends JpaRepository<XbrlMetric, XbrlMetricId> {

    @Query("SELECT xm FROM XbrlMetric xm WHERE xm.id.filingId = :filingId")
    List<XbrlMetric> findByFilingId(@Param("filingId") Long filingId);

    @Query("SELECT xm FROM XbrlMetric xm WHERE xm.id.filingId = :filingId AND xm.id.basis = :basis")
    List<XbrlMetric> findByFilingIdAndBasis(@Param("filingId") Long filingId,
                                              @Param("basis") String basis);

    @Query("SELECT xm FROM XbrlMetric xm WHERE xm.id.filingId = :filingId AND xm.id.metric = :metric")
    List<XbrlMetric> findByFilingIdAndMetric(@Param("filingId") Long filingId,
                                               @Param("metric") String metric);

    @Query("DELETE FROM XbrlMetric xm WHERE xm.id.filingId = :filingId")
    void deleteByFilingId(@Param("filingId") Long filingId);
}
