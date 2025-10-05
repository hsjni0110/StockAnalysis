package com.stockdelta.common.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Normalized metrics entity for storing calculated QoQ, YoY changes
 * Based on FAC concepts from normalized_financials table
 */
@Entity
@Table(name = "normalized_metrics",
       indexes = {
           @Index(name = "idx_normalized_metrics_filing", columnList = "filing_id")
       })
public class NormalizedMetric {

    @EmbeddedId
    private NormalizedMetricId id;

    @Column(precision = 20, scale = 2)
    private BigDecimal value;

    @Column(name = "quality_score", precision = 3, scale = 2)
    private BigDecimal qualityScore;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filing_id", insertable = false, updatable = false)
    @JsonIgnore
    private Filing filing;

    @Embeddable
    public static class NormalizedMetricId implements java.io.Serializable {
        @Column(name = "filing_id")
        private Long filingId;

        @NotNull
        @Size(max = 100)
        private String metric;  // FAC concept name

        @NotNull
        @Size(max = 10)
        private String basis;  // QoQ, YoY, Abs

        public NormalizedMetricId() {}

        public NormalizedMetricId(Long filingId, String metric, String basis) {
            this.filingId = filingId;
            this.metric = metric;
            this.basis = basis;
        }

        // Getters and Setters
        public Long getFilingId() {
            return filingId;
        }

        public void setFilingId(Long filingId) {
            this.filingId = filingId;
        }

        public String getMetric() {
            return metric;
        }

        public void setMetric(String metric) {
            this.metric = metric;
        }

        public String getBasis() {
            return basis;
        }

        public void setBasis(String basis) {
            this.basis = basis;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NormalizedMetricId that = (NormalizedMetricId) o;
            return Objects.equals(filingId, that.filingId) &&
                   Objects.equals(metric, that.metric) &&
                   Objects.equals(basis, that.basis);
        }

        @Override
        public int hashCode() {
            return Objects.hash(filingId, metric, basis);
        }
    }

    public NormalizedMetric() {
        this.createdAt = LocalDateTime.now();
        this.qualityScore = BigDecimal.ONE;
    }

    public NormalizedMetric(Long filingId, String metric, String basis, BigDecimal value) {
        this.id = new NormalizedMetricId(filingId, metric, basis);
        this.value = value;
        this.createdAt = LocalDateTime.now();
        this.qualityScore = BigDecimal.ONE;
    }

    public NormalizedMetric(Long filingId, String metric, String basis, BigDecimal value, BigDecimal qualityScore) {
        this.id = new NormalizedMetricId(filingId, metric, basis);
        this.value = value;
        this.qualityScore = qualityScore;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (qualityScore == null) {
            qualityScore = BigDecimal.ONE;
        }
    }

    // Getters and Setters

    public NormalizedMetricId getId() {
        return id;
    }

    public void setId(NormalizedMetricId id) {
        this.id = id;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public BigDecimal getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(BigDecimal qualityScore) {
        this.qualityScore = qualityScore;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Filing getFiling() {
        return filing;
    }

    public void setFiling(Filing filing) {
        this.filing = filing;
    }

    @Override
    public String toString() {
        return "NormalizedMetric{" +
                "id=" + id +
                ", value=" + value +
                ", qualityScore=" + qualityScore +
                '}';
    }
}
