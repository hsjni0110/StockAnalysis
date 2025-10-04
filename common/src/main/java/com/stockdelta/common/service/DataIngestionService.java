package com.stockdelta.common.service;

import com.stockdelta.common.entity.*;
import com.stockdelta.common.parser.DailyIndexParser;
import com.stockdelta.common.parser.SubmissionsParser;
import com.stockdelta.common.parser.XbrlFactsParser;
import com.stockdelta.common.repository.FilingRepository;
import com.stockdelta.common.repository.IngestLogRepository;
import com.stockdelta.common.repository.IssuerRepository;
import com.stockdelta.common.sec.SecApiClient;
import com.stockdelta.common.sec.TickerResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Transactional
public class DataIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(DataIngestionService.class);

    private final SecApiClient secApiClient;
    private final TickerResolver tickerResolver;
    private final DailyIndexParser dailyIndexParser;
    private final SubmissionsParser submissionsParser;
    private final XbrlFactsParser xbrlFactsParser;

    private final IssuerRepository issuerRepository;
    private final FilingRepository filingRepository;
    private final IngestLogRepository ingestLogRepository;

    @Autowired
    public DataIngestionService(SecApiClient secApiClient,
                               TickerResolver tickerResolver,
                               DailyIndexParser dailyIndexParser,
                               SubmissionsParser submissionsParser,
                               XbrlFactsParser xbrlFactsParser,
                               IssuerRepository issuerRepository,
                               FilingRepository filingRepository,
                               IngestLogRepository ingestLogRepository) {
        this.secApiClient = secApiClient;
        this.tickerResolver = tickerResolver;
        this.dailyIndexParser = dailyIndexParser;
        this.submissionsParser = submissionsParser;
        this.xbrlFactsParser = xbrlFactsParser;
        this.issuerRepository = issuerRepository;
        this.filingRepository = filingRepository;
        this.ingestLogRepository = ingestLogRepository;
    }

    public static class IngestionRequest {
        private String[] symbols;
        private String mode; // "today" or "latest"

        public IngestionRequest() {}

        public IngestionRequest(String[] symbols, String mode) {
            this.symbols = symbols;
            this.mode = mode;
        }

        public String[] getSymbols() { return symbols; }
        public void setSymbols(String[] symbols) { this.symbols = symbols; }

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
    }

    public static class IngestionResult {
        private UUID logId;
        private int totalProcessed;
        private int totalInserted;
        private int totalSkipped;
        private List<String> warnings;
        private String status;

        public IngestionResult(UUID logId) {
            this.logId = logId;
            this.warnings = new ArrayList<>();
            this.status = "completed";
        }

        // Getters and Setters
        public UUID getLogId() { return logId; }
        public void setLogId(UUID logId) { this.logId = logId; }

        public int getTotalProcessed() { return totalProcessed; }
        public void setTotalProcessed(int totalProcessed) { this.totalProcessed = totalProcessed; }

        public int getTotalInserted() { return totalInserted; }
        public void setTotalInserted(int totalInserted) { this.totalInserted = totalInserted; }

        public int getTotalSkipped() { return totalSkipped; }
        public void setTotalSkipped(int totalSkipped) { this.totalSkipped = totalSkipped; }

        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public Mono<IngestionResult> ingestData(IngestionRequest request) {
        IngestLog log = new IngestLog(request.getMode(), request.getSymbols());
        ingestLogRepository.save(log);

        logger.info("Starting data ingestion with mode: {}, symbols: {}",
                   request.getMode(), Arrays.toString(request.getSymbols()));

        return processIngestion(request, log)
                .doOnSuccess(result -> {
                    log.setTotalProcessed(result.getTotalProcessed());
                    log.setTotalInserted(result.getTotalInserted());
                    log.setTotalSkipped(result.getTotalSkipped());
                    log.markCompleted();
                    ingestLogRepository.save(log);
                })
                .doOnError(error -> {
                    logger.error("Ingestion failed", error);
                    log.markFailed();
                    ingestLogRepository.save(log);
                })
                .onErrorReturn(new IngestionResult(log.getId()) {{
                    setStatus("failed");
                }});
    }

    private Mono<IngestionResult> processIngestion(IngestionRequest request, IngestLog log) {
        return resolveTickers(request.getSymbols())
                .flatMap(cikMap -> {
                    if ("today".equals(request.getMode())) {
                        return ingestTodayFilings(cikMap, log);
                    } else {
                        return ingestLatestFilings(cikMap, log);
                    }
                });
    }

    private Mono<Map<String, String>> resolveTickers(String[] symbols) {
        if (symbols == null || symbols.length == 0) {
            // If no symbols provided, get all symbols from database
            List<Issuer> allIssuers = issuerRepository.findAllWithTicker();
            Map<String, String> cikMap = allIssuers.stream()
                    .collect(Collectors.toMap(Issuer::getTicker, Issuer::getCik));
            return Mono.just(cikMap);
        }

        return Flux.fromArray(symbols)
                .flatMap(symbol -> tickerResolver.resolveTicker(symbol)
                        .map(cik -> Map.entry(symbol.toUpperCase(), cik))
                        .onErrorResume(error -> {
                            logger.warn("Failed to resolve ticker {}: {}", symbol, error.getMessage());
                            return Mono.empty();
                        }))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    private Mono<IngestionResult> ingestTodayFilings(Map<String, String> cikMap, IngestLog log) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Set<String> targetCiks = new HashSet<>(cikMap.values());

        return secApiClient.fetchDailyIndex(today)
                .flatMap(indexContent -> {
                    List<DailyIndexParser.IndexEntry> entries =
                            dailyIndexParser.parseIndex(indexContent, targetCiks);

                    return processIndexEntries(entries, log.getId());
                })
                .onErrorResume(error -> {
                    logger.error("Failed to fetch daily index for {}: {}", today, error.getMessage());
                    return Mono.just(new IngestionResult(log.getId()) {{
                        getWarnings().add("Failed to fetch daily index: " + error.getMessage());
                    }});
                });
    }

    private Mono<IngestionResult> ingestLatestFilings(Map<String, String> cikMap, IngestLog log) {
        AtomicInteger processed = new AtomicInteger(0);
        AtomicInteger inserted = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);

        return Flux.fromIterable(cikMap.values())
                .flatMap(cik -> ingestCompanySubmissions(cik)
                        .doOnSuccess(result -> {
                            processed.incrementAndGet();
                            inserted.addAndGet(result.getInsertedCount());
                            skipped.addAndGet(result.getSkippedCount());
                        })
                        .onErrorResume(error -> {
                            logger.warn("Failed to ingest submissions for CIK {}: {}", cik, error.getMessage());
                            processed.incrementAndGet();
                            return Mono.empty();
                        }))
                .then(Mono.fromCallable(() -> {
                    IngestionResult result = new IngestionResult(log.getId());
                    result.setTotalProcessed(processed.get());
                    result.setTotalInserted(inserted.get());
                    result.setTotalSkipped(skipped.get());
                    return result;
                }));
    }

    private Mono<IngestionResult> processIndexEntries(List<DailyIndexParser.IndexEntry> entries, UUID logId) {
        AtomicInteger processed = new AtomicInteger(0);
        AtomicInteger inserted = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);

        return Flux.fromIterable(entries)
                .flatMap(entry -> processIndexEntry(entry)
                        .doOnSuccess(wasInserted -> {
                            processed.incrementAndGet();
                            if (wasInserted) {
                                inserted.incrementAndGet();
                            } else {
                                skipped.incrementAndGet();
                            }
                        })
                        .onErrorResume(error -> {
                            logger.warn("Failed to process entry {}: {}", entry, error.getMessage());
                            processed.incrementAndGet();
                            skipped.incrementAndGet();
                            return Mono.just(false);
                        }))
                .then(Mono.fromCallable(() -> {
                    IngestionResult result = new IngestionResult(logId);
                    result.setTotalProcessed(processed.get());
                    result.setTotalInserted(inserted.get());
                    result.setTotalSkipped(skipped.get());
                    return result;
                }));
    }

    private Mono<Boolean> processIndexEntry(DailyIndexParser.IndexEntry entry) {
        // Check if already exists
        if (filingRepository.existsByAccessionNo(entry.getAccessionNo())) {
            return Mono.just(false); // Skip, already exists
        }

        // Create and save filing
        Filing filing = new Filing();
        filing.setCik(entry.getCik());
        filing.setAccessionNo(entry.getAccessionNo());
        filing.setForm(entry.getForm());
        filing.setFiledAt(entry.getFiledDate().atStartOfDay());
        filing.setPrimaryDocUrl(entry.getDocumentUrl());
        filing.setSource("daily-index");

        filingRepository.save(filing);
        logger.debug("Saved filing: {}", filing.getAccessionNo());

        return Mono.just(true);
    }

    private static class CompanyIngestionResult {
        private int insertedCount;
        private int skippedCount;

        public CompanyIngestionResult(int insertedCount, int skippedCount) {
            this.insertedCount = insertedCount;
            this.skippedCount = skippedCount;
        }

        public int getInsertedCount() { return insertedCount; }
        public int getSkippedCount() { return skippedCount; }
    }

    private Mono<CompanyIngestionResult> ingestCompanySubmissions(String cik) {
        return secApiClient.fetchCompanySubmissions(cik)
                .flatMap(response -> {
                    try {
                        SubmissionsParser.SubmissionData data = submissionsParser.parseSubmissions(response);

                        // Update/create issuer
                        Issuer issuer = data.getIssuer();
                        Optional<Issuer> existingIssuer = issuerRepository.findById(issuer.getCik());
                        if (existingIssuer.isPresent()) {
                            Issuer existing = existingIssuer.get();
                            existing.setName(issuer.getName());
                            existing.setTicker(issuer.getTicker());
                            existing.setExchange(issuer.getExchange());
                            existing.setSic(issuer.getSic());
                            existing.setIndustry(issuer.getIndustry());
                            issuerRepository.save(existing);
                        } else {
                            issuerRepository.save(issuer);
                        }

                        // Process filings - filter only necessary filings for comparison
                        int inserted = 0;
                        int skipped = 0;

                        // Get filings needed for comparison analysis (10-K: 2, 10-Q: 4, etc.)
                        java.util.Map<String, List<Filing>> comparisonFilings =
                            submissionsParser.filterComparisonFilings(data.getFilings());

                        // Track counts per form type for logging
                        java.util.Map<String, Integer> formCounts = new java.util.HashMap<>();

                        for (java.util.Map.Entry<String, List<Filing>> entry : comparisonFilings.entrySet()) {
                            String formType = entry.getKey();
                            List<Filing> filings = entry.getValue();
                            int formInserted = 0;

                            for (Filing filing : filings) {
                                if (!filingRepository.existsByAccessionNo(filing.getAccessionNo())) {
                                    filingRepository.save(filing);
                                    inserted++;
                                    formInserted++;
                                    logger.debug("Saved {} filing: {} (period: {})",
                                        filing.getForm(), filing.getAccessionNo(), filing.getPeriodEnd());
                                } else {
                                    skipped++;
                                }
                            }

                            if (formInserted > 0) {
                                formCounts.put(formType, formInserted);
                            }
                        }

                        // Build detailed log message
                        StringBuilder logMsg = new StringBuilder();
                        logMsg.append("Processed submissions for CIK ").append(cik)
                              .append(": ").append(inserted).append(" inserted");

                        if (!formCounts.isEmpty()) {
                            logMsg.append(" (");
                            formCounts.forEach((form, count) ->
                                logMsg.append(form).append(":").append(count).append(" "));
                            logMsg.append(")");
                        }

                        logMsg.append(", ").append(skipped).append(" skipped");
                        logger.info(logMsg.toString());

                        return Mono.just(new CompanyIngestionResult(inserted, skipped));

                    } catch (Exception e) {
                        logger.error("Failed to parse submissions for CIK {}: {}", cik, e.getMessage());
                        return Mono.error(e);
                    }
                });
    }
}