package com.agentic.marketplace.agent.service;

import com.agentic.marketplace.agent.config.McpKafkaConfig;
import com.agentic.marketplace.agent.model.ParsedIntent;
import com.agentic.marketplace.sdk.model.AgentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class McpKafkaService {

    private final WebClient mcpWebClient;
    private final McpKafkaConfig mcpKafkaConfig;

    public McpKafkaService(@Qualifier("mcpWebClient") WebClient mcpWebClient,
                           McpKafkaConfig mcpKafkaConfig) {
        this.mcpWebClient = mcpWebClient;
        this.mcpKafkaConfig = mcpKafkaConfig;
    }

    public AgentResponse executeAction(ParsedIntent intent) {
        log.info("Executing action: {} for topic: {}", intent.getAction(), intent.getTopicName());

        return switch (intent.getAction().toLowerCase()) {
            case "create" -> createTopic(intent);
            case "list" -> listTopics();
            case "delete" -> deleteTopic(intent);
            case "describe" -> describeTopic(intent);
            default -> AgentResponse.builder()
                    .success(false)
                    .error("Unknown action: " + intent.getAction())
                    .build();
        };
    }

    private AgentResponse createTopic(ParsedIntent intent) {
        if (intent.getTopicName() == null || intent.getTopicName().isBlank()) {
            return AgentResponse.builder()
                    .success(false)
                    .error("Topic name is required for create action")
                    .build();
        }

        Map<String, Object> request = new HashMap<>();
        request.put("topicName", intent.getTopicName());
        if (intent.getPartitions() != null) {
            request.put("partitions", intent.getPartitions());
        }
        if (intent.getReplicationFactor() != null) {
            request.put("replicationFactor", intent.getReplicationFactor());
        }

        return mcpWebClient.post()
                .uri("/api/topics")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AgentResponse.class)
                .timeout(Duration.ofMillis(mcpKafkaConfig.getTimeout()))
                .block();
    }

    private AgentResponse listTopics() {
        return mcpWebClient.get()
                .uri("/api/topics")
                .retrieve()
                .bodyToMono(AgentResponse.class)
                .timeout(Duration.ofMillis(mcpKafkaConfig.getTimeout()))
                .block();
    }

    private AgentResponse deleteTopic(ParsedIntent intent) {
        if (intent.getTopicName() == null || intent.getTopicName().isBlank()) {
            return AgentResponse.builder()
                    .success(false)
                    .error("Topic name is required for delete action")
                    .build();
        }

        return mcpWebClient.delete()
                .uri("/api/topics/{topicName}", intent.getTopicName())
                .retrieve()
                .bodyToMono(AgentResponse.class)
                .timeout(Duration.ofMillis(mcpKafkaConfig.getTimeout()))
                .block();
    }

    private AgentResponse describeTopic(ParsedIntent intent) {
        if (intent.getTopicName() == null || intent.getTopicName().isBlank()) {
            return AgentResponse.builder()
                    .success(false)
                    .error("Topic name is required for describe action")
                    .build();
        }

        return mcpWebClient.get()
                .uri("/api/topics/{topicName}", intent.getTopicName())
                .retrieve()
                .bodyToMono(AgentResponse.class)
                .timeout(Duration.ofMillis(mcpKafkaConfig.getTimeout()))
                .block();
    }
}
