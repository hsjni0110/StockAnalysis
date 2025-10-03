package com.stockdelta.api.controller;

import com.stockdelta.common.entity.Filing;
import com.stockdelta.common.repository.FilingRepository;
import com.stockdelta.common.repository.IssuerRepository;
import com.stockdelta.api.dto.FilingDto;
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

    private final FilingRepository filingRepository;
    private final IssuerRepository issuerRepository;

    @Autowired
    public FilingController(FilingRepository filingRepository, IssuerRepository issuerRepository) {
        this.filingRepository = filingRepository;
        this.issuerRepository = issuerRepository;
    }

    @GetMapping("/{symbol}/latest")
    public ResponseEntity<List<FilingDto>> getLatestFilings(
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

        // Limit results and convert to DTOs
        String ticker = issuer.get().getTicker();
        String companyName = issuer.get().getName();

        List<FilingDto> filingDtos = filings.stream()
                .limit(limit)
                .map(filing -> new FilingDto(filing, ticker, companyName))
                .toList();

        return ResponseEntity.ok(filingDtos);
    }

    @GetMapping("/{symbol}/recent")
    public ResponseEntity<List<FilingDto>> getRecentFilings(
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

        String ticker = issuer.get().getTicker();
        String companyName = issuer.get().getName();

        List<FilingDto> filingDtos = filings.stream()
                .map(filing -> new FilingDto(filing, ticker, companyName))
                .toList();

        return ResponseEntity.ok(filingDtos);
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
                    var issuer = issuerRepository.findByCik(filing.getCik());
                    String ticker = issuer.map(com.stockdelta.common.entity.Issuer::getTicker).orElse(null);
                    String companyName = issuer.map(com.stockdelta.common.entity.Issuer::getName).orElse(null);
                    return new FilingDto(filing, ticker, companyName);
                })
                .toList();

        return ResponseEntity.ok(filingDtos);
    }

    @GetMapping("/search")
    public ResponseEntity<List<FilingDto>> searchFilings(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String form,
            @RequestParam(required = false) String accessionNo) {

        if (accessionNo != null && !accessionNo.isEmpty()) {
            Optional<Filing> filing = filingRepository.findByAccessionNo(accessionNo);
            if (filing.isPresent()) {
                var issuer = issuerRepository.findByCik(filing.get().getCik());
                String ticker = issuer.map(com.stockdelta.common.entity.Issuer::getTicker).orElse(null);
                String companyName = issuer.map(com.stockdelta.common.entity.Issuer::getName).orElse(null);
                return ResponseEntity.ok(List.of(new FilingDto(filing.get(), ticker, companyName)));
            }
            return ResponseEntity.notFound().build();
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

            // Convert to DTOs
            String ticker = issuer.get().getTicker();
            String companyName = issuer.get().getName();

            List<FilingDto> filingDtos = filings.stream()
                    .map(filing -> new FilingDto(filing, ticker, companyName))
                    .toList();

            return ResponseEntity.ok(filingDtos);
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