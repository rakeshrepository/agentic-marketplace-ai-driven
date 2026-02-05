package com.agentic.marketplace.agent.service;

import com.agentic.marketplace.agent.config.LlmConfig;
import com.agentic.marketplace.agent.config.OllamaConfig;
import com.agentic.marketplace.agent.model.OllamaRequest;
import com.agentic.marketplace.agent.model.OllamaResponse;
import com.agentic.marketplace.agent.model.ParsedIntent;
import com.agentic.marketplace.agent.prompt.KafkaTopicPrompts;
import com.agentic.marketplace.sdk.model.ConversationMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaService implements LlmService {

    private final WebClient ollamaWebClient;
    private final OllamaConfig ollamaConfig;
    private final LlmConfig llmConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaService(@Qualifier("ollamaWebClient") WebClient ollamaWebClient,
                         OllamaConfig ollamaConfig,
                         LlmConfig llmConfig) {
        this.ollamaWebClient = ollamaWebClient;
        this.ollamaConfig = ollamaConfig;
        this.llmConfig = llmConfig;
        log.info("Using Ollama LLM Provider with model: {}", ollamaConfig.getModel());
    }

    @Override
    public ParsedIntent parseIntent(String userQuery, List<ConversationMessage> conversationHistory, Map<String, Object> context) {
        log.info("Parsing intent with conversation context for query: {}", userQuery);
        
        try {
            // Build enhanced prompt with conversation history and context
            StringBuilder promptBuilder = new StringBuilder();
            
            // Add conversation history if available
            if (conversationHistory != null && !conversationHistory.isEmpty()) {
                promptBuilder.append("CONVERSATION HISTORY:\n");
                for (ConversationMessage msg : conversationHistory) {
                    promptBuilder.append(msg.getRole().toUpperCase())
                        .append(": ")
                        .append(msg.getContent())
                        .append("\n");
                }
                promptBuilder.append("\n");
            }
            
            // Add stored context (like suggestions from previous response)
            if (context != null && !context.isEmpty()) {
                promptBuilder.append("STORED CONTEXT FROM PREVIOUS INTERACTION:\n");
                context.forEach((key, value) -> 
                    promptBuilder.append("- ").append(key).append(": ").append(value).append("\n")
                );
                promptBuilder.append("\n");
            }
            
            // Add system prompt and current query
            promptBuilder.append(String.format(KafkaTopicPrompts.INTENT_EXTRACTION_PROMPT, userQuery));
            
            String fullPrompt = promptBuilder.toString();
            log.debug("Full prompt with context:\n{}", fullPrompt);
            
            OllamaRequest request = OllamaRequest.builder()
                    .model(ollamaConfig.getModel())
                    .prompt(fullPrompt)
                    .stream(false)
                    .format("json")
                    .options(OllamaRequest.Options.builder().temperature(0.1).build())
                    .build();

            OllamaResponse response = ollamaWebClient.post()
                    .uri("/api/generate")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OllamaResponse.class)
                    .timeout(Duration.ofMillis(llmConfig.getTimeout()))
                    .block();

            if (response == null || response.getResponse() == null) {
                log.error("Failed to get response from Ollama LLM");
                // Return empty intent - orchestrator will handle validation
                return ParsedIntent.builder().build();
            }

            log.debug("LLM response: {}", response.getResponse());
            return parseJsonResponse(response.getResponse());
            
        } catch (Exception e) {
            log.error("Error parsing intent", e);
            // Return empty intent - orchestrator will handle validation
            return ParsedIntent.builder().build();
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
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse JSON response: {}", jsonResponse, e);
            // Return empty intent on parse failure - orchestrator will handle validation
            return ParsedIntent.builder().build();
        }
    }

    private String getTextValue(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private Integer getIntValue(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asInt() : null;
    }

    @Override
    public String generateErrorSuggestion(String errorContext) {
        log.info("Generating intelligent error suggestion for context: {}", errorContext);
        
        String prompt = String.format("""
            You are a helpful Kafka topic management assistant. An error occurred while processing a user's request.
            
            Analyze the error and provide a friendly, actionable suggestion to the user.
            
            Guidelines:
            - If the error mentions replication factor exceeding available brokers, suggest using replication factor 1-3 (typically 1 for dev, 3 for prod)
            - If the error mentions partition limits, suggest using 1-10 partitions for most use cases
            - If the error is about topic already exists, suggest using a different name or deleting the existing topic first
            - If the error is about topic not found, suggest checking the topic name or listing available topics
            - Keep response concise (2-3 sentences max)
            - Be friendly and helpful
            - Include specific actionable recommendations
            
            Error Context:
            %s
            
            Provide a helpful suggestion to the user (plain text, no JSON):
            """, errorContext);
        
        try {
            OllamaRequest request = OllamaRequest.builder()
                    .model(ollamaConfig.getModel())
                    .prompt(prompt)
                    .stream(false)
                    .options(OllamaRequest.Options.builder().temperature(0.7).build())
                    .build();

            OllamaResponse response = ollamaWebClient.post()
                    .uri("/api/generate")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OllamaResponse.class)
                    .timeout(Duration.ofMillis(llmConfig.getTimeout()))
                    .block();

            if (response != null && response.getResponse() != null) {
                return response.getResponse().trim();
            }
        } catch (Exception e) {
            log.error("Error generating intelligent error suggestion", e);
        }
        
        // Fallback to original error context if LLM fails
        return "An error occurred: " + errorContext;
    }

    @Override
    public String generateSuccessResponse(String userQuery, String action, String topicName, Object mcpResult) {
        log.info("Generating natural language response for action: {}", action);
        
        String resultSummary = mcpResult != null ? mcpResult.toString() : "operation completed";
        
        String prompt = String.format("""
            You are a friendly and helpful Kafka topic management assistant.
            
            The user asked: "%s"
            
            You successfully performed: %s operation on topic '%s'
            
            Result data: %s
            
            Generate a natural, conversational response (2-3 sentences) that:
            - Confirms what was done in a friendly way
            - Mentions key details naturally (topic name, partitions if relevant)
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
            topicName != null ? topicName : "topics",
            resultSummary
        );
        
        try {
            OllamaRequest request = OllamaRequest.builder()
                    .model(ollamaConfig.getModel())
                    .prompt(prompt)
                    .stream(false)
                    .options(OllamaRequest.Options.builder().temperature(0.7).build())
                    .build();

            OllamaResponse response = ollamaWebClient.post()
                    .uri("/api/generate")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OllamaResponse.class)
                    .timeout(Duration.ofMillis(llmConfig.getTimeout()))
                    .block();

            if (response != null && response.getResponse() != null) {
                String naturalResponse = response.getResponse().trim();
                log.debug("Generated natural response: {}", naturalResponse);
                return naturalResponse;
            }
        } catch (Exception e) {
            log.error("Error generating natural language response", e);
        }
        
        // Fallback to simple confirmation if LLM fails
        return String.format("Successfully completed %s operation on topic '%s'", action, topicName);
    }
}
