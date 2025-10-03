package com.stockdelta.common.service;

import com.stockdelta.common.entity.Filing;
import com.stockdelta.common.entity.FilingSection;
import com.stockdelta.common.repository.FilingRepository;
import com.stockdelta.common.sec.SecApiClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts and parses sections from SEC filings (10-K, 10-Q, 8-K)
 * Focuses on Item 1A (Risk Factors), Item 7 (MD&A), and Item 7A (Market Risk)
 */
@Service
public class FilingSectionExtractor {

    private static final Logger logger = LoggerFactory.getLogger(FilingSectionExtractor.class);

    private static final int MIN_PARAGRAPH_LENGTH = 50;
    private static final int MAX_PARAGRAPH_LENGTH = 50000;

    // Section patterns for different form types
    private static final Pattern ITEM_1A_PATTERN = Pattern.compile(
            "(?i)(?:ITEM\\s*1A[.:\\s]*|Item\\s*1A[.:\\s]*)\\s*(RISK\\s*FACTORS|Risk\\s*Factors)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ITEM_7_PATTERN = Pattern.compile(
            "(?i)(?:ITEM\\s*7[.:\\s]*|Item\\s*7[.:\\s]*)\\s*(MANAGEMENT'?S\\s*DISCUSSION|Management'?s\\s*Discussion)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ITEM_7A_PATTERN = Pattern.compile(
            "(?i)(?:ITEM\\s*7A[.:\\s]*|Item\\s*7A[.:\\s]*)\\s*(QUANTITATIVE\\s*AND\\s*QUALITATIVE|Quantitative\\s*and\\s*Qualitative)",
            Pattern.CASE_INSENSITIVE
    );

    private final SecApiClient secApiClient;
    private final FilingRepository filingRepository;

    @Autowired
    public FilingSectionExtractor(SecApiClient secApiClient,
                                   FilingRepository filingRepository) {
        this.secApiClient = secApiClient;
        this.filingRepository = filingRepository;
    }

    /**
     * Extract sections from a filing document
     */
    public Mono<List<FilingSection>> extractSections(Long filingId) {
        Filing filing = filingRepository.findById(filingId).orElse(null);
        if (filing == null) {
            logger.warn("Filing not found: {}", filingId);
            return Mono.just(new ArrayList<>());
        }

        return fetchAndParseFiling(filing)
                .onErrorResume(error -> {
                    logger.error("Failed to extract sections for filing {}: {}", filingId, error.getMessage());
                    return Mono.just(new ArrayList<>());
                });
    }

    private Mono<List<FilingSection>> fetchAndParseFiling(Filing filing) {
        String documentUrl = filing.getPrimaryDocUrl();
        if (documentUrl == null || documentUrl.isEmpty()) {
            logger.warn("No document URL for filing: {}", filing.getAccessionNo());
            return Mono.just(new ArrayList<>());
        }

        return secApiClient.fetchDocument(documentUrl)
                .map(html -> parseDocument(filing, html))
                .onErrorResume(error -> {
                    logger.error("Failed to fetch document {}: {}", documentUrl, error.getMessage());
                    return Mono.just(new ArrayList<>());
                });
    }

    private List<FilingSection> parseDocument(Filing filing, String html) {
        List<FilingSection> sections = new ArrayList<>();

        try {
            Document doc = Jsoup.parse(html);

            // Clean up the document
            doc.select("script, style, noscript").remove();

            String fullText = doc.text();

            // Extract Item 1A (Risk Factors) - only for 10-K and 10-Q
            if (filing.getForm().matches("10-[KQ]")) {
                extractSection(filing.getId(), "Item1A", fullText, ITEM_1A_PATTERN)
                        .ifPresent(sections::add);
            }

            // Extract Item 7 (MD&A) - only for 10-K and 10-Q
            if (filing.getForm().matches("10-[KQ]")) {
                extractSection(filing.getId(), "Item7", fullText, ITEM_7_PATTERN)
                        .ifPresent(sections::add);

                // Extract Item 7A (Market Risk) - only for 10-K
                if (filing.getForm().equals("10-K")) {
                    extractSection(filing.getId(), "Item7A", fullText, ITEM_7A_PATTERN)
                            .ifPresent(sections::add);
                }
            }

            logger.info("Extracted {} sections from filing: {}", sections.size(), filing.getAccessionNo());

        } catch (Exception e) {
            logger.error("Error parsing document for filing {}: {}", filing.getAccessionNo(), e.getMessage());
        }

        return sections;
    }

