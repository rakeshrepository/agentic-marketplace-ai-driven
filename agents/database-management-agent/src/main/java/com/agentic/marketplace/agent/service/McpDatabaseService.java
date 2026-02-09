package com.agentic.marketplace.agent.service;

import com.agentic.marketplace.agent.config.McpDatabaseConfig;
import com.agentic.marketplace.agent.model.ParsedIntent;
import com.agentic.marketplace.sdk.model.AgentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
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
        log.info("Executing action: {}", intent.getAction());

        try {
            return switch (intent.getAction().toLowerCase()) {
                // Table operations
                case "create_table" -> createTable(intent);
                case "update_table" -> updateTable(intent);
                case "delete_table" -> dropTable(intent);
                case "list_tables" -> listTables();
                case "describe_table" -> describeTable(intent);
                
                // User operations
                case "create_user" -> createUser(intent);
                case "update_user" -> updateUser(intent);
                case "delete_user" -> deleteUser(intent);
                case "list_users" -> listUsers();
                case "describe_user" -> describeUser(intent);
                case "grant_privileges" -> grantPrivileges(intent);
                case "revoke_privileges" -> revokePrivileges(intent);
                
                // Data operations
                case "insert_data" -> insertData(intent);
                case "update_data" -> updateData(intent);
                case "delete_data" -> deleteData(intent);
                case "query_data" -> queryData(intent);
                
                // Legacy support
                case "create" -> createTable(intent);
                case "list" -> listTables();
                case "drop", "delete" -> dropTable(intent);
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
    
    // ============== TABLE UPDATE OPERATIONS ==============
    
    private AgentResponse updateTable(ParsedIntent intent) {
        Map<String, Object> request = new HashMap<>();
        request.put("action", intent.getAlterAction());
        request.put("column", intent.getColumnToAlter());

        AgentResponse response = mcpWebClient.put()
                .uri("/api/tables/{tableName}", intent.getTableName())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AgentResponse.class)
                .timeout(Duration.ofMillis(mcpDatabaseConfig.getTimeout()))
                .block();
                
        if (response != null && !response.isSuccess() && response.getError() != null) {
            throw new RuntimeException(response.getError());
        }
        
        return response;
    }
    
    // ============== USER MANAGEMENT OPERATIONS ==============
    
    private AgentResponse createUser(ParsedIntent intent) {
        Map<String, Object> request = new HashMap<>();
        request.put("username", intent.getUsername());
        request.put("password", intent.getPassword());
        request.put("privileges", intent.getPrivileges());

        AgentResponse response = mcpWebClient.post()
                .uri("/api/users")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AgentResponse.class)
                .timeout(Duration.ofMillis(mcpDatabaseConfig.getTimeout()))
                .block();
                
        if (response != null && !response.isSuccess() && response.getError() != null) {
            throw new RuntimeException(response.getError());
        }
        
        return response;
    }
    
    private AgentResponse updateUser(ParsedIntent intent) {
        Map<String, Object> request = new HashMap<>();
        if (intent.getPassword() != null) {
            request.put("password", intent.getPassword());
        }
        if (intent.getPrivileges() != null) {
            request.put("privileges", intent.getPrivileges());
        }

        AgentResponse response = mcpWebClient.put()
                .uri("/api/users/{username}", intent.getUsername())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AgentResponse.class)
                .timeout(Duration.ofMillis(mcpDatabaseConfig.getTimeout()))
                .block();
                
        if (response != null && !response.isSuccess() && response.getError() != null) {
            throw new RuntimeException(response.getError());
        }
        
        return response;
    }
    
    private AgentResponse deleteUser(ParsedIntent intent) {
        AgentResponse response = mcpWebClient.delete()
                .uri("/api/users/{username}", intent.getUsername())
                .retrieve()
                .bodyToMono(AgentResponse.class)
                .timeout(Duration.ofMillis(mcpDatabaseConfig.getTimeout()))
                .block();
                
        if (response != null && !response.isSuccess() && response.getError() != null) {
            throw new RuntimeException(response.getError());
        }
        
        return response;
    }
    
    private AgentResponse listUsers() {
        return mcpWebClient.get()
                .uri("/api/users")
                .retrieve()
                .bodyToMono(AgentResponse.class)
                .timeout(Duration.ofMillis(mcpDatabaseConfig.getTimeout()))
                .block();
    }
    
    private AgentResponse describeUser(ParsedIntent intent) {
        AgentResponse response = mcpWebClient.get()
                .uri("/api/users/{username}", intent.getUsername())
                .retrieve()
                .bodyToMono(AgentResponse.class)
                .timeout(Duration.ofMillis(mcpDatabaseConfig.getTimeout()))
                .block();
                
        if (response != null && !response.isSuccess() && response.getError() != null) {
            throw new RuntimeException(response.getError());
        }
        
        return response;
    }
    
    private AgentResponse grantPrivileges(ParsedIntent intent) {
        Map<String, Object> request = new HashMap<>();
        request.put("privileges", intent.getPrivileges());
        if (intent.getTableName() != null) {
            request.put("tableName", intent.getTableName());
        }

        AgentResponse response = mcpWebClient.post()
                .uri("/api/users/{username}/grant", intent.getUsername())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AgentResponse.class)
                .timeout(Duration.ofMillis(mcpDatabaseConfig.getTimeout()))
                .block();
                
        if (response != null && !response.isSuccess() && response.getError() != null) {
            throw new RuntimeException(response.getError());
        }
        
        return response;
    }
    
    private AgentResponse revokePrivileges(ParsedIntent intent) {
        Map<String, Object> request = new HashMap<>();
        request.put("privileges", intent.getPrivileges());
        if (intent.getTableName() != null) {
            request.put("tableName", intent.getTableName());
        }

        AgentResponse response = mcpWebClient.post()
                .uri("/api/users/{username}/revoke", intent.getUsername())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AgentResponse.class)
                .timeout(Duration.ofMillis(mcpDatabaseConfig.getTimeout()))
                .block();
                
        if (response != null && !response.isSuccess() && response.getError() != null) {
            throw new RuntimeException(response.getError());
        }
        
        return response;
    }
    
    // ============== DATA OPERATIONS ==============
    
    private AgentResponse insertData(ParsedIntent intent) {
        AgentResponse response = mcpWebClient.post()
                .uri("/api/tables/{tableName}/data", intent.getTableName())
                .bodyValue(intent.getData())
                .retrieve()
                .bodyToMono(AgentResponse.class)
                .timeout(Duration.ofMillis(mcpDatabaseConfig.getTimeout()))
                .block();
                
        if (response != null && !response.isSuccess() && response.getError() != null) {
            throw new RuntimeException(response.getError());
        }
        
        return response;
    }
    
    private AgentResponse updateData(ParsedIntent intent) {
        Map<String, Object> request = new HashMap<>();
        request.put("data", intent.getData());
        request.put("whereConditions", intent.getWhereConditions());

        AgentResponse response = mcpWebClient.put()
                .uri("/api/tables/{tableName}/data", intent.getTableName())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AgentResponse.class)
                .timeout(Duration.ofMillis(mcpDatabaseConfig.getTimeout()))
                .block();
                
        if (response != null && !response.isSuccess() && response.getError() != null) {
            throw new RuntimeException(response.getError());
        }
        
        return response;
    }
    
    private AgentResponse deleteData(ParsedIntent intent) {
        AgentResponse response = mcpWebClient.method(HttpMethod.DELETE)
                .uri("/api/tables/{tableName}/data", intent.getTableName())
                .bodyValue(intent.getWhereConditions())
                .retrieve()
                .bodyToMono(AgentResponse.class)
                .timeout(Duration.ofMillis(mcpDatabaseConfig.getTimeout()))
                .block();
                
        if (response != null && !response.isSuccess() && response.getError() != null) {
            throw new RuntimeException(response.getError());
        }
        
        return response;
    }
    
    private AgentResponse queryData(ParsedIntent intent) {
        Map<String, Object> request = new HashMap<>();
        if (intent.getSelectColumns() != null) {
            request.put("columns", intent.getSelectColumns());
        }
        if (intent.getWhereConditions() != null) {
            request.put("whereConditions", intent.getWhereConditions());
        }
        if (intent.getOrderBy() != null) {
            request.put("orderBy", intent.getOrderBy());
        }
        if (intent.getLimit() != null) {
            request.put("limit", intent.getLimit());
        }
        if (intent.getOffset() != null) {
            request.put("offset", intent.getOffset());
        }

        AgentResponse response = mcpWebClient.method(org.springframework.http.HttpMethod.GET)
                .uri("/api/tables/{tableName}/data", intent.getTableName())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AgentResponse.class)
                .timeout(Duration.ofMillis(mcpDatabaseConfig.getTimeout()))
                .block();
                
        if (response != null && !response.isSuccess() && response.getError() != null) {
            throw new RuntimeException(response.getError());
        }
        
        return response;
    }
}
