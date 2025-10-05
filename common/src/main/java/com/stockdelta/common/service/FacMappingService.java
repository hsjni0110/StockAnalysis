package com.stockdelta.common.service;

import com.stockdelta.common.entity.XbrlTagMapping;
import com.stockdelta.common.repository.XbrlTagMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * FAC (Fundamental Accounting Concepts) Mapping Service
 * Maps XBRL tags to standardized FAC concepts for normalization
 */
@Service
@Transactional
public class FacMappingService {

    private static final Logger logger = LoggerFactory.getLogger(FacMappingService.class);

    private final XbrlTagMappingRepository mappingRepository;

    // Pattern-based inference rules for common concepts
    private static final Map<String, List<Pattern>> CONCEPT_PATTERNS = new HashMap<>();

    static {
        // Revenue patterns
        CONCEPT_PATTERNS.put("Revenue", List.of(
                Pattern.compile(".*Revenue.*", Pattern.CASE_INSENSITIVE),
                Pattern.compile(".*Sales.*", Pattern.CASE_INSENSITIVE)
        ));

        // Asset patterns
        CONCEPT_PATTERNS.put("Assets", List.of(
                Pattern.compile("^Assets$", Pattern.CASE_INSENSITIVE),
                Pattern.compile(".*TotalAssets.*", Pattern.CASE_INSENSITIVE)
        ));

        // Liability patterns
        CONCEPT_PATTERNS.put("Liabilities", List.of(
                Pattern.compile("^Liabilities$", Pattern.CASE_INSENSITIVE),
                Pattern.compile(".*TotalLiabilities.*", Pattern.CASE_INSENSITIVE)
        ));

        // Equity patterns
        CONCEPT_PATTERNS.put("Equity", List.of(
                Pattern.compile(".*Equity$", Pattern.CASE_INSENSITIVE),
                Pattern.compile(".*StockholdersEquity.*", Pattern.CASE_INSENSITIVE)
        ));

        // Cash patterns
        CONCEPT_PATTERNS.put("Cash", List.of(
                Pattern.compile("^Cash$", Pattern.CASE_INSENSITIVE),
                Pattern.compile(".*CashAndCashEquivalents.*", Pattern.CASE_INSENSITIVE)
        ));

        // Operating Income patterns
        CONCEPT_PATTERNS.put("OperatingIncome", List.of(
                Pattern.compile(".*OperatingIncome.*", Pattern.CASE_INSENSITIVE)
        ));

        // Net Income patterns
        CONCEPT_PATTERNS.put("NetIncome", List.of(
                Pattern.compile(".*NetIncome.*", Pattern.CASE_INSENSITIVE),
                Pattern.compile("^ProfitLoss$", Pattern.CASE_INSENSITIVE)
        ));
    }

    @Autowired
    public FacMappingService(XbrlTagMappingRepository mappingRepository) {
        this.mappingRepository = mappingRepository;
    }

    /**
     * Map an XBRL tag to a FAC fundamental concept
     *
     * @param tag      XBRL tag name
     * @param taxonomy Taxonomy (e.g., 'us-gaap')
     * @return FAC concept name or null if no mapping found
     */
    public String mapToFundamentalConcept(String tag, String taxonomy) {
        if (tag == null || tag.trim().isEmpty()) {
            return null;
        }

        // 1. Try direct lookup from database
        Optional<XbrlTagMapping> mapping = mappingRepository.findBySourceTagAndTaxonomy(tag, taxonomy);
        if (mapping.isPresent()) {
            logger.debug("Direct mapping found: {} -> {}", tag, mapping.get().getFundamentalConcept());
            return mapping.get().getFundamentalConcept();
        }

        // 2. Try pattern-based inference
        String inferredConcept = inferConceptFromPattern(tag);
        if (inferredConcept != null) {
            logger.debug("Pattern-based mapping: {} -> {}", tag, inferredConcept);

            // Optionally save inferred mapping for future use
            saveInferredMapping(tag, taxonomy, inferredConcept, BigDecimal.valueOf(0.85), "pattern-match");

            return inferredConcept;
        }

        // 3. No mapping found
        logger.debug("No FAC mapping found for: {} (taxonomy: {})", tag, taxonomy);
        return null;
    }

    /**
     * Get confidence score for a mapping
     *
     * @param tag      XBRL tag name
     * @param taxonomy Taxonomy
     * @return Confidence score (0.0 to 1.0) or 1.0 if not found
     */
    public BigDecimal getConfidenceScore(String tag, String taxonomy) {
        return mappingRepository.findBySourceTagAndTaxonomy(tag, taxonomy)
                .map(XbrlTagMapping::getConfidenceScore)
                .orElse(BigDecimal.ONE);
    }

    /**
     * Infer FAC concept from tag name using pattern matching
     *
     * @param tag XBRL tag name
     * @return Inferred concept or null
     */
    private String inferConceptFromPattern(String tag) {
        for (Map.Entry<String, List<Pattern>> entry : CONCEPT_PATTERNS.entrySet()) {
            String concept = entry.getKey();
            List<Pattern> patterns = entry.getValue();

            for (Pattern pattern : patterns) {
                if (pattern.matcher(tag).matches()) {
                    return concept;
                }
            }
        }
        return null;
    }

