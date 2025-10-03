package com.stockdelta.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "sec")
public class SecConfig {

    private String userAgent = "StockDeltaSystem/1.0; admin@stockdelta.com";
    private int rateLimitRps = 8;
    private String baseUrl = "https://data.sec.gov";

    // Getters and Setters
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public int getRateLimitRps() { return rateLimitRps; }
    public void setRateLimitRps(int rateLimitRps) { this.rateLimitRps = rateLimitRps; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}