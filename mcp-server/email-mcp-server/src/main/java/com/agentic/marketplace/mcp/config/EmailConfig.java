package com.agentic.marketplace.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for email service.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "email")
public class EmailConfig {

    private From from = new From();
    private RateLimit rateLimit = new RateLimit();
    private Retry retry = new Retry();

    @Data
    public static class From {
        private String address = "noreply@agentic-marketplace.local";
        private String name = "Agentic Marketplace";
    }

    @Data
    public static class RateLimit {
        private boolean enabled = true;
        private int permitsPerMinute = 10;
    }

    @Data
    public static class Retry {
        private int maxAttempts = 3;
        private long delayMs = 1000;
    }
}
