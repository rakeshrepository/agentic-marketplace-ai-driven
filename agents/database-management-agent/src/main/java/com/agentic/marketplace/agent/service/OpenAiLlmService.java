package com.agentic.marketplace.agent.service;

import com.agentic.marketplace.agent.config.LlmConfig;
import com.agentic.marketplace.agent.config.OpenAiConfig;
import com.agentic.marketplace.agent.model.OpenAiRequest;
import com.agentic.marketplace.agent.model.OpenAiResponse;
import com.agentic.marketplace.agent.model.ParsedIntent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
public class OpenAiLlmService implements LlmService {

    private final WebClient openaiWebClient;
    private final OpenAiConfig openAiConfig;
    private final LlmConfig llmConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiLlmService(@Qualifier("openaiWebClient") WebClient openaiWebClient,
                            OpenAiConfig openAiConfig,
                            LlmConfig llmConfig) {
        this.openaiWebClient = openaiWebClient;
        this.openAiConfig = openAiConfig;
        this.llmConfig = llmConfig;
        log.info("Using OpenAI LLM Provider with model: {}", openAiConfig.getModel());
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
        - For "create" action, parse column names and infer SQL types (VARCHAR(255) for text, INTEGER for numbers, TIMESTAMP for dates, BOOLEAN for flags, etc.)
        - For "drop" and "describe" actions, only tableName is needed
        - Set valid=false if the request doesn't make sense for database management
        - When valid=false, provide a FRIENDLY, CONVERSATIONAL errorMessage (not robotic or template-like)
        - Error messages should feel natural, like you're helpfully explaining to a colleague
        - When valid=true, set errorMessage=null (do not provide suggestions for valid requests)
        
        Examples:
        - "Create a table called users with columns id, username, email" -> {"action":"create","tableName":"users","columns":[{"name":"id","type":"INTEGER"},{"name":"username","type":"VARCHAR(255)"},{"name":"email","type":"VARCHAR(255)"}],"valid":true,"errorMessage":null}
        - "List all tables" -> {"action":"list","tableName":null,"columns":null,"valid":true,"errorMessage":null}
        - "Drop table users" -> {"action":"drop","tableName":"users","columns":null,"valid":true,"errorMessage":null}
        - "Describe table orders" -> {"action":"describe","tableName":"orders","columns":null,"valid":true,"errorMessage":null}
        - "Create table products with id, name, price, in_stock" -> {"action":"create","tableName":"products","columns":[{"name":"id","type":"INTEGER"},{"name":"name","type":"VARCHAR(255)"},{"name":"price","type":"DECIMAL(10,2)"},{"name":"in_stock","type":"BOOLEAN"}],"valid":true,"errorMessage":null}
        - "Build me a website" -> {"action":null,"tableName":null,"columns":null,"valid":false,"errorMessage":"That sounds interesting, but I'm specialized in database table management. I can help you create, list, describe, or drop tables. What would you like to do with your database?"}
        """;

    @Override
    public ParsedIntent parseIntent(String userQuery) {
        log.info("Parsing intent with OpenAI for query: {}", userQuery);

        try {
            OpenAiRequest request = OpenAiRequest.builder()
                    .model(openAiConfig.getModel())
                    .messages(List.of(
                            OpenAiRequest.Message.builder()
                                    .role("system")
                                    .content(SYSTEM_PROMPT)
                                    .build(),
                            OpenAiRequest.Message.builder()
                                    .role("user")
                                    .content(userQuery)
                                    .build()
                    ))
                    .temperature(0.1)
                    .responseFormat(OpenAiRequest.ResponseFormat.builder()
                            .type("json_object")
                            .build())
                    .build();

            OpenAiResponse response = openaiWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OpenAiResponse.class)
                    .timeout(Duration.ofMillis(llmConfig.getTimeout()))
                    .block();

            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                return createErrorIntent("Failed to get response from OpenAI");
            }

            String content = response.getChoices().get(0).getMessage().getContent();
            log.debug("OpenAI response: {}", content);
            return parseJsonResponse(content);

        } catch (Exception e) {
            log.error("Error parsing intent with OpenAI", e);
            return createErrorIntent("Error processing request: " + e.getMessage());
        }
    }

    @Override
    public String generateSuccessResponse(String userQuery, String action, String entityName, Object mcpResult) {
        log.info("Generating natural language response with OpenAI for action: {}", action);

        String resultSummary = mcpResult != null ? mcpResult.toString() : "operation completed";

        String prompt = String.format("""
            You are a friendly and helpful database management assistant.
            
            The user asked: "%s"
            
            You successfully performed: %s operation on table '%s'
            
            Result data: %s
            
            Generate a natural, conversational response (2-3 sentences) that:
            - Confirms what was done in a friendly way
            - Mentions key details naturally (table name, columns if relevant)
            - Is brief but informative
            - Uses a casual, helpful tone
            - You may use emojis sparingly if it feels natural (✅ 🎉 📊)
            
            Do NOT:
            - Use templates or robotic language
            - Be overly formal or verbose
            - Include technical jargon unless necessary
            
            Response (plain text, conversational):
            """,
                userQuery,
                action,
                entityName != null ? entityName : "tables",
                resultSummary
        );

        try {
            OpenAiRequest request = OpenAiRequest.builder()
                    .model(openAiConfig.getModel())
                    .messages(List.of(
                            OpenAiRequest.Message.builder()
                                    .role("user")
                                    .content(prompt)
                                    .build()
                    ))
                    .temperature(0.7)
                    .build();

            OpenAiResponse response = openaiWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OpenAiResponse.class)
                    .timeout(Duration.ofMillis(llmConfig.getTimeout()))
                    .block();

            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                String naturalResponse = response.getChoices().get(0).getMessage().getContent().trim();
                log.debug("Generated natural response: {}", naturalResponse);
                return naturalResponse;
            }
        } catch (Exception e) {
            log.error("Error generating natural language response with OpenAI", e);
        }

        // Fallback to simple confirmation if OpenAI fails
        return String.format("Successfully completed %s operation on table '%s'", action, entityName);
    }

    @Override
    public String generateErrorSuggestion(String errorContext) {
        log.info("Generating intelligent error suggestion with OpenAI for context: {}", errorContext);

        String prompt = String.format("""
            You are a helpful database management assistant. An error occurred while processing a user's request.
            
            Analyze the error and provide a friendly, actionable suggestion to the user.
            
            Guidelines:
            - If the error is about table already exists, suggest using a different name or dropping the existing table first
            - If the error is about table not found, suggest checking the table name or listing available tables
            - If the error is about invalid column types, suggest checking SQL type syntax (VARCHAR, INTEGER, etc.)
            - If the error is about missing columns, remind them to specify column names and types for CREATE operations
            - Keep response concise (2-3 sentences max)
            - Be friendly and helpful
            - Include specific actionable recommendations
            
            Error Context:
            %s
            
            Provide a helpful suggestion to the user (plain text, no JSON):
            """, errorContext);

        try {
            OpenAiRequest request = OpenAiRequest.builder()
                    .model(openAiConfig.getModel())
                    .messages(List.of(
                            OpenAiRequest.Message.builder()
                                    .role("user")
                                    .content(prompt)
                                    .build()
                    ))
                    .temperature(0.7)
                    .build();

            OpenAiResponse response = openaiWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OpenAiResponse.class)
                    .timeout(Duration.ofMillis(llmConfig.getTimeout()))
                    .block();

            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                return response.getChoices().get(0).getMessage().getContent().trim();
            }
        } catch (Exception e) {
            log.error("Error generating intelligent error suggestion with OpenAI", e);
        }

        // Fallback to original error context if OpenAI fails
        return "An error occurred: " + errorContext;
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
