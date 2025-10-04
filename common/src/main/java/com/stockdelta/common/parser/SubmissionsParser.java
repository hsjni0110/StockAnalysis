package com.stockdelta.common.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockdelta.common.entity.Filing;
import com.stockdelta.common.entity.Issuer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class SubmissionsParser {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionsParser.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<String> TARGET_FORMS = Set.of(
            "10-K", "10-Q", "8-K", "4", "13F-HR", "13D", "13G"
    );

    public static class SubmissionData {
        private Issuer issuer;
        private List<Filing> filings;

        public SubmissionData(Issuer issuer, List<Filing> filings) {
            this.issuer = issuer;
            this.filings = filings;
        }

        public Issuer getIssuer() { return issuer; }
        public void setIssuer(Issuer issuer) { this.issuer = issuer; }

        public List<Filing> getFilings() { return filings; }
        public void setFilings(List<Filing> filings) { this.filings = filings; }
    }

    public SubmissionData parseSubmissions(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);

        // Parse issuer information
        Issuer issuer = parseIssuerInfo(root);

        // Parse recent filings
        List<Filing> filings = parseRecentFilings(root, issuer.getCik());

        return new SubmissionData(issuer, filings);
    }

    private Issuer parseIssuerInfo(JsonNode root) {
        Issuer issuer = new Issuer();

        // Basic company information
        String cik = root.path("cik").asText();
        issuer.setCik(String.format("%010d", Long.parseLong(cik)));
        issuer.setName(root.path("name").asText());
        issuer.setSic(root.path("sic").asText());
        issuer.setIndustry(root.path("sicDescription").asText());

        // Extract ticker from tickers object (most recent one)
        JsonNode tickers = root.path("tickers");
        if (tickers.isArray() && tickers.size() > 0) {
            // Take the first (most recent) ticker
            issuer.setTicker(tickers.get(0).asText().toUpperCase());
        }

        // Extract exchange from exchanges object
        JsonNode exchanges = root.path("exchanges");
        if (exchanges.isArray() && exchanges.size() > 0) {
            issuer.setExchange(exchanges.get(0).asText());
        }

        return issuer;
    }

    private List<Filing> parseRecentFilings(JsonNode root, String cik) {
        List<Filing> filings = new ArrayList<>();

        JsonNode recentFilings = root.path("filings").path("recent");
        if (recentFilings.isMissingNode()) {
            logger.warn("No recent filings found for CIK: {}", cik);
            return filings;
        }

        // Get arrays for each field
        JsonNode accessionNumbers = recentFilings.path("accessionNumber");
        JsonNode filingDates = recentFilings.path("filingDate");
        JsonNode reportDates = recentFilings.path("reportDate");
        JsonNode forms = recentFilings.path("form");
        JsonNode primaryDocuments = recentFilings.path("primaryDocument");

        if (!accessionNumbers.isArray()) {
            logger.warn("Invalid filings format for CIK: {}", cik);
            return filings;
        }

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        int size = accessionNumbers.size();

        for (int i = 0; i < size; i++) {
            try {
                String form = forms.get(i).asText();

                // Only process target forms
                if (!TARGET_FORMS.contains(form)) {
                    continue;
                }

                Filing filing = new Filing();
                filing.setCik(cik);
                filing.setAccessionNo(accessionNumbers.get(i).asText());
                filing.setForm(form);

                // Parse filing date
                String filingDateStr = filingDates.get(i).asText();
                LocalDate filingDate = LocalDate.parse(filingDateStr, dateFormatter);
                filing.setFiledAt(filingDate.atStartOfDay());

                // Parse report date (period end)
                if (i < reportDates.size() && !reportDates.get(i).isNull()) {
                    String reportDateStr = reportDates.get(i).asText();
                    if (!reportDateStr.isEmpty()) {
                        filing.setPeriodEnd(LocalDate.parse(reportDateStr, dateFormatter));
                    }
                }

                // Construct primary document URL
                if (i < primaryDocuments.size() && !primaryDocuments.get(i).isNull()) {
                    String primaryDoc = primaryDocuments.get(i).asText();
                    String accessionNoForUrl = filing.getAccessionNo().replace("-", "");
                    filing.setPrimaryDocUrl(String.format(
                            "https://www.sec.gov/Archives/edgar/data/%s/%s/%s",
                            Long.parseLong(cik), accessionNoForUrl, primaryDoc
                    ));
                }

                filing.setSource("submissions");
                filings.add(filing);

            } catch (Exception e) {
                logger.debug("Failed to parse filing at index {}: {}", i, e.getMessage());
            }
        }

        logger.info("Parsed {} filings for CIK: {}", filings.size(), cik);
        return filings;
    }

    public List<Filing> filterByDateRange(List<Filing> filings, LocalDateTime since) {
        return filings.stream()
                .filter(filing -> filing.getFiledAt().isAfter(since))
                .toList();
    }

    public List<Filing> filterByForms(List<Filing> filings, String... forms) {
        Set<String> formSet = Set.of(forms);
        return filings.stream()
                .filter(filing -> formSet.contains(filing.getForm()))
                .toList();
    }

    /**
     * Get filings for comparison analysis
     * Returns the most recent N filings of a specific form
     */
    public List<Filing> getFilingsForComparison(List<Filing> allFilings, String form, int minCount) {
        return allFilings.stream()
                .filter(f -> f.getForm().equals(form))
                .sorted((a, b) -> b.getFiledAt().compareTo(a.getFiledAt())) // Sort by filed date descending
                .limit(minCount)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Filter filings needed for comparison analysis
     * Returns Map of form type -> list of filings (minimum N filings per form)
     * - 10-K: 2 filings (current year + previous year)
     * - 10-Q: 4 filings (4 quarters for YoY comparison)
     * - 8-K: 5 filings (recent events)
     * - Form 4: 10 filings (insider trading tracking)
     * - 13F-HR: 4 filings (quarterly institutional holdings)
     * - 13D/13G: 5 filings (activist/large holder events)
     */
    public java.util.Map<String, List<Filing>> filterComparisonFilings(List<Filing> filings) {
        java.util.Map<String, List<Filing>> result = new java.util.HashMap<>();

        // 10-K: minimum 2 filings (current + previous year)
        result.put("10-K", getFilingsForComparison(filings, "10-K", 2));

        // 10-Q: minimum 4 filings (1 year of quarterly data for QoQ/YoY)
        result.put("10-Q", getFilingsForComparison(filings, "10-Q", 4));

        // 8-K: recent 5 filings (material events)
        result.put("8-K", getFilingsForComparison(filings, "8-K", 5));

        // Form 4: recent 10 filings (insider transaction tracking)
        result.put("4", getFilingsForComparison(filings, "4", 10));

        // 13F-HR: 4 filings (quarterly institutional holdings for 1 year)
        result.put("13F-HR", getFilingsForComparison(filings, "13F-HR", 4));

        // 13D: recent 5 filings (activist investor tracking)
        result.put("13D", getFilingsForComparison(filings, "13D", 5));

        // 13G: recent 5 filings (large holder tracking)
        result.put("13G", getFilingsForComparison(filings, "13G", 5));

        return result;
    }
}