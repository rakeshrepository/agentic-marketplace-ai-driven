package com.agentic.marketplace.agent.service;

import com.agentic.marketplace.agent.config.OllamaConfig;
import com.agentic.marketplace.agent.model.OllamaRequest;
import com.agentic.marketplace.agent.model.OllamaResponse;
import com.agentic.marketplace.agent.model.ParsedIntent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Slf4j
@Service
public class OllamaService {

    private final WebClient ollamaWebClient;
    private final OllamaConfig ollamaConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaService(@Qualifier("ollamaWebClient") WebClient ollamaWebClient, 
                         OllamaConfig ollamaConfig) {
        this.ollamaWebClient = ollamaWebClient;
        this.ollamaConfig = ollamaConfig;
    }

    private static final String SYSTEM_PROMPT = """
        You are a Kafka topic management assistant. Parse the user's natural language request and extract the intent.
        
        Respond ONLY with valid JSON in this exact format:
        {
            "action": "create|list|delete|describe",
            "topicName": "topic-name-if-applicable",
            "partitions": number-or-null,
            "replicationFactor": number-or-null,
            "valid": true|false,
            "errorMessage": "error-message-if-invalid"
        }
        
        Rules:
        - action must be one of: create, list, delete, describe
        - For "list" action, topicName can be null
        - For "create" action, if partitions not specified, use null (server will use defaults)
        - For "create" action, if replicationFactor not specified, use null
        - Set valid=false if the request doesn't make sense for Kafka topic management
        
        Examples:
        - "Create a topic called orders" -> {"action":"create","topicName":"orders","partitions":null,"replicationFactor":null,"valid":true,"errorMessage":null}
        - "List all topics" -> {"action":"list","topicName":null,"partitions":null,"replicationFactor":null,"valid":true,"errorMessage":null}
        - "Delete the users topic" -> {"action":"delete","topicName":"users","partitions":null,"replicationFactor":null,"valid":true,"errorMessage":null}
        - "Describe topic events" -> {"action":"describe","topicName":"events","partitions":null,"replicationFactor":null,"valid":true,"errorMessage":null}
        - "Create topic payments with 5 partitions" -> {"action":"create","topicName":"payments","partitions":5,"replicationFactor":null,"valid":true,"errorMessage":null}
        
        User request: %s
        """;

    public ParsedIntent parseIntent(String userQuery) {
        log.info("Parsing intent for query: {}", userQuery);
        
        try {
            String prompt = String.format(SYSTEM_PROMPT, userQuery);
            
            OllamaRequest request = OllamaRequest.builder()
                    .model(ollamaConfig.getModel())
                    .prompt(prompt)
                    .stream(false)
                    .format("json")
                    .options(OllamaRequest.Options.builder().temperature(0.1).build())
                    .build();

            OllamaResponse response = ollamaWebClient.post()
                    .uri("/api/generate")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OllamaResponse.class)
                    .timeout(Duration.ofMillis(ollamaConfig.getTimeout()))
                    .block();

            if (response == null || response.getResponse() == null) {
                return createErrorIntent("Failed to get response from LLM");
            }

            log.debug("LLM response: {}", response.getResponse());
            return parseJsonResponse(response.getResponse());
            
        } catch (Exception e) {
            log.error("Error parsing intent", e);
            return createErrorIntent("Error processing request: " + e.getMessage());
        }
    }

    private ParsedIntent parseJsonResponse(String jsonResponse) {
        try {
            JsonNode node = objectMapper.readTree(jsonResponse);
            
            return ParsedIntent.builder()
                    .action(getTextValue(node, "action"))
                    .topicName(getTextValue(node, "topicName"))
                    .partitions(getIntValue(node, "partitions"))
                    .replicationFactor(getIntValue(node, "replicationFactor"))
                    .valid(node.has("valid") ? node.get("valid").asBoolean() : true)
                    .errorMessage(getTextValue(node, "errorMessage"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse JSON response: {}", jsonResponse, e);
            return createErrorIntent("Failed to parse LLM response");
        }
    }

    private String getTextValue(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private Integer getIntValue(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asInt() : null;
    }

    private ParsedIntent createErrorIntent(String message) {
        return ParsedIntent.builder()
                .valid(false)
                .errorMessage(message)
                .build();
    }
}
