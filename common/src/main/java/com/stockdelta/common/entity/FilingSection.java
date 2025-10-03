package com.stockdelta.common.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@Entity
@Table(name = "filing_sections",
       indexes = {
           @Index(name = "idx_filing_sections_filing_id_section", columnList = "filing_id, section")
       })
public class FilingSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "filing_id")
    private Long filingId;

    @NotNull
    @Size(max = 20)
    private String section;

    @Size(max = 64)
    @Column(name = "text_hash")
    private String textHash;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(name = "char_count")
    private Integer charCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filing_id", insertable = false, updatable = false)
    @JsonIgnore
    private Filing filing;

    public FilingSection() {
        this.createdAt = LocalDateTime.now();
    }

    public FilingSection(Long filingId, String section, String text) {
        this();
        this.filingId = filingId;
        this.section = section;
        this.text = text;
        this.charCount = text != null ? text.length() : 0;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (text != null && charCount == null) {
            charCount = text.length();
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFilingId() { return filingId; }
    public void setFilingId(Long filingId) { this.filingId = filingId; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public String getTextHash() { return textHash; }
    public void setTextHash(String textHash) { this.textHash = textHash; }

    public String getText() { return text; }
    public void setText(String text) {
        this.text = text;
        this.charCount = text != null ? text.length() : 0;
    }

    public Integer getCharCount() { return charCount; }
    public void setCharCount(Integer charCount) { this.charCount = charCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Filing getFiling() { return filing; }
    public void setFiling(Filing filing) { this.filing = filing; }
}