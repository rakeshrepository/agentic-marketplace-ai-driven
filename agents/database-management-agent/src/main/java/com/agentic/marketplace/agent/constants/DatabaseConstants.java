package com.agentic.marketplace.agent.constants;

public class DatabaseConstants {
    
    public static class Actions {
        // Table operations
        public static final String CREATE_TABLE = "create_table";
        public static final String UPDATE_TABLE = "update_table";
        public static final String DELETE_TABLE = "delete_table";
        public static final String LIST_TABLES = "list_tables";
        public static final String DESCRIBE_TABLE = "describe_table";
        
        // User operations
        public static final String CREATE_USER = "create_user";
        public static final String UPDATE_USER = "update_user";
        public static final String DELETE_USER = "delete_user";
        public static final String LIST_USERS = "list_users";
        public static final String DESCRIBE_USER = "describe_user";
        public static final String GRANT_PRIVILEGES = "grant_privileges";
        public static final String REVOKE_PRIVILEGES = "revoke_privileges";
        
        // Data operations
        public static final String INSERT_DATA = "insert_data";
        public static final String UPDATE_DATA = "update_data";
        public static final String DELETE_DATA = "delete_data";
        public static final String QUERY_DATA = "query_data";
    }
    
    public static class AlterActions {
        public static final String ADD_COLUMN = "ADD_COLUMN";
        public static final String DROP_COLUMN = "DROP_COLUMN";
        public static final String MODIFY_COLUMN = "MODIFY_COLUMN";
    }
    
    public static class Privileges {
        public static final String SELECT = "SELECT";
        public static final String INSERT = "INSERT";
        public static final String UPDATE = "UPDATE";
        public static final String DELETE = "DELETE";
        public static final String ALL = "ALL";
    }
    
    public static class LlmTemperature {
        public static final double EXTRACTION = 0.1;  // Low temperature for structured extraction
        public static final double GENERATION = 0.7;  // Higher temperature for natural responses
    }
    
    public static class Defaults {
        public static final int QUERY_LIMIT = 100;
        public static final int QUERY_OFFSET = 0;
    }
    
    private DatabaseConstants() {
        // Private constructor to prevent instantiation
    }
}
