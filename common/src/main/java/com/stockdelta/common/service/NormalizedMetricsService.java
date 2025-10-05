package com.stockdelta.common.service;

import com.stockdelta.common.entity.Filing;
import com.stockdelta.common.entity.NormalizedFinancial;
import com.stockdelta.common.entity.NormalizedMetric;
import com.stockdelta.common.repository.FilingRepository;
import com.stockdelta.common.repository.NormalizedFinancialRepository;
import com.stockdelta.common.repository.NormalizedMetricRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Normalized Metrics Service
 * Calculates QoQ, YoY, and absolute metrics from normalized financial data
 */
@Service
@Transactional
public class NormalizedMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(NormalizedMetricsService.class);

    // Core concepts to track for metrics
    private static final List<String> CORE_CONCEPTS = Arrays.asList(
            "Revenue",
            "OperatingIncome",
            "NetIncome",
            "Assets",
            "CurrentAssets",
            "Liabilities",
            "CurrentLiabilities",
            "Equity",
            "Cash",
            "Inventory",
            "CapitalExpenditures",
            "EPS",
            "EPSDiluted"
    );

    private final FilingRepository filingRepository;
    private final NormalizedFinancialRepository normalizedFinancialRepository;
    private final NormalizedMetricRepository normalizedMetricRepository;

    @Autowired
    public NormalizedMetricsService(
            FilingRepository filingRepository,
            NormalizedFinancialRepository normalizedFinancialRepository,
            NormalizedMetricRepository normalizedMetricRepository) {
        this.filingRepository = filingRepository;
        this.normalizedFinancialRepository = normalizedFinancialRepository;
        this.normalizedMetricRepository = normalizedMetricRepository;
    }

    /**
     * Calculate all metrics for a filing
     *
     * @param filingId Filing ID
     * @return List of calculated metrics
     */
    public List<NormalizedMetric> calculateMetrics(Long filingId) {
        logger.info("Calculating metrics for filing ID: {}", filingId);

        Optional<Filing> currentFilingOpt = filingRepository.findById(filingId);
        if (currentFilingOpt.isEmpty()) {
            logger.warn("Filing not found: {}", filingId);
            return Collections.emptyList();
        }

        Filing currentFiling = currentFilingOpt.get();
        List<NormalizedMetric> metrics = new ArrayList<>();

        // Get normalized financials for current filing
        List<NormalizedFinancial> currentFinancials =
                normalizedFinancialRepository.findByFilingId(filingId);

        if (currentFinancials.isEmpty()) {
            logger.info("No normalized financials found for filing {}", filingId);
            return metrics;
        }

        // Store absolute values for core concepts
        metrics.addAll(saveAbsoluteMetrics(currentFinancials, filingId));

        // Find previous filings for QoQ and YoY comparison
        Optional<Filing> qoqFilingOpt = findPreviousFilingByQuarter(currentFiling);
        Optional<Filing> yoyFilingOpt = findPreviousFilingByYear(currentFiling);

        if (qoqFilingOpt.isEmpty() && yoyFilingOpt.isEmpty()) {
            logger.info("No previous filing found for comparison: {}", currentFiling.getAccessionNo());
            // Save absolute metrics before returning
            normalizedMetricRepository.saveAll(metrics);
            logger.info("Calculated {} absolute metrics for filing {}", metrics.size(), filingId);
            return metrics;
        }

        // Calculate QoQ if previous quarter filing exists
        if (qoqFilingOpt.isPresent()) {
            Filing qoqFiling = qoqFilingOpt.get();
            List<NormalizedFinancial> qoqFinancials =
                    normalizedFinancialRepository.findByFilingId(qoqFiling.getId());
            if (!qoqFinancials.isEmpty()) {
                metrics.addAll(calculateChanges(currentFinancials, qoqFinancials,
                        currentFiling, qoqFiling, filingId, "QoQ"));
                logger.info("Calculated QoQ metrics using filing {}", qoqFiling.getId());
            }
        }

        // Calculate YoY if previous year filing exists
        if (yoyFilingOpt.isPresent()) {
            Filing yoyFiling = yoyFilingOpt.get();
            List<NormalizedFinancial> yoyFinancials =
                    normalizedFinancialRepository.findByFilingId(yoyFiling.getId());
            if (!yoyFinancials.isEmpty()) {
                metrics.addAll(calculateChanges(currentFinancials, yoyFinancials,
                        currentFiling, yoyFiling, filingId, "YoY"));
                logger.info("Calculated YoY metrics using filing {}", yoyFiling.getId());
            }
        }

        // Save all metrics
        normalizedMetricRepository.saveAll(metrics);
        logger.info("Calculated {} total metrics for filing {}", metrics.size(), filingId);

        return metrics;
    }

    /**
     * Save absolute value metrics
     */
    private List<NormalizedMetric> saveAbsoluteMetrics(
            List<NormalizedFinancial> financials, Long filingId) {

        List<NormalizedMetric> metrics = new ArrayList<>();

        // Group by concept and get latest value for each
        Map<String, NormalizedFinancial> latestByConcept = financials.stream()
                .filter(f -> CORE_CONCEPTS.contains(f.getConcept()))
                .filter(f -> f.getValue() != null)
                .collect(Collectors.toMap(
                        NormalizedFinancial::getConcept,
                        f -> f,
                        (f1, f2) -> {
                            // Prefer the one with later end date or higher quality score
                            if (f1.getEndDate() != null && f2.getEndDate() != null) {
                                int dateCompare = f1.getEndDate().compareTo(f2.getEndDate());
                                if (dateCompare != 0) {
                                    return dateCompare > 0 ? f1 : f2;
                                }
                            }
                            return f1.getQualityScore().compareTo(f2.getQualityScore()) >= 0 ? f1 : f2;
                        }
                ));

        for (Map.Entry<String, NormalizedFinancial> entry : latestByConcept.entrySet()) {
            String concept = entry.getKey();
            NormalizedFinancial financial = entry.getValue();

            NormalizedMetric metric = new NormalizedMetric(
                    filingId,
                    concept,
                    "Abs",
                    financial.getValue(),
                    financial.getQualityScore()
            );
            metrics.add(metric);
        }

        return metrics;
    }

    /**
     * Calculate QoQ/YoY changes
     */
    private List<NormalizedMetric> calculateChanges(
            List<NormalizedFinancial> currentFinancials,
            List<NormalizedFinancial> previousFinancials,
            Filing currentFiling,
            Filing previousFiling,
            Long filingId,
            String basis) {

        List<NormalizedMetric> metrics = new ArrayList<>();

        // Group financials by concept
        Map<String, BigDecimal> currentValues = getLatestValuesByConcept(currentFinancials);
        Map<String, BigDecimal> previousValues = getLatestValuesByConcept(previousFinancials);

        // Calculate percentage changes for core concepts
        for (String concept : CORE_CONCEPTS) {
            if (currentValues.containsKey(concept) && previousValues.containsKey(concept)) {
                BigDecimal currentValue = currentValues.get(concept);
                BigDecimal previousValue = previousValues.get(concept);

                BigDecimal percentChange = calculatePercentageChange(previousValue, currentValue);

                // Calculate average quality score
                BigDecimal avgQualityScore = getAverageQualityScore(
                        currentFinancials, previousFinancials, concept);

                NormalizedMetric metric = new NormalizedMetric(
                        filingId,
                        concept,
                        basis,
                        percentChange,
                        avgQualityScore
                );
                metrics.add(metric);

                logger.debug("Metric {}: {} -> {} ({}%)", concept, previousValue, currentValue, percentChange);
            }
        }

        return metrics;
    }

    /**
     * Get latest values grouped by concept
     */
    private Map<String, BigDecimal> getLatestValuesByConcept(List<NormalizedFinancial> financials) {
        return financials.stream()
                .filter(f -> CORE_CONCEPTS.contains(f.getConcept()))
                .filter(f -> f.getValue() != null)
                .collect(Collectors.toMap(
                        NormalizedFinancial::getConcept,
                        NormalizedFinancial::getValue,
                        (v1, v2) -> v1  // Keep first if duplicate
                ));
    }

    /**
     * Get average quality score for a concept across current and previous periods
     */
    private BigDecimal getAverageQualityScore(
            List<NormalizedFinancial> currentFinancials,
            List<NormalizedFinancial> previousFinancials,
            String concept) {

        List<BigDecimal> scores = new ArrayList<>();

        currentFinancials.stream()
                .filter(f -> concept.equals(f.getConcept()))
                .findFirst()
                .ifPresent(f -> scores.add(f.getQualityScore()));

        previousFinancials.stream()
                .filter(f -> concept.equals(f.getConcept()))
                .findFirst()
                .ifPresent(f -> scores.add(f.getQualityScore()));

        if (scores.isEmpty()) {
            return BigDecimal.ONE;
        }

        BigDecimal sum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate percentage change
     */
    private BigDecimal calculatePercentageChange(BigDecimal previousValue, BigDecimal currentValue) {
        if (previousValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal change = currentValue.subtract(previousValue);
        BigDecimal percentage = change.divide(previousValue.abs(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return percentage.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Find previous quarter filing (3 months before)
     */
    private Optional<Filing> findPreviousFilingByQuarter(Filing current) {
        String form = current.getForm();
        String cik = current.getCik();
        LocalDate periodEnd = current.getPeriodEnd();

        if (periodEnd == null) {
            return Optional.empty();
        }

        List<Filing> previousFilings = filingRepository.findByCikAndFormOrderByFiledAtDesc(cik, form);

        return previousFilings.stream()
                .filter(f -> f.getPeriodEnd() != null && f.getPeriodEnd().isBefore(periodEnd))
                .filter(f -> {
                    long monthsDiff = java.time.temporal.ChronoUnit.MONTHS.between(f.getPeriodEnd(), periodEnd);
                    return monthsDiff >= 2 && monthsDiff <= 4;  // ~3 months
                })
                .findFirst();
    }

    /**
     * Find previous year filing (12 months before)
     */
    private Optional<Filing> findPreviousFilingByYear(Filing current) {
        String form = current.getForm();
        String cik = current.getCik();
        LocalDate periodEnd = current.getPeriodEnd();

        if (periodEnd == null) {
            return Optional.empty();
        }

        List<Filing> previousFilings = filingRepository.findByCikAndFormOrderByFiledAtDesc(cik, form);

        return previousFilings.stream()
                .filter(f -> f.getPeriodEnd() != null && f.getPeriodEnd().isBefore(periodEnd))
                .filter(f -> {
                    long monthsDiff = java.time.temporal.ChronoUnit.MONTHS.between(f.getPeriodEnd(), periodEnd);
                    return monthsDiff >= 11 && monthsDiff <= 13;  // ~12 months
                })
                .findFirst();
    }

    /**
     * Get heatmap data for a filing
     */
    public HeatmapData getHeatmapData(Long filingId) {
        List<NormalizedMetric> metrics = normalizedMetricRepository.findByFilingId(filingId);

        HeatmapData heatmap = new HeatmapData();
        heatmap.setFilingId(filingId);

        // Group by metric and basis
        Map<String, Map<String, BigDecimal>> data = new HashMap<>();

        for (NormalizedMetric metric : metrics) {
            String metricName = metric.getId().getMetric();
            String basis = metric.getId().getBasis();

            data.computeIfAbsent(metricName, k -> new HashMap<>())
                    .put(basis, metric.getValue());
        }

        // Convert to heatmap format
        List<HeatmapRow> rows = new ArrayList<>();
        for (Map.Entry<String, Map<String, BigDecimal>> entry : data.entrySet()) {
            HeatmapRow row = new HeatmapRow();
            row.setMetric(entry.getKey());
            row.setValues(entry.getValue());

            // Calculate Z-score for anomaly detection
            if (entry.getValue().containsKey("QoQ") || entry.getValue().containsKey("YoY")) {
                BigDecimal change = entry.getValue().getOrDefault("QoQ", entry.getValue().get("YoY"));
                if (change != null) {
                    row.setZScore(calculateZScore(change));
                }
            }

            rows.add(row);
        }

        heatmap.setRows(rows);
        return heatmap;
    }

    /**
     * Calculate Z-score for anomaly detection (simplified)
     */
    private double calculateZScore(BigDecimal value) {
        double absValue = Math.abs(value.doubleValue());

        if (absValue > 50) return 3.0;
        if (absValue > 30) return 2.5;
        if (absValue > 20) return 2.0;
        if (absValue > 10) return 1.5;
        return absValue / 10.0;
    }

    // DTO Classes

    public static class HeatmapData {
        private Long filingId;
        private List<HeatmapRow> rows;

        public Long getFilingId() { return filingId; }
        public void setFilingId(Long filingId) { this.filingId = filingId; }

        public List<HeatmapRow> getRows() { return rows; }
        public void setRows(List<HeatmapRow> rows) { this.rows = rows; }
    }

    public static class HeatmapRow {
        private String metric;
        private Map<String, BigDecimal> values;  // basis -> value
        private double zScore;

        public String getMetric() { return metric; }
        public void setMetric(String metric) { this.metric = metric; }

        public Map<String, BigDecimal> getValues() { return values; }
        public void setValues(Map<String, BigDecimal> values) { this.values = values; }

        @com.fasterxml.jackson.annotation.JsonProperty("zScore")
        public double getZScore() { return zScore; }
        public void setZScore(double zScore) { this.zScore = zScore; }
    }
}
