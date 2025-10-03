package com.stockdelta.api.controller;

import com.stockdelta.common.entity.Issuer;
import com.stockdelta.common.repository.IssuerRepository;
import com.stockdelta.common.sec.TickerResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/ticker")
@CrossOrigin(origins = "*")
public class TickerController {

    private static final Logger logger = LoggerFactory.getLogger(TickerController.class);

    private final TickerResolver tickerResolver;
    private final IssuerRepository issuerRepository;

    @Autowired
    public TickerController(TickerResolver tickerResolver, IssuerRepository issuerRepository) {
        this.tickerResolver = tickerResolver;
        this.issuerRepository = issuerRepository;
    }

    public static class TickerResolution {
        private String symbol;
        private String cik;
        private String name;
        private String exchange;

        public TickerResolution() {}

        public TickerResolution(String symbol, Issuer issuer) {
            this.symbol = symbol;
            this.cik = issuer.getCik();
            this.name = issuer.getName();
            this.exchange = issuer.getExchange();
        }

        // Getters and Setters
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }

        public String getCik() { return cik; }
        public void setCik(String cik) { this.cik = cik; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getExchange() { return exchange; }
        public void setExchange(String exchange) { this.exchange = exchange; }
    }

    @GetMapping("/resolve")
    public ResponseEntity<TickerResolution> resolveTicker(@RequestParam String symbol) {
        logger.info("Resolving ticker: {}", symbol);

        try {
            String cik = tickerResolver.resolveTicker(symbol.toUpperCase()).block();
            if (cik != null) {
                Optional<Issuer> issuer = issuerRepository.findById(cik);
                if (issuer.isPresent()) {
                    TickerResolution resolution = new TickerResolution(symbol.toUpperCase(), issuer.get());
                    return ResponseEntity.ok(resolution);
                }
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Failed to resolve ticker {}: {}", symbol, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/list")
    public ResponseEntity<List<Issuer>> listAllTickers() {
        List<Issuer> issuers = issuerRepository.findAllWithTicker();
        return ResponseEntity.ok(issuers);
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<String>> refreshTickerMappings() {
        logger.info("Refreshing ticker mappings from SEC");

        return tickerResolver.refreshTickerMappings()
                .then(Mono.just(ResponseEntity.ok("Ticker mappings refreshed successfully")))
                .onErrorReturn(ResponseEntity.internalServerError().body("Failed to refresh ticker mappings"));
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<Issuer> getIssuerByTicker(@PathVariable String symbol) {
        Optional<Issuer> issuer = issuerRepository.findByTicker(symbol.toUpperCase());

        if (issuer.isPresent()) {
            return ResponseEntity.ok(issuer.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}