package com.agentic.marketplace.mcp.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@Slf4j
public class HealthController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        try {
            // Test database connectivity
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            
            response.put("status", "UP");
            response.put("service", "database-mcp-server");
            response.put("database", "H2 (In-Memory)");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Health check failed", e);
            response.put("status", "DOWN");
            response.put("service", "database-mcp-server");
            response.put("error", e.getMessage());
            return ResponseEntity.status(503).body(response);
        }
    }
}
