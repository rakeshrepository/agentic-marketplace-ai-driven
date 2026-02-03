package com.agentic.marketplace.registry.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AgentRegistrationResponse {
    private boolean success;
    private String message;
    private String agentId;
}
