package com.agentic.marketplace.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedIntent {
    private String action;  // create, list, delete, describe
    private String topicName;
    private Integer partitions;
    private Integer replicationFactor;
    private Map<String, String> config;
    
    // Note: Validation removed from LLM responsibility
    // LLM now focuses on intelligent extraction only
    // Validation logic moved to AgentOrchestrator
}
