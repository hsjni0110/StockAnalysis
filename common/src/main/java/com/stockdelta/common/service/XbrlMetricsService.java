package com.stockdelta.common.service;

import com.stockdelta.common.entity.Filing;
import com.stockdelta.common.entity.XbrlFact;
import com.stockdelta.common.entity.XbrlMetric;
import com.stockdelta.common.repository.FilingRepository;
import com.stockdelta.common.repository.XbrlFactRepository;
import com.stockdelta.common.repository.XbrlMetricRepository;
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
 * Service for calculating XBRL metrics changes (QoQ, YoY)
 * Compares current filing XBRL data with previous periods
 */
@Service
@Transactional
public class XbrlMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(XbrlMetricsService.class);

    // Key financial metrics to track
    private static final List<String> KEY_METRICS = Arrays.asList(
            "Revenue", "Revenues",
            "RevenueFromContractWithCustomerExcludingAssessedTax",
            "OperatingIncomeLoss",
            "NetIncomeLoss",
            "InventoryNet",
            "CashAndCashEquivalentsAtCarryingValue",
            "CapitalExpenditures", "PaymentsToAcquirePropertyPlantAndEquipment",
            "AssetsCurrent",
            "LiabilitiesCurrent",
            "StockholdersEquity"
    );

    private final FilingRepository filingRepository;
    private final XbrlFactRepository xbrlFactRepository;
    private final XbrlMetricRepository xbrlMetricRepository;

    @Autowired
    public XbrlMetricsService(FilingRepository filingRepository,
                               XbrlFactRepository xbrlFactRepository,
                               XbrlMetricRepository xbrlMetricRepository) {
        this.filingRepository = filingRepository;
        this.xbrlFactRepository = xbrlFactRepository;
        this.xbrlMetricRepository = xbrlMetricRepository;
    }

    /**
     * Calculate metrics for a filing
     */
    public List<XbrlMetric> calculateMetrics(Long filingId) {
        Optional<Filing> currentFilingOpt = filingRepository.findById(filingId);
        if (currentFilingOpt.isEmpty()) {
            logger.warn("Filing not found: {}", filingId);
            return new ArrayList<>();
        }

        Filing currentFiling = currentFilingOpt.get();
        List<XbrlMetric> metrics = new ArrayList<>();

        // Get current filing's XBRL facts
        List<XbrlFact> currentFacts = xbrlFactRepository.findByFilingId(filingId);
        if (currentFacts.isEmpty()) {
            logger.info("No XBRL facts found for filing {}", filingId);
            return metrics;
        }

        // Store absolute values
        for (XbrlFact fact : currentFacts) {
            if (KEY_METRICS.contains(fact.getTag()) && fact.getValue() != null) {
                XbrlMetric metric = new XbrlMetric(
                        filingId,
                        fact.getTag(),
                        "Abs",
                        fact.getValue()
                );
                metrics.add(metric);
            }
        }

        // Find previous filing for comparison
        Optional<Filing> previousFilingOpt = findPreviousFiling(currentFiling);
        if (previousFilingOpt.isEmpty()) {
            logger.info("No previous filing found for comparison: {}", currentFiling.getAccessionNo());
            // Save absolute metrics only
            xbrlMetricRepository.saveAll(metrics);
            return metrics;
        }

        Filing previousFiling = previousFilingOpt.get();
        List<XbrlFact> previousFacts = xbrlFactRepository.findByFilingId(previousFiling.getId());

        // Calculate QoQ/YoY changes
        metrics.addAll(calculateChanges(filingId, currentFiling, currentFacts, previousFiling, previousFacts));

        // Save all metrics
        xbrlMetricRepository.saveAll(metrics);
        logger.info("Calculated {} metrics for filing {}", metrics.size(), filingId);

        return metrics;
    }

    private List<XbrlMetric> calculateChanges(Long filingId, Filing currentFiling,
                                                List<XbrlFact> currentFacts,
                                                Filing previousFiling,
                                                List<XbrlFact> previousFacts) {
        List<XbrlMetric> metrics = new ArrayList<>();

        // Group facts by tag for easy lookup
        Map<String, XbrlFact> currentFactsMap = currentFacts.stream()
                .collect(Collectors.toMap(XbrlFact::getTag, fact -> fact, (f1, f2) -> f1));

        Map<String, XbrlFact> previousFactsMap = previousFacts.stream()
                .collect(Collectors.toMap(XbrlFact::getTag, fact -> fact, (f1, f2) -> f1));

        // Determine if this is QoQ or YoY comparison
        String basis = determineBasis(currentFiling, previousFiling);

        for (String tag : KEY_METRICS) {
            XbrlFact currentFact = currentFactsMap.get(tag);
            XbrlFact previousFact = previousFactsMap.get(tag);

            if (currentFact != null && previousFact != null &&
                currentFact.getValue() != null && previousFact.getValue() != null) {

                BigDecimal currentValue = currentFact.getValue();
                BigDecimal previousValue = previousFact.getValue();

                // Calculate percentage change
                BigDecimal change = calculatePercentageChange(previousValue, currentValue);

                XbrlMetric metric = new XbrlMetric(
                        filingId,
                        tag,
                        basis,
                        change
                );
                metrics.add(metric);

                logger.debug("Metric {}: {} -> {} ({}%)", tag, previousValue, currentValue, change);
            }
        }

        return metrics;
    }

    private String determineBasis(Filing current, Filing previous) {
        LocalDate currentPeriod = current.getPeriodEnd();
        LocalDate previousPeriod = previous.getPeriodEnd();

        if (currentPeriod == null || previousPeriod == null) {
            return "QoQ"; // Default
        }

        long monthsDiff = java.time.temporal.ChronoUnit.MONTHS.between(previousPeriod, currentPeriod);

        if (monthsDiff >= 11 && monthsDiff <= 13) {
            return "YoY"; // Year-over-year
        } else if (monthsDiff >= 2 && monthsDiff <= 4) {
            return "QoQ"; // Quarter-over-quarter
        }

        return "QoQ";
    }

    private BigDecimal calculatePercentageChange(BigDecimal previousValue, BigDecimal currentValue) {
        if (previousValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal change = currentValue.subtract(previousValue);
        BigDecimal percentage = change.divide(previousValue.abs(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return percentage.setScale(2, RoundingMode.HALF_UP);
    }

    private Optional<Filing> findPreviousFiling(Filing current) {
        String form = current.getForm();
        String cik = current.getCik();
        LocalDate periodEnd = current.getPeriodEnd();

        if (periodEnd == null) {
            return filingRepository.findByCikAndFormOrderByFiledAtDesc(cik, form).stream()
                    .filter(f -> f.getFiledAt().isBefore(current.getFiledAt()))
                    .findFirst();
        }

        List<Filing> previousFilings = filingRepository.findByCikAndFormOrderByFiledAtDesc(cik, form);

        return previousFilings.stream()
                .filter(f -> f.getPeriodEnd() != null && f.getPeriodEnd().isBefore(periodEnd))
                .findFirst();
    }

    /**
     * Get heatmap data for a filing
     */
    public HeatmapData getHeatmapData(Long filingId) {
        List<XbrlMetric> metrics = xbrlMetricRepository.findByFilingId(filingId);

        HeatmapData heatmap = new HeatmapData();
        heatmap.setFilingId(filingId);

        // Group by metric and basis
        Map<String, Map<String, BigDecimal>> data = new HashMap<>();

        for (XbrlMetric metric : metrics) {
            String metricName = metric.getId().getMetric();
            String basis = metric.getId().getBasis();

            data.computeIfAbsent(metricName, k -> new HashMap<>())
                    .put(basis, metric.getValue());
        }

        // Convert to heatmap format
        List<HeatmapRow> rows = new ArrayList<>();
        for (Map.Entry<String, Map<String, BigDecimal>> entry : data.entrySet()) {
            HeatmapRow row = new HeatmapRow();
            row.setMetric(simplifyMetricName(entry.getKey()));
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

    private String simplifyMetricName(String tag) {
        // Simplify long XBRL tag names for display
        Map<String, String> simplifications = Map.of(
                "RevenueFromContractWithCustomerExcludingAssessedTax", "Revenue",
                "PaymentsToAcquirePropertyPlantAndEquipment", "CapEx",
                "CashAndCashEquivalentsAtCarryingValue", "Cash",
                "OperatingIncomeLoss", "Operating Income",
                "NetIncomeLoss", "Net Income",
                "StockholdersEquity", "Equity"
        );

        return simplifications.getOrDefault(tag, tag);
    }

    private double calculateZScore(BigDecimal value) {
        // Simplified Z-score calculation
        // In a real implementation, you would calculate from historical distribution
        double absValue = Math.abs(value.doubleValue());

        if (absValue > 50) return 3.0;
        if (absValue > 30) return 2.5;
        if (absValue > 20) return 2.0;
        if (absValue > 10) return 1.5;
        return absValue / 10.0;
    }

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
        private Map<String, BigDecimal> values; // basis -> value

        @com.fasterxml.jackson.annotation.JsonProperty("zScore")
        private double zScore;

        public String getMetric() { return metric; }
        public void setMetric(String metric) { this.metric = metric; }

        public Map<String, BigDecimal> getValues() { return values; }
        public void setValues(Map<String, BigDecimal> values) { this.values = values; }

        public double getZScore() { return zScore; }
        public void setZScore(double zScore) { this.zScore = zScore; }
    }
}
