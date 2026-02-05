package com.agentic.marketplace.sdk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationHistory {
    private String sessionId;
    private List<ConversationMessage> messages;
    private Map<String, Object> context; // Store suggestions, pending actions
    private LocalDateTime lastActivity;
    
    /**
     * Add a message to the conversation history
     */
    public void addMessage(String role, String content) {
        addMessage(role, content, null);
    }
    
    /**
     * Add a message with metadata to the conversation history
     */
    public void addMessage(String role, String content, Map<String, Object> metadata) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(ConversationMessage.builder()
            .role(role)
            .content(content)
            .timestamp(LocalDateTime.now())
            .metadata(metadata)
            .build());
        this.lastActivity = LocalDateTime.now();
    }
    
    /**
     * Update context with a key-value pair
     */
    public void updateContext(String key, Object value) {
        if (context == null) {
            context = new HashMap<>();
        }
        context.put(key, value);
    }
    
    /**
     * Get a value from context
     */
    public Object getContextValue(String key) {
        return context != null ? context.get(key) : null;
    }
    
    /**
     * Clear all context
     */
    public void clearContext() {
        if (context != null) {
            context.clear();
        }
    }
    
    /**
     * Get the number of messages in the conversation
     */
    public int getMessageCount() {
        return messages != null ? messages.size() : 0;
    }
}
