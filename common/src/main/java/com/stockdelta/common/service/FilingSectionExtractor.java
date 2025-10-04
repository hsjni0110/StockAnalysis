package com.stockdelta.common.service;

import com.stockdelta.common.entity.Filing;
import com.stockdelta.common.entity.FilingSection;
import com.stockdelta.common.repository.FilingRepository;
import com.stockdelta.common.repository.FilingSectionRepository;
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
            "(?i)ITEM\\s*7[.:\\s]*(?:MANAGEMENT|MD&A|DISCUSSION)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ITEM_7A_PATTERN = Pattern.compile(
            "(?i)(?:ITEM\\s*7A[.:\\s]*|Item\\s*7A[.:\\s]*)\\s*(QUANTITATIVE\\s*AND\\s*QUALITATIVE|Quantitative\\s*and\\s*Qualitative)",
            Pattern.CASE_INSENSITIVE
    );

    private final SecApiClient secApiClient;
    private final FilingRepository filingRepository;
    private final FilingSectionRepository sectionRepository;

    @Autowired
    public FilingSectionExtractor(SecApiClient secApiClient,
                                   FilingRepository filingRepository,
                                   FilingSectionRepository sectionRepository) {
        this.secApiClient = secApiClient;
        this.filingRepository = filingRepository;
        this.sectionRepository = sectionRepository;
    }

    /**
     * Extract sections from a filing document and save them
     */
    public Mono<List<FilingSection>> extractSections(Long filingId) {
        return extractSections(filingId, false);
    }

    /**
     * Extract sections from a filing document and save them
     * @param forceReextract if true, delete existing sections and re-extract
     */
    public Mono<List<FilingSection>> extractSections(Long filingId, boolean forceReextract) {
        // Check if sections already exist
        List<FilingSection> existingSections = sectionRepository.findByFilingId(filingId);
        if (!existingSections.isEmpty() && !forceReextract) {
            logger.info("Sections already extracted for filing {}, returning cached sections", filingId);
            return Mono.just(existingSections);
        }

        if (forceReextract && !existingSections.isEmpty()) {
            logger.info("Force re-extraction: deleting {} existing sections for filing {}", existingSections.size(), filingId);
            sectionRepository.deleteAll(existingSections);
        }

        Filing filing = filingRepository.findById(filingId).orElse(null);
        if (filing == null) {
            logger.warn("Filing not found: {}", filingId);
            return Mono.just(new ArrayList<>());
        }

        return fetchAndParseFiling(filing)
                .map(sections -> {
                    if (!sections.isEmpty()) {
                        // Save extracted sections to database
                        List<FilingSection> savedSections = sectionRepository.saveAll(sections);
                        logger.info("Saved {} sections for filing {}", savedSections.size(), filingId);
                        return savedSections;
                    }
                    return sections;
                })
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

            // Try HTML-based extraction first (more accurate)
            sections.addAll(extractSectionsFromHtml(doc, filing));

            // If HTML extraction failed, fall back to text-based extraction
            if (sections.isEmpty()) {
                logger.warn("HTML-based extraction failed for {}, falling back to text-based extraction", filing.getAccessionNo());
                String fullText = doc.text();

                // Extract Item 1A (Risk Factors) - only for 10-K and 10-Q
                if (filing.getForm().matches("10-[KQ]")) {
                    extractSection(filing.getId(), "Item1A", fullText, ITEM_1A_PATTERN)
                            .ifPresent(sections::add);
                }

                // Extract Item 7 (MD&A) - only for 10-K and 10-Q
                if (filing.getForm().matches("10-[KQ]")) {
                    logger.debug("Attempting to extract Item 7 for filing {}", filing.getAccessionNo());
                    extractSection(filing.getId(), "Item7", fullText, ITEM_7_PATTERN)
                            .ifPresentOrElse(
                                    sections::add,
                                    () -> logger.warn("Item 7 not found for filing {}", filing.getAccessionNo())
                            );

                    // Extract Item 7A (Market Risk) - only for 10-K
                    if (filing.getForm().equals("10-K")) {
                        extractSection(filing.getId(), "Item7A", fullText, ITEM_7A_PATTERN)
                                .ifPresent(sections::add);
                    }
                }
            }

            logger.info("Extracted {} sections from filing: {}", sections.size(), filing.getAccessionNo());

        } catch (Exception e) {
            logger.error("Error parsing document for filing {}: {}", filing.getAccessionNo(), e.getMessage());
        }

        return sections;
    }

    /**
     * Extract sections using HTML structure (anchors, divs, etc.)
     * This is more reliable than text-based pattern matching
     */
    private List<FilingSection> extractSectionsFromHtml(Document doc, Filing filing) {
        List<FilingSection> sections = new ArrayList<>();

        // Strategy 1: Look for anchor tags with names like "item1a", "item7", etc.
        // Common pattern: <a name="item1a"></a> or <a id="item1a"></a>
        extractByAnchor(doc, filing.getId(), "Item1A", sections);
        extractByAnchor(doc, filing.getId(), "Item7", sections);
        if (filing.getForm().equals("10-K")) {
            extractByAnchor(doc, filing.getId(), "Item7A", sections);
        }

        // Strategy 2: If no anchors found, look for div/span with specific IDs or classes
        if (sections.isEmpty()) {
            extractByDivId(doc, filing.getId(), sections);
        }

        return sections;
    }

    /**
     * Extract section by finding anchor tags
     */
    private void extractByAnchor(Document doc, Long filingId, String itemName, List<FilingSection> sections) {
        // Try various anchor name patterns
        String[] patterns = {
            itemName.toLowerCase(),  // "item1a", "item7"
            itemName.toLowerCase().replace("item", "item_"),  // "item_1a", "item_7"
            itemName.toLowerCase().replace("item", "item"),  // "item1a"
            itemName.toLowerCase().replace("item", "item "),  // "item 1a"
            itemName.substring(4).toLowerCase(),  // "1a", "7"
            "s" + itemName.substring(4).toLowerCase()  // "s1a", "s7" (some filings use this)
        };

        for (String pattern : patterns) {
            // Try name attribute
            org.jsoup.nodes.Element anchor = doc.selectFirst(String.format("a[name~=(?i)%s]", pattern));
            if (anchor == null) {
                // Try id attribute
                anchor = doc.selectFirst(String.format("a[id~=(?i)%s]", pattern));
            }
            if (anchor == null) {
                // Try div with id
                anchor = doc.selectFirst(String.format("div[id~=(?i)%s]", pattern));
            }

            if (anchor != null) {
                String sectionText = extractTextFromAnchor(anchor);
                if (sectionText != null && sectionText.length() >= MIN_PARAGRAPH_LENGTH) {
                    FilingSection section = new FilingSection();
                    section.setFilingId(filingId);
                    section.setSection(itemName);
                    section.setText(cleanParagraphs(sectionText));
                    section.setTextHash(calculateHash(sectionText));
                    section.setCharCount(sectionText.length());
                    sections.add(section);
                    logger.info("Extracted {} using anchor pattern: {}", itemName, pattern);
                    return;
                }
            }
        }
    }

    /**
     * Extract text content from an anchor point to the next section
     * Uses simple traversal from anchor to next Item section
     */
    private String extractTextFromAnchor(org.jsoup.nodes.Element anchor) {
        StringBuilder text = new StringBuilder();

        // Find the parent container that has the actual content
        org.jsoup.nodes.Element container = anchor.parent();
        if (container == null) {
            return "";
        }

        // Get all siblings after the anchor until we hit the next Item section
        org.jsoup.nodes.Element current = anchor.nextElementSibling();

        // If anchor has no next sibling, try getting content from parent's siblings
        if (current == null) {
            current = container.nextElementSibling();
        }

        int elementCount = 0;
        int maxElements = 200;

        while (current != null && elementCount < maxElements) {
            elementCount++;

            // Get the complete text of this element
            String fullText = current.text();

            // Stop if we find the next Item section
            // Check if this looks like a new Item header
            if (fullText.matches("^\\s*ITEM\\s+\\d+[A-Z]?[.:\\s].*") && elementCount > 2) {
                logger.debug("Found next section after {} elements", elementCount);
                break;
            }

            // Add the text from this element
            if (!fullText.trim().isEmpty()) {
                text.append(fullText).append(" ");
            }

            // Move to next sibling
            current = current.nextElementSibling();

            // Stop if we've collected enough text
            if (text.length() > MAX_PARAGRAPH_LENGTH) {
                logger.debug("Reached max length at {} characters", text.length());
                break;
            }
        }

        String result = text.toString().trim();
        logger.debug("Extracted {} characters from anchor after {} elements", result.length(), elementCount);

        // If we didn't get much text, it might be that content is in nested structure
        // Return empty to trigger fallback
        if (result.length() < MIN_PARAGRAPH_LENGTH) {
            logger.warn("Extracted text too short ({} chars), returning empty to trigger fallback", result.length());
            return "";
        }

        return result;
    }

    /**
     * Extract sections by looking for div/span elements with item-related IDs
     */
    private void extractByDivId(Document doc, Long filingId, List<FilingSection> sections) {
        // This is a fallback method - implementation can be added if needed
        logger.debug("Div-based extraction not yet implemented for filing {}", filingId);
    }

    private java.util.Optional<FilingSection> extractSection(Long filingId, String sectionName,
                                                              String fullText, Pattern startPattern) {
        try {
            Matcher startMatcher = startPattern.matcher(fullText);

            // Find all matches to skip Table of Contents
            int matchCount = 0;
            int startPos = -1;
            while (startMatcher.find()) {
                matchCount++;
                // First match is usually in TOC, second match (or first if only one) is actual section
                // Use the last match if multiple, or first if only one exists
                startPos = startMatcher.start();

                // Check if this looks like TOC by looking for multiple Item references nearby
                String context = fullText.substring(
                    Math.max(0, startPos - 200),
                    Math.min(fullText.length(), startPos + 500)
                );

                // If we see many "Item" references in a short span, it's likely TOC
                long itemCount = Pattern.compile("Item\\s+[0-9]").matcher(context).results().count();
                if (itemCount < 3) {
                    // This looks like actual content, not TOC
                    break;
                }
            }

            if (startPos == -1) {
                logger.warn("Section {} not found in filing {} - no matches found for pattern", sectionName, filingId);
                return java.util.Optional.empty();
            }

            logger.debug("Found section {} at position {} (after {} matches)", sectionName, startPos, matchCount);

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
