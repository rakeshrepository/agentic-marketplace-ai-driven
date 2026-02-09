package com.agentic.marketplace.agent.service;

import com.agentic.marketplace.agent.config.LlmConfig;
import com.agentic.marketplace.agent.config.OpenAiConfig;
import com.agentic.marketplace.agent.constants.DatabaseConstants;
import com.agentic.marketplace.agent.model.OpenAiRequest;
import com.agentic.marketplace.agent.model.OpenAiResponse;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
public class OpenAiLlmService implements LlmService {

    private final WebClient openaiWebClient;
    private final OpenAiConfig openAiConfig;
    private final LlmConfig llmConfig;

    public OpenAiLlmService(@Qualifier("openaiWebClient") WebClient openaiWebClient,
                            OpenAiConfig openAiConfig,
                            LlmConfig llmConfig) {
        this.openaiWebClient = openaiWebClient;
        this.openAiConfig = openAiConfig;
        this.llmConfig = llmConfig;
        log.info("Using OpenAI LLM Provider with model: {}", openAiConfig.getModel());
    }

    @Override
    public ParsedIntent parseIntent(String userQuery, List<ConversationMessage> conversationHistory, Map<String, Object> context) {
        log.info("Parsing intent with conversation context for query: {}", userQuery);

        try {
            // Build messages list with conversation history
            List<OpenAiRequest.Message> messages = new ArrayList<>();
            
            // Add conversation history
            if (conversationHistory != null && !conversationHistory.isEmpty()) {
                for (ConversationMessage msg : conversationHistory) {
                    messages.add(OpenAiRequest.Message.builder()
                            .role(msg.getRole())
                            .content(msg.getContent())
                            .build());
                }
            }
            
            // Build context string
            StringBuilder contextBuilder = new StringBuilder();
            if (context != null && !context.isEmpty()) {
                contextBuilder.append("STORED CONTEXT:\n");
                context.forEach((key, value) -> 
                    contextBuilder.append("- ").append(key).append(": ").append(value).append("\n")
                );
                contextBuilder.append("\n");
            }
            
            // Add system prompt with context
            String fullPrompt = contextBuilder.toString() + String.format(DatabasePrompts.INTENT_EXTRACTION_PROMPT, userQuery);
            
            messages.add(OpenAiRequest.Message.builder()
                    .role("user")
                    .content(fullPrompt)
                    .build());

            OpenAiRequest request = OpenAiRequest.builder()
                    .model(openAiConfig.getModel())
                    .messages(messages)
                    .temperature(DatabaseConstants.LlmTemperature.EXTRACTION)
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
                log.error("Failed to get response from OpenAI");
                return ParsedIntent.builder().build();
            }

            String content = response.getChoices().get(0).getMessage().getContent();
            log.debug("OpenAI response: {}", content);
            return JsonParsingUtil.parseJsonResponse(content);

        } catch (Exception e) {
            log.error("Error parsing intent with OpenAI", e);
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

            OpenAiRequest request = OpenAiRequest.builder()
                    .model(openAiConfig.getModel())
                    .messages(List.of(
                            OpenAiRequest.Message.builder()
                                    .role("user")
                                    .content(prompt)
                                    .build()
                    ))
                    .temperature(DatabaseConstants.LlmTemperature.GENERATION)
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
            log.error("Error generating success response with OpenAI", e);
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

            OpenAiRequest request = OpenAiRequest.builder()
                    .model(openAiConfig.getModel())
                    .messages(List.of(
                            OpenAiRequest.Message.builder()
                                    .role("user")
                                    .content(prompt)
                                    .build()
                    ))
                    .temperature(DatabaseConstants.LlmTemperature.GENERATION)
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
            log.error("Error generating error suggestion with OpenAI", e);
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

            OpenAiRequest request = OpenAiRequest.builder()
                    .model(openAiConfig.getModel())
                    .messages(List.of(
                            OpenAiRequest.Message.builder()
                                    .role("user")
                                    .content(prompt)
                                    .build()
                    ))
                    .temperature(DatabaseConstants.LlmTemperature.GENERATION)
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
            log.error("Error generating validation error with OpenAI", e);
        }

        return validationError; // Fallback to raw validation error
    }
}

