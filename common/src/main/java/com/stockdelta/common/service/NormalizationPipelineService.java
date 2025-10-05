package com.stockdelta.common.service;

import com.stockdelta.common.client.ArelleNormalizationClient;
import com.stockdelta.common.entity.DataQualityValidation;
import com.stockdelta.common.entity.Filing;
import com.stockdelta.common.entity.NormalizedFinancial;
import com.stockdelta.common.repository.DataQualityValidationRepository;
import com.stockdelta.common.repository.FilingRepository;
import com.stockdelta.common.repository.NormalizedFinancialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Normalization Pipeline Service
 * Orchestrates the XBRL normalization process using Arelle and FAC mapping
 */
@Service
@Transactional
public class NormalizationPipelineService {

    private static final Logger logger = LoggerFactory.getLogger(NormalizationPipelineService.class);

    private final FilingRepository filingRepository;
    private final NormalizedFinancialRepository normalizedFinancialRepository;
    private final DataQualityValidationRepository validationRepository;
    private final ArelleNormalizationClient arelleClient;

    @Autowired
    public NormalizationPipelineService(
            FilingRepository filingRepository,
            NormalizedFinancialRepository normalizedFinancialRepository,
            DataQualityValidationRepository validationRepository,
            ArelleNormalizationClient arelleClient) {
        this.filingRepository = filingRepository;
        this.normalizedFinancialRepository = normalizedFinancialRepository;
        this.validationRepository = validationRepository;
        this.arelleClient = arelleClient;
    }

    /**
     * Process a filing through the normalization pipeline
     *
     * @param filingId Filing ID
     * @return Normalization result
     */
    public Mono<NormalizationResult> processFiling(Long filingId) {
        logger.info("Starting normalization pipeline for filing ID: {}", filingId);

        return Mono.fromCallable(() -> filingRepository.findById(filingId))
                .flatMap(filingOpt -> {
                    if (filingOpt.isEmpty()) {
                        return Mono.error(new IllegalArgumentException("Filing not found: " + filingId));
                    }

                    Filing filing = filingOpt.get();

                    // Check if filing has XBRL data URL
                    if (filing.getPrimaryDocUrl() == null || filing.getPrimaryDocUrl().isEmpty()) {
                        return Mono.error(new IllegalArgumentException(
                                "Filing has no primary document URL: " + filingId));
                    }

                    // Delete existing normalized data for this filing (re-normalization)
                    cleanupExistingData(filingId);

                    // Get XBRL instance URL
                    String xbrlUrl = getXbrlInstanceUrl(filing);
                    logger.info("Using XBRL URL for filing {}: {}", filingId, xbrlUrl);

                    // Step 1: Normalize using Arelle
                    return arelleClient.normalize(xbrlUrl, filing.getCik(), filing.getAccessionNo())
                            .flatMap(normalizedResponse -> {
                                logger.info("Received normalized response with {} concepts",
                                    normalizedResponse.getConcepts() != null ? normalizedResponse.getConcepts().size() : 0);

                                // Step 2: Validate using DQC
                                return arelleClient.validate(xbrlUrl)
                                        .map(validationResult -> {
                                            logger.info("Received validation result: {} errors, {} warnings",
                                                validationResult.getErrorCount(), validationResult.getWarningCount());

                                            // Step 3: Save normalized financials
                                            List<NormalizedFinancial> financials =
                                                    convertToNormalizedFinancials(normalizedResponse, filing);
                                            logger.info("Converted {} normalized financials, saving to DB...", financials.size());
                                            normalizedFinancialRepository.saveAll(financials);
                                            logger.info("Successfully saved {} normalized financials to DB", financials.size());

                                            // Step 4: Save DQC validation results
                                            List<DataQualityValidation> validations =
                                                    convertToValidations(validationResult, filingId);
                                            logger.info("Converted {} validations, saving to DB...", validations.size());
                                            validationRepository.saveAll(validations);
                                            logger.info("Successfully saved {} validations to DB", validations.size());

                                            // Step 5: Build result
                                            NormalizationResult result = new NormalizationResult();
                                            result.setFilingId(filingId);
                                            result.setNormalizedConceptCount(financials.size());
                                            result.setErrorCount(validationResult.getErrorCount());
                                            result.setWarningCount(validationResult.getWarningCount());
                                            result.setStatus("completed");
                                            result.setProcessingTimeMs(
                                                    normalizedResponse.getProcessingTimeMs() +
                                                            validationResult.getProcessingTimeMs());

                                            logger.info("Normalization completed for filing {}: {} concepts, {} errors, {} warnings",
                                                    filingId, financials.size(),
                                                    validationResult.getErrorCount(),
                                                    validationResult.getWarningCount());

                                            return result;
                                        });
                            });
                })
                .onErrorResume(error -> {
                    logger.error("Normalization pipeline failed for filing {}: {}",
                            filingId, error.getMessage(), error);

                    NormalizationResult result = new NormalizationResult();
                    result.setFilingId(filingId);
                    result.setStatus("failed");
                    result.setErrorMessage(error.getMessage());
                    return Mono.just(result);
                });
    }

