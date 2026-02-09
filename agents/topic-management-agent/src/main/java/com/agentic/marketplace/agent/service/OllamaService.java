package com.agentic.marketplace.agent.service;

import com.agentic.marketplace.agent.config.LlmConfig;
import com.agentic.marketplace.agent.config.OllamaConfig;
import com.agentic.marketplace.agent.constants.KafkaTopicConstants;
import com.agentic.marketplace.agent.model.OllamaRequest;
import com.agentic.marketplace.agent.model.OllamaResponse;
import com.agentic.marketplace.agent.model.ParsedIntent;
import com.agentic.marketplace.agent.prompt.KafkaTopicPrompts;
import com.agentic.marketplace.agent.util.JsonParsingUtil;
import com.agentic.marketplace.sdk.model.ConversationMessage;
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
                    .options(OllamaRequest.Options.builder().temperature(KafkaTopicConstants.LlmTemperature.EXTRACTION).build())
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
            return JsonParsingUtil.parseJsonResponse(response.getResponse());
            
        } catch (Exception e) {
            log.error("Error parsing intent", e);
            // Return empty intent - orchestrator will handle validation
            return ParsedIntent.builder().build();
        }
    }

    @Override
    public String generateErrorSuggestion(String errorContext) {
        log.info("Generating intelligent error suggestion for context: {}", errorContext);
        
        String prompt = String.format(KafkaTopicPrompts.ERROR_SUGGESTION_TEMPLATE, errorContext);
        
        try {
            OllamaRequest request = OllamaRequest.builder()
                    .model(ollamaConfig.getModel())
                    .prompt(prompt)
                    .stream(false)
                    .options(OllamaRequest.Options.builder().temperature(KafkaTopicConstants.LlmTemperature.GENERATION).build())
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
        
        // Handle null or empty results gracefully - don't show "null" to user
        String resultSummary;
        if (mcpResult == null) {
            resultSummary = "operation completed successfully";
        } else {
            String resultStr = mcpResult.toString();
            if (resultStr == null || resultStr.trim().isEmpty() || resultStr.equals("null") || resultStr.equals("[]")) {
                resultSummary = "operation completed successfully";
            } else {
                resultSummary = resultStr;
            }
        }
        
        String prompt = String.format(KafkaTopicPrompts.SUCCESS_RESPONSE_TEMPLATE,
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
                    .options(OllamaRequest.Options.builder().temperature(KafkaTopicConstants.LlmTemperature.GENERATION).build())
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
