package com.agentic.marketplace.agent.service;

import com.agentic.marketplace.agent.config.LlmConfig;
import com.agentic.marketplace.agent.config.OpenAiConfig;
import com.agentic.marketplace.agent.constants.KafkaTopicConstants;
import com.agentic.marketplace.agent.model.OpenAiRequest;
import com.agentic.marketplace.agent.model.OpenAiResponse;
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
        log.info("Parsing intent with OpenAI and conversation context for query: {}", userQuery);

        try {
            List<OpenAiRequest.Message> messages = new ArrayList<>();
            
            // Build enhanced content with conversation history and context
            StringBuilder contentBuilder = new StringBuilder();
            
            // Add conversation history if available
            if (conversationHistory != null && !conversationHistory.isEmpty()) {
                contentBuilder.append("CONVERSATION HISTORY:\n");
                for (ConversationMessage msg : conversationHistory) {
                    contentBuilder.append(msg.getRole().toUpperCase())
                        .append(": ")
                        .append(msg.getContent())
                        .append("\n");
                }
                contentBuilder.append("\n");
            }
            
            // Add stored context if available
            if (context != null && !context.isEmpty()) {
                contentBuilder.append("STORED CONTEXT FROM PREVIOUS INTERACTION:\n");
                context.forEach((key, value) -> 
                    contentBuilder.append("- ").append(key).append(": ").append(value).append("\n")
                );
                contentBuilder.append("\n");
            }
            
            // Add system prompt with user query
            contentBuilder.append(String.format(KafkaTopicPrompts.INTENT_EXTRACTION_PROMPT, userQuery));
            
            // Add as system message
            messages.add(OpenAiRequest.Message.builder()
                    .role("system")
                    .content(contentBuilder.toString())
                    .build());
            
            // Add current user query
            messages.add(OpenAiRequest.Message.builder()
                    .role("user")
                    .content(userQuery)
                    .build());
            
            OpenAiRequest request = OpenAiRequest.builder()
                    .model(openAiConfig.getModel())
                    .messages(messages)
                    .temperature(KafkaTopicConstants.LlmTemperature.EXTRACTION)
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
                return ParsedIntent.builder().build(); // Orchestrator will handle validation
            }

            String content = response.getChoices().get(0).getMessage().getContent();
            log.debug("OpenAI response: {}", content);
            return JsonParsingUtil.parseJsonResponse(content);

        } catch (Exception e) {
            log.error("Error parsing intent with OpenAI", e);
            return ParsedIntent.builder().build(); // Orchestrator will handle validation
        }
    }

    @Override
    public String generateSuccessResponse(String userQuery, String action, String entityName, Object mcpResult) {
        log.info("Generating natural language response with OpenAI for action: {}", action);

        String resultSummary = mcpResult != null ? mcpResult.toString() : "operation completed";

        String prompt = String.format(KafkaTopicPrompts.SUCCESS_RESPONSE_TEMPLATE,
                userQuery,
                action,
                entityName != null ? entityName : "topics",
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
                    .temperature(KafkaTopicConstants.LlmTemperature.GENERATION)
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
        return String.format("Successfully completed %s operation on topic '%s'", action, entityName);
    }

    @Override
    public String generateErrorSuggestion(String errorContext) {
        log.info("Generating intelligent error suggestion with OpenAI for context: {}", errorContext);

        String prompt = String.format(KafkaTopicPrompts.ERROR_SUGGESTION_TEMPLATE, errorContext);

        try {
            OpenAiRequest request = OpenAiRequest.builder()
                    .model(openAiConfig.getModel())
                    .messages(List.of(
                            OpenAiRequest.Message.builder()
                                    .role("user")
                                    .content(prompt)
                                    .build()
                    ))
                    .temperature(KafkaTopicConstants.LlmTemperature.GENERATION)
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
}
