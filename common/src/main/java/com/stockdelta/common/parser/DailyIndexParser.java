package com.stockdelta.common.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Component
public class DailyIndexParser {

    private static final Logger logger = LoggerFactory.getLogger(DailyIndexParser.class);

    private static final Set<String> TARGET_FORMS = Set.of(
            "10-K", "10-Q", "8-K", "4", "13F-HR", "13D", "13G"
    );

    public static class IndexEntry {
        private String cik;
        private String companyName;
        private String form;
        private LocalDate filedDate;
        private String accessionNo;
        private String documentUrl;

        // Constructors
        public IndexEntry() {}

        public IndexEntry(String cik, String companyName, String form,
                         LocalDate filedDate, String accessionNo, String documentUrl) {
            this.cik = cik;
            this.companyName = companyName;
            this.form = form;
            this.filedDate = filedDate;
            this.accessionNo = accessionNo;
            this.documentUrl = documentUrl;
        }

        // Getters and Setters
        public String getCik() { return cik; }
        public void setCik(String cik) { this.cik = cik; }

        public String getCompanyName() { return companyName; }
        public void setCompanyName(String companyName) { this.companyName = companyName; }

        public String getForm() { return form; }
        public void setForm(String form) { this.form = form; }

        public LocalDate getFiledDate() { return filedDate; }
        public void setFiledDate(LocalDate filedDate) { this.filedDate = filedDate; }

        public String getAccessionNo() { return accessionNo; }
        public void setAccessionNo(String accessionNo) { this.accessionNo = accessionNo; }

        public String getDocumentUrl() { return documentUrl; }
        public void setDocumentUrl(String documentUrl) { this.documentUrl = documentUrl; }

        @Override
        public String toString() {
            return String.format("IndexEntry{cik='%s', form='%s', filedDate=%s, accessionNo='%s'}",
                               cik, form, filedDate, accessionNo);
        }
    }

    public List<IndexEntry> parseIndex(String indexContent, Set<String> targetCiks) {
        List<IndexEntry> entries = new ArrayList<>();

        if (indexContent == null || indexContent.trim().isEmpty()) {
            logger.warn("Empty index content provided");
            return entries;
        }

        String[] lines = indexContent.split("\n");
        boolean inDataSection = false;
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (String line : lines) {
            line = line.trim();

            // Skip until we reach the data section
            if (!inDataSection) {
                if (line.startsWith("-")) {
                    inDataSection = true;
                }
                continue;
            }

            // Skip empty lines and separators
            if (line.isEmpty() || line.startsWith("-")) {
                continue;
            }

            try {
                IndexEntry entry = parseLine(line, dateFormatter);
                if (entry != null && shouldIncludeEntry(entry, targetCiks)) {
                    entries.add(entry);
                }
            } catch (Exception e) {
                logger.debug("Failed to parse line: {} - {}", line, e.getMessage());
            }
        }

        logger.info("Parsed {} relevant entries from daily index", entries.size());
        return entries;
    }

    private IndexEntry parseLine(String line, DateTimeFormatter dateFormatter) {
        // EDGAR daily index format: CIK|Company Name|Form Type|Date Filed|File Name
        // Example: 320193|APPLE INC|10-Q|2024-11-01|edgar/data/320193/0000320193-24-000123/aapl-20240930.htm

        String[] parts = line.split("\\|");
        if (parts.length < 5) {
            return null;
        }

        try {
            String cik = String.format("%010d", Long.parseLong(parts[0].trim()));
            String companyName = parts[1].trim();
            String form = parts[2].trim();
            LocalDate filedDate = LocalDate.parse(parts[3].trim(), dateFormatter);
            String fileName = parts[4].trim();

            // Extract accession number from file name
            String accessionNo = extractAccessionNumber(fileName);

            // Construct document URL
            String documentUrl = "https://www.sec.gov/Archives/" + fileName;

            return new IndexEntry(cik, companyName, form, filedDate, accessionNo, documentUrl);

        } catch (Exception e) {
            logger.debug("Failed to parse index line: {}", line);
            return null;
        }
    }

    private String extractAccessionNumber(String fileName) {
        // Extract accession number from path like: edgar/data/320193/0000320193-24-000123/aapl-20240930.htm
        // Accession number pattern: NNNNNNNNNN-NN-NNNNNN

        String[] pathParts = fileName.split("/");
        for (String part : pathParts) {
            if (part.matches("\\d{10}-\\d{2}-\\d{6}")) {
                return part;
            }
        }

        logger.warn("Could not extract accession number from: {}", fileName);
        return null;
    }

    private boolean shouldIncludeEntry(IndexEntry entry, Set<String> targetCiks) {
        // Include if form is in target list
        if (!TARGET_FORMS.contains(entry.getForm())) {
            return false;
        }

        // Include if CIK is in target set (or if no target set specified, include all)
        if (targetCiks != null && !targetCiks.isEmpty()) {
            return targetCiks.contains(entry.getCik());
        }

        return true;
    }

    public List<IndexEntry> filterByForms(List<IndexEntry> entries, String... forms) {
        Set<String> formSet = Set.of(forms);
        return entries.stream()
                .filter(entry -> formSet.contains(entry.getForm()))
                .toList();
    }

    public List<IndexEntry> filterByCiks(List<IndexEntry> entries, String... ciks) {
        Set<String> cikSet = Set.of(ciks);
        return entries.stream()
                .filter(entry -> cikSet.contains(entry.getCik()))
                .toList();
    }
}