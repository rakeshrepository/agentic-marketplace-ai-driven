package com.agentic.marketplace.agent.service;

import com.agentic.marketplace.agent.config.McpDatabaseConfig;
import com.agentic.marketplace.agent.model.ParsedIntent;
import com.agentic.marketplace.sdk.model.AgentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class McpDatabaseService {

    private final WebClient mcpWebClient;
    private final McpDatabaseConfig mcpDatabaseConfig;

    public McpDatabaseService(@Qualifier("mcpWebClient") WebClient mcpWebClient,
                              McpDatabaseConfig mcpDatabaseConfig) {
        this.mcpWebClient = mcpWebClient;
        this.mcpDatabaseConfig = mcpDatabaseConfig;
    }

    public AgentResponse executeAction(ParsedIntent intent) {
        log.info("Executing action: {} for table: {}", intent.getAction(), intent.getTableName());

        try {
            return switch (intent.getAction().toLowerCase()) {
                case "create" -> createTable(intent);
                case "list" -> listTables();
                case "drop" -> dropTable(intent);
                case "describe" -> describeTable(intent);
                default -> AgentResponse.builder()
                        .success(false)
                        .error("Unknown action: " + intent.getAction())
                        .build();
            };
        } catch (WebClientResponseException e) {
            log.error("MCP server error", e);
            return AgentResponse.builder()
                    .success(false)
                    .error("MCP server error: " + e.getResponseBodyAsString())
                    .build();
        } catch (Exception e) {
            log.error("Error executing action", e);
            return AgentResponse.builder()
                    .success(false)
                    .error("Error: " + e.getMessage())
                    .build();
        }
    }

    private AgentResponse createTable(ParsedIntent intent) {
        if (intent.getTableName() == null || intent.getTableName().isBlank()) {
            return AgentResponse.builder()
                    .success(false)
                    .error("Table name is required for create action")
                    .build();
        }

        if (intent.getColumns() == null || intent.getColumns().isEmpty()) {
            return AgentResponse.builder()
                    .success(false)
                    .error("Columns are required for create action")
                    .build();
        }

        Map<String, Object> request = new HashMap<>();
        request.put("tableName", intent.getTableName());
        request.put("columns", intent.getColumns());

        AgentResponse response = mcpWebClient.post()
                .uri("/api/tables")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AgentResponse.class)
                .timeout(Duration.ofMillis(mcpDatabaseConfig.getTimeout()))
                .block();
                
        // Check if response indicates an error and throw exception for LLM to handle
        if (response != null && !response.isSuccess() && response.getError() != null) {
            throw new RuntimeException(response.getError());
        }
        
        return response;
    }

    private AgentResponse listTables() {
        return mcpWebClient.get()
                .uri("/api/tables")
                .retrieve()
                .bodyToMono(AgentResponse.class)
                .timeout(Duration.ofMillis(mcpDatabaseConfig.getTimeout()))
                .block();
    }

    private AgentResponse dropTable(ParsedIntent intent) {
        if (intent.getTableName() == null || intent.getTableName().isBlank()) {
            return AgentResponse.builder()
                    .success(false)
                    .error("Table name is required for drop action")
                    .build();
        }

        AgentResponse response = mcpWebClient.delete()
                .uri("/api/tables/{tableName}", intent.getTableName())
                .retrieve()
                .bodyToMono(AgentResponse.class)
                .timeout(Duration.ofMillis(mcpDatabaseConfig.getTimeout()))
                .block();
                
        // Check if response indicates an error and throw exception for LLM to handle
        if (response != null && !response.isSuccess() && response.getError() != null) {
            throw new RuntimeException(response.getError());
        }
        
        return response;
    }

    private AgentResponse describeTable(ParsedIntent intent) {
        if (intent.getTableName() == null || intent.getTableName().isBlank()) {
            return AgentResponse.builder()
                    .success(false)
                    .error("Table name is required for describe action")
                    .build();
        }

        AgentResponse response = mcpWebClient.get()
                .uri("/api/tables/{tableName}", intent.getTableName())
                .retrieve()
                .bodyToMono(AgentResponse.class)
                .timeout(Duration.ofMillis(mcpDatabaseConfig.getTimeout()))
                .block();
                
        // Check if response indicates an error and throw exception for LLM to handle
        if (response != null && !response.isSuccess() && response.getError() != null) {
            throw new RuntimeException(response.getError());
        }
        
        return response;
    }
}
