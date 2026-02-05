package com.agentic.marketplace.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmConfig {
    private String provider = "ollama"; // ollama or openai
    private Long timeout = 60000L;
}
