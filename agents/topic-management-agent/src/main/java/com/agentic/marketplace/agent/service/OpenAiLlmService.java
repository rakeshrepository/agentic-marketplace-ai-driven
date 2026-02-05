package com.agentic.marketplace.agent.service;

import com.agentic.marketplace.agent.config.LlmConfig;
import com.agentic.marketplace.agent.config.OpenAiConfig;
import com.agentic.marketplace.agent.model.OpenAiRequest;
import com.agentic.marketplace.agent.model.OpenAiResponse;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
                log.error("Failed to get response from OpenAI");
                return ParsedIntent.builder().build(); // Orchestrator will handle validation
            }

            String content = response.getChoices().get(0).getMessage().getContent();
            log.debug("OpenAI response: {}", content);
            return parseJsonResponse(content);

        } catch (Exception e) {
            log.error("Error parsing intent with OpenAI", e);
            return ParsedIntent.builder().build(); // Orchestrator will handle validation
        }
    }

    @Override
    public String generateSuccessResponse(String userQuery, String action, String entityName, Object mcpResult) {
        log.info("Generating natural language response with OpenAI for action: {}", action);

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
        return String.format("Successfully completed %s operation on topic '%s'", action, entityName);
    }

    @Override
    public String generateErrorSuggestion(String errorContext) {
        log.info("Generating intelligent error suggestion with OpenAI for context: {}", errorContext);

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

            return ParsedIntent.builder()
                    .action(getTextValue(node, "action"))
                    .topicName(getTextValue(node, "topicName"))
                    .partitions(getIntValue(node, "partitions"))
                    .replicationFactor(getIntValue(node, "replicationFactor"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse JSON response: {}", jsonResponse, e);
            // Return empty intent - orchestrator will handle validation
            return ParsedIntent.builder().build();
        }
    }

    private String getTextValue(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private Integer getIntValue(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asInt() : null;
    }
}
