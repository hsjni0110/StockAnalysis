package com.stockdelta.api.controller;

import com.stockdelta.common.entity.Filing;
import com.stockdelta.common.entity.FilingDelta;
import com.stockdelta.common.entity.FilingSection;
import com.stockdelta.common.repository.FilingRepository;
import com.stockdelta.common.repository.IssuerRepository;
import com.stockdelta.common.service.FilingDiffService;
import com.stockdelta.common.service.FilingSectionExtractor;
import com.stockdelta.common.service.XbrlMetricsService;
import com.stockdelta.api.dto.DeltaMapDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DeltaMap API Controller
 * Provides endpoints for filing difference analysis and visualization
 */
@RestController
@RequestMapping("/api/deltamap")
@CrossOrigin(origins = "*")
public class DeltaMapController {

    private static final Logger logger = LoggerFactory.getLogger(DeltaMapController.class);

    private final FilingRepository filingRepository;
    private final IssuerRepository issuerRepository;
    private final FilingSectionExtractor sectionExtractor;
    private final FilingDiffService diffService;
    private final XbrlMetricsService metricsService;

    @Autowired
    public DeltaMapController(FilingRepository filingRepository,
                               IssuerRepository issuerRepository,
                               FilingSectionExtractor sectionExtractor,
                               FilingDiffService diffService,
                               XbrlMetricsService metricsService) {
        this.filingRepository = filingRepository;
        this.issuerRepository = issuerRepository;
        this.sectionExtractor = sectionExtractor;
        this.diffService = diffService;
        this.metricsService = metricsService;
    }

    /**
     * Trigger delta analysis for a filing
     * POST /api/deltamap/filings/{filingId}/analyze
     */
    @PostMapping("/filings/{filingId}/analyze")
    public Mono<ResponseEntity<AnalysisResult>> analyzeFiling(
            @PathVariable Long filingId,
            @RequestParam(required = false, defaultValue = "false") boolean forceReextract) {
        return sectionExtractor.extractSections(filingId, forceReextract)
                .map(sections -> {
                    // Compute deltas
                    List<FilingDelta> deltas = diffService.computeDeltas(filingId);

                    // Calculate XBRL metrics
                    metricsService.calculateMetrics(filingId);

                    AnalysisResult result = new AnalysisResult();
                    result.setFilingId(filingId);
                    result.setSectionsExtracted(sections.size());
                    result.setDeltasComputed(deltas.size());
                    result.setStatus("completed");

                    return ResponseEntity.ok(result);
                })
                .onErrorResume(error -> {
                    AnalysisResult result = new AnalysisResult();
                    result.setFilingId(filingId);
                    result.setStatus("failed");
                    result.setError(error.getMessage());
                    return Mono.just(ResponseEntity.status(500).body(result));
                });
    }

