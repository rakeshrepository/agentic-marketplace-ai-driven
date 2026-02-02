package com.agentic.marketplace.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public WebClient ollamaWebClient(WebClient.Builder builder, OllamaConfig config) {
        return builder
                .baseUrl(config.getBaseUrl())
                .build();
    }

    @Bean
    public WebClient mcpWebClient(WebClient.Builder builder, McpKafkaConfig config) {
        return builder
                .baseUrl(config.getBaseUrl())
                .build();
    }
}
