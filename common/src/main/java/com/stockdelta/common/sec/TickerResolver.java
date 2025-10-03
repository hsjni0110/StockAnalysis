package com.stockdelta.common.sec;

import com.fasterxml.jackson.databind.JsonNode;
import com.stockdelta.common.entity.Issuer;
import com.stockdelta.common.repository.IssuerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Iterator;
import java.util.Optional;

@Component
public class TickerResolver {

    private static final Logger logger = LoggerFactory.getLogger(TickerResolver.class);

    private final SecApiClient secApiClient;
    private final IssuerRepository issuerRepository;

    @Autowired
    public TickerResolver(SecApiClient secApiClient, IssuerRepository issuerRepository) {
        this.secApiClient = secApiClient;
        this.issuerRepository = issuerRepository;
    }

    public Mono<String> resolveTicker(String symbol) {
        // First check database cache
        Optional<Issuer> cached = issuerRepository.findByTicker(symbol.toUpperCase());
        if (cached.isPresent()) {
            logger.debug("Found cached CIK for ticker {}: {}", symbol, cached.get().getCik());
            return Mono.just(cached.get().getCik());
        }

        // If not cached, fetch from SEC and update cache
        return fetchAndCacheTickerMapping(symbol.toUpperCase());
    }

    public Mono<Void> refreshTickerMappings() {
        logger.info("Refreshing ticker mappings from SEC");

        return secApiClient.fetchCompanyTickers()
                .flatMap(this::parseAndStoreTickerMappings)
                .then();
    }

    private Mono<String> fetchAndCacheTickerMapping(String symbol) {
        return secApiClient.fetchCompanyTickers()
                .flatMap(response -> {
                    try {
                        JsonNode root = secApiClient.parseJsonResponse(response);
                        return findTickerInResponse(root, symbol)
                                .switchIfEmpty(
                                    // Try exchange tickers file if not found in main file
                                    secApiClient.fetchCompanyTickersExchange()
                                            .flatMap(exchangeResponse -> {
                                                try {
                                                    JsonNode exchangeRoot = secApiClient.parseJsonResponse(exchangeResponse);
                                                    return findTickerInExchangeResponse(exchangeRoot, symbol);
                                                } catch (Exception e) {
                                                    logger.error("Failed to parse exchange tickers response", e);
                                                    return Mono.empty();
                                                }
                                            })
                                );
                    } catch (Exception e) {
                        logger.error("Failed to parse company tickers response", e);
                        return Mono.error(e);
                    }
                });
    }

    private Mono<String> findTickerInResponse(JsonNode root, String symbol) {
        try {
            Iterator<String> fieldNames = root.fieldNames();
            while (fieldNames.hasNext()) {
                String key = fieldNames.next();
                JsonNode company = root.get(key);

                String ticker = company.get("ticker").asText();
                if (symbol.equalsIgnoreCase(ticker)) {
                    String cik = String.format("%010d", company.get("cik_str").asLong());
                    String title = company.get("title").asText();

                    // Cache in database
                    Issuer issuer = new Issuer();
                    issuer.setCik(cik);
                    issuer.setTicker(ticker.toUpperCase());
                    issuer.setName(title);
                    issuerRepository.save(issuer);

                    logger.info("Cached new ticker mapping: {} -> {}", symbol, cik);
                    return Mono.just(cik);
                }
            }
        } catch (Exception e) {
            logger.error("Error searching for ticker {} in response", symbol, e);
        }

        return Mono.empty();
    }

    private Mono<String> findTickerInExchangeResponse(JsonNode root, String symbol) {
        try {
            JsonNode fields = root.get("fields");
            JsonNode data = root.get("data");

            if (fields == null || data == null) {
                return Mono.empty();
            }

            // Find index positions
            int cikIndex = -1, tickerIndex = -1, nameIndex = -1, exchangeIndex = -1;

            for (int i = 0; i < fields.size(); i++) {
                String field = fields.get(i).asText();
                switch (field) {
                    case "cik": cikIndex = i; break;
                    case "ticker": tickerIndex = i; break;
                    case "name": nameIndex = i; break;
                    case "exchange": exchangeIndex = i; break;
                }
            }

            if (cikIndex == -1 || tickerIndex == -1) {
                logger.warn("Required fields not found in exchange tickers response");
                return Mono.empty();
            }

            // Search through data rows
            for (JsonNode row : data) {
                if (row.isArray() && row.size() > Math.max(cikIndex, tickerIndex)) {
                    String ticker = row.get(tickerIndex).asText();
                    if (symbol.equalsIgnoreCase(ticker)) {
                        String cik = String.format("%010d", row.get(cikIndex).asLong());
                        String name = nameIndex >= 0 && row.size() > nameIndex ?
                                     row.get(nameIndex).asText() : "";
                        String exchange = exchangeIndex >= 0 && row.size() > exchangeIndex ?
                                         row.get(exchangeIndex).asText() : "";

                        // Cache in database
                        Issuer issuer = new Issuer();
                        issuer.setCik(cik);
                        issuer.setTicker(ticker.toUpperCase());
                        issuer.setName(name);
                        issuer.setExchange(exchange);
                        issuerRepository.save(issuer);

                        logger.info("Cached new ticker mapping from exchange file: {} -> {}", symbol, cik);
                        return Mono.just(cik);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error searching for ticker {} in exchange response", symbol, e);
        }

        return Mono.empty();
    }

    private Mono<Void> parseAndStoreTickerMappings(String response) {
        try {
            JsonNode root = secApiClient.parseJsonResponse(response);
            int processed = 0;

            Iterator<String> fieldNames = root.fieldNames();
            while (fieldNames.hasNext()) {
                String key = fieldNames.next();
                JsonNode company = root.get(key);

                String ticker = company.get("ticker").asText();
                String cik = String.format("%010d", company.get("cik_str").asLong());
                String title = company.get("title").asText();

                // Check if already exists
                if (!issuerRepository.existsByTicker(ticker)) {
                    Issuer issuer = new Issuer();
                    issuer.setCik(cik);
                    issuer.setTicker(ticker.toUpperCase());
                    issuer.setName(title);
                    issuerRepository.save(issuer);
                    processed++;
                }
            }

            logger.info("Processed {} ticker mappings", processed);
            return Mono.empty();

        } catch (Exception e) {
            logger.error("Failed to parse and store ticker mappings", e);
            return Mono.error(e);
        }
    }
}