    /**
     * Get sections for a filing
     * GET /api/deltamap/filings/{filingId}/sections
     */
    @GetMapping("/filings/{filingId}/sections")
    public ResponseEntity<Map<String, Object>> getFilingSections(
            @PathVariable Long filingId,
            @RequestParam(required = false) String section) {

        return sectionExtractor.extractSections(filingId)
                .map(sections -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("filingId", filingId);

                    if (section != null) {
                        sections = sections.stream()
                                .filter(s -> s.getSection().equalsIgnoreCase(section))
                                .toList();
                    }

                    response.put("sections", sections);
                    return ResponseEntity.ok(response);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .block();
    }

    /**
     * Get delta diff results for a filing
     * GET /api/deltamap/filings/{filingId}/deltas
     */
    @GetMapping("/filings/{filingId}/deltas")
    public ResponseEntity<DeltaMapDto> getFilingDeltas(
            @PathVariable Long filingId,
            @RequestParam(required = false) String section) {

        Optional<Filing> filingOpt = filingRepository.findById(filingId);
        if (filingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Filing currentFiling = filingOpt.get();

        // Check if analysis has been done
        FilingDiffService.DeltaSummary summary = diffService.getDeltaSummary(filingId);

        // If no deltas exist, trigger analysis automatically
        if (summary.getTotalChanges() == 0) {
            logger.info("No deltas found for filing {}, triggering automatic analysis", filingId);

            // Extract sections and compute deltas
            try {
                sectionExtractor.extractSections(filingId).block();
                diffService.computeDeltas(filingId);
                // Refresh summary after analysis
                summary = diffService.getDeltaSummary(filingId);
            } catch (Exception e) {
                logger.error("Failed to auto-analyze filing {}: {}", filingId, e.getMessage());
            }
        }

        // Build response
        DeltaMapDto dto = new DeltaMapDto();
        dto.setCurrent(buildFilingInfo(currentFiling));

        // Find previous filing
        findPreviousFiling(currentFiling).ifPresent(previous -> {
            dto.setPrevious(buildFilingInfo(previous));
        });

        dto.setTotalChanges(summary.getTotalChanges());
        dto.setInsertCount(summary.getInsertCount());
        dto.setDeleteCount(summary.getDeleteCount());
        dto.setModifyCount(summary.getModifyCount());

        List<FilingDelta> deltas = summary.getTopChanges();
        if (section != null) {
            deltas = deltas.stream()
                    .filter(d -> d.getSection().equalsIgnoreCase(section))
                    .toList();
        }
        dto.setDeltas(deltas);

        return ResponseEntity.ok(dto);
    }

    /**
     * Get XBRL heatmap data for a filing
     * GET /api/deltamap/filings/{filingId}/xbrl-heatmap
     */
    @GetMapping("/filings/{filingId}/xbrl-heatmap")
    public ResponseEntity<XbrlMetricsService.HeatmapData> getXbrlHeatmap(@PathVariable Long filingId) {
        XbrlMetricsService.HeatmapData heatmap = metricsService.getHeatmapData(filingId);

        if (heatmap.getRows() == null || heatmap.getRows().isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(heatmap);
    }

    /**
     * Get delta summary for a ticker's latest filing
     * GET /api/deltamap/tickers/{symbol}/summary
     */
    @GetMapping("/tickers/{symbol}/summary")
    public ResponseEntity<TickerDeltaSummary> getTickerDeltaSummary(@PathVariable String symbol) {
        var issuer = issuerRepository.findByTicker(symbol.toUpperCase());
        if (issuer.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String cik = issuer.get().getCik();

        // Get latest 10-K or 10-Q
        List<Filing> recentFilings = filingRepository.findByCikOrderByFiledAtDesc(cik);
        Optional<Filing> latestFiling = recentFilings.stream()
                .filter(f -> f.getForm().matches("10-[KQ]"))
                .findFirst();

        if (latestFiling.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        Filing filing = latestFiling.get();
        FilingDiffService.DeltaSummary deltaSummary = diffService.getDeltaSummary(filing.getId());
        XbrlMetricsService.HeatmapData heatmap = metricsService.getHeatmapData(filing.getId());

        TickerDeltaSummary summary = new TickerDeltaSummary();
        summary.setSymbol(symbol.toUpperCase());
        summary.setCompanyName(issuer.get().getName());
        summary.setLatestFiling(buildFilingInfo(filing));
        summary.setTotalChanges(deltaSummary.getTotalChanges());

        // Create change badges
        summary.addBadge(createDeltaBadge(deltaSummary));
        summary.addBadge(createXbrlBadge(heatmap));

        return ResponseEntity.ok(summary);
    }

    // Helper methods

    private DeltaMapDto.FilingInfo buildFilingInfo(Filing filing) {
        DeltaMapDto.FilingInfo info = new DeltaMapDto.FilingInfo();
        info.setFilingId(filing.getId());
        info.setForm(filing.getForm());
        info.setPeriodEnd(filing.getPeriodEnd());
        info.setFiledAt(filing.getFiledAt());
        info.setAccessionNo(filing.getAccessionNo());
        info.setPrimaryDocUrl(filing.getPrimaryDocUrl());
        return info;
    }

    private Optional<Filing> findPreviousFiling(Filing current) {
        List<Filing> filings = filingRepository.findByCikAndFormOrderByFiledAtDesc(
                current.getCik(), current.getForm());

        return filings.stream()
                .filter(f -> f.getFiledAt().isBefore(current.getFiledAt()))
                .findFirst();
    }

    private TickerDeltaSummary.ChangeBadge createDeltaBadge(FilingDiffService.DeltaSummary summary) {
        if (summary.getTotalChanges() == 0) {
            return null;
        }

        String label = String.format("MD&A %d건 변경", summary.getTotalChanges());
        String severity = summary.getTotalChanges() > 10 ? "high" : "medium";

        TickerDeltaSummary.ChangeBadge badge = new TickerDeltaSummary.ChangeBadge();
        badge.setType("section");
        badge.setLabel(label);
        badge.setSeverity(severity);
        return badge;
    }

    private TickerDeltaSummary.ChangeBadge createXbrlBadge(XbrlMetricsService.HeatmapData heatmap) {
        if (heatmap.getRows() == null || heatmap.getRows().isEmpty()) {
            return null;
        }

        // Find highest Z-score
        double maxZScore = heatmap.getRows().stream()
                .mapToDouble(XbrlMetricsService.HeatmapRow::getZScore)
                .max()
                .orElse(0.0);

        if (maxZScore < 2.0) {
            return null;
        }

        TickerDeltaSummary.ChangeBadge badge = new TickerDeltaSummary.ChangeBadge();
        badge.setType("xbrl");
        badge.setLabel("재무지표 이상치 감지");
        badge.setSeverity(maxZScore > 2.5 ? "high" : "medium");
        return badge;
    }

    // Response DTOs

    public static class AnalysisResult {
        private Long filingId;
        private int sectionsExtracted;
        private int deltasComputed;
        private String status;
        private String error;

        // Getters and Setters
        public Long getFilingId() { return filingId; }
        public void setFilingId(Long filingId) { this.filingId = filingId; }

        public int getSectionsExtracted() { return sectionsExtracted; }
        public void setSectionsExtracted(int sectionsExtracted) { this.sectionsExtracted = sectionsExtracted; }

        public int getDeltasComputed() { return deltasComputed; }
        public void setDeltasComputed(int deltasComputed) { this.deltasComputed = deltasComputed; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    public static class TickerDeltaSummary {
        private String symbol;
        private String companyName;
        private DeltaMapDto.FilingInfo latestFiling;
        private int totalChanges;
        private List<ChangeBadge> changeBadges = new java.util.ArrayList<>();

        public void addBadge(ChangeBadge badge) {
            if (badge != null) {
                changeBadges.add(badge);
            }
        }

        // Getters and Setters
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }

        public String getCompanyName() { return companyName; }
        public void setCompanyName(String companyName) { this.companyName = companyName; }

        public DeltaMapDto.FilingInfo getLatestFiling() { return latestFiling; }
        public void setLatestFiling(DeltaMapDto.FilingInfo latestFiling) { this.latestFiling = latestFiling; }

        public int getTotalChanges() { return totalChanges; }
        public void setTotalChanges(int totalChanges) { this.totalChanges = totalChanges; }

        public List<ChangeBadge> getChangeBadges() { return changeBadges; }
        public void setChangeBadges(List<ChangeBadge> changeBadges) { this.changeBadges = changeBadges; }

        public static class ChangeBadge {
            private String type; // "section" or "xbrl"
            private String label;
            private String severity; // "low", "medium", "high"

            public String getType() { return type; }
            public void setType(String type) { this.type = type; }

            public String getLabel() { return label; }
            public void setLabel(String label) { this.label = label; }

            public String getSeverity() { return severity; }
            public void setSeverity(String severity) { this.severity = severity; }
        }
    }
}
