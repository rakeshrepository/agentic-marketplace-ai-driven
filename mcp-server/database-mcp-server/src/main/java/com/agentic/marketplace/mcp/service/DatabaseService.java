package com.agentic.marketplace.mcp.service;

import com.agentic.marketplace.mcp.model.ColumnInfo;
import com.agentic.marketplace.mcp.model.TableDetails;
import com.agentic.marketplace.mcp.model.TableRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
}
