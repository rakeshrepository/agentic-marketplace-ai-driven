package com.agentic.marketplace.mcp.controller;

import com.agentic.marketplace.sdk.model.AgentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<AgentResponse> health() {
        return ResponseEntity.ok(AgentResponse.builder()
                .success(true)
                .message("Kafka MCP Server is running")
                .build());
    }
}
