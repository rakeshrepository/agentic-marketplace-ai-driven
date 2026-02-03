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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        You are a database management assistant. Parse the user's natural language request and extract the intent.
        
        Respond ONLY with valid JSON in this exact format:
        {
            "action": "create|list|drop|describe",
            "tableName": "table-name-if-applicable",
            "columns": [{"name": "column1", "type": "VARCHAR(255)"}, {"name": "column2", "type": "INTEGER"}],
            "valid": true|false,
            "errorMessage": "error-message-if-invalid"
        }
        
        Rules:
        - action must be one of: create, list, drop, describe
        - For "list" action, tableName and columns can be null
        - For "create" action, parse column names and infer SQL types (VARCHAR(255) for text, INTEGER for numbers, etc.)
        - For "drop" and "describe" actions, only tableName is needed
        - Set valid=false if the request doesn't make sense for database management
        
        Examples:
        - "Create a table called users with columns id, username, email" -> {"action":"create","tableName":"users","columns":[{"name":"id","type":"INTEGER"},{"name":"username","type":"VARCHAR(255)"},{"name":"email","type":"VARCHAR(255)"}],"valid":true,"errorMessage":null}
        - "List all tables" -> {"action":"list","tableName":null,"columns":null,"valid":true,"errorMessage":null}
        - "Drop table users" -> {"action":"drop","tableName":"users","columns":null,"valid":true,"errorMessage":null}
        - "Describe table orders" -> {"action":"describe","tableName":"orders","columns":null,"valid":true,"errorMessage":null}
        
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
            
            List<Map<String, String>> columns = new ArrayList<>();
            if (node.has("columns") && node.get("columns").isArray()) {
                for (JsonNode colNode : node.get("columns")) {
                    Map<String, String> column = new HashMap<>();
                    column.put("name", colNode.get("name").asText());
                    column.put("type", colNode.get("type").asText());
                    columns.add(column);
                }
            }
            
            return ParsedIntent.builder()
                    .action(getTextValue(node, "action"))
                    .tableName(getTextValue(node, "tableName"))
                    .columns(columns.isEmpty() ? null : columns)
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

    private ParsedIntent createErrorIntent(String message) {
        return ParsedIntent.builder()
                .valid(false)
                .errorMessage(message)
                .build();
    }
    
    public String getHelpfulErrorExplanation(String errorAnalysisPrompt) {
        log.info("Generating helpful error explanation with LLM");
        
        try {
            OllamaRequest request = OllamaRequest.builder()
                    .model(ollamaConfig.getModel())
                    .prompt(errorAnalysisPrompt)
                    .stream(false)
                    .options(OllamaRequest.Options.builder().temperature(0.7).build())
                    .build();

            OllamaResponse response = ollamaWebClient.post()
                    .uri("/api/generate")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OllamaResponse.class)
                    .timeout(Duration.ofMillis(ollamaConfig.getTimeout()))
                    .block();

            if (response != null && response.getResponse() != null) {
                return response.getResponse().trim();
            }
            
            return "An error occurred while processing your request. Please try again with different table or column names.";
            
        } catch (Exception e) {
            log.error("Error generating helpful explanation", e);
            return "An error occurred while processing your request. Please try again.";
        }
    }
}

