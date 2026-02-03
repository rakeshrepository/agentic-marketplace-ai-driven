package com.agentic.marketplace.agent.controller;

import com.agentic.marketplace.sdk.model.AgentRequest;
import com.agentic.marketplace.sdk.model.AgentResponse;
import com.agentic.marketplace.agent.service.AgentOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AgentController {

    private final AgentOrchestrator agentOrchestrator;

    @PostMapping("/query")
    public ResponseEntity<AgentResponse> processQuery(@RequestBody AgentRequest request) {
        log.info("Received query: {}", request.getQuery());
        AgentResponse response = agentOrchestrator.processQuery(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<AgentResponse> health() {
        return ResponseEntity.ok(AgentResponse.builder()
                .success(true)
                .message("Database Management Agent is running")
                .build());
    }

    @GetMapping("/info")
    public ResponseEntity<AgentResponse> info() {
        return ResponseEntity.ok(AgentResponse.builder()
                .success(true)
                .message("Database Management Agent")
                .data(java.util.Map.of(
                        "id", "database-management-agent",
                        "name", "Database Management Agent",
                        "category", "infrastructure",
                        "capabilities", java.util.List.of("create-table", "list-tables", "drop-table", "describe-table")
                ))
                .build());
    }
}
