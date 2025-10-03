package com.stockdelta.common.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "filing_deltas")
public class FilingDelta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "filing_id")
    private Long filingId;

    @NotNull
    @Size(max = 20)
    private String section;

    @Enumerated(EnumType.STRING)
    @Size(max = 10)
    private Operation operation;

    @Column(columnDefinition = "TEXT")
    private String snippet;

    @Column(precision = 5, scale = 3)
    private BigDecimal score;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filing_id", insertable = false, updatable = false)
    @JsonIgnore
    private Filing filing;

    public enum Operation {
        INSERT, DELETE, MODIFY
    }

    public FilingDelta() {
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFilingId() { return filingId; }
    public void setFilingId(Long filingId) { this.filingId = filingId; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public Operation getOperation() { return operation; }
    public void setOperation(Operation operation) { this.operation = operation; }

    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Filing getFiling() { return filing; }
    public void setFiling(Filing filing) { this.filing = filing; }
}