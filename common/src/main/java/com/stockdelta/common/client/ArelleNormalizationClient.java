package com.stockdelta.common.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Client for communicating with the Arelle XBRL Normalization Service
 * Handles normalization and DQC validation requests
 */
@Component
public class ArelleNormalizationClient {

    private static final Logger logger = LoggerFactory.getLogger(ArelleNormalizationClient.class);

    private final WebClient webClient;

    public ArelleNormalizationClient(
            @Value("${normalization.service.url:http://localhost:5000}") String serviceUrl,
            WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl(serviceUrl)
                .build();
        logger.info("Arelle Normalization Client initialized with URL: {}", serviceUrl);
    }

    /**
     * Normalize an XBRL filing to FAC concepts
     *
     * @param filingUrl    URL to the XBRL instance document
     * @param cik          Company CIK
     * @param accessionNo  Filing accession number (optional)
     * @return Normalized XBRL response
     */
    public Mono<NormalizedXbrlResponse> normalize(String filingUrl, String cik, String accessionNo) {
        logger.info("Requesting normalization for filing: {} (CIK: {})", filingUrl, cik);

        NormalizationRequest request = new NormalizationRequest(filingUrl, cik, accessionNo);

        return webClient.post()
                .uri("/normalize")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(NormalizedXbrlResponse.class)
                .timeout(Duration.ofMinutes(5))
                .doOnSuccess(response -> logger.info("Normalization completed: {} concepts extracted",
                        response.getConceptCount()))
                .doOnError(error -> logger.error("Normalization failed for {}: {}",
                        filingUrl, error.getMessage()));
    }

    /**
     * Validate an XBRL filing using DQC rules
     *
     * @param filingUrl URL to the XBRL instance document
     * @return DQC validation result
     */
    public Mono<DqcValidationResult> validate(String filingUrl) {
        logger.info("Requesting DQC validation for filing: {}", filingUrl);

        ValidationRequest request = new ValidationRequest(filingUrl);

        return webClient.post()
                .uri("/validate")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(DqcValidationResult.class)
                .timeout(Duration.ofMinutes(5))
                .doOnSuccess(result -> logger.info("Validation completed: {} errors, {} warnings",
                        result.getErrorCount(), result.getWarningCount()))
                .doOnError(error -> logger.error("Validation failed for {}: {}",
                        filingUrl, error.getMessage()));
    }

