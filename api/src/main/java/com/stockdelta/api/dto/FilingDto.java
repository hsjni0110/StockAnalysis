package com.stockdelta.api.dto;

import com.stockdelta.common.entity.Filing;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class FilingDto {
    private Long id;
    private String cik;
    private String accessionNo;
    private String form;
    private LocalDateTime filedAt;
    private LocalDate periodEnd;
    private String primaryDocUrl;
    private String source;
    private String ticker;
    private String companyName;
    private LocalDateTime createdAt;

    public FilingDto() {}

    public FilingDto(Filing filing, String ticker, String companyName) {
        this.id = filing.getId();
        this.cik = filing.getCik();
        this.accessionNo = filing.getAccessionNo();
        this.form = filing.getForm();
        this.filedAt = filing.getFiledAt();
        this.periodEnd = filing.getPeriodEnd();
        this.primaryDocUrl = filing.getPrimaryDocUrl();
        this.source = filing.getSource();
        this.createdAt = filing.getCreatedAt();
        this.ticker = ticker;
        this.companyName = companyName;
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
}