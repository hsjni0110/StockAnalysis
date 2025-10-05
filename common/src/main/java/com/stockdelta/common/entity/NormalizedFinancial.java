package com.stockdelta.common.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Normalized financial data entity based on FAC (Fundamental Accounting Concepts)
 * Stores XBRL facts mapped to standardized concepts for cross-company comparability
 */
@Entity
@Table(name = "normalized_financials",
       indexes = {
           @Index(name = "idx_normalized_financials_filing_concept", columnList = "filing_id, concept"),
           @Index(name = "idx_normalized_financials_concept_value", columnList = "concept, value")
       })
public class NormalizedFinancial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "filing_id")
    private Long filingId;

    @NotNull
    @Size(max = 100)
    @Column(name = "concept")
    private String concept;  // FAC fundamental concept (Revenue, Assets, etc.)

    @Column(precision = 20, scale = 2)
    private BigDecimal value;

    @Size(max = 20)
    @Column(name = "period_type")
    private String periodType;  // 'instant' or 'duration'

    @Size(max = 255)
    @Column(name = "context_ref", length = 255)
    private String contextRef;  // XBRL context reference

    @Size(max = 20)
    private String unit;  // USD, shares, etc.

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "quality_score", precision = 3, scale = 2)
    private BigDecimal qualityScore;  // Data quality score (0.0 to 1.0)

    @Size(max = 50)
    private String source;  // 'arelle-xule', 'dqc-validated', 'fac-mapped'

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filing_id", insertable = false, updatable = false)
    @JsonIgnore
    private Filing filing;

    public NormalizedFinancial() {
        this.createdAt = LocalDateTime.now();
        this.qualityScore = BigDecimal.ONE;  // Default to 1.0
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFilingId() {
        return filingId;
    }

    public void setFilingId(Long filingId) {
        this.filingId = filingId;
    }

    public String getConcept() {
        return concept;
    }

    public void setConcept(String concept) {
        this.concept = concept;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public String getPeriodType() {
        return periodType;
    }

    public void setPeriodType(String periodType) {
        this.periodType = periodType;
    }

    public String getContextRef() {
        return contextRef;
    }

    public void setContextRef(String contextRef) {
        this.contextRef = contextRef;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(BigDecimal qualityScore) {
        this.qualityScore = qualityScore;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
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
        return "NormalizedFinancial{" +
                "id=" + id +
                ", filingId=" + filingId +
                ", concept='" + concept + '\'' +
                ", value=" + value +
                ", periodType='" + periodType + '\'' +
                ", qualityScore=" + qualityScore +
                ", source='" + source + '\'' +
                '}';
    }
}
