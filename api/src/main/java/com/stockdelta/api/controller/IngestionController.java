package com.stockdelta.api.controller;

import com.stockdelta.common.service.DataIngestionService;
import com.stockdelta.common.entity.IngestLog;
import com.stockdelta.common.repository.IngestLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/ingest")
@CrossOrigin(origins = "*")
public class IngestionController {

    private static final Logger logger = LoggerFactory.getLogger(IngestionController.class);

    private final DataIngestionService dataIngestionService;
    private final IngestLogRepository ingestLogRepository;

    @Autowired
    public IngestionController(DataIngestionService dataIngestionService,
                              IngestLogRepository ingestLogRepository) {
        this.dataIngestionService = dataIngestionService;
        this.ingestLogRepository = ingestLogRepository;
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<DataIngestionService.IngestionResult>> refreshData(
            @RequestBody DataIngestionService.IngestionRequest request) {

        logger.info("Received ingestion request: mode={}, symbols={}",
                   request.getMode(), request.getSymbols());

        // Validate request
        if (request.getMode() == null) {
            request.setMode("today");
        }

        if (!"today".equals(request.getMode()) && !"latest".equals(request.getMode())) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return dataIngestionService.ingestData(request)
                .map(result -> {
                    if ("failed".equals(result.getStatus())) {
                        return ResponseEntity.internalServerError().body(result);
                    }
                    return ResponseEntity.ok(result);
                })
                .onErrorReturn(ResponseEntity.internalServerError().build());
    }

    @GetMapping("/status/{requestId}")
    public ResponseEntity<IngestLog> getIngestionStatus(@PathVariable UUID requestId) {
        Optional<IngestLog> log = ingestLogRepository.findById(requestId);

        if (log.isPresent()) {
            return ResponseEntity.ok(log.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/status")
    public ResponseEntity<List<IngestLog>> getRecentIngestionLogs(@RequestParam(defaultValue = "10") int limit) {
        List<IngestLog> logs = ingestLogRepository.findTop10ByOrderByRequestTimestampDesc();
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Ingestion service is healthy");
    }
}