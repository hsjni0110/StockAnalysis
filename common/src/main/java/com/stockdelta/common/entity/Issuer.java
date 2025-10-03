package com.stockdelta.common.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "issuers")
public class Issuer {

    @Id
    @Column(name = "cik", length = 10, columnDefinition = "CHAR(10)")
    @Size(min = 10, max = 10)
    private String cik;

    @Size(max = 10)
    private String ticker;

    @NotNull
    private String name;

    @Size(max = 10)
    private String exchange;

    @Size(max = 10)
    private String sic;

    private String industry;

    private String sector;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "issuer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Filing> filings;

    public Issuer() {
        this.updatedAt = LocalDateTime.now();
    }

    public Issuer(String cik, String ticker, String name) {
        this();
        this.cik = cik;
        this.ticker = ticker;
        this.name = name;
    }

    @PrePersist
    @PreUpdate
    protected void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getCik() { return cik; }
    public void setCik(String cik) { this.cik = cik; }

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public String getSic() { return sic; }
    public void setSic(String sic) { this.sic = sic; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<Filing> getFilings() { return filings; }
    public void setFilings(List<Filing> filings) { this.filings = filings; }
}