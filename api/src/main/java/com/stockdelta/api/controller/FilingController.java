package com.stockdelta.api.controller;

import com.stockdelta.common.entity.Filing;
import com.stockdelta.common.repository.FilingRepository;
import com.stockdelta.common.repository.IssuerRepository;
import com.stockdelta.api.dto.FilingDto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/filings")
@CrossOrigin(origins = "*")
public class FilingController {

    private static final Logger logger = LoggerFactory.getLogger(FilingController.class);

    private final FilingRepository filingRepository;
    private final IssuerRepository issuerRepository;

    @Autowired
    public FilingController(FilingRepository filingRepository, IssuerRepository issuerRepository) {
        this.filingRepository = filingRepository;
        this.issuerRepository = issuerRepository;
    }

    @GetMapping("/{symbol}/latest")
    public ResponseEntity<List<Filing>> getLatestFilings(
            @PathVariable String symbol,
            @RequestParam(required = false) String form,
            @RequestParam(defaultValue = "10") int limit) {

        // Resolve ticker to CIK
        Optional<com.stockdelta.common.entity.Issuer> issuer =
                issuerRepository.findByTicker(symbol.toUpperCase());

        if (issuer.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String cik = issuer.get().getCik();
        List<Filing> filings;

        if (form != null && !form.isEmpty()) {
            filings = filingRepository.findByCikAndFormOrderByFiledAtDesc(cik, form);
        } else {
            filings = filingRepository.findByCikOrderByFiledAtDesc(cik);
        }

        // Limit results and populate issuer info
        List<Filing> limitedFilings = filings.stream()
                .limit(limit)
                .peek(filing -> {
                    filing.setTicker(issuer.get().getTicker());
                    filing.setCompanyName(issuer.get().getName());
                })
                .toList();

        return ResponseEntity.ok(limitedFilings);
    }

    @GetMapping("/{symbol}/recent")
    public ResponseEntity<List<Filing>> getRecentFilings(
            @PathVariable String symbol,
            @RequestParam(required = false) String form,
            @RequestParam(defaultValue = "7") int days) {

        // Resolve ticker to CIK
        Optional<com.stockdelta.common.entity.Issuer> issuer =
                issuerRepository.findByTicker(symbol.toUpperCase());

        if (issuer.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String cik = issuer.get().getCik();
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        List<Filing> filings;
        if (form != null && !form.isEmpty()) {
            filings = filingRepository.findByCikAndFormSince(cik, form, since);
        } else {
            filings = filingRepository.findByCiksAndFiledAtAfter(List.of(cik), since);
        }

        filings.forEach(filing -> {
            filing.setTicker(issuer.get().getTicker());
            filing.setCompanyName(issuer.get().getName());
        });

        return ResponseEntity.ok(filings);
    }

    @GetMapping("/recent")
    public ResponseEntity<List<FilingDto>> getAllRecentFilings(
            @RequestParam(required = false) String[] forms,
            @RequestParam(defaultValue = "1") int days,
            @RequestParam(defaultValue = "50") int limit) {

        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Filing> filings;

        if (forms != null && forms.length > 0) {
            filings = filingRepository.findByFormsAndFiledAtAfter(List.of(forms), since);
        } else {
            // Get all recent filings
            List<String> allCiks = issuerRepository.findAllWithTicker()
                    .stream()
                    .map(com.stockdelta.common.entity.Issuer::getCik)
                    .toList();
            filings = filingRepository.findByCiksAndFiledAtAfter(allCiks, since);
        }

        // Convert to DTOs with issuer info
        List<FilingDto> filingDtos = filings.stream()
                .sorted((f1, f2) -> f2.getFiledAt().compareTo(f1.getFiledAt()))
                .limit(limit)
                .map(filing -> {
                    var issuer = issuerRepository.findByCik(filing.getCik()).orElse(null);
                    return new FilingDto(filing, issuer);
                })
                .toList();

        return ResponseEntity.ok(filingDtos);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Filing>> searchFilings(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String form,
            @RequestParam(required = false) String accessionNo) {

        if (accessionNo != null && !accessionNo.isEmpty()) {
            Optional<Filing> filing = filingRepository.findByAccessionNo(accessionNo);
            return filing.map(f -> ResponseEntity.ok(List.of(f)))
                         .orElse(ResponseEntity.notFound().build());
        }

        if (symbol != null && !symbol.isEmpty()) {
            Optional<com.stockdelta.common.entity.Issuer> issuer =
                    issuerRepository.findByTicker(symbol.toUpperCase());

            if (issuer.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            String cik = issuer.get().getCik();
            List<Filing> filings;

            if (form != null && !form.isEmpty()) {
                filings = filingRepository.findByCikAndFormOrderByFiledAtDesc(cik, form);
            } else {
                filings = filingRepository.findByCikOrderByFiledAtDesc(cik);
            }

            // Populate issuer info
            filings.forEach(filing -> {
                filing.setTicker(issuer.get().getTicker());
                filing.setCompanyName(issuer.get().getName());
            });

            return ResponseEntity.ok(filings);
        }

        return ResponseEntity.badRequest().build();
    }

    @GetMapping("/stats/{symbol}")
    public ResponseEntity<FilingStats> getFilingStats(@PathVariable String symbol) {
        Optional<com.stockdelta.common.entity.Issuer> issuer =
                issuerRepository.findByTicker(symbol.toUpperCase());

        if (issuer.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String cik = issuer.get().getCik();
        long totalFilings = filingRepository.countByCik(cik);

        FilingStats stats = new FilingStats();
        stats.setSymbol(symbol.toUpperCase());
        stats.setCik(cik);
        stats.setTotalFilings(totalFilings);

        return ResponseEntity.ok(stats);
    }

    public static class FilingStats {
        private String symbol;
        private String cik;
        private long totalFilings;

        // Getters and Setters
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }

        public String getCik() { return cik; }
        public void setCik(String cik) { this.cik = cik; }

        public long getTotalFilings() { return totalFilings; }
        public void setTotalFilings(long totalFilings) { this.totalFilings = totalFilings; }
    }
}