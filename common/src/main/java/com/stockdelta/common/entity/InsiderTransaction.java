package com.stockdelta.common.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "insider_transactions",
       indexes = {
           @Index(name = "idx_insider_tx_transaction_date", columnList = "transaction_date")
       })
public class InsiderTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "filing_id")
    private Long filingId;

    @NotNull
    @Size(max = 200)
    @Column(name = "insider_name")
    private String insiderName;

    @Size(max = 100)
    private String role;

    @Size(max = 2)
    @Column(name = "transaction_code")
    private String transactionCode;

    @Column(precision = 15, scale = 2)
    private BigDecimal quantity;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    private Boolean acquired;

    @Column(name = "transaction_date")
    private LocalDate transactionDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filing_id", insertable = false, updatable = false)
    @JsonIgnore
    private Filing filing;

    public InsiderTransaction() {
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

    public String getInsiderName() { return insiderName; }
    public void setInsiderName(String insiderName) { this.insiderName = insiderName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getTransactionCode() { return transactionCode; }
    public void setTransactionCode(String transactionCode) { this.transactionCode = transactionCode; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Boolean getAcquired() { return acquired; }
    public void setAcquired(Boolean acquired) { this.acquired = acquired; }

    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Filing getFiling() { return filing; }
    public void setFiling(Filing filing) { this.filing = filing; }
}