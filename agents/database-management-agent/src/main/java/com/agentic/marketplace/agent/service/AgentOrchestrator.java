package com.agentic.marketplace.agent.service;

import com.agentic.marketplace.agent.model.ParsedIntent;
import com.agentic.marketplace.sdk.model.AgentRequest;
import com.agentic.marketplace.sdk.model.AgentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final OllamaService ollamaService;
    private final McpDatabaseService mcpDatabaseService;

    public AgentResponse processQuery(AgentRequest request) {
        log.info("Processing query: {}", request.getQuery());

        // Step 1: Parse intent using LLM with conversation context (extraction only, no validation)
        ParsedIntent intent = ollamaService.parseIntent(request.getQuery(), null, null);
        log.info("Parsed intent: {}", intent);

        // Step 2: Validate action
        if (intent.getAction() == null || intent.getAction().isBlank()) {
            return AgentResponse.builder()
                    .success(false)
                    .error("Could not determine the action. Please specify what you want to do (create/list/delete tables, manage users, or work with data).")
                    .build();
        }

        // Step 3: Execute action via MCP server with intelligent error handling
        try {
            AgentResponse response = mcpDatabaseService.executeAction(intent);
            
            // Step 4: Generate natural language response using LLM
            return generateNaturalResponse(response, intent, request.getQuery());
        } catch (Exception e) {
            log.error("Error executing database action: {}", e.getMessage(), e);
            // Use LLM to provide intelligent error explanation and suggestions
            return handleErrorWithLLM(e, intent, request.getQuery());
        }
    }

    private AgentResponse generateNaturalResponse(AgentResponse mcpResponse, ParsedIntent intent, String originalQuery) {
        if (mcpResponse.isSuccess()) {
            // Let LLM generate conversational response based on what happened
            String naturalMessage = ollamaService.generateSuccessResponse(
                    originalQuery,
                    intent.getAction(),
                    intent.getTableName(),
                    mcpResponse.getData()
            );
            
            return AgentResponse.builder()
                    .success(true)
                    .message(naturalMessage)  // LLM-generated conversational message
                    .data(mcpResponse.getData())  // Keep structured data for UI
                    .build();
        } else if (mcpResponse.getError() != null) {
            // Even failed responses from MCP might have SQL errors - use LLM to explain
            return handleErrorWithLLM(new RuntimeException(mcpResponse.getError()), intent, originalQuery);
        }
        return mcpResponse;
    }
    
    private AgentResponse handleErrorWithLLM(Exception error, ParsedIntent intent, String originalQuery) {
        String errorMessage = error.getMessage();
        log.info("Using LLM to generate helpful error explanation for: {}", errorMessage);
        
        // Create a prompt for the LLM to explain the error and suggest alternatives
        String errorAnalysisPrompt = String.format(
            "The user tried to: '%s'\n\n" +
            "We parsed their intent as: Action=%s, Table=%s, Columns=%s\n\n" +
            "But we got this database error: %s\n\n" +
            "Please explain this error in simple terms and suggest 2-3 alternative approaches or table/column names they could use instead. " +
            "Be helpful, concise, and practical. If it's a reserved keyword issue, suggest similar non-reserved alternatives.",
            originalQuery,
            intent.getAction(),
            intent.getTableName(),
            intent.getColumns(),
            errorMessage
        );
        
        try {
            String helpfulExplanation = ollamaService.generateErrorSuggestion(errorAnalysisPrompt);
            return AgentResponse.builder()
                    .success(false)
                    .error(helpfulExplanation)
                    .build();
        } catch (Exception llmError) {
            log.error("Failed to generate helpful error message with LLM: {}", llmError.getMessage());
            // Fallback to basic error message
            return AgentResponse.builder()
                    .success(false)
                    .error(String.format("Database operation failed: %s", errorMessage))
                    .build();
        }
    }
}
