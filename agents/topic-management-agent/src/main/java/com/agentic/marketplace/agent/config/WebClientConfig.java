package com.agentic.marketplace.agent.config;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;

@Slf4j
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
    public WebClient openaiWebClient(WebClient.Builder builder, OpenAiConfig config) {
        try {
            // Create SSL context that trusts all certificates (for OpenAI HTTPS)
            SslContext sslContext = SslContextBuilder
                    .forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();

            // Create HTTP client with SSL context
            HttpClient httpClient = HttpClient.create()
                    .secure(sslContextSpec -> sslContextSpec.sslContext(sslContext));

            return builder
                    .baseUrl(config.getBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + config.getApiKey())
                    .defaultHeader("Content-Type", "application/json")
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .build();
        } catch (SSLException e) {
            log.error("Failed to create SSL context for OpenAI client", e);
            // Fallback to basic client without SSL configuration
            return builder
                    .baseUrl(config.getBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + config.getApiKey())
                    .defaultHeader("Content-Type", "application/json")
                    .build();
        }
    }

    @Bean
    public WebClient mcpWebClient(WebClient.Builder builder, McpKafkaConfig config) {
        return builder
                .baseUrl(config.getBaseUrl())
                .build();
    }
}
