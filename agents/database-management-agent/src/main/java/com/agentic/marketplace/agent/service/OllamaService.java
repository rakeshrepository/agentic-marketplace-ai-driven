package com.agentic.marketplace.agent.service;

import com.agentic.marketplace.agent.config.OllamaConfig;
import com.agentic.marketplace.agent.constants.DatabaseConstants;
import com.agentic.marketplace.agent.model.OllamaRequest;
import com.agentic.marketplace.agent.model.OllamaResponse;
import com.agentic.marketplace.agent.model.ParsedIntent;
import com.agentic.marketplace.agent.prompt.DatabasePrompts;
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

    public OllamaService(@Qualifier("ollamaWebClient") WebClient ollamaWebClient,
                         OllamaConfig ollamaConfig) {
        this.ollamaWebClient = ollamaWebClient;
        this.ollamaConfig = ollamaConfig;
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
            promptBuilder.append(String.format(DatabasePrompts.INTENT_EXTRACTION_PROMPT, userQuery));
            
            String fullPrompt = promptBuilder.toString();
            log.debug("Full prompt with context:\n{}", fullPrompt);
            
            OllamaRequest request = OllamaRequest.builder()
                    .model(ollamaConfig.getModel())
                    .prompt(fullPrompt)
                    .stream(false)
                    .format("json")
                    .options(OllamaRequest.Options.builder()
                            .temperature(DatabaseConstants.LlmTemperature.EXTRACTION)
                            .build())
                    .build();

            OllamaResponse response = ollamaWebClient.post()
                    .uri("/api/generate")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OllamaResponse.class)
                    .timeout(Duration.ofMillis(ollamaConfig.getTimeout()))
                    .block();

            if (response == null || response.getResponse() == null) {
                log.error("Failed to get response from Ollama LLM");
                return ParsedIntent.builder().build();
            }

            log.debug("LLM response: {}", response.getResponse());
            return JsonParsingUtil.parseJsonResponse(response.getResponse());
            
        } catch (Exception e) {
            log.error("Error parsing intent with Ollama", e);
            return ParsedIntent.builder().build();
        }
    }

    @Override
    public String generateSuccessResponse(String originalQuery, String action, String target, Object resultData) {
        log.info("Generating success response for action: {}", action);
        
        try {
            String prompt = String.format(
                DatabasePrompts.SUCCESS_RESPONSE_PROMPT,
                originalQuery,
                action,
                target != null ? target : "N/A",
                resultData != null ? resultData.toString() : "{}"
            );
            
            OllamaRequest request = OllamaRequest.builder()
                    .model(ollamaConfig.getModel())
                    .prompt(prompt)
                    .stream(false)
                    .options(OllamaRequest.Options.builder()
                            .temperature(DatabaseConstants.LlmTemperature.GENERATION)
                            .build())
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
            
        } catch (Exception e) {
            log.error("Error generating success response", e);
        }
        
        // Fallback response
        return String.format("Successfully completed %s operation%s", 
            action, 
            target != null ? " on " + target : "");
    }

    @Override
    public String generateErrorSuggestion(String errorContext) {
        log.info("Generating error suggestion");
        
        try {
            String prompt = String.format(DatabasePrompts.ERROR_SUGGESTION_PROMPT, errorContext);
            
            OllamaRequest request = OllamaRequest.builder()
                    .model(ollamaConfig.getModel())
                    .prompt(prompt)
                    .stream(false)
                    .options(OllamaRequest.Options.builder()
                            .temperature(DatabaseConstants.LlmTemperature.GENERATION)
                            .build())
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
            
        } catch (Exception e) {
            log.error("Error generating error suggestion", e);
        }
        
        return "An error occurred while processing your request. Please check your input and try again.";
    }

    @Override
    public String generateValidationError(String userQuery, ParsedIntent intent, String validationError) {
        log.info("Generating validation error message");
        
        try {
            String prompt = String.format(
                DatabasePrompts.VALIDATION_ERROR_PROMPT,
                userQuery,
                intent.getAction() != null ? intent.getAction() : "unknown",
                intent.toString(),
                validationError
            );
            
            OllamaRequest request = OllamaRequest.builder()
                    .model(ollamaConfig.getModel())
                    .prompt(prompt)
                    .stream(false)
                    .options(OllamaRequest.Options.builder()
                            .temperature(DatabaseConstants.LlmTemperature.GENERATION)
                            .build())
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
            
        } catch (Exception e) {
            log.error("Error generating validation error", e);
        }
        
        return validationError; // Fallback to raw validation error
    }
}

