package com.stockdelta.common.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ingest_logs")
public class IngestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "request_timestamp")
    private LocalDateTime requestTimestamp;

    @Size(max = 20)
    private String mode;

    @Column(columnDefinition = "text[]")
    private String[] symbols;

    @Column(name = "total_processed")
    private Integer totalProcessed = 0;

    @Column(name = "total_inserted")
    private Integer totalInserted = 0;

    @Column(name = "total_skipped")
    private Integer totalSkipped = 0;

    @Column(columnDefinition = "TEXT")
    private String warnings;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Size(max = 20)
    private String status = "in_progress";

    public IngestLog() {
        this.requestTimestamp = LocalDateTime.now();
        this.id = UUID.randomUUID();
    }

    public IngestLog(String mode, String[] symbols) {
        this();
        this.mode = mode;
        this.symbols = symbols;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public LocalDateTime getRequestTimestamp() { return requestTimestamp; }
    public void setRequestTimestamp(LocalDateTime requestTimestamp) { this.requestTimestamp = requestTimestamp; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String[] getSymbols() { return symbols; }
    public void setSymbols(String[] symbols) { this.symbols = symbols; }

    public Integer getTotalProcessed() { return totalProcessed; }
    public void setTotalProcessed(Integer totalProcessed) { this.totalProcessed = totalProcessed; }

    public Integer getTotalInserted() { return totalInserted; }
    public void setTotalInserted(Integer totalInserted) { this.totalInserted = totalInserted; }

    public Integer getTotalSkipped() { return totalSkipped; }
    public void setTotalSkipped(Integer totalSkipped) { this.totalSkipped = totalSkipped; }

    public String getWarnings() { return warnings; }
    public void setWarnings(String warnings) { this.warnings = warnings; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public void markCompleted() {
        this.status = "completed";
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = "failed";
        this.completedAt = LocalDateTime.now();
    }
}