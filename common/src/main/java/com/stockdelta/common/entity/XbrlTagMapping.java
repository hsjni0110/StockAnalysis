package com.stockdelta.common.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * XBRL Tag to FAC Concept Mapping Entity
 * Stores mappings from US-GAAP XBRL tags to Fundamental Accounting Concepts
 */
@Entity
@Table(name = "xbrl_tag_mapping",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"source_tag", "taxonomy"})
       },
       indexes = {
           @Index(name = "idx_xbrl_tag_mapping_source_taxonomy", columnList = "source_tag, taxonomy"),
           @Index(name = "idx_xbrl_tag_mapping_concept", columnList = "fundamental_concept")
       })
public class XbrlTagMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Size(max = 255)
    @Column(name = "source_tag")
    private String sourceTag;  // Original XBRL tag (e.g., "Revenues")

    @NotNull
    @Size(max = 100)
    private String taxonomy;  // 'us-gaap', 'ifrs-full', etc.

    @NotNull
    @Size(max = 100)
    @Column(name = "fundamental_concept")
    private String fundamentalConcept;  // FAC concept (e.g., "Revenue")

    @Column(name = "confidence_score", precision = 3, scale = 2)
    private BigDecimal confidenceScore;  // Mapping confidence (0.0 to 1.0)

    @Size(max = 100)
    @Column(name = "rule_source")
    private String ruleSource;  // 'fac-standard', 'pattern-match', 'manual'

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public XbrlTagMapping() {
        this.createdAt = LocalDateTime.now();
        this.confidenceScore = BigDecimal.ONE;
    }

    public XbrlTagMapping(String sourceTag, String taxonomy, String fundamentalConcept) {
        this.sourceTag = sourceTag;
        this.taxonomy = taxonomy;
        this.fundamentalConcept = fundamentalConcept;
        this.confidenceScore = BigDecimal.ONE;
        this.ruleSource = "fac-standard";
        this.createdAt = LocalDateTime.now();
    }

    public XbrlTagMapping(String sourceTag, String taxonomy, String fundamentalConcept,
                          BigDecimal confidenceScore, String ruleSource) {
        this.sourceTag = sourceTag;
        this.taxonomy = taxonomy;
        this.fundamentalConcept = fundamentalConcept;
        this.confidenceScore = confidenceScore;
        this.ruleSource = ruleSource;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (confidenceScore == null) {
            confidenceScore = BigDecimal.ONE;
        }
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSourceTag() {
        return sourceTag;
    }

    public void setSourceTag(String sourceTag) {
        this.sourceTag = sourceTag;
    }

    public String getTaxonomy() {
        return taxonomy;
    }

    public void setTaxonomy(String taxonomy) {
        this.taxonomy = taxonomy;
    }

    public String getFundamentalConcept() {
        return fundamentalConcept;
    }

    public void setFundamentalConcept(String fundamentalConcept) {
        this.fundamentalConcept = fundamentalConcept;
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getRuleSource() {
        return ruleSource;
    }

    public void setRuleSource(String ruleSource) {
        this.ruleSource = ruleSource;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "XbrlTagMapping{" +
                "id=" + id +
                ", sourceTag='" + sourceTag + '\'' +
                ", taxonomy='" + taxonomy + '\'' +
                ", fundamentalConcept='" + fundamentalConcept + '\'' +
                ", confidenceScore=" + confidenceScore +
                ", ruleSource='" + ruleSource + '\'' +
                '}';
    }
}
