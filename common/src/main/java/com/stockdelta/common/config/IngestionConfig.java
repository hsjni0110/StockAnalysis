package com.stockdelta.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for data ingestion filing limits
 * Controls how many filings of each type to store for comparison analysis
 */
@Configuration
@ConfigurationProperties(prefix = "stockdelta.ingestion")
public class IngestionConfig {

    /**
     * Number of filings to keep per form type
     * Key: Form type (e.g., "10-K", "10-Q")
     * Value: Number of filings to retain
     *
     * Defaults:
     * - 10-K: 2 (current year + previous year)
     * - 10-Q: 4 (4 quarters for YoY comparison)
     * - 8-K: 5 (recent material events)
     * - Form 4: 10 (insider trading tracking)
     * - 13F-HR: 4 (quarterly institutional holdings)
     * - 13D/13G: 5 (activist/large holder events)
     */
    private Map<String, Integer> filingLimits = new HashMap<>();

    public IngestionConfig() {
        // Set default values
        filingLimits.put("10-K", 2);
        filingLimits.put("10-Q", 4);
        filingLimits.put("8-K", 5);
        filingLimits.put("4", 10);
        filingLimits.put("13F-HR", 4);
        filingLimits.put("13D", 5);
        filingLimits.put("13G", 5);
    }

    public Map<String, Integer> getFilingLimits() {
        return filingLimits;
    }

    public void setFilingLimits(Map<String, Integer> filingLimits) {
        this.filingLimits = filingLimits;
    }

    /**
     * Get filing limit for a specific form type
     * Returns default value of 5 if not configured
     */
    public int getFilingLimit(String formType) {
        return filingLimits.getOrDefault(formType, 5);
    }
}
