package com.stockdelta.common.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "filings",
       indexes = {
           @Index(name = "idx_filings_cik_filed_at", columnList = "cik, filed_at"),
           @Index(name = "idx_filings_form_filed_at", columnList = "form, filed_at")
       })
public class Filing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "cik", length = 10, columnDefinition = "CHAR(10)")
    @Size(min = 10, max = 10)
    private String cik;

    @NotNull
    @Size(max = 25)
    @Column(name = "accession_no", unique = true)
    private String accessionNo;

    @NotNull
    @Size(max = 20)
    private String form;

    @NotNull
    @Column(name = "filed_at")
    private LocalDateTime filedAt;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "primary_doc_url")
    private String primaryDocUrl;

    @Size(max = 20)
    private String source = "daily-index";

    @Transient
    @Size(max = 10)
    private String ticker;

    @Transient
    private String companyName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cik", insertable = false, updatable = false)
    @JsonIgnore
    private Issuer issuer;

    @OneToMany(mappedBy = "filing", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<FilingSection> sections;

    @OneToMany(mappedBy = "filing", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<FilingDelta> deltas;

    @OneToMany(mappedBy = "filing", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<XbrlFact> xbrlFacts;

    @OneToMany(mappedBy = "filing", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<InsiderTransaction> insiderTransactions;

    public Filing() {
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

    public String getCik() { return cik; }
    public void setCik(String cik) { this.cik = cik; }

    public String getAccessionNo() { return accessionNo; }
    public void setAccessionNo(String accessionNo) { this.accessionNo = accessionNo; }

    public String getForm() { return form; }
    public void setForm(String form) { this.form = form; }

    public LocalDateTime getFiledAt() { return filedAt; }
    public void setFiledAt(LocalDateTime filedAt) { this.filedAt = filedAt; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public String getPrimaryDocUrl() { return primaryDocUrl; }
    public void setPrimaryDocUrl(String primaryDocUrl) { this.primaryDocUrl = primaryDocUrl; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Issuer getIssuer() { return issuer; }
    public void setIssuer(Issuer issuer) { this.issuer = issuer; }

    public List<FilingSection> getSections() { return sections; }
    public void setSections(List<FilingSection> sections) { this.sections = sections; }

    public List<FilingDelta> getDeltas() { return deltas; }
    public void setDeltas(List<FilingDelta> deltas) { this.deltas = deltas; }

    public List<XbrlFact> getXbrlFacts() { return xbrlFacts; }
    public void setXbrlFacts(List<XbrlFact> xbrlFacts) { this.xbrlFacts = xbrlFacts; }

    public List<InsiderTransaction> getInsiderTransactions() { return insiderTransactions; }
    public void setInsiderTransactions(List<InsiderTransaction> insiderTransactions) { this.insiderTransactions = insiderTransactions; }
}