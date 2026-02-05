package com.agentic.marketplace.sdk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessage {
    private String role;        // "user" or "assistant"
    private String content;     // Message text
    private LocalDateTime timestamp;
    private Map<String, Object> metadata; // Optional: store suggestions, intent, etc.
}
