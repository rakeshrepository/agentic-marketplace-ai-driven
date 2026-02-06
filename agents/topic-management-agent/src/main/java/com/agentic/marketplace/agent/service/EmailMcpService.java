package com.agentic.marketplace.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Service to interact with Email MCP Server for sending emails.
 */
@Slf4j
@Service
public class EmailMcpService {

    private final WebClient webClient;

    @Value("${mcp.email.url:http://localhost:8084}")
    private String emailMcpUrl;

    @Value("${mcp.email.timeout:30000}")
    private int timeout;

    public EmailMcpService(@Qualifier("mcpWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Send Kafka credentials email after topic creation.
     *
     * @param recipientEmail Email address to send credentials to
     * @param topicName Name of the created topic
     * @param partitions Number of partitions
     * @param replicationFactor Replication factor
     * @param bootstrapServers Kafka bootstrap servers
     * @return Response from Email MCP Server
     */
    public String sendKafkaCredentials(String recipientEmail, String topicName, 
                                      int partitions, int replicationFactor, 
                                      String bootstrapServers) {
        try {
            log.info("Sending Kafka credentials email to: {} for topic: {}", recipientEmail, topicName);

            // Build email request with Kafka topic details
            Map<String, Object> emailRequest = Map.of(
                "to", recipientEmail,
                "subject", "Kafka Topic Created: " + topicName,
                "templateName", "kafka-credentials",
                "templateData", Map.of(
                    "topicName", topicName,
                    "partitions", partitions,
                    "replicationFactor", replicationFactor,
                    "bootstrapServers", bootstrapServers
                )
            );

            // Call Email MCP Server
            Map<String, Object> response = webClient.post()
                    .uri(emailMcpUrl + "/api/emails/send")
                    .bodyValue(emailRequest)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();

            Objects.requireNonNull(response, "Email MCP Server returned null response");

            boolean success = (boolean) response.getOrDefault("success", false);
            if (success) {
                log.info("Email sent successfully to: {}", recipientEmail);
                return "✓ Email sent successfully to " + recipientEmail;
            } else {
                String errorMessage = (String) response.getOrDefault("error", "Unknown error");
                log.error("Failed to send email: {}", errorMessage);
                return "⚠ Failed to send email: " + errorMessage;
            }

        } catch (Exception e) {
            log.error("Error sending email via Email MCP Server", e);
            return "⚠ Email notification failed: " + e.getMessage();
        }
    }

    /**
     * Health check for Email MCP Server.
     */
    public boolean isEmailMcpServerHealthy() {
        try {
            Map<String, Object> response = webClient.get()
                    .uri(emailMcpUrl + "/api/emails/health")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(5000))
                    .block();

            Objects.requireNonNull(response, "Email MCP Server health check returned null");
            String status = (String) response.getOrDefault("status", "DOWN");
            return "UP".equals(status);

        } catch (Exception e) {
            log.warn("Email MCP Server health check failed: {}", e.getMessage());
            return false;
        }
    }
}
