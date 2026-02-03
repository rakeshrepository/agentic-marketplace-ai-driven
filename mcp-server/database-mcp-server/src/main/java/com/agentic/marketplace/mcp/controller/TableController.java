package com.agentic.marketplace.mcp.controller;

import com.agentic.marketplace.mcp.model.TableDetails;
import com.agentic.marketplace.mcp.model.TableRequest;
import com.agentic.marketplace.mcp.service.DatabaseService;
import com.agentic.marketplace.sdk.model.AgentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tables")
@Slf4j
public class TableController {

    @Autowired
    private DatabaseService databaseService;

    @PostMapping
    public ResponseEntity<AgentResponse> createTable(@RequestBody TableRequest request) {
        try {
            log.info("Received request to create table: {}", request.getTableName());
            
            // Check if table already exists
            if (databaseService.tableExists(request.getTableName())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(AgentResponse.builder()
                                .success(false)
                                .message("Table already exists: " + request.getTableName())
                                .build());
            }
            
            databaseService.createTable(request);
            
            return ResponseEntity.ok(AgentResponse.builder()
                    .success(true)
                    .message("Table created successfully: " + request.getTableName())
                    .data(request)
                    .build());
        } catch (Exception e) {
            log.error("Error creating table", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AgentResponse.builder()
                            .success(false)
                            .message("Failed to create table")
                            .error(e.getMessage())
                            .build());
        }
    }

    @GetMapping
    public ResponseEntity<AgentResponse> listTables() {
        try {
            log.info("Received request to list tables");
            List<String> tables = databaseService.listTables();
            
            return ResponseEntity.ok(AgentResponse.builder()
                    .success(true)
                    .message("Found " + tables.size() + " table(s)")
                    .data(tables)
                    .build());
        } catch (Exception e) {
            log.error("Error listing tables", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AgentResponse.builder()
                            .success(false)
                            .message("Failed to list tables")
                            .error(e.getMessage())
                            .build());
        }
    }

    @GetMapping("/{tableName}")
    public ResponseEntity<AgentResponse> describeTable(@PathVariable("tableName") String tableName) {
        try {
            log.info("Received request to describe table: {}", tableName);
            
            if (!databaseService.tableExists(tableName)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(AgentResponse.builder()
                                .success(false)
                                .message("Table not found: " + tableName)
                                .build());
            }
            
            TableDetails details = databaseService.describeTable(tableName);
            
            return ResponseEntity.ok(AgentResponse.builder()
                    .success(true)
                    .message("Table details retrieved successfully")
                    .data(details)
                    .build());
        } catch (Exception e) {
            log.error("Error describing table", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AgentResponse.builder()
                            .success(false)
                            .message("Failed to describe table")
                            .error(e.getMessage())
                            .build());
        }
    }

    @DeleteMapping("/{tableName}")
    public ResponseEntity<AgentResponse> dropTable(@PathVariable("tableName") String tableName) {
        try {
            log.info("Received request to drop table: {}", tableName);
            
            if (!databaseService.tableExists(tableName)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(AgentResponse.builder()
                                .success(false)
                                .message("Table not found: " + tableName)
                                .build());
            }
            
            databaseService.dropTable(tableName);
            
            return ResponseEntity.ok(AgentResponse.builder()
                    .success(true)
                    .message("Table dropped successfully: " + tableName)
                    .build());
        } catch (Exception e) {
            log.error("Error dropping table", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AgentResponse.builder()
                            .success(false)
                            .message("Failed to drop table")
                            .error(e.getMessage())
                            .build());
        }
    }
}
