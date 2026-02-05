package com.agentic.marketplace.agent.service;

import com.agentic.marketplace.agent.model.ParsedIntent;
import com.agentic.marketplace.sdk.model.AgentRequest;
import com.agentic.marketplace.sdk.model.AgentResponse;
import com.agentic.marketplace.sdk.model.ConversationMessage;
import com.agentic.marketplace.sdk.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final LlmService llmService;
    private final McpKafkaService mcpKafkaService;
    private final ConversationService conversationService;

    public AgentResponse processQuery(AgentRequest request) {
        // Generate or use provided session ID
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = UUID.randomUUID().toString();
            log.info("Generated new session ID: {}", sessionId);
        }
        
        log.info("Processing query for session {}: {}", sessionId, request.getQuery());

        try {
            // Add user message to conversation history
            conversationService.addUserMessage(sessionId, request.getQuery());
            
            // Get conversation context for smarter parsing
            Map<String, Object> context = conversationService.getContext(sessionId);
            List<ConversationMessage> recentHistory = 
                conversationService.getRecentMessages(sessionId, 10); // Last 10 messages
            
            log.debug("Session {} has {} messages in history, context keys: {}", 
                sessionId, recentHistory.size(), context.keySet());
            
            // Step 1: Parse intent with conversation context (extraction only, no validation)
            ParsedIntent intent = llmService.parseIntent(request.getQuery(), recentHistory, context);
            log.info("Parsed intent: {}", intent);

            // Step 2: Validate action
            if (intent.getAction() == null || intent.getAction().isBlank()) {
                String errorMsg = "Could not determine the action. Please specify if you want to create, list, delete, or describe topics.";
                conversationService.addAssistantMessage(sessionId, errorMsg, null);
                return AgentResponse.builder()
                        .success(false)
                        .error(errorMsg)
                        .sessionId(sessionId)
                        .build();
            }

            // Step 3: Validate required fields based on action
            String validationError = validateIntent(intent);
            if (validationError != null) {
                // Ask LLM to generate a friendly error message with context
                String friendlyError = generateValidationErrorMessage(
                    request.getQuery(), 
                    intent, 
                    validationError
                );
                
                // Store suggestions in context for next turn
                Map<String, Object> suggestions = new HashMap<>();
                if (intent.getPartitions() != null) {
                    suggestions.put("suggestedPartitions", intent.getPartitions());
                }
                if (intent.getReplicationFactor() != null) {
                    suggestions.put("suggestedReplicationFactor", intent.getReplicationFactor());
                }
                
                conversationService.addAssistantMessage(sessionId, friendlyError, suggestions);
                
                AgentResponse.AgentResponseBuilder responseBuilder = AgentResponse.builder()
                        .success(false)
                        .error(friendlyError)
                        .sessionId(sessionId);
                
                if (!suggestions.isEmpty()) {
                    responseBuilder.data(suggestions);
                }
                
                return responseBuilder.build();
            }

            // Step 4: Execute action via MCP server
            AgentResponse response = mcpKafkaService.executeAction(intent);
            
            // Step 5: Generate natural language response using LLM
            return generateNaturalResponse(response, intent, request.getQuery(), sessionId);
            
        } catch (Exception e) {
            log.error("Error processing query for session {}", sessionId, e);
            
            // Step 5: Use LLM to generate intelligent error response
            String errorContext = String.format(
                "User query: %s\nError: %s\nError type: %s",
                request.getQuery(),
                e.getMessage(),
                e.getClass().getSimpleName()
            );
            
            String intelligentError = llmService.generateErrorSuggestion(errorContext);
            conversationService.addAssistantMessage(sessionId, intelligentError, null);
            
            return AgentResponse.builder()
                    .success(false)
                    .error(intelligentError)
                    .sessionId(sessionId)
                    .build();
        }
    }

    private AgentResponse generateNaturalResponse(AgentResponse mcpResponse, ParsedIntent intent, String originalQuery, String sessionId) {
        if (mcpResponse.isSuccess()) {
            // Let LLM generate conversational response based on what happened
            String naturalMessage = llmService.generateSuccessResponse(
                    originalQuery,
                    intent.getAction(),
                    intent.getTopicName(),
                    mcpResponse.getData()
            );
            
            // Add assistant message to history
            conversationService.addAssistantMessage(sessionId, naturalMessage, null);
            
            return AgentResponse.builder()
                    .success(true)
                    .message(naturalMessage)  // LLM-generated conversational message
                    .data(mcpResponse.getData())  // Keep structured data for UI
                    .sessionId(sessionId)
                    .build();
        } else {
            // If MCP server returned an error response, throw it so orchestrator can enhance it with LLM
            throw new RuntimeException(mcpResponse.getError() != null ? mcpResponse.getError() : "Unknown error from MCP server");
        }
    }
    
    /**
     * Validates the parsed intent based on business rules.
     * Returns error message if invalid, null if valid.
     */
    private String validateIntent(ParsedIntent intent) {
        String action = intent.getAction().toLowerCase();
        
        switch (action) {
            case "create":
                // CREATE requires topic name
                if (intent.getTopicName() == null || intent.getTopicName().trim().isEmpty()) {
                    return "MISSING_TOPIC_NAME_FOR_CREATE";
                }
                // Validate partition range (1-10)
                if (intent.getPartitions() != null && (intent.getPartitions() < 1 || intent.getPartitions() > 10)) {
                    return "PARTITIONS_OUT_OF_RANGE:" + intent.getPartitions();
                }
                // Validate replication factor range (1-3)
                if (intent.getReplicationFactor() != null && (intent.getReplicationFactor() < 1 || intent.getReplicationFactor() > 3)) {
                    return "REPLICATION_OUT_OF_RANGE:" + intent.getReplicationFactor();
                }
                break;
                
            case "delete":
            case "describe":
                // DELETE and DESCRIBE require topic name
                if (intent.getTopicName() == null || intent.getTopicName().trim().isEmpty()) {
                    return "MISSING_TOPIC_NAME_FOR_" + action.toUpperCase();
                }
                break;
                
            case "list":
                // LIST doesn't require any parameters
                break;
                
            default:
                return "UNKNOWN_ACTION:" + action;
        }
        
        return null; // Valid
    }
    
    /**
     * Generates a friendly, conversational error message using LLM.
     */
    private String generateValidationErrorMessage(String userQuery, ParsedIntent intent, String validationError) {
        log.debug("Generating friendly error for validation: {}", validationError);
        
        // Build context for LLM
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("User asked: \"").append(userQuery).append("\"\n");
        contextBuilder.append("Extracted intent: action=").append(intent.getAction());
        if (intent.getTopicName() != null) {
            contextBuilder.append(", topicName=").append(intent.getTopicName());
        }
        if (intent.getPartitions() != null) {
            contextBuilder.append(", partitions=").append(intent.getPartitions());
        }
        if (intent.getReplicationFactor() != null) {
            contextBuilder.append(", replicationFactor=").append(intent.getReplicationFactor());
        }
        contextBuilder.append("\n");
        
        // Add specific validation error context
        if (validationError.startsWith("MISSING_TOPIC_NAME")) {
            contextBuilder.append("Problem: User didn't provide a topic name, which is required.\n");
            contextBuilder.append("Task: Ask for the topic name in a friendly way. If they mentioned partitions/replication, acknowledge those values.");
        } else if (validationError.startsWith("PARTITIONS_OUT_OF_RANGE")) {
            String value = validationError.split(":")[1];
            contextBuilder.append("Problem: User requested ").append(value).append(" partitions, but valid range is 1-10.\n");
            contextBuilder.append("Task: Explain the limit and suggest 5 partitions (good for concurrency). Ask for topic name if missing.");
        } else if (validationError.startsWith("REPLICATION_OUT_OF_RANGE")) {
            String value = validationError.split(":")[1];
            contextBuilder.append("Problem: User requested replication factor ").append(value).append(", but valid range is 1-3.\n");
            contextBuilder.append("Task: Explain the limit and suggest 3 for prod or 1 for dev. Ask for topic name if missing.");
        }
        
        String prompt = String.format("""
            You are a helpful Kafka assistant. Generate a friendly, conversational error message.
            
            Context:
            %s
            
            Generate a response (2-3 sentences max) that:
            - Explains what's wrong in a friendly way
            - Suggests a solution or asks for missing information
            - Feels like advice from a colleague, not a robotic error
            
            Response (plain text):
            """, contextBuilder.toString());
        
        try {
            return llmService.generateErrorSuggestion(prompt);
        } catch (Exception e) {
            log.error("Failed to generate friendly error message", e);
            // Fallback to simple message
            return validationError.replace("_", " ").toLowerCase();
        }
    }
}
