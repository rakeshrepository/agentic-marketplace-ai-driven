package com.agentic.marketplace.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "mcp.kafka")
public class McpKafkaConfig {
    private String baseUrl = "http://localhost:8081";
    private int timeout = 30000;
}
