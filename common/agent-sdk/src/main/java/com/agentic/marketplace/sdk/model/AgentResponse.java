package com.agentic.marketplace.sdk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {
    private boolean success;
    private String message;
    private Object data;
    private String error;
    private String sessionId;  // For tracking conversation sessions
}
