package com.stockdelta.common.sec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockdelta.common.config.SecConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Component
public class SecApiClient {

    private static final Logger logger = LoggerFactory.getLogger(SecApiClient.class);

    private final WebClient webClient;
    private final SecConfig secConfig;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Autowired
    public SecApiClient(SecConfig secConfig, RedisTemplate<String, Object> redisTemplate) {
        this.secConfig = secConfig;
        this.redisTemplate = redisTemplate;
        this.rateLimiter = new RateLimiter(secConfig.getRateLimitRps());
        this.objectMapper = new ObjectMapper();

        this.webClient = WebClient.builder()
                .baseUrl(secConfig.getBaseUrl())
                .defaultHeader("User-Agent", secConfig.getUserAgent())
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Accept-Encoding", "gzip, deflate")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();

        logger.info("SEC API Client initialized with User-Agent: {}", secConfig.getUserAgent());
    }

    public Mono<String> fetchCompanyTickers() {
        return executeWithRateLimit("/files/company_tickers.json", "company_tickers");
    }

    public Mono<String> fetchCompanyTickersExchange() {
        return executeWithRateLimit("/files/company_tickers_exchange.json", "company_tickers_exchange");
    }

    public Mono<String> fetchCompanySubmissions(String cik) {
        String normalizedCik = normalizeCik(cik);
        String endpoint = String.format("/submissions/CIK%s.json", normalizedCik);
        String cacheKey = String.format("submissions_%s", normalizedCik);
        return executeWithRateLimit(endpoint, cacheKey);
    }

    public Mono<String> fetchCompanyFacts(String cik) {
        String normalizedCik = normalizeCik(cik);
        String endpoint = String.format("/api/xbrl/companyfacts/CIK%s.json", normalizedCik);
        String cacheKey = String.format("facts_%s", normalizedCik);
        return executeWithRateLimit(endpoint, cacheKey);
    }

    public Mono<String> fetchDailyIndex(String date) {
        // Format: /Archives/edgar/daily-index/2024/QTR4/master.20241201.idx
        String year = date.substring(0, 4);
        int month = Integer.parseInt(date.substring(4, 6));
        int quarter = (month - 1) / 3 + 1;

        String endpoint = String.format("/Archives/edgar/daily-index/%s/QTR%d/master.%s.idx",
                                       year, quarter, date);
        String cacheKey = String.format("daily_index_%s", date);

        return executeWithRateLimit(endpoint, cacheKey);
    }

    public Mono<String> fetchDocument(String url) {
        // URL can be absolute (full SEC URL) or relative path
        String finalUrl = url.startsWith("http") ? url : secConfig.getBaseUrl() + url;

        // Extract a cache key from the URL
        String cacheKey = "doc_" + url.hashCode();

        // Check cache first
        String cached = getCachedResponse(cacheKey);
        if (cached != null) {
            logger.debug("Cache hit for document: {}", url);
            return Mono.just(cached);
        }

        // Apply rate limiting
        return Mono.fromCallable(() -> {
            rateLimiter.acquire();
            return true;
        })
        .flatMap(ignored -> {
            logger.debug("Fetching document: {}", url);

            WebClient client = url.startsWith("http") ?
                WebClient.builder()
                    .defaultHeader("User-Agent", secConfig.getUserAgent())
                    .defaultHeader("Accept", "text/html,application/xhtml+xml")
                    .defaultHeader("Accept-Encoding", "gzip, deflate")
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024)) // 50MB for documents
                    .build() :
                webClient;

            return client.get()
                    .uri(url.startsWith("http") ? finalUrl : url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                            .filter(this::isRetryableError))
                    .doOnSuccess(response -> cacheResponse(cacheKey, response))
                    .doOnError(error -> logger.error("Document fetch failed for {}: {}", url, error.getMessage()));
        });
    }

    private Mono<String> executeWithRateLimit(String endpoint, String cacheKey) {
        // Check cache first
        String cached = getCachedResponse(cacheKey);
        if (cached != null) {
            logger.debug("Cache hit for endpoint: {}", endpoint);
            return Mono.just(cached);
        }

        // Apply rate limiting
        return Mono.fromCallable(() -> {
            rateLimiter.acquire();
            return true;
        })
        .flatMap(ignored -> {
            logger.debug("Making SEC API call: {}", endpoint);
            return webClient.get()
                    .uri(endpoint)
                    .retrieve()
                    .bodyToMono(String.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                            .filter(this::isRetryableError))
                    .doOnSuccess(response -> cacheResponse(cacheKey, response))
                    .doOnError(error -> logger.error("SEC API call failed for {}: {}", endpoint, error.getMessage()));
        });
    }

    private boolean isRetryableError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException ex = (WebClientResponseException) throwable;
            return ex.getStatusCode().is5xxServerError() ||
                   ex.getStatusCode().value() == 429; // Too Many Requests
        }
        return false;
    }

    private String getCachedResponse(String key) {
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            return cached != null ? cached.toString() : null;
        } catch (Exception e) {
            logger.warn("Cache retrieval failed for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    private void cacheResponse(String key, String response) {
        try {
            // Cache for 1 hour for most endpoints, 24 hours for ticker mappings
            long ttl = key.startsWith("company_tickers") ? 24 : 1;
            redisTemplate.opsForValue().set(key, response, ttl, TimeUnit.HOURS);
            logger.debug("Cached response for key: {} (TTL: {}h)", key, ttl);
        } catch (Exception e) {
            logger.warn("Cache storage failed for key {}: {}", key, e.getMessage());
        }
    }

    public static String normalizeCik(String cik) {
        if (cik == null || cik.trim().isEmpty()) {
            throw new IllegalArgumentException("CIK cannot be null or empty");
        }

        // Remove any non-numeric characters and pad to 10 digits
        String numericCik = cik.replaceAll("[^0-9]", "");
        return String.format("%010d", Long.parseLong(numericCik));
    }

    public JsonNode parseJsonResponse(String response) throws IOException {
        return objectMapper.readTree(response);
    }

    // Simple token bucket rate limiter
    private static class RateLimiter {
        private final long intervalMillis;
        private long lastCallTime = 0;

        public RateLimiter(int requestsPerSecond) {
            this.intervalMillis = 1000L / requestsPerSecond;
        }

        public synchronized void acquire() {
            long now = System.currentTimeMillis();
            long timeSinceLastCall = now - lastCallTime;

            if (timeSinceLastCall < intervalMillis) {
                try {
                    Thread.sleep(intervalMillis - timeSinceLastCall);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            lastCallTime = System.currentTimeMillis();
        }
    }
}