    /**
     * Save an inferred mapping to the database
     *
     * @param tag              Source tag
     * @param taxonomy         Taxonomy
     * @param concept          Fundamental concept
     * @param confidenceScore  Confidence score
     * @param ruleSource       Rule source
     */
    private void saveInferredMapping(String tag, String taxonomy, String concept,
                                      BigDecimal confidenceScore, String ruleSource) {
        try {
            // Check if already exists
            if (mappingRepository.existsBySourceTagAndTaxonomy(tag, taxonomy)) {
                return;
            }

            XbrlTagMapping mapping = new XbrlTagMapping(tag, taxonomy, concept, confidenceScore, ruleSource);
            mappingRepository.save(mapping);
            logger.info("Saved inferred mapping: {} ({}) -> {} (confidence: {})",
                    tag, taxonomy, concept, confidenceScore);
        } catch (Exception e) {
            logger.warn("Failed to save inferred mapping for {} -> {}: {}",
                    tag, concept, e.getMessage());
        }
    }

    /**
     * Get all supported FAC concepts
     *
     * @return List of concept names
     */
    public List<String> getSupportedConcepts() {
        return mappingRepository.findDistinctFundamentalConcepts();
    }

    /**
     * Get all mappings for a specific concept
     *
     * @param concept FAC concept name
     * @return List of mappings
     */
    public List<XbrlTagMapping> getMappingsForConcept(String concept) {
        return mappingRepository.findByFundamentalConcept(concept);
    }

    /**
     * Load FAC mappings from a predefined source (can be extended to load from CSV/JSON)
     */
    public void loadCoreMappings() {
        logger.info("Loading core FAC mappings...");

        List<XbrlTagMapping> coreMappings = Arrays.asList(
                // Revenue concepts
                new XbrlTagMapping("Revenues", "us-gaap", "Revenue", BigDecimal.ONE, "fac-standard"),
                new XbrlTagMapping("Revenue", "us-gaap", "Revenue", BigDecimal.ONE, "fac-standard"),
                new XbrlTagMapping("SalesRevenueNet", "us-gaap", "Revenue", BigDecimal.ONE, "fac-standard"),
                new XbrlTagMapping("RevenueFromContractWithCustomerExcludingAssessedTax", "us-gaap", "Revenue",
                        BigDecimal.ONE, "fac-standard"),

                // Operating Income concepts
                new XbrlTagMapping("OperatingIncomeLoss", "us-gaap", "OperatingIncome",
                        BigDecimal.ONE, "fac-standard"),

                // Net Income concepts
                new XbrlTagMapping("NetIncomeLoss", "us-gaap", "NetIncome", BigDecimal.ONE, "fac-standard"),
                new XbrlTagMapping("ProfitLoss", "us-gaap", "NetIncome", BigDecimal.ONE, "fac-standard"),

                // Asset concepts
                new XbrlTagMapping("Assets", "us-gaap", "Assets", BigDecimal.ONE, "fac-standard"),
                new XbrlTagMapping("AssetsCurrent", "us-gaap", "CurrentAssets", BigDecimal.ONE, "fac-standard"),

                // Liability concepts
                new XbrlTagMapping("Liabilities", "us-gaap", "Liabilities", BigDecimal.ONE, "fac-standard"),
                new XbrlTagMapping("LiabilitiesCurrent", "us-gaap", "CurrentLiabilities",
                        BigDecimal.ONE, "fac-standard"),

                // Equity concepts
                new XbrlTagMapping("StockholdersEquity", "us-gaap", "Equity", BigDecimal.ONE, "fac-standard"),

                // Cash concepts
                new XbrlTagMapping("Cash", "us-gaap", "Cash", BigDecimal.ONE, "fac-standard"),
                new XbrlTagMapping("CashAndCashEquivalentsAtCarryingValue", "us-gaap", "Cash",
                        BigDecimal.ONE, "fac-standard"),

                // Inventory
                new XbrlTagMapping("InventoryNet", "us-gaap", "Inventory", BigDecimal.ONE, "fac-standard"),

                // CapEx
                new XbrlTagMapping("PaymentsToAcquirePropertyPlantAndEquipment", "us-gaap",
                        "CapitalExpenditures", BigDecimal.ONE, "fac-standard"),

                // EPS
                new XbrlTagMapping("EarningsPerShareBasic", "us-gaap", "EPS", BigDecimal.ONE, "fac-standard"),
                new XbrlTagMapping("EarningsPerShareDiluted", "us-gaap", "EPSDiluted",
                        BigDecimal.ONE, "fac-standard")
        );

        int savedCount = 0;
        for (XbrlTagMapping mapping : coreMappings) {
            try {
                if (!mappingRepository.existsBySourceTagAndTaxonomy(
                        mapping.getSourceTag(), mapping.getTaxonomy())) {
                    mappingRepository.save(mapping);
                    savedCount++;
                }
            } catch (Exception e) {
                logger.warn("Failed to save mapping for {}: {}", mapping.getSourceTag(), e.getMessage());
            }
        }

        logger.info("Loaded {} core FAC mappings", savedCount);
    }

    /**
     * Get mapping statistics
     *
     * @return Map of statistics
     */
    public Map<String, Object> getMappingStatistics() {
        Map<String, Object> stats = new HashMap<>();

        long totalMappings = mappingRepository.count();
        List<String> concepts = mappingRepository.findDistinctFundamentalConcepts();

        stats.put("total_mappings", totalMappings);
        stats.put("unique_concepts", concepts.size());
        stats.put("concepts", concepts);

        // Count by rule source
        Map<String, Long> bySource = new HashMap<>();
        bySource.put("fac-standard", (long) mappingRepository.findByRuleSource("fac-standard").size());
        bySource.put("pattern-match", (long) mappingRepository.findByRuleSource("pattern-match").size());
        bySource.put("manual", (long) mappingRepository.findByRuleSource("manual").size());
        stats.put("by_source", bySource);

        return stats;
    }
}