    /**
     * Cleanup existing normalized data for a filing
     */
    private void cleanupExistingData(Long filingId) {
        try {
            if (normalizedFinancialRepository.existsByFilingId(filingId)) {
                normalizedFinancialRepository.deleteByFilingId(filingId);
                logger.debug("Deleted existing normalized financials for filing {}", filingId);
            }

            validationRepository.deleteByFilingId(filingId);
            logger.debug("Deleted existing DQC validations for filing {}", filingId);
        } catch (Exception e) {
            logger.warn("Error cleaning up existing data for filing {}: {}", filingId, e.getMessage());
        }
    }

    /**
     * Convert Arelle normalized response to NormalizedFinancial entities
     */
    private List<NormalizedFinancial> convertToNormalizedFinancials(
            ArelleNormalizationClient.NormalizedXbrlResponse response, Filing filing) {

        List<NormalizedFinancial> financials = new ArrayList<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_DATE;

        for (ArelleNormalizationClient.NormalizedConcept concept : response.getConcepts()) {
            NormalizedFinancial financial = new NormalizedFinancial();
            financial.setFilingId(filing.getId());
            financial.setConcept(concept.getConcept());
            financial.setValue(concept.getValue());
            financial.setPeriodType(concept.getPeriodType());
            financial.setContextRef(concept.getContextRef());
            financial.setUnit(concept.getUnit());

            // Parse dates
            if (concept.getStartDate() != null && !concept.getStartDate().isEmpty()) {
                try {
                    financial.setStartDate(LocalDate.parse(concept.getStartDate(), dateFormatter));
                } catch (Exception e) {
                    logger.debug("Failed to parse start date: {}", concept.getStartDate());
                }
            }

            if (concept.getEndDate() != null && !concept.getEndDate().isEmpty()) {
                try {
                    financial.setEndDate(LocalDate.parse(concept.getEndDate(), dateFormatter));
                } catch (Exception e) {
                    logger.debug("Failed to parse end date: {}", concept.getEndDate());
                }
            }

            financial.setQualityScore(concept.getQualityScore());
            financial.setSource(concept.getSource());

            financials.add(financial);
        }

        return financials;
    }

    /**
     * Convert DQC validation result to DataQualityValidation entities
     */
    private List<DataQualityValidation> convertToValidations(
            ArelleNormalizationClient.DqcValidationResult result, Long filingId) {

        List<DataQualityValidation> validations = new ArrayList<>();

        // Add errors
        if (result.getErrors() != null) {
            for (ArelleNormalizationClient.ValidationIssue issue : result.getErrors()) {
                validations.add(new DataQualityValidation(
                        filingId,
                        issue.getRuleId(),
                        "error",
                        issue.getMessage(),
                        issue.getAffectedConcept()
                ));
            }
        }

        // Add warnings
        if (result.getWarnings() != null) {
            for (ArelleNormalizationClient.ValidationIssue issue : result.getWarnings()) {
                validations.add(new DataQualityValidation(
                        filingId,
                        issue.getRuleId(),
                        "warning",
                        issue.getMessage(),
                        issue.getAffectedConcept()
                ));
            }
        }

        // Add info
        if (result.getInfo() != null) {
            for (ArelleNormalizationClient.ValidationIssue issue : result.getInfo()) {
                validations.add(new DataQualityValidation(
                        filingId,
                        issue.getRuleId(),
                        "info",
                        issue.getMessage(),
                        issue.getAffectedConcept()
                ));
            }
        }

        return validations;
    }