    /**
     * Check health of the normalization service
     *
     * @return Health status
     */
    public Mono<Map<String, Object>> checkHealth() {
        return webClient.get()
                .uri("/health")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(10))
                .doOnError(error -> logger.error("Health check failed: {}", error.getMessage()));
    }

    /**
     * Get list of supported FAC concepts
     *
     * @return List of concept names
     */
    public Mono<Map<String, Object>> getSupportedConcepts() {
        return webClient.get()
                .uri("/concepts")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(30));
    }

    // DTO Classes

    public static class NormalizationRequest {
        @JsonProperty("filing_url")
        private String filingUrl;

        private String cik;

        @JsonProperty("accession_no")
        private String accessionNo;

        public NormalizationRequest() {}

        public NormalizationRequest(String filingUrl, String cik, String accessionNo) {
            this.filingUrl = filingUrl;
            this.cik = cik;
            this.accessionNo = accessionNo;
        }

        public String getFilingUrl() { return filingUrl; }
        public void setFilingUrl(String filingUrl) { this.filingUrl = filingUrl; }

        public String getCik() { return cik; }
        public void setCik(String cik) { this.cik = cik; }

        public String getAccessionNo() { return accessionNo; }
        public void setAccessionNo(String accessionNo) { this.accessionNo = accessionNo; }
    }

    public static class ValidationRequest {
        @JsonProperty("filing_url")
        private String filingUrl;

        public ValidationRequest() {}

        public ValidationRequest(String filingUrl) {
            this.filingUrl = filingUrl;
        }

        public String getFilingUrl() { return filingUrl; }
        public void setFilingUrl(String filingUrl) { this.filingUrl = filingUrl; }
    }

    public static class NormalizedXbrlResponse {
        @JsonProperty("filing_url")
        private String filingUrl;

        private String cik;

        private List<NormalizedConcept> concepts;

        private Map<String, Object> metadata;

        @JsonProperty("processing_time_ms")
        private Integer processingTimeMs;

        @JsonProperty("concept_count")
        private Integer conceptCount;

        // Getters and Setters
        public String getFilingUrl() { return filingUrl; }
        public void setFilingUrl(String filingUrl) { this.filingUrl = filingUrl; }

        public String getCik() { return cik; }
        public void setCik(String cik) { this.cik = cik; }

        public List<NormalizedConcept> getConcepts() { return concepts; }
        public void setConcepts(List<NormalizedConcept> concepts) { this.concepts = concepts; }

        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

        public Integer getProcessingTimeMs() { return processingTimeMs; }
        public void setProcessingTimeMs(Integer processingTimeMs) { this.processingTimeMs = processingTimeMs; }

        public Integer getConceptCount() { return conceptCount; }
        public void setConceptCount(Integer conceptCount) { this.conceptCount = conceptCount; }
    }

    public static class NormalizedConcept {
        private String concept;

        private BigDecimal value;

        @JsonProperty("period_type")
        private String periodType;

        @JsonProperty("context_ref")
        private String contextRef;

        private String unit;

        @JsonProperty("start_date")
        private String startDate;

        @JsonProperty("end_date")
        private String endDate;

        @JsonProperty("quality_score")
        private BigDecimal qualityScore;

        private String source;

        // Getters and Setters
        public String getConcept() { return concept; }
        public void setConcept(String concept) { this.concept = concept; }

        public BigDecimal getValue() { return value; }
        public void setValue(BigDecimal value) { this.value = value; }

        public String getPeriodType() { return periodType; }
        public void setPeriodType(String periodType) { this.periodType = periodType; }

        public String getContextRef() { return contextRef; }
        public void setContextRef(String contextRef) { this.contextRef = contextRef; }

        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }

        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }

        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }

        public BigDecimal getQualityScore() { return qualityScore; }
        public void setQualityScore(BigDecimal qualityScore) { this.qualityScore = qualityScore; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }

    public static class DqcValidationResult {
        @JsonProperty("filing_url")
        private String filingUrl;

        private List<ValidationIssue> errors;

        private List<ValidationIssue> warnings;

        private List<ValidationIssue> info;

        @JsonProperty("error_count")
        private Integer errorCount;

        @JsonProperty("warning_count")
        private Integer warningCount;

        @JsonProperty("info_count")
        private Integer infoCount;

        @JsonProperty("processing_time_ms")
        private Integer processingTimeMs;

        // Getters and Setters
        public String getFilingUrl() { return filingUrl; }
        public void setFilingUrl(String filingUrl) { this.filingUrl = filingUrl; }

        public List<ValidationIssue> getErrors() { return errors; }
        public void setErrors(List<ValidationIssue> errors) { this.errors = errors; }

        public List<ValidationIssue> getWarnings() { return warnings; }
        public void setWarnings(List<ValidationIssue> warnings) { this.warnings = warnings; }

        public List<ValidationIssue> getInfo() { return info; }
        public void setInfo(List<ValidationIssue> info) { this.info = info; }

        public Integer getErrorCount() { return errorCount; }
        public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }

        public Integer getWarningCount() { return warningCount; }
        public void setWarningCount(Integer warningCount) { this.warningCount = warningCount; }

        public Integer getInfoCount() { return infoCount; }
        public void setInfoCount(Integer infoCount) { this.infoCount = infoCount; }

        public Integer getProcessingTimeMs() { return processingTimeMs; }
        public void setProcessingTimeMs(Integer processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    }

    public static class ValidationIssue {
        @JsonProperty("rule_id")
        private String ruleId;

        private String severity;

        private String message;

        @JsonProperty("affected_concept")
        private String affectedConcept;

        @JsonProperty("line_number")
        private Integer lineNumber;

        // Getters and Setters
        public String getRuleId() { return ruleId; }
        public void setRuleId(String ruleId) { this.ruleId = ruleId; }

        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getAffectedConcept() { return affectedConcept; }
        public void setAffectedConcept(String affectedConcept) { this.affectedConcept = affectedConcept; }

        public Integer getLineNumber() { return lineNumber; }
        public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }
    }
}
