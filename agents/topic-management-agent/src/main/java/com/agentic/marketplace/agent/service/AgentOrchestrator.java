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
    private final McpKafkaService mcpKafkaService;

    public AgentResponse processQuery(AgentRequest request) {
        log.info("Processing query: {}", request.getQuery());

        // Step 1: Parse intent using Ollama LLM
        ParsedIntent intent = ollamaService.parseIntent(request.getQuery());
        log.info("Parsed intent: {}", intent);

        // Step 2: Validate intent
        if (!intent.isValid()) {
            return AgentResponse.builder()
                    .success(false)
                    .error(intent.getErrorMessage() != null 
                            ? intent.getErrorMessage() 
                            : "Could not understand the request. Please try rephrasing.")
                    .build();
        }

        if (intent.getAction() == null) {
            return AgentResponse.builder()
                    .success(false)
                    .error("Could not determine the action. Please specify if you want to create, list, delete, or describe topics.")
                    .build();
        }

        // Step 3: Execute action via MCP server
        AgentResponse response = mcpKafkaService.executeAction(intent);
        
        // Step 4: Enhance response with human-readable message
        return enhanceResponse(response, intent);
    }

    private AgentResponse enhanceResponse(AgentResponse response, ParsedIntent intent) {
        if (response.isSuccess()) {
            String message = switch (intent.getAction().toLowerCase()) {
                case "create" -> String.format("Successfully created topic '%s'", intent.getTopicName());
                case "list" -> "Here are the available topics";
                case "delete" -> String.format("Successfully deleted topic '%s'", intent.getTopicName());
                case "describe" -> String.format("Details for topic '%s'", intent.getTopicName());
                default -> response.getMessage();
            };
            return AgentResponse.builder()
                    .success(true)
                    .message(message)
                    .data(response.getData())
                    .build();
        }
        return response;
    }
}
