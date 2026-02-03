package com.agentic.marketplace.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "mcp.database")
public class McpDatabaseConfig {
    private String baseUrl = "http://localhost:8083";
    private int timeout = 30000;
}
