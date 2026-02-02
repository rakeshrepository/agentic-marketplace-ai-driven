package com.agentic.marketplace.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "kafka")
public class KafkaConfig {
    private String bootstrapServers = "localhost:9092";
    private Admin admin = new Admin();
    
    @Data
    public static class Admin {
        private int requestTimeoutMs = 30000;
        private int defaultPartitions = 1;
        private int defaultReplicationFactor = 1;
    }
}
