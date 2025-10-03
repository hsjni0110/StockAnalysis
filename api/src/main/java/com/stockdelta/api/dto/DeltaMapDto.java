package com.stockdelta.api.dto;

import com.stockdelta.common.entity.FilingDelta;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for DeltaMap response
 * Contains current and previous filing info with deltas
 */
public class DeltaMapDto {
    private FilingInfo current;
    private FilingInfo previous;
    private int totalChanges;
    private int insertCount;
    private int deleteCount;
    private int modifyCount;
    private List<FilingDelta> deltas;

    public static class FilingInfo {
        private Long filingId;
        private String form;
        private LocalDate periodEnd;
        private LocalDateTime filedAt;
        private String accessionNo;
        private String primaryDocUrl;

        // Getters and Setters
        public Long getFilingId() { return filingId; }
        public void setFilingId(Long filingId) { this.filingId = filingId; }

        public String getForm() { return form; }
        public void setForm(String form) { this.form = form; }

        public LocalDate getPeriodEnd() { return periodEnd; }
        public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

        public LocalDateTime getFiledAt() { return filedAt; }
        public void setFiledAt(LocalDateTime filedAt) { this.filedAt = filedAt; }

        public String getAccessionNo() { return accessionNo; }
        public void setAccessionNo(String accessionNo) { this.accessionNo = accessionNo; }

        public String getPrimaryDocUrl() { return primaryDocUrl; }
        public void setPrimaryDocUrl(String primaryDocUrl) { this.primaryDocUrl = primaryDocUrl; }
    }

    // Getters and Setters
    public FilingInfo getCurrent() { return current; }
    public void setCurrent(FilingInfo current) { this.current = current; }

    public FilingInfo getPrevious() { return previous; }
    public void setPrevious(FilingInfo previous) { this.previous = previous; }

    public int getTotalChanges() { return totalChanges; }
    public void setTotalChanges(int totalChanges) { this.totalChanges = totalChanges; }

    public int getInsertCount() { return insertCount; }
    public void setInsertCount(int insertCount) { this.insertCount = insertCount; }

    public int getDeleteCount() { return deleteCount; }
    public void setDeleteCount(int deleteCount) { this.deleteCount = deleteCount; }

    public int getModifyCount() { return modifyCount; }
    public void setModifyCount(int modifyCount) { this.modifyCount = modifyCount; }

    public List<FilingDelta> getDeltas() { return deltas; }
    public void setDeltas(List<FilingDelta> deltas) { this.deltas = deltas; }
}
