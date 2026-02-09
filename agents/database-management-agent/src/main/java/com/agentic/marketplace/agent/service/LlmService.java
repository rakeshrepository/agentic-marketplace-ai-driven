package com.agentic.marketplace.agent.service;

import com.agentic.marketplace.agent.model.ParsedIntent;
import com.agentic.marketplace.sdk.model.ConversationMessage;

import java.util.List;
import java.util.Map;

/**
 * Interface for LLM providers (Ollama, OpenAI, etc.)
 */
public interface LlmService {
    
    /**
     * Parse user's natural language query into structured intent with conversation context
     */
    ParsedIntent parseIntent(String userQuery, List<ConversationMessage> conversationHistory, Map<String, Object> context);
    
    /**
     * Generate a natural language success response
     */
    String generateSuccessResponse(String userQuery, String action, String entityName, Object mcpResult);
    
    /**
     * Generate an intelligent error suggestion
     */
    String generateErrorSuggestion(String errorContext);
    
    /**
     * Generate a friendly validation error message
     */
    String generateValidationError(String userQuery, ParsedIntent intent, String validationError);
}