    /**
     * Check if a filing has been normalized
     */
    public boolean isNormalized(Long filingId) {
        return normalizedFinancialRepository.existsByFilingId(filingId);
    }

    /**
     * Get normalization statistics for a filing
     */
    public NormalizationStats getStats(Long filingId) {
        NormalizationStats stats = new NormalizationStats();
        stats.setFilingId(filingId);
        stats.setNormalizedConceptCount(normalizedFinancialRepository.countByFilingId(filingId));
        stats.setDistinctConcepts(normalizedFinancialRepository.findDistinctConceptsByFilingId(filingId));
        stats.setErrorCount(validationRepository.countErrorsByFilingId(filingId));
        stats.setWarningCount(validationRepository.countWarningsByFilingId(filingId));
        stats.setHasErrors(validationRepository.hasErrors(filingId));

        return stats;
    }

    /**
     * Convert primary_doc_url to XBRL instance URL
     * Converts .htm files to .xml XBRL instance documents
     */
    private String getXbrlInstanceUrl(Filing filing) {
        String primaryUrl = filing.getPrimaryDocUrl();

        if (primaryUrl == null || primaryUrl.isEmpty()) {
            return null;
        }

        // If it's an .htm file, try to convert to .xml
        if (primaryUrl.endsWith(".htm")) {
            // Extract the ticker/company from filename
            // Example: msft-10k_20190630.htm -> msft-20190630.xml
            String[] parts = primaryUrl.split("/");
            String filename = parts[parts.length - 1];
            String basePath = primaryUrl.substring(0, primaryUrl.lastIndexOf('/') + 1);

            // Try common naming patterns
            if (filename.contains("-10k") || filename.contains("-10q")) {
                // Pattern: msft-10k_20190630.htm -> msft-20190630.xml
                String xmlFilename = filename
                        .replaceFirst("-10[kq]_", "-")
                        .replace(".htm", ".xml");
                return basePath + xmlFilename;
            }
        }

        // Return original URL if no conversion needed
        return primaryUrl;
    }

    // Result classes

    public static class NormalizationResult {
        private Long filingId;
        private int normalizedConceptCount;
        private int errorCount;
        private int warningCount;
        private String status;
        private String errorMessage;
        private Integer processingTimeMs;

        // Getters and Setters
        public Long getFilingId() { return filingId; }
        public void setFilingId(Long filingId) { this.filingId = filingId; }

        public int getNormalizedConceptCount() { return normalizedConceptCount; }
        public void setNormalizedConceptCount(int normalizedConceptCount) {
            this.normalizedConceptCount = normalizedConceptCount;
        }

        public int getErrorCount() { return errorCount; }
        public void setErrorCount(int errorCount) { this.errorCount = errorCount; }

        public int getWarningCount() { return warningCount; }
        public void setWarningCount(int warningCount) { this.warningCount = warningCount; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public Integer getProcessingTimeMs() { return processingTimeMs; }
        public void setProcessingTimeMs(Integer processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    }

    public static class NormalizationStats {
        private Long filingId;
        private long normalizedConceptCount;
        private List<String> distinctConcepts;
        private long errorCount;
        private long warningCount;
        private boolean hasErrors;

        // Getters and Setters
        public Long getFilingId() { return filingId; }
        public void setFilingId(Long filingId) { this.filingId = filingId; }

        public long getNormalizedConceptCount() { return normalizedConceptCount; }
        public void setNormalizedConceptCount(long normalizedConceptCount) {
            this.normalizedConceptCount = normalizedConceptCount;
        }

        public List<String> getDistinctConcepts() { return distinctConcepts; }
        public void setDistinctConcepts(List<String> distinctConcepts) {
            this.distinctConcepts = distinctConcepts;
        }

        public long getErrorCount() { return errorCount; }
        public void setErrorCount(long errorCount) { this.errorCount = errorCount; }

        public long getWarningCount() { return warningCount; }
        public void setWarningCount(long warningCount) { this.warningCount = warningCount; }

        public boolean isHasErrors() { return hasErrors; }
        public void setHasErrors(boolean hasErrors) { this.hasErrors = hasErrors; }
    }
}
