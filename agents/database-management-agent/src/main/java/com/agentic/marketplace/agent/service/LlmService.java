package com.agentic.marketplace.agent.service;

import com.agentic.marketplace.agent.model.ParsedIntent;

/**
 * Interface for LLM providers (Ollama, OpenAI, etc.)
 */
public interface LlmService {
    
    /**
     * Parse user's natural language query into structured intent
     */
    ParsedIntent parseIntent(String userQuery);
    
    /**
     * Generate a natural language success response
     */
    String generateSuccessResponse(String userQuery, String action, String entityName, Object mcpResult);
    
    /**
     * Generate an intelligent error suggestion
     */
    String generateErrorSuggestion(String errorContext);
}
