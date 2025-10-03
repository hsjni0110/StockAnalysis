package com.stockdelta.common.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "xbrl_metrics")
public class XbrlMetric {

    @EmbeddedId
    private XbrlMetricId id;

    @Column(precision = 20, scale = 2)
    private BigDecimal value;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filing_id", insertable = false, updatable = false)
    @JsonIgnore
    private Filing filing;

    @Embeddable
    public static class XbrlMetricId implements java.io.Serializable {
        @Column(name = "filing_id")
        private Long filingId;

        @NotNull
        @Size(max = 100)
        private String metric;

        @NotNull
        @Size(max = 10)
        private String basis; // QoQ, YoY, Abs

        public XbrlMetricId() {}

        public XbrlMetricId(Long filingId, String metric, String basis) {
            this.filingId = filingId;
            this.metric = metric;
            this.basis = basis;
        }

        // Getters and Setters
        public Long getFilingId() { return filingId; }
        public void setFilingId(Long filingId) { this.filingId = filingId; }

        public String getMetric() { return metric; }
        public void setMetric(String metric) { this.metric = metric; }

        public String getBasis() { return basis; }
        public void setBasis(String basis) { this.basis = basis; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            XbrlMetricId that = (XbrlMetricId) o;
            return filingId.equals(that.filingId) &&
                   metric.equals(that.metric) &&
                   basis.equals(that.basis);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(filingId, metric, basis);
        }
    }

    public XbrlMetric() {
        this.createdAt = LocalDateTime.now();
    }

    public XbrlMetric(Long filingId, String metric, String basis, BigDecimal value) {
        this.id = new XbrlMetricId(filingId, metric, basis);
        this.value = value;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Getters and Setters
    public XbrlMetricId getId() { return id; }
    public void setId(XbrlMetricId id) { this.id = id; }

    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Filing getFiling() { return filing; }
    public void setFiling(Filing filing) { this.filing = filing; }
}