    private java.util.Optional<FilingSection> extractSection(Long filingId, String sectionName,
                                                              String fullText, Pattern startPattern) {
        try {
            Matcher startMatcher = startPattern.matcher(fullText);
            if (!startMatcher.find()) {
                logger.debug("Section {} not found in filing {}", sectionName, filingId);
                return java.util.Optional.empty();
            }

            int startPos = startMatcher.start();

            // Find the next "ITEM" to determine end boundary
            int endPos = findNextItemBoundary(fullText, startPos);

            if (endPos == -1 || endPos <= startPos) {
                endPos = Math.min(startPos + MAX_PARAGRAPH_LENGTH, fullText.length());
            }

            String sectionText = fullText.substring(startPos, endPos).trim();

            // Tokenize into paragraphs
            String cleanedText = cleanParagraphs(sectionText);

            if (cleanedText.length() < MIN_PARAGRAPH_LENGTH) {
                logger.debug("Section {} too short ({} chars) in filing {}", sectionName, cleanedText.length(), filingId);
                return java.util.Optional.empty();
            }

            // Create FilingSection entity
            FilingSection section = new FilingSection();
            section.setFilingId(filingId);
            section.setSection(sectionName);
            section.setText(cleanedText);
            section.setTextHash(calculateHash(cleanedText));
            section.setCharCount(cleanedText.length());

            return java.util.Optional.of(section);

        } catch (Exception e) {
            logger.error("Error extracting section {} from filing {}: {}", sectionName, filingId, e.getMessage());
            return java.util.Optional.empty();
        }
    }

    private int findNextItemBoundary(String text, int fromPos) {
        // Look for next "ITEM" marker
        Pattern nextItemPattern = Pattern.compile(
                "(?i)ITEM\\s*(?:[0-9]{1,2}[A-Z]?|[IVX]+)[.:\\s]",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = nextItemPattern.matcher(text);
        if (matcher.find(fromPos + 100)) { // Skip at least 100 chars to avoid matching the same item
            return matcher.start();
        }

        return -1;
    }

    private String cleanParagraphs(String text) {
        // Remove excessive whitespace
        text = text.replaceAll("\\s+", " ");

        // Remove special characters that are not useful
        text = text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");

        // Normalize quotes
        text = text.replaceAll("[\u201C\u201D]", "\"");
        text = text.replaceAll("[\u2018\u2019]", "'");

        // Remove page numbers and headers (common patterns)
        text = text.replaceAll("(?i)Page\\s+\\d+\\s+of\\s+\\d+", "");
        text = text.replaceAll("(?i)Table\\s+of\\s+Contents", "");

        return text.trim();
    }

    private String calculateHash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not available", e);
            return "";
        }
    }

    /**
     * Extract sections and calculate importance scores
     */
    public static class SectionWithScore {
        private final FilingSection section;
        private final double importanceScore;

        public SectionWithScore(FilingSection section, double importanceScore) {
            this.section = section;
            this.importanceScore = importanceScore;
        }

        public FilingSection getSection() {
            return section;
        }

        public double getImportanceScore() {
            return importanceScore;
        }
    }

    /**
     * Calculate importance score based on keywords
     */
    public double calculateImportanceScore(String text) {
        double score = 0.0;

        // High-importance keywords
        String[] highKeywords = {"risk", "uncertainty", "lawsuit", "litigation", "material adverse",
                "investigation", "bankruptcy", "default", "breach", "violation"};

        // Medium-importance keywords
        String[] mediumKeywords = {"challenge", "competition", "regulatory", "compliance",
                "depend", "may not", "could adversely", "potential"};

        String lowerText = text.toLowerCase();

        for (String keyword : highKeywords) {
            int count = countOccurrences(lowerText, keyword);
            score += count * 0.3;
        }

        for (String keyword : mediumKeywords) {
            int count = countOccurrences(lowerText, keyword);
            score += count * 0.1;
        }

        // Length factor (longer changes are often more significant)
        score += Math.log10(text.length() + 1) * 0.05;

        return Math.min(score, 1.0); // Cap at 1.0
    }

    private int countOccurrences(String text, String keyword) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(keyword, index)) != -1) {
            count++;
            index += keyword.length();
        }
        return count;
    }
}
