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
     * Parse user's natural language query into structured intent with conversation history
     * @param userQuery The current user query
     * @param conversationHistory Recent conversation history for context
     * @param context Stored context from previous interactions (suggestions, pending actions, etc.)
     * @return Parsed intent with action, parameters, validation status
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
}
