package com.agentic.marketplace.agent.validator;

import com.agentic.marketplace.agent.constants.DatabaseConstants;
import com.agentic.marketplace.agent.model.ParsedIntent;
import org.springframework.stereotype.Component;

@Component
public class IntentValidator {
    
    public String validate(ParsedIntent intent) {
        if (intent.getAction() == null || intent.getAction().isBlank()) {
            return "Action is required";
        }
        
        return switch (intent.getAction().toLowerCase()) {
            case "create_table", "update_table", "delete_table", "describe_table", "insert_data", "update_data", "delete_data", "query_data" -> 
                validateTableOperation(intent);
            case "create_user", "update_user", "delete_user", "describe_user" -> 
                validateUserOperation(intent);
            case "grant_privileges", "revoke_privileges" -> 
                validatePrivilegeOperation(intent);
            case "list_tables", "list_users" -> 
                null; // No additional validation needed
            default -> 
                "Unknown action: " + intent.getAction();
        };
    }
    
    private String validateTableOperation(ParsedIntent intent) {
        String action = intent.getAction().toLowerCase();
        
        return switch (action) {
            case "create_table" -> {
                if (isBlank(intent.getTableName())) {
                    yield "Table name is required for creating a table";
                }
                if (intent.getColumns() == null || intent.getColumns().isEmpty()) {
                    yield "At least one column is required for creating a table";
                }
                yield null;
            }
            case "update_table" -> {
                if (isBlank(intent.getTableName())) {
                    yield "Table name is required for updating a table";
                }
                if (isBlank(intent.getAlterAction())) {
                    yield "Alter action is required (ADD_COLUMN, DROP_COLUMN, or MODIFY_COLUMN)";
                }
                if (intent.getColumnToAlter() == null || intent.getColumnToAlter().isEmpty()) {
                    yield "Column information is required for table alteration";
                }
                yield null;
            }
            case "delete_table", "describe_table" -> {
                if (isBlank(intent.getTableName())) {
                    yield "Table name is required";
                }
                yield null;
            }
            case "insert_data" -> {
                if (isBlank(intent.getTableName())) {
                    yield "Table name is required for inserting data";
                }
                if (intent.getData() == null || intent.getData().isEmpty()) {
                    yield "Data is required for insert operation";
                }
                yield null;
            }
            case "update_data" -> {
                if (isBlank(intent.getTableName())) {
                    yield "Table name is required for updating data";
                }
                if (intent.getData() == null || intent.getData().isEmpty()) {
                    yield "Data is required for update operation";
                }
                if (intent.getWhereConditions() == null || intent.getWhereConditions().isEmpty()) {
                    yield "WHERE conditions are required for update operation (for safety)";
                }
                yield null;
            }
            case "delete_data" -> {
                if (isBlank(intent.getTableName())) {
                    yield "Table name is required for deleting data";
                }
                if (intent.getWhereConditions() == null || intent.getWhereConditions().isEmpty()) {
                    yield "WHERE conditions are required for delete operation (for safety)";
                }
                yield null;
            }
            case "query_data" -> {
                if (isBlank(intent.getTableName())) {
                    yield "Table name is required for querying data";
                }
                yield null;
            }
            default -> "Unknown table operation: " + action;
        };
    }
    
    private String validateUserOperation(ParsedIntent intent) {
        String action = intent.getAction().toLowerCase();
        
        return switch (action) {
            case "create_user" -> {
                if (isBlank(intent.getUsername())) {
                    yield "Username is required for creating a user";
                }
                if (isBlank(intent.getPassword())) {
                    yield "Password is required for creating a user";
                }
                yield null;
            }
            case "update_user" -> {
                if (isBlank(intent.getUsername())) {
                    yield "Username is required for updating a user";
                }
                // Password and privileges are optional for update
                if (isBlank(intent.getPassword()) && 
                    (intent.getPrivileges() == null || intent.getPrivileges().isEmpty())) {
                    yield "Either password or privileges must be provided for user update";
                }
                yield null;
            }
            case "delete_user", "describe_user" -> {
                if (isBlank(intent.getUsername())) {
                    yield "Username is required";
                }
                yield null;
            }
            default -> "Unknown user operation: " + action;
        };
    }
    
    private String validatePrivilegeOperation(ParsedIntent intent) {
        if (isBlank(intent.getUsername())) {
            return "Username is required for privilege operations";
        }
        if (intent.getPrivileges() == null || intent.getPrivileges().isEmpty()) {
            return "At least one privilege is required (SELECT, INSERT, UPDATE, DELETE, or ALL)";
        }
        return null;
    }
    
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
