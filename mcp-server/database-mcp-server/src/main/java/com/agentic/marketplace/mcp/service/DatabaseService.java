package com.agentic.marketplace.mcp.service;

import com.agentic.marketplace.mcp.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DatabaseService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void createTable(TableRequest request) {
        log.info("Creating table: {}", request.getTableName());
        
        StringBuilder sql = new StringBuilder("CREATE TABLE ");
        sql.append(quoteIdentifier(request.getTableName())).append(" (");
        
        List<String> columnDefs = new ArrayList<>();
        List<String> primaryKeys = new ArrayList<>();
        
        for (ColumnInfo column : request.getColumns()) {
            StringBuilder colDef = new StringBuilder();
            colDef.append(quoteIdentifier(column.getName())).append(" ").append(column.getType());
            
            if (!column.isNullable()) {
                colDef.append(" NOT NULL");
            }
            
            if (column.isPrimaryKey()) {
                primaryKeys.add(quoteIdentifier(column.getName()));
            }
            
            columnDefs.add(colDef.toString());
        }
        
        sql.append(String.join(", ", columnDefs));
        
        if (!primaryKeys.isEmpty()) {
            sql.append(", PRIMARY KEY (").append(String.join(", ", primaryKeys)).append(")");
        }
        
        sql.append(")");
        
        log.debug("Executing SQL: {}", sql);
        jdbcTemplate.execute(sql.toString());
        log.info("Table created successfully: {}", request.getTableName());
    }

    public List<String> listTables() {
        log.info("Listing all tables");
        String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'";
        List<String> tables = jdbcTemplate.queryForList(sql, String.class);
        log.info("Found {} tables", tables.size());
        return tables;
    }

    public TableDetails describeTable(String tableName) {
        log.info("Describing table: {}", tableName);
        
        // Get column details using standard JDBC DatabaseMetaData
        List<ColumnInfo> columns = new ArrayList<>();
        List<String> primaryKeys = new ArrayList<>();
        
        try {
            java.sql.Connection conn = jdbcTemplate.getDataSource().getConnection();
            java.sql.DatabaseMetaData metaData = conn.getMetaData();
            
            // Get primary keys (try both lowercase and uppercase)
            java.sql.ResultSet pkResultSet = metaData.getPrimaryKeys(null, null, tableName);
            while (pkResultSet.next()) {
                primaryKeys.add(pkResultSet.getString("COLUMN_NAME"));
            }
            pkResultSet.close();
            
            // If no results with original case, try uppercase (PostgreSQL stores unquoted identifiers as uppercase)
            if (primaryKeys.isEmpty()) {
                pkResultSet = metaData.getPrimaryKeys(null, null, tableName.toUpperCase());
                while (pkResultSet.next()) {
                    primaryKeys.add(pkResultSet.getString("COLUMN_NAME"));
                }
                pkResultSet.close();
            }
            
            // Get columns (try both cases)
            java.sql.ResultSet columnsResultSet = metaData.getColumns(null, null, tableName, null);
            List<ColumnInfo> tempColumns = new ArrayList<>();
            while (columnsResultSet.next()) {
                ColumnInfo column = new ColumnInfo();
                String columnName = columnsResultSet.getString("COLUMN_NAME");
                column.setName(columnName);
                column.setType(columnsResultSet.getString("TYPE_NAME"));
                column.setNullable(columnsResultSet.getInt("NULLABLE") == java.sql.DatabaseMetaData.columnNullable);
                column.setPrimaryKey(primaryKeys.contains(columnName));
                tempColumns.add(column);
            }
            columnsResultSet.close();
            
            // If no results with original case, try uppercase
            if (tempColumns.isEmpty()) {
                columnsResultSet = metaData.getColumns(null, null, tableName.toUpperCase(), null);
                while (columnsResultSet.next()) {
                    ColumnInfo column = new ColumnInfo();
                    String columnName = columnsResultSet.getString("COLUMN_NAME");
                    column.setName(columnName);
                    column.setType(columnsResultSet.getString("TYPE_NAME"));
                    column.setNullable(columnsResultSet.getInt("NULLABLE") == java.sql.DatabaseMetaData.columnNullable);
                    column.setPrimaryKey(primaryKeys.contains(columnName));
                    tempColumns.add(column);
                }
                columnsResultSet.close();
            }
            
            columns = tempColumns;
            conn.close();
        } catch (Exception e) {
            log.error("Error fetching table metadata", e);
            throw new RuntimeException("Failed to fetch table metadata: " + e.getMessage());
        }
        
        // Get row count
        String countSql = "SELECT COUNT(*) FROM " + quoteIdentifier(tableName);
        Long rowCount = jdbcTemplate.queryForObject(countSql, Long.class);
        
        TableDetails details = new TableDetails();
        details.setTableName(tableName);
        details.setColumns(columns);
        details.setRowCount(rowCount != null ? rowCount : 0);
        
        log.info("Table {} has {} columns and {} rows", tableName, columns.size(), rowCount);
        return details;
    }

    public void dropTable(String tableName) {
        log.info("Dropping table: {}", tableName);
        String sql = "DROP TABLE IF EXISTS " + quoteIdentifier(tableName);
        jdbcTemplate.execute(sql);
        log.info("Table dropped successfully: {}", tableName);
    }

    public boolean tableExists(String tableName) {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                     "WHERE TABLE_NAME = ? AND TABLE_SCHEMA = 'PUBLIC'";
        Integer count = jdbcTemplate.queryForObject(sql, new Object[]{tableName.toUpperCase()}, Integer.class);
        return count != null && count > 0;
    }
    
    /**
     * Quote SQL identifiers to handle reserved keywords and special characters
     */
    private String quoteIdentifier(String identifier) {
        return "\"" + identifier + "\"";
    }
    
    // ============== USER MANAGEMENT ==============
    
    public void createUser(UserRequest request) {
        log.info("Creating user: {}", request.getUsername());
        
        // Create user with password
        String createUserSql = "CREATE USER " + quoteIdentifier(request.getUsername()) + 
                              " PASSWORD '" + request.getPassword() + "'";
        jdbcTemplate.execute(createUserSql);
        
        // Grant privileges if provided
        if (request.getPrivileges() != null && !request.getPrivileges().isEmpty()) {
            for (String privilege : request.getPrivileges()) {
                grantPrivilege(request.getUsername(), privilege, null);
            }
        }
        
        log.info("User created successfully: {}", request.getUsername());
    }
    
    public List<String> listUsers() {
        log.info("Listing all users");
        String sql = "SELECT NAME FROM INFORMATION_SCHEMA.USERS WHERE ADMIN = FALSE";
        List<String> users = jdbcTemplate.queryForList(sql, String.class);
        log.info("Found {} users", users.size());
        return users;
    }
    
    public UserDetails getUserDetails(String username) {
        log.info("Getting details for user: {}", username);
        
        String checkUserSql = "SELECT NAME, ADMIN FROM INFORMATION_SCHEMA.USERS WHERE NAME = ?";
        List<Map<String, Object>> result = jdbcTemplate.queryForList(checkUserSql, username.toUpperCase());
        
        if (result.isEmpty()) {
            throw new RuntimeException("User not found: " + username);
        }
        
        Map<String, Object> userInfo = result.get(0);
        boolean isAdmin = (Boolean) userInfo.get("ADMIN");
        
        // Get user privileges
        String privilegeSql = "SELECT TABLE_SCHEMA, TABLE_NAME, PRIVILEGE_TYPE " +
                             "FROM INFORMATION_SCHEMA.TABLE_PRIVILEGES WHERE GRANTEE = ?";
        List<Map<String, Object>> privileges = jdbcTemplate.queryForList(privilegeSql, username.toUpperCase());
        
        List<String> privilegeList = privileges.stream()
                .map(p -> p.get("PRIVILEGE_TYPE") + " ON " + p.get("TABLE_SCHEMA") + "." + p.get("TABLE_NAME"))
                .collect(Collectors.toList());
        
        UserDetails details = new UserDetails();
        details.setUsername(username);
        details.setAdmin(isAdmin);
        details.setPrivileges(privilegeList);
        
        log.info("User {} has {} privileges", username, privilegeList.size());
        return details;
    }
    
    public void updateUser(String username, UserRequest request) {
        log.info("Updating user: {}", username);
        
        // Update password if provided
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            String sql = "ALTER USER " + quoteIdentifier(username) + 
                        " SET PASSWORD '" + request.getPassword() + "'";
            jdbcTemplate.execute(sql);
            log.info("Password updated for user: {}", username);
        }
        
        // Update privileges if provided
        if (request.getPrivileges() != null) {
            // Revoke all existing privileges first
            String revokeAllSql = "REVOKE ALL ON SCHEMA PUBLIC FROM " + quoteIdentifier(username);
            try {
                jdbcTemplate.execute(revokeAllSql);
            } catch (Exception e) {
                log.debug("No existing privileges to revoke for user: {}", username);
            }
            
            // Grant new privileges
            for (String privilege : request.getPrivileges()) {
                grantPrivilege(username, privilege, null);
            }
        }
        
        log.info("User updated successfully: {}", username);
    }
    
    public void deleteUser(String username) {
        log.info("Deleting user: {}", username);
        String sql = "DROP USER IF EXISTS " + quoteIdentifier(username);
        jdbcTemplate.execute(sql);
        log.info("User deleted successfully: {}", username);
    }
    
    public void grantPrivilege(String username, String privilege, String tableName) {
        log.info("Granting {} privilege to user: {}", privilege, username);
        
        String sql;
        if (tableName != null && !tableName.isEmpty()) {
            sql = "GRANT " + privilege + " ON " + quoteIdentifier(tableName) + 
                  " TO " + quoteIdentifier(username);
        } else {
            sql = "GRANT " + privilege + " TO " + quoteIdentifier(username);
        }
        
        jdbcTemplate.execute(sql);
        log.info("Privilege granted successfully");
    }
    
    public void revokePrivilege(String username, String privilege, String tableName) {
        log.info("Revoking {} privilege from user: {}", privilege, username);
        
        String sql;
        if (tableName != null && !tableName.isEmpty()) {
            sql = "REVOKE " + privilege + " ON " + quoteIdentifier(tableName) + 
                  " FROM " + quoteIdentifier(username);
        } else {
            sql = "REVOKE " + privilege + " FROM " + quoteIdentifier(username);
        }
        
        jdbcTemplate.execute(sql);
        log.info("Privilege revoked successfully");
    }
    
    public boolean userExists(String username) {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.USERS WHERE NAME = ?";
        Integer count = jdbcTemplate.queryForObject(sql, new Object[]{username.toUpperCase()}, Integer.class);
        return count != null && count > 0;
    }
    
    // ============== DATA OPERATIONS ==============
    
    public Map<String, Object> insertData(String tableName, Map<String, Object> data) {
        log.info("Inserting data into table: {}", tableName);
        
        List<String> columns = new ArrayList<>(data.keySet());
        List<Object> values = new ArrayList<>(data.values());
        
        String columnList = columns.stream()
                .map(this::quoteIdentifier)
                .collect(Collectors.joining(", "));
        
        String placeholders = columns.stream()
                .map(c -> "?")
                .collect(Collectors.joining(", "));
        
        String sql = "INSERT INTO " + quoteIdentifier(tableName) + 
                    " (" + columnList + ") VALUES (" + placeholders + ")";
        
        jdbcTemplate.update(sql, values.toArray());
        log.info("Data inserted successfully into: {}", tableName);
        
        return data;
    }
    
    public List<Map<String, Object>> queryData(String tableName, QueryRequest request) {
        log.info("Querying data from table: {}", tableName);
        
        StringBuilder sql = new StringBuilder("SELECT ");
        
        // Select columns
        if (request.getColumns() != null && !request.getColumns().isEmpty()) {
            String columnList = request.getColumns().stream()
                    .map(this::quoteIdentifier)
                    .collect(Collectors.joining(", "));
            sql.append(columnList);
        } else {
            sql.append("*");
        }
        
        sql.append(" FROM ").append(quoteIdentifier(tableName));
        
        List<Object> params = new ArrayList<>();
        
        // Where conditions
        if (request.getWhereConditions() != null && !request.getWhereConditions().isEmpty()) {
            sql.append(" WHERE ");
            List<String> conditions = new ArrayList<>();
            for (Map.Entry<String, Object> entry : request.getWhereConditions().entrySet()) {
                conditions.add(quoteIdentifier(entry.getKey()) + " = ?");
                params.add(entry.getValue());
            }
            sql.append(String.join(" AND ", conditions));
        }
        
        // Order by
        if (request.getOrderBy() != null && !request.getOrderBy().isEmpty()) {
            sql.append(" ORDER BY ").append(quoteIdentifier(request.getOrderBy()));
        }
        
        // Limit and offset
        if (request.getLimit() != null) {
            sql.append(" LIMIT ").append(request.getLimit());
        }
        if (request.getOffset() != null) {
            sql.append(" OFFSET ").append(request.getOffset());
        }
        
        log.debug("Executing query: {}", sql);
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        log.info("Query returned {} rows", results.size());
        
        return results;
    }
    
    public int updateData(String tableName, Map<String, Object> data, Map<String, Object> whereConditions) {
        log.info("Updating data in table: {}", tableName);
        
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("No data provided for update");
        }
        
        StringBuilder sql = new StringBuilder("UPDATE ").append(quoteIdentifier(tableName)).append(" SET ");
        
        List<Object> params = new ArrayList<>();
        
        // Set clause
        List<String> setClauses = new ArrayList<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            setClauses.add(quoteIdentifier(entry.getKey()) + " = ?");
            params.add(entry.getValue());
        }
        sql.append(String.join(", ", setClauses));
        
        // Where clause
        if (whereConditions != null && !whereConditions.isEmpty()) {
            sql.append(" WHERE ");
            List<String> conditions = new ArrayList<>();
            for (Map.Entry<String, Object> entry : whereConditions.entrySet()) {
                conditions.add(quoteIdentifier(entry.getKey()) + " = ?");
                params.add(entry.getValue());
            }
            sql.append(String.join(" AND ", conditions));
        }
        
        log.debug("Executing update: {}", sql);
        int rowsAffected = jdbcTemplate.update(sql.toString(), params.toArray());
        log.info("Updated {} rows in table: {}", rowsAffected, tableName);
        
        return rowsAffected;
    }
    
    public int deleteData(String tableName, Map<String, Object> whereConditions) {
        log.info("Deleting data from table: {}", tableName);
        
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(quoteIdentifier(tableName));
        
        List<Object> params = new ArrayList<>();
        
        // Where clause
        if (whereConditions != null && !whereConditions.isEmpty()) {
            sql.append(" WHERE ");
            List<String> conditions = new ArrayList<>();
            for (Map.Entry<String, Object> entry : whereConditions.entrySet()) {
                conditions.add(quoteIdentifier(entry.getKey()) + " = ?");
                params.add(entry.getValue());
            }
            sql.append(String.join(" AND ", conditions));
        } else {
            throw new IllegalArgumentException("WHERE conditions required for delete operation");
        }
        
        log.debug("Executing delete: {}", sql);
        int rowsAffected = jdbcTemplate.update(sql.toString(), params.toArray());
        log.info("Deleted {} rows from table: {}", rowsAffected, tableName);
        
        return rowsAffected;
    }
    
    // ============== TABLE ALTERATION ==============
    
    public void alterTable(String tableName, AlterTableRequest request) {
        log.info("Altering table: {} with action: {}", tableName, request.getAction());
        
        String sql = switch (request.getAction().toUpperCase()) {
            case "ADD_COLUMN" -> {
                StringBuilder addSql = new StringBuilder("ALTER TABLE ");
                addSql.append(quoteIdentifier(tableName))
                      .append(" ADD COLUMN ")
                      .append(quoteIdentifier(request.getColumn().getName()))
                      .append(" ")
                      .append(request.getColumn().getType());
                
                if (!request.getColumn().isNullable()) {
                    addSql.append(" NOT NULL");
                }
                
                yield addSql.toString();
            }
            case "DROP_COLUMN" -> "ALTER TABLE " + quoteIdentifier(tableName) + 
                                 " DROP COLUMN " + quoteIdentifier(request.getColumn().getName());
            case "MODIFY_COLUMN" -> "ALTER TABLE " + quoteIdentifier(tableName) + 
                                   " ALTER COLUMN " + quoteIdentifier(request.getColumn().getName()) + 
                                   " " + request.getColumn().getType();
            default -> throw new IllegalArgumentException("Unknown alter action: " + request.getAction());
        };
        
        log.debug("Executing alter table: {}", sql);
        jdbcTemplate.execute(sql);
        log.info("Table altered successfully: {}", tableName);
    }
}
