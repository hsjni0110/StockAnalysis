package com.stockdelta.common.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

/**
 * DQC (Data Quality Committee) Validation Results Entity
 * Stores validation issues found by Arelle DQC rules
 */
@Entity
@Table(name = "data_quality_validations",
       indexes = {
           @Index(name = "idx_dqc_validations_filing_severity", columnList = "filing_id, severity")
       })
public class DataQualityValidation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "filing_id")
    private Long filingId;

    @NotNull
    @Size(max = 50)
    @Column(name = "rule_id")
    private String ruleId;  // DQC rule identifier (e.g., DQC_0001)

    @NotNull
    @Size(max = 20)
    private String severity;  // 'error', 'warning', 'info'

    @Column(columnDefinition = "TEXT")
    private String message;  // Human-readable error message

    @Size(max = 100)
    @Column(name = "affected_concept")
    private String affectedConcept;  // XBRL concept affected by this issue

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filing_id", insertable = false, updatable = false)
    @JsonIgnore
    private Filing filing;

    public DataQualityValidation() {
        this.createdAt = LocalDateTime.now();
    }

    public DataQualityValidation(Long filingId, String ruleId, String severity, String message) {
        this.filingId = filingId;
        this.ruleId = ruleId;
        this.severity = severity;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }

    public DataQualityValidation(Long filingId, String ruleId, String severity,
                                 String message, String affectedConcept) {
        this.filingId = filingId;
        this.ruleId = ruleId;
        this.severity = severity;
        this.message = message;
        this.affectedConcept = affectedConcept;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
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

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAffectedConcept() {
        return affectedConcept;
    }

    public void setAffectedConcept(String affectedConcept) {
        this.affectedConcept = affectedConcept;
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
        return "DataQualityValidation{" +
                "id=" + id +
                ", filingId=" + filingId +
                ", ruleId='" + ruleId + '\'' +
                ", severity='" + severity + '\'' +
                ", message='" + message + '\'' +
                ", affectedConcept='" + affectedConcept + '\'' +
                '}';
    }
}
