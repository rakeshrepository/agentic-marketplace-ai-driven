package com.agentic.marketplace.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedIntent {
    // Action type
    private String action;  // Table: create_table, update_table, delete_table, list_tables, describe_table, query_data
                           // User: create_user, update_user, delete_user, list_users, describe_user, grant_privileges, revoke_privileges
                           // Data: insert_data, update_data, delete_data, query_data
    
    // Table-related fields
    private String tableName;
    private List<Map<String, String>> columns; // [{name: "id", type: "INTEGER", nullable: "false", primaryKey: "true"}]
    private String alterAction; // ADD_COLUMN, DROP_COLUMN, MODIFY_COLUMN
    private Map<String, String> columnToAlter;
    
    // User-related fields
    private String username;
    private String password;
    private List<String> privileges; // SELECT, INSERT, UPDATE, DELETE, ALL
    
    // Data operation fields
    private Map<String, Object> data; // Data to insert or update
    private Map<String, Object> whereConditions; // Conditions for query, update, or delete
    private List<String> selectColumns; // Columns to select in query
    private String orderBy; // Column to order by
    private Integer limit;
    private Integer offset;
    
    // Search/query fields
    private String searchQuery;
    private List<String> searchColumns;
    
    // Email notification
    private String email;
    
    // Note: Validation removed from LLM responsibility
    // LLM now focuses on intelligent extraction only
    // Validation logic handled by IntentValidator in Java
